// Command hub is the site hub's relay: phones on the laptop's hotspot mirror their unsent saves
// here, sealed, and read each other's (docs: hub/README.md).
package main

import (
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"os"
	"time"
)

func main() {
	addr := flag.String("addr", ":443", "address to listen on")
	cert := flag.String("cert", "/certs/hub.crt", "TLS certificate (PEM)")
	key := flag.String("key", "/certs/hub.key", "TLS key (PEM)")
	origins := flag.String("origins", "https://api.kiel.party", "app origins allowed to call, comma-separated")
	flag.Parse()

	logLine := func(event string, fields map[string]any) {
		fields["event"] = event
		fields["at"] = time.Now().UTC().Format(time.RFC3339)
		b, _ := json.Marshal(fields)
		os.Stdout.Write(append(b, '\n'))
	}
	relay := NewRelay(logLine)
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
