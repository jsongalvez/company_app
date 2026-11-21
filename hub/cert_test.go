package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"sync"
	"testing"
	"time"
)

// Guards the hub certificate: Let's Encrypt allows only five certificates a week for one name, so
// the laptop renews at two thirds of a certificate's life, not on every start; and the DNS-01
// proof goes through the cloud with the laptop's token, which slides on every renewal.

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
		t.Fatal("renewing before two thirds wastes the weekly limit")
	}
	if !c.Due(nb.Add(60 * 24 * time.Hour)) {
		t.Fatal("not renewed at two thirds")
	}
}

func TestNotSetUpIsNamed(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "not set up", http.StatusServiceUnavailable)
	}))
	defer srv.Close()
	_, err := (&Cloud{API: srv.URL}).Challenge(context.Background(), http.MethodPut, "t", "v")
	if err != ErrNotSetUp {
		t.Fatalf("want ErrNotSetUp, got %v", err)
	}
}

// fakeCloud records the DNS-01 values the laptop asks for, as the app's server would publish them.
type fakeCloud struct {
	mu   sync.Mutex
	puts []string
	dels []string
}

func (f *fakeCloud) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	var in struct{ Token, Value string }
	_ = json.NewDecoder(r.Body).Decode(&in)
	f.mu.Lock()
	defer f.mu.Unlock()
	if in.Token != "laptop-token" && in.Token != "renewed" {
		http.Error(w, "bad token", http.StatusUnauthorized)
		return
	}
	if r.Method == http.MethodPut {
		f.puts = append(f.puts, in.Value)
		_ = json.NewEncoder(w).Encode(map[string]string{"token": "renewed"})
		return
	}
	f.dels = append(f.dels, in.Value)
	w.WriteHeader(http.StatusNoContent)
}

// Runs against Pebble, Let's Encrypt's test server (CI hub.yml starts it with
// PEBBLE_VA_ALWAYS_VALID=1): the whole order, proof, finalize and save path.
func TestObtainFromPebble(t *testing.T) {
	dir := os.Getenv("PEBBLE_DIRECTORY")
	if dir == "" {
		t.Skip("PEBBLE_DIRECTORY not set")
	}
	fc := &fakeCloud{}
	cloud := httptest.NewServer(fc)
	defer cloud.Close()
	token := "laptop-token"
	c := &Certs{
		Dir:       t.TempDir(),
		Name:      "hub.kiel.party",
		Directory: dir,
		// Pebble serves its directory with its own test CA.
		ACMEHTTP:  &http.Client{Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}},
		Cloud:     &Cloud{API: cloud.URL},
		Token:     func() string { return token },
		SaveToken: func(s string) { token = s },
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	if err := c.Obtain(ctx); err != nil {
		t.Fatal(err)
	}
	if cur := c.Current(); cur == nil || cur.Leaf.DNSNames[0] != "hub.kiel.party" {
		t.Fatalf("cert: %+v", cur)
	}
	if len(fc.puts) != 1 || len(fc.dels) != 1 || fc.puts[0] != fc.dels[0] || token != "renewed" {
		t.Fatalf("cloud calls puts=%v dels=%v token=%s", fc.puts, fc.dels, token)
	}
	again := &Certs{Dir: c.Dir}
	if err := again.Load(); err != nil || again.Due(time.Now()) {
		t.Fatalf("saved cert not reloaded: %v", err)
	}
}
