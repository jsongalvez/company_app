// Command hub is the site hub: phones on the laptop's hotspot mirror their unsent saves here,
// sealed, and read each other's (docs: hub/README.md). On Windows with no arguments it is the
// laptop program (laptop.go); with flags it is the bare relay, as CI's container runs it.
package main

import (
	"bufio"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"time"
)

func jsonLog(w func([]byte)) func(string, map[string]any) {
	return func(event string, fields map[string]any) {
		if fields == nil {
			fields = map[string]any{}
		}
		fields["event"] = event
		fields["at"] = time.Now().UTC().Format(time.RFC3339)
		b, _ := json.Marshal(fields)
		w(append(b, '\n'))
	}
}

func main() {
	if runtime.GOOS == "windows" && len(os.Args) == 1 {
		os.Exit(laptopMain())
	}
	addr := flag.String("addr", ":443", "address to listen on")
	cert := flag.String("cert", "/certs/hub.crt", "TLS certificate (PEM)")
	key := flag.String("key", "/certs/hub.key", "TLS key (PEM)")
	origins := flag.String("origins", "https://api.kiel.party", "app origins allowed to call, comma-separated")
	battery := flag.Int("battery", -1, "report this battery percent to phones (tests of the low-battery line)")
	setup := flag.Bool("setup", false, "Windows: add the firewall rule and hosts line (run with admin rights)")
	flag.Parse()
	if *setup {
		if err := setupElevated(); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		return
	}

	logLine := jsonLog(func(b []byte) { os.Stdout.Write(b) })
	relay := NewRelay(logLine)
	if *battery >= 0 {
		relay.battery = func() *Battery { return &Battery{Percent: *battery} }
	}
	logLine("start", map[string]any{"addr": *addr, "boot": relay.boot})
	srv := &http.Server{
		Addr:              *addr,
		Handler:           relay.Handler(splitList(*origins)),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
	}
	log.Fatal(srv.ListenAndServeTLS(*cert, *key))
}

// stateDir is where the laptop program keeps its config, certificate, log and report.
func stateDir() string {
	base, err := os.UserConfigDir()
	if err != nil {
		base = os.TempDir()
	}
	return filepath.Join(base, "CompanyHub")
}

func runLaptopWith(p platform, readPassword func() string) int {
	dir := stateDir()
	if err := os.MkdirAll(dir, 0o700); err != nil {
		fmt.Println(err)
		return 1
	}
	f, err := os.OpenFile(filepath.Join(dir, "hub.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		fmt.Println(err)
		return 1
	}
	defer f.Close()
	l := &laptop{dir: dir, os: p, in: newStdin(), out: os.Stdout, status: map[string]string{}, logf: jsonLog(func(b []byte) { _, _ = f.Write(b) })}
	exe, _ := os.Executable()
	if err := l.run(context.Background(), exe, readPassword); err != nil {
		fmt.Println(err)
		return 1
	}
	return 0
}

func newStdin() *bufio.Reader { return bufio.NewReader(os.Stdin) }
