package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// Guards the laptop pairing and renewal loops against unbounded retries: closed or non-console
// input must abort without sending login calls, HTTP 429 must stop immediately, repeated bad
// logins stay strictly below the server's 10-try lockout, and failed cert renewals back off.

type stubPlatform struct{}

func (stubPlatform) KeepAwake() error                       { return nil }
func (stubPlatform) Battery() *Battery                      { return nil }
func (stubPlatform) Hotspot(string, string) (string, error) { return "On", nil }
func (stubPlatform) SetupDone(string) bool                  { return true }
func (stubPlatform) Setup(string) error                     { return nil }

func newTestLaptop(t *testing.T, api, input string) (*laptop, *bytes.Buffer) {
	t.Helper()
	var out bytes.Buffer
	l := &laptop{
		dir:    t.TempDir(),
		os:     stubPlatform{},
		in:     bufio.NewReader(strings.NewReader(input)),
		out:    &out,
		cfg:    LaptopConfig{API: api},
		status: map[string]string{},
		logf:   quiet,
	}
	if err := l.save(); err != nil {
		t.Fatal(err)
	}
	return l, &out
}

func TestEOFStdinDoesNotHammerLogin(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, "")
	err := l.run(context.Background(), "company-hub.exe", func() (string, error) { return "", nil })
	if err == nil {
		t.Fatal("expected error on closed stdin, got nil")
	}
	if got := hits.Load(); got != 0 {
		t.Fatalf("expected 0 /auth/login requests on EOF stdin, got %d", got)
	}
}

func TestPasswordReadErrorDoesNotHammerLogin(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, "twisty\n")
	err := l.pairLoop(context.Background(), func() (string, error) {
		return "", errors.New("The handle is invalid")
	})
	if err == nil {
		t.Fatal("expected error when ReadPassword fails, got nil")
	}
	if got := hits.Load(); got != 0 {
		t.Fatalf("expected 0 /auth/login requests on ReadPassword error, got %d", got)
	}
}

func TestEmptyCredentialsNeverPostedAndBounded(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, strings.Repeat("\n", 20))
	err := l.pairLoop(context.Background(), func() (string, error) { return "", nil })
	if err == nil {
		t.Fatal("expected error on repeated empty credentials, got nil")
	}
	if got := hits.Load(); got != 0 {
		t.Fatalf("expected 0 /auth/login requests on blank credentials, got %d", got)
	}
}

func TestPairStopsImmediatelyOn429(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusTooManyRequests)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "Too many tries. Wait a minute and try again."})
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, strings.Repeat("twisty\n", 10))
	err := l.pairLoop(context.Background(), func() (string, error) { return "secret", nil })
	if err == nil {
		t.Fatal("expected error on 429, got nil")
	}
	if got := hits.Load(); got != 1 {
		t.Fatalf("expected exactly 1 request before stopping on 429, got %d", got)
	}
}

func TestPairCapsWrongPasswordsBelowLockout(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "Wrong username or password."})
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, strings.Repeat("twisty\n", 20))
	err := l.pairLoop(context.Background(), func() (string, error) { return "wrong", nil })
	if err == nil {
		t.Fatal("expected error after max failed attempts, got nil")
	}
	if got := hits.Load(); got != maxPairAttempts {
		t.Fatalf("expected %d attempts, got %d", maxPairAttempts, got)
	}
}

func TestPairSucceedsAfterRetry(t *testing.T) {
	var loginHits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/auth/login":
			if loginHits.Add(1) == 1 {
				w.WriteHeader(http.StatusUnauthorized)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": "Wrong username or password."})
				return
			}
			_ = json.NewEncoder(w).Encode(map[string]string{"token": "jwt-ok"})
		case "/api/site-hub/laptops":
			if r.Header.Get("Authorization") != "Bearer jwt-ok" {
				w.WriteHeader(http.StatusUnauthorized)
				return
			}
			_ = json.NewEncoder(w).Encode(map[string]string{"token": "laptop-tok-1"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, "twisty\ntwisty\n")
	passwords := []string{"typo", "right"}
	idx := 0
	err := l.pairLoop(context.Background(), func() (string, error) {
		p := passwords[idx]
		idx++
		return p, nil
	})
	if err != nil {
		t.Fatalf("expected pairing to succeed on second try, got %v", err)
	}
	if l.cfg.Token != "laptop-tok-1" {
		t.Fatalf("token not saved: %q", l.cfg.Token)
	}
}

func TestCertRetryBackoff(t *testing.T) {
	plain := errors.New("network down")
	w := nextCertWait(0, plain)
	if w != certRetryInitial {
		t.Fatalf("first wait: got %v want %v", w, certRetryInitial)
	}
	w = nextCertWait(w, plain)
	if w != 2*certRetryInitial {
		t.Fatalf("second wait: got %v want %v", w, 2*certRetryInitial)
	}
	if got := nextCertWait(20*time.Minute, plain); got != certRetryMax {
		t.Fatalf("capped wait: got %v want %v", got, certRetryMax)
	}
	rateLimited := &httpError{Status: http.StatusTooManyRequests, Msg: "too many"}
	if got := nextCertWait(0, rateLimited); got != certRetryRateLim {
		t.Fatalf("429 wait: got %v want %v", got, certRetryRateLim)
	}
}

func TestPromptMentionsStaffAccountNotOwnerOnly(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/auth/login":
			_ = json.NewEncoder(w).Encode(map[string]string{"token": "jwt-ok"})
		case "/api/site-hub/laptops":
			_ = json.NewEncoder(w).Encode(map[string]string{"id": "1", "token": "hl2.staff"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	l, out := newTestLaptop(t, srv.URL, "staff1\n")
	if err := l.pair(context.Background(), func() (string, error) { return "pass", nil }); err != nil {
		t.Fatalf("pair failed: %v", err)
	}
	text := out.String()
	if strings.Contains(text, "Owner or Manager") {
		t.Fatalf("prompt still restricts to Owner or Manager: %q", text)
	}
	if !strings.Contains(text, "staff account") {
		t.Fatalf("expected staff account wording in prompt, got: %q", text)
	}
}

func TestRevokedTokenClearsAndRepairs(t *testing.T) {
	certPEM, keyPEM := testCertPair(t, "hub.kiel.party")
	var certAttempts atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/site-hub/cert":
			var in struct{ Token string }
			_ = json.NewDecoder(r.Body).Decode(&in)
			certAttempts.Add(1)
			if in.Token == "hl2.revoked" {
				w.WriteHeader(http.StatusUnauthorized)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": "This laptop isn't paired."})
				return
			}
			if in.Token == "hl2.fresh" {
				_ = json.NewEncoder(w).Encode(map[string]string{"certPem": certPEM, "keyPem": keyPEM})
				return
			}
			w.WriteHeader(http.StatusUnauthorized)
		case "/auth/login":
			_ = json.NewEncoder(w).Encode(map[string]string{"token": "jwt-ok"})
		case "/api/site-hub/laptops":
			_ = json.NewEncoder(w).Encode(map[string]string{"id": "2", "token": "hl2.fresh"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	l, _ := newTestLaptop(t, srv.URL, "staff2\n")
	l.cfg.Token = "hl2.revoked"
	if err := l.save(); err != nil {
		t.Fatal(err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		for certAttempts.Load() < 2 {
			time.Sleep(10 * time.Millisecond)
		}
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()

	err := l.run(ctx, "company-hub.exe", func() (string, error) { return "secret", nil })
	if err != nil {
		t.Fatalf("run returned error: %v", err)
	}
	if l.cfg.Token != "hl2.fresh" {
		t.Fatalf("expected re-paired token hl2.fresh, got %q", l.cfg.Token)
	}
	if certAttempts.Load() < 2 {
		t.Fatalf("expected revoked attempt then fresh fetch, got %d", certAttempts.Load())
	}
}

func TestCheckNamesQueriesUDPDirectly(t *testing.T) {
	d := &Names{Hub: hubName, IP: net.IPv4(192, 168, 137, 1)}
	addr, stop, err := d.Listen("127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer stop()

	got := checkNamesAt(addr)
	if !strings.Contains(got, "hub.kiel.party -> 192.168.137.1") || !strings.Contains(got, "Private Relay off") {
		t.Fatalf("unexpected checkNamesAt output: %q", got)
	}
	if d.Counts()["hub"] != 1 {
		t.Fatalf("expected hub DNS query to hit UDP listener once, got %v", d.Counts())
	}
}

func TestServeLocalAnswersWithPrivateNetworkCORS(t *testing.T) {
	l, _ := newTestLaptop(t, "https://api.kiel.party", "")
	relay := NewRelay(quiet)
	addr, stop, err := l.serveLocal("127.0.0.1:0", relay)
	if err != nil {
		t.Fatal(err)
	}
	defer stop()

	req, err := http.NewRequest(http.MethodGet, "http://"+addr+"/health", nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Origin", "https://api.kiel.party")
	res, err := (&http.Client{Timeout: 3 * time.Second}).Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status %d", res.StatusCode)
	}
	if res.Header.Get("Access-Control-Allow-Private-Network") != "true" {
		t.Fatalf("missing private-network header: %v", res.Header)
	}
	if res.Header.Get("Access-Control-Allow-Origin") != "https://api.kiel.party" {
		t.Fatalf("missing CORS origin: %v", res.Header)
	}
}
