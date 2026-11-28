package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// The laptop program: one window that turns on the laptop's hotspot, answers DNS for
// hub.kiel.party on it, keeps the certificate fresh, keeps the laptop awake and runs the relay.
// Windows only: the site laptops run Windows. The Windows parts live in
// laptop_windows.go behind the platform interface so the rest is tested on Linux too.

const (
	hubName     = "hub.kiel.party"
	defaultAPI  = "https://api.kiel.party"
	defaultSSID = "Company Site Hub"
	// The address Windows gives its own end of the Mobile Hotspot.
	defaultHotspotIP = "192.168.137.1"
	// Half of the server's 10-try IP and account lockout window (RateLimiter / LoginFailures), so
	// one hub run cannot trip a 429 lockout on its own.
	maxPairAttempts = 5
	// Backoff bounds for failed certificate requests.
	certRetryInitial = time.Minute
	certRetryMax     = 30 * time.Minute
	certRetryRateLim = time.Hour
)

type httpError struct {
	Status int
	Msg    string
}

func (e *httpError) Error() string { return e.Msg }

type inputError struct {
	err error
}

func (e *inputError) Error() string { return e.err.Error() }
func (e *inputError) Unwrap() error { return e.err }

var errEmptyCredentials = errors.New("username and password are required")

// platform is what the laptop program needs from the operating system.
type platform interface {
	// KeepAwake stops the laptop from sleeping for the next minute or so; called every 30 s.
	KeepAwake() error
	Battery() *Battery
	// Hotspot turns the hotspot on with this name and password, or reports it on; the answer is
	// one line for the window and the report.
	Hotspot(ssid, pass string) (string, error)
	// SetupDone says whether the firewall rule and hosts line exist; Setup makes them, asking
	// Windows for admin rights once.
	SetupDone(exe string) bool
	Setup(exe string) error
}

// LaptopConfig is saved in the program's folder; nothing in it is a secret but the laptop token.
type LaptopConfig struct {
	API        string `json:"api"`
	Token      string `json:"token"`
	SSID       string `json:"ssid"`
	Passphrase string `json:"passphrase"`
}

func newPassphrase() string {
	const letters = "abcdefghjkmnpqrstuvwxyz23456789"
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	for i := range b {
		b[i] = letters[int(b[i])%len(letters)]
	}
	return string(b[:4]) + "-" + string(b[4:8]) + "-" + string(b[8:])
}

type laptop struct {
	dir  string
	os   platform
	in   *bufio.Reader
	out  io.Writer
	cfg  LaptopConfig
	logf func(event string, fields map[string]any)

	mu       sync.Mutex
	status   map[string]string
	relayErr error
	serving  bool
}

func (l *laptop) set(k, v string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.status[k] != v {
		l.logf("status", map[string]any{"part": k, "now": v})
	}
	l.status[k] = v
}

func (l *laptop) save() error {
	b, _ := json.MarshalIndent(l.cfg, "", "  ")
	return os.WriteFile(filepath.Join(l.dir, "config.json"), b, 0o600)
}

func (l *laptop) load() {
	if b, err := os.ReadFile(filepath.Join(l.dir, "config.json")); err == nil {
		_ = json.Unmarshal(b, &l.cfg)
	}
	if l.cfg.API == "" {
		l.cfg.API = defaultAPI
	}
	if l.cfg.SSID == "" {
		l.cfg.SSID = defaultSSID
	}
	if l.cfg.Passphrase == "" {
		l.cfg.Passphrase = newPassphrase()
	}
}

func (l *laptop) ask(prompt string) (string, error) {
	fmt.Fprint(l.out, prompt)
	s, err := l.in.ReadString('\n')
	s = strings.TrimSpace(s)
	if err != nil && (err != io.EOF || s == "") {
		return "", err
	}
	return s, nil
}

// pair signs an active staff member in once and trades the sign-in for a revocable laptop token.
func (l *laptop) pair(ctx context.Context, readPassword func() (string, error)) error {
	fmt.Fprintln(l.out, "\nFirst setup: sign in once with your staff account (this laptop needs internet now).")
	user, err := l.ask("Username: ")
	if err != nil {
		return &inputError{err: err}
	}
	fmt.Fprint(l.out, "Password: ")
	pass, err := readPassword()
	fmt.Fprintln(l.out)
	if err != nil {
		return &inputError{err: err}
	}
	if user == "" || pass == "" {
		return errEmptyCredentials
	}
	var login struct {
		Token string `json:"token"`
	}
	if err := postJSON(ctx, l.cfg.API+"/auth/login", "", map[string]string{"username": user, "password": pass}, &login); err != nil {
		return fmt.Errorf("sign-in: %w", err)
	}
	host, _ := os.Hostname()
	var paired struct {
		Token string `json:"token"`
	}
	if err := postJSON(ctx, l.cfg.API+"/api/site-hub/laptops", login.Token, map[string]string{"name": host}, &paired); err != nil {
		return fmt.Errorf("pairing: %w", err)
	}
	l.cfg.Token = paired.Token
	return l.save()
}

func (l *laptop) pairLoop(ctx context.Context, readPassword func() (string, error)) error {
	for attempt := 1; attempt <= maxPairAttempts; attempt++ {
		if err := ctx.Err(); err != nil {
			return err
		}
		err := l.pair(ctx, readPassword)
		if err == nil {
			l.logf("paired", map[string]any{"attempt": attempt})
			return nil
		}
		l.logf("pair_failed", map[string]any{"attempt": attempt, "error": err.Error()})
		fmt.Fprintln(l.out, "Could not sign in:", err)
		var inErr *inputError
		if errors.As(err, &inErr) {
			return fmt.Errorf("sign-in aborted (no console input): %w", inErr.err)
		}
		var he *httpError
		if errors.As(err, &he) && he.Status == http.StatusTooManyRequests {
			return err
		}
	}
	return fmt.Errorf("sign-in stopped after %d failed attempts", maxPairAttempts)
}

func isUnauthorized(err error) bool {
	var he *httpError
	return errors.As(err, &he) && he.Status == http.StatusUnauthorized
}

func nextCertWait(prev time.Duration, err error) time.Duration {
	var he *httpError
	if errors.As(err, &he) && he.Status == http.StatusTooManyRequests {
		return certRetryRateLim
	}
	if prev <= 0 {
		return certRetryInitial
	}
	if next := prev * 2; next < certRetryMax {
		return next
	}
	return certRetryMax
}

func postJSON(ctx context.Context, url, bearer string, in, out any) error {
	b, _ := json.Marshal(in)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(b))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}
	res, err := (&http.Client{Timeout: 30 * time.Second}).Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(res.Body, 1<<16))
	if res.StatusCode/100 != 2 {
		var e struct {
			Error string `json:"error"`
		}
		msg := fmt.Sprintf("server answered %d", res.StatusCode)
		if json.Unmarshal(body, &e) == nil && e.Error != "" {
			msg = e.Error
		}
		return &httpError{Status: res.StatusCode, Msg: msg}
	}
	return json.Unmarshal(body, out)
}

// hotspotIP finds the laptop's address on its hotspot: Windows' default first, else any private
// IPv4 on an adapter Windows names "Local Area Connection*".
func hotspotIP() net.IP {
	ifs, _ := net.Interfaces()
	var fallback net.IP
	for _, it := range ifs {
		addrs, _ := it.Addrs()
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok || ipn.IP.To4() == nil {
				continue
			}
			if ipn.IP.String() == defaultHotspotIP {
				return ipn.IP
			}
			if strings.HasPrefix(it.Name, "Local Area Connection*") && ipn.IP.IsPrivate() {
				fallback = ipn.IP
			}
		}
	}
	return fallback
}

// checkNames asks whoever answers DNS on the hotspot address where hub.kiel.party is: the probe
// P3 result the owner sends back.
func checkNames(ip net.IP) string {
	r := &net.Resolver{PreferGo: true, Dial: func(ctx context.Context, _, _ string) (net.Conn, error) {
		return (&net.Dialer{Timeout: 2 * time.Second}).DialContext(ctx, "udp", net.JoinHostPort(ip.String(), "53"))
	}}
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()
	got, err := r.LookupHost(ctx, hubName)
	hub := "no answer"
	if err == nil {
		hub = strings.Join(got, ",")
	}
	_, err = r.LookupHost(ctx, "mask.icloud.com")
	relay := "answered (Private Relay stays on)"
	var de *net.DNSError
	if errors.As(err, &de) && de.IsNotFound {
		relay = "no such name (Private Relay off on this Wi-Fi)"
	} else if err != nil {
		relay = "no answer"
	}
	return fmt.Sprintf("%s -> %s; mask.icloud.com -> %s", hubName, hub, relay)
}

// run is the laptop program's main loop; it returns only when ctx ends.
func (l *laptop) run(ctx context.Context, exe string, readPassword func() (string, error)) error {
	l.load()
	if err := l.save(); err != nil {
		return err
	}
	l.set("Wi-Fi", fmt.Sprintf("%q, password %s", l.cfg.SSID, l.cfg.Passphrase))
	if !l.os.SetupDone(exe) {
		fmt.Fprintln(l.out, "Windows will ask once to let the site hub change settings: firewall and hub.kiel.party. Click Yes.")
		if err := l.os.Setup(exe); err != nil {
			l.set("Setup", "not done: "+err.Error())
		}
	}
	if l.os.SetupDone(exe) {
		l.set("Setup", "firewall rule and hosts line in place")
	}
	certs := &Certs{
		Dir:   l.dir,
		Name:  hubName,
		Cloud: &Cloud{API: l.cfg.API},
		Token: func() string { return l.cfg.Token },
	}
	_ = certs.Load()
	if certs.Current() == nil && l.cfg.Token == "" {
		if err := l.pairLoop(ctx, readPassword); err != nil {
			return err
		}
	}
	relay := NewRelay(l.logf)
	relay.battery = l.os.Battery
	names := &Names{Hub: hubName, Log: l.logf}
	var stopNames func()
	var boundIP string
	var nextCertTry time.Time
	var certWait time.Duration

	tick := time.NewTicker(30 * time.Second)
	defer tick.Stop()
	for {
		if err := l.os.KeepAwake(); err != nil {
			l.set("Awake", "can't keep the laptop awake: "+err.Error())
		} else {
			l.set("Awake", "kept awake while this window is open (keep the lid open)")
		}
		if b := l.os.Battery(); b != nil {
			s := fmt.Sprintf("%d%%", b.Percent)
			if b.Charging {
				s += ", charging"
			} else if b.Percent <= 15 {
				s += ", LOW: plug in the laptop"
			}
			l.set("Battery", s)
		}
		if line, err := l.os.Hotspot(l.cfg.SSID, l.cfg.Passphrase); err != nil {
			l.set("Hotspot", "off: "+err.Error())
		} else {
			l.set("Hotspot", line)
		}
		ip := hotspotIP()
		switch {
		case ip == nil:
			l.set("Names", "waiting for the hotspot's address")
		case boundIP != ip.String():
			if stopNames != nil {
				stopNames()
				stopNames = nil
			}
			names.IP = ip
			names.Upstream = []string{"1.1.1.1:53", "8.8.8.8:53"}
			if _, stop, err := names.Listen(net.JoinHostPort(ip.String(), "53")); err == nil {
				stopNames, boundIP = stop, ip.String()
				l.set("Names", "answered by the site hub on "+ip.String())
			} else {
				l.set("Names", "port 53 taken ("+err.Error()+"); Windows answers from the hosts file")
			}
		}
		if ip != nil {
			l.set("Name check", checkNames(ip))
		}
		now := time.Now()
		if certs.Due(now) && !now.Before(nextCertTry) {
			cctx, cancel := context.WithTimeout(ctx, 3*time.Minute)
			err := certs.Obtain(cctx)
			cancel()
			if isUnauthorized(err) {
				l.logf("token_rejected", map[string]any{"error": err.Error()})
				l.cfg.Token = ""
				_ = l.save()
				certWait = 0
				nextCertTry = time.Time{}
				fmt.Fprintln(l.out, "\nThis laptop's pairing is no longer active. Sign in again to re-pair.")
				if perr := l.pairLoop(ctx, readPassword); perr != nil {
					if certs.Current() == nil {
						return perr
					}
					certWait = nextCertWait(certWait, perr)
					nextCertTry = time.Now().Add(certWait)
				} else {
					rctx, rcancel := context.WithTimeout(ctx, 3*time.Minute)
					err = certs.Obtain(rctx)
					rcancel()
				}
			}
			if err != nil {
				certWait = nextCertWait(certWait, err)
				nextCertTry = time.Now().Add(certWait)
				l.logf("cert_failed", map[string]any{"error": err.Error(), "retryIn": certWait.String()})
				if certs.Current() == nil {
					l.set("Certificate", "none yet: "+err.Error())
				}
			} else {
				certWait = 0
				nextCertTry = time.Time{}
			}
		}
		if cur := certs.Current(); cur != nil {
			l.set("Certificate", fmt.Sprintf("%s until %s", hubName, cur.Leaf.NotAfter.Format("2 Jan 2006")))
			if !l.serving {
				l.serving = true
				go l.serve(relay, certs)
			}
		}
		l.show(relay, names)
		select {
		case <-ctx.Done():
			return nil
		case <-tick.C:
		}
	}
}

func (l *laptop) serve(relay *Relay, certs *Certs) {
	srv := &http.Server{
		Addr:              ":443",
		Handler:           relay.Handler([]string{"https://api.kiel.party", "https://app.kiel.party"}),
		TLSConfig:         &tls.Config{GetCertificate: certs.GetCertificate, MinVersion: tls.VersionTLS12},
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
	}
	err := srv.ListenAndServeTLS("", "")
	l.mu.Lock()
	l.relayErr = err
	l.mu.Unlock()
}

var statusOrder = []string{"Hotspot", "Wi-Fi", "Names", "Name check", "Certificate", "Relay", "Phones", "Battery", "Awake", "Setup"}

// show prints the status block and writes it to report.txt, the file the owner sends back.
func (l *laptop) show(relay *Relay, names *Names) {
	phones, records := relay.Counts()
	l.mu.Lock()
	relayErr := l.relayErr
	l.mu.Unlock()
	if relayErr != nil {
		l.set("Relay", "stopped: "+relayErr.Error())
	} else if l.serving {
		l.set("Relay", fmt.Sprintf("https://%s, %d records", hubName, records))
	}
	l.set("Phones", fmt.Sprintf("%d seen in the last 2 minutes; DNS answers %v", phones, names.Counts()))
	l.mu.Lock()
	var b strings.Builder
	fmt.Fprintf(&b, "Company site hub  %s\n", time.Now().Format("2 Jan 15:04:05"))
	for _, k := range statusOrder {
		if v, ok := l.status[k]; ok {
			fmt.Fprintf(&b, "  %-12s %s\n", k+":", v)
		}
	}
	l.mu.Unlock()
	fmt.Fprintf(&b, "  Keep this window open. Send back: %s\n", filepath.Join(l.dir, "report.txt"))
	fmt.Fprint(l.out, "\n"+b.String())
	_ = os.WriteFile(filepath.Join(l.dir, "report.txt"), []byte(b.String()), 0o644)
}
