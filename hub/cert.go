package main

import (
	"bytes"
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"

	"golang.org/x/crypto/acme"
)

// The laptop gets its own public certificate for hub.kiel.party from Let's Encrypt, so a phone's
// browser trusts the hub with no internet and no setup on the phone. The private key never leaves
// the laptop. Proof of the name is DNS-01: the cloud, which holds the DNS token, writes the
// challenge record for a laptop that shows its hub token (paired once by an Owner or Manager).

const letsEncrypt = "https://acme-v02.api.letsencrypt.org/directory"

// Cloud is the part of the app's server the laptop talks to while online.
type Cloud struct {
	API    string // e.g. https://api.kiel.party
	Client *http.Client
}

// ErrNotSetUp is the cloud saying it holds no DNS token yet (an owner step), so no laptop can get
// a certificate.
var ErrNotSetUp = errors.New("the app's server has no DNS token yet, so it can't prove the name")

// Challenge asks the cloud to publish (put) or delete the DNS-01 value. A put answers with a
// fresh laptop token, so a laptop that renews on time never has to be paired again.
func (c *Cloud) Challenge(ctx context.Context, method, token, value string) (string, error) {
	body, _ := json.Marshal(map[string]string{"token": token, "value": value})
	req, err := http.NewRequestWithContext(ctx, method, c.API+"/site-hub/challenge", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	res, err := c.client().Do(req)
	if err != nil {
		return "", err
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(res.Body, 1<<16))
	switch {
	case res.StatusCode == http.StatusServiceUnavailable:
		return "", ErrNotSetUp
	case res.StatusCode/100 != 2:
		return "", &httpError{
			Status: res.StatusCode,
			Msg:    fmt.Sprintf("cloud answered %d: %s", res.StatusCode, bytes.TrimSpace(b)),
		}
	}
	var out struct {
		Token string `json:"token"`
	}
	_ = json.Unmarshal(b, &out)
	return out.Token, nil
}

func (c *Cloud) client() *http.Client {
	if c.Client != nil {
		return c.Client
	}
	return &http.Client{Timeout: 30 * time.Second}
}

// Certs holds the hub's certificate in Dir and renews it at two thirds of its life.
type Certs struct {
	Dir       string
	Name      string
	Directory string // ACME directory; Let's Encrypt unless a test says otherwise
	ACMEHTTP  *http.Client
	Cloud     *Cloud
	// Token is the laptop's hub token; SaveToken keeps the renewed one.
	Token     func() string
	SaveToken func(string)
	// Wait is how long the DNS record gets to reach Cloudflare's servers before Let's Encrypt looks.
	Wait time.Duration

	current atomic.Pointer[tls.Certificate]
}

func (c *Certs) crtPath() string { return filepath.Join(c.Dir, "hub.crt") }
func (c *Certs) keyPath() string { return filepath.Join(c.Dir, "hub.key") }

// Load reads the saved certificate, if any.
func (c *Certs) Load() error {
	pair, err := tls.LoadX509KeyPair(c.crtPath(), c.keyPath())
	if err != nil {
		return err
	}
	leaf, err := x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		return err
	}
	pair.Leaf = leaf
	c.current.Store(&pair)
	return nil
}

// Current is the certificate served now, or nil.
func (c *Certs) Current() *tls.Certificate { return c.current.Load() }

// GetCertificate serves the current certificate to every TLS handshake.
func (c *Certs) GetCertificate(*tls.ClientHelloInfo) (*tls.Certificate, error) {
	if cur := c.current.Load(); cur != nil {
		return cur, nil
	}
	return nil, errors.New("no certificate yet")
}

// DueAt is when the current certificate should be renewed: two thirds into its life.
func DueAt(leaf *x509.Certificate) time.Time {
	life := leaf.NotAfter.Sub(leaf.NotBefore)
	return leaf.NotBefore.Add(life * 2 / 3)
}

// Due says whether a new certificate is needed now.
func (c *Certs) Due(now time.Time) bool {
	cur := c.current.Load()
	return cur == nil || cur.Leaf == nil || !now.Before(DueAt(cur.Leaf))
}

func (c *Certs) accountKey() (crypto.Signer, error) {
	p := filepath.Join(c.Dir, "acme-account.key")
	if b, err := os.ReadFile(p); err == nil {
		if blk, _ := pem.Decode(b); blk != nil {
			return x509.ParseECPrivateKey(blk.Bytes)
		}
	}
	k, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}
	der, _ := x509.MarshalECPrivateKey(k)
	return k, os.WriteFile(p, pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: der}), 0o600)
}

// Obtain gets a new certificate from the ACME directory and starts serving it.
func (c *Certs) Obtain(ctx context.Context) error {
	if err := os.MkdirAll(c.Dir, 0o700); err != nil {
		return err
	}
	akey, err := c.accountKey()
	if err != nil {
		return err
	}
	dir := c.Directory
	if dir == "" {
		dir = letsEncrypt
	}
	cl := &acme.Client{Key: akey, DirectoryURL: dir, HTTPClient: c.ACMEHTTP}
	if _, err := cl.Register(ctx, &acme.Account{}, acme.AcceptTOS); err != nil && !errors.Is(err, acme.ErrAccountAlreadyExists) {
		return fmt.Errorf("acme account: %w", err)
	}
	order, err := cl.AuthorizeOrder(ctx, acme.DomainIDs(c.Name))
	if err != nil {
		return fmt.Errorf("acme order: %w", err)
	}
	for _, u := range order.AuthzURLs {
		if err := c.prove(ctx, cl, u); err != nil {
			return err
		}
	}
	if order, err = cl.WaitOrder(ctx, order.URI); err != nil {
		return fmt.Errorf("acme order wait: %w", err)
	}
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return err
	}
	csr, err := x509.CreateCertificateRequest(rand.Reader, &x509.CertificateRequest{DNSNames: []string{c.Name}}, key)
	if err != nil {
		return err
	}
	chain, _, err := cl.CreateCertFromOrder(ctx, order, csr, true)
	if err != nil {
		return fmt.Errorf("acme finalize: %w", err)
	}
	var crt bytes.Buffer
	for _, der := range chain {
		_ = pem.Encode(&crt, &pem.Block{Type: "CERTIFICATE", Bytes: der})
	}
	kder, _ := x509.MarshalECPrivateKey(key)
	if err := os.WriteFile(c.keyPath()+".new", pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: kder}), 0o600); err != nil {
		return err
	}
	if err := os.WriteFile(c.crtPath()+".new", crt.Bytes(), 0o644); err != nil {
		return err
	}
	if err := os.Rename(c.keyPath()+".new", c.keyPath()); err != nil {
		return err
	}
	if err := os.Rename(c.crtPath()+".new", c.crtPath()); err != nil {
		return err
	}
	return c.Load()
}

func (c *Certs) prove(ctx context.Context, cl *acme.Client, authzURL string) error {
	z, err := cl.GetAuthorization(ctx, authzURL)
	if err != nil {
		return err
	}
	if z.Status == acme.StatusValid {
		return nil
	}
	var ch *acme.Challenge
	for _, x := range z.Challenges {
		if x.Type == "dns-01" {
			ch = x
		}
	}
	if ch == nil {
		return errors.New("acme offered no dns-01 challenge")
	}
	value, err := cl.DNS01ChallengeRecord(ch.Token)
	if err != nil {
		return err
	}
	renewed, err := c.Cloud.Challenge(ctx, http.MethodPut, c.Token(), value)
	if err != nil {
		return err
	}
	if renewed != "" && c.SaveToken != nil {
		c.SaveToken(renewed)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		_, _ = c.Cloud.Challenge(ctx, http.MethodDelete, c.Token(), value)
	}()
	select {
	case <-time.After(c.Wait):
	case <-ctx.Done():
		return ctx.Err()
	}
	if _, err := cl.Accept(ctx, ch); err != nil {
		return fmt.Errorf("acme accept: %w", err)
	}
	if _, err := cl.WaitAuthorization(ctx, z.URI); err != nil {
		return fmt.Errorf("acme proof refused: %w", err)
	}
	return nil
}
