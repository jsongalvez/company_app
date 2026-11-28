package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"errors"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// Guards the hub certificate cache: laptops refresh from the server at two thirds of the
// certificate's life, validate the hostname before writing to disk, and surface 401/503 clearly.

func TestRenewsAtTwoThirds(t *testing.T) {
	nb := time.Date(2027, 3, 1, 0, 0, 0, 0, time.UTC)
	leaf := &x509.Certificate{NotBefore: nb, NotAfter: nb.Add(90 * 24 * time.Hour)}
	if got := DueAt(leaf); !got.Equal(nb.Add(60 * 24 * time.Hour)) {
		t.Fatalf("90-day cert due %v", got)
	}
	c := &Certs{}
	if !c.Due(nb) {
		t.Fatal("no certificate must be due")
	}
	c.current.Store(&tls.Certificate{Leaf: leaf})
	if c.Due(nb.Add(59 * 24 * time.Hour)) {
		t.Fatal("refreshing before two thirds is unnecessary")
	}
	if !c.Due(nb.Add(60 * 24 * time.Hour)) {
		t.Fatal("not refreshed at two thirds")
	}
}

func TestNotSetUpIsNamed(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "not set up", http.StatusServiceUnavailable)
	}))
	defer srv.Close()
	_, _, err := (&Cloud{API: srv.URL}).FetchCert(context.Background(), "hl2.token")
	if err != ErrNotSetUp {
		t.Fatalf("want ErrNotSetUp, got %v", err)
	}
}

func testCertPair(t *testing.T, dnsName string) (certPEM, keyPEM string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	nb := time.Now().Add(-time.Hour)
	na := nb.Add(90 * 24 * time.Hour)
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: dnsName},
		DNSNames:     []string{dnsName},
		NotBefore:    nb,
		NotAfter:     na,
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	pkcs8, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	certPEM = string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	keyPEM = string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: pkcs8}))
	return certPEM, keyPEM
}

func TestObtainFromCloudAndReload(t *testing.T) {
	certPEM, keyPEM := testCertPair(t, "hub.kiel.party")
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/site-hub/cert" || r.Method != http.MethodPost {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		var in struct{ Token string }
		_ = json.NewDecoder(r.Body).Decode(&in)
		if in.Token != "hl2.good" {
			w.WriteHeader(http.StatusUnauthorized)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "This laptop isn't paired."})
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]string{
			"certPem": certPEM,
			"keyPem":  keyPEM,
		})
	}))
	defer srv.Close()

	c := &Certs{
		Dir:   t.TempDir(),
		Name:  "hub.kiel.party",
		Cloud: &Cloud{API: srv.URL},
		Token: func() string { return "hl2.good" },
	}
	if err := c.Obtain(context.Background()); err != nil {
		t.Fatal(err)
	}
	if cur := c.Current(); cur == nil || cur.Leaf.DNSNames[0] != "hub.kiel.party" {
		t.Fatalf("cert: %+v", cur)
	}
	again := &Certs{Dir: c.Dir}
	if err := again.Load(); err != nil || again.Due(time.Now()) {
		t.Fatalf("saved cert not reloaded: %v", err)
	}

	bad := &Certs{
		Dir:   t.TempDir(),
		Name:  "hub.kiel.party",
		Cloud: &Cloud{API: srv.URL},
		Token: func() string { return "hl2.revoked" },
	}
	err := bad.Obtain(context.Background())
	var he *httpError
	if !errors.As(err, &he) || he.Status != http.StatusUnauthorized {
		t.Fatalf("expected 401 httpError, got %v", err)
	}
}
