package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"
)

// The server owns the single public Let's Encrypt certificate for hub.kiel.party and hands it to
// paired laptops over HTTPS, so a phone's browser trusts the hub with no internet and no setup on
// the phone, and any number of branch laptops can pair without hitting Let's Encrypt's weekly cap.

// Cloud is the part of the app's server the laptop talks to while online.
type Cloud struct {
	API    string // e.g. https://api.kiel.party
	Client *http.Client
}

// ErrNotSetUp is the cloud saying it holds no DNS token yet (an owner step), so no laptop can get
// a certificate.
var ErrNotSetUp = errors.New("the app's server has no DNS token yet, so it can't issue the hub certificate")

// FetchCert pulls the current hub.kiel.party certificate chain and private key from the server.
func (c *Cloud) FetchCert(ctx context.Context, token string) (certPEM, keyPEM string, err error) {
	body, _ := json.Marshal(map[string]string{"token": token})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.API+"/site-hub/cert", bytes.NewReader(body))
	if err != nil {
		return "", "", err
	}
	req.Header.Set("Content-Type", "application/json")
	res, err := c.client().Do(req)
	if err != nil {
		return "", "", err
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(res.Body, 1<<18))
	switch {
	case res.StatusCode == http.StatusServiceUnavailable:
		return "", "", ErrNotSetUp
	case res.StatusCode/100 != 2:
		var e struct {
			Error string `json:"error"`
		}
		msg := fmt.Sprintf("cloud answered %d: %s", res.StatusCode, bytes.TrimSpace(b))
		if json.Unmarshal(b, &e) == nil && e.Error != "" {
			msg = e.Error
		}
		return "", "", &httpError{Status: res.StatusCode, Msg: msg}
	}
	var out struct {
		CertPEM string `json:"certPem"`
		KeyPEM  string `json:"keyPem"`
	}
	if err := json.Unmarshal(b, &out); err != nil {
		return "", "", fmt.Errorf("bad certificate response: %w", err)
	}
	if out.CertPEM == "" || out.KeyPEM == "" {
		return "", "", errors.New("server returned an empty certificate")
	}
	return out.CertPEM, out.KeyPEM, nil
}

func (c *Cloud) client() *http.Client {
	if c.Client != nil {
		return c.Client
	}
	return &http.Client{Timeout: 2 * time.Minute}
}

// Certs holds the hub's certificate in Dir and refreshes it at two thirds of its life.
type Certs struct {
	Dir   string
	Name  string
	Cloud *Cloud
	// Token is the laptop's paired hub token.
	Token func() string

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

// DueAt is when the current certificate should be refreshed: two thirds into its life.
func DueAt(leaf *x509.Certificate) time.Time {
	life := leaf.NotAfter.Sub(leaf.NotBefore)
	return leaf.NotBefore.Add(life * 2 / 3)
}

// Due says whether a fresh certificate should be fetched now.
func (c *Certs) Due(now time.Time) bool {
	cur := c.current.Load()
	return cur == nil || cur.Leaf == nil || !now.Before(DueAt(cur.Leaf))
}

// Obtain fetches the server-managed certificate, validates it for c.Name, saves it and starts serving it.
func (c *Certs) Obtain(ctx context.Context) error {
	if err := os.MkdirAll(c.Dir, 0o700); err != nil {
		return err
	}
	certPEM, keyPEM, err := c.Cloud.FetchCert(ctx, c.Token())
	if err != nil {
		return err
	}
	pair, err := tls.X509KeyPair([]byte(certPEM), []byte(keyPEM))
	if err != nil {
		return fmt.Errorf("invalid certificate from server: %w", err)
	}
	leaf, err := x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		return fmt.Errorf("unreadable certificate from server: %w", err)
	}
	if c.Name != "" {
		if err := leaf.VerifyHostname(c.Name); err != nil {
			return fmt.Errorf("certificate does not match %s: %w", c.Name, err)
		}
	}
	if err := os.WriteFile(c.keyPath()+".new", []byte(keyPEM), 0o600); err != nil {
		return err
	}
	if err := os.WriteFile(c.crtPath()+".new", []byte(certPEM), 0o644); err != nil {
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
