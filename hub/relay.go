package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
)

// A relay, not a judge: it keeps opaque records per room (a branch's day) and hands them to every
// phone that knows the room id. The cloud gives the room id and the key to the branch's members;
// the relay never sees the key, so it can't read what it carries.

const (
	maxBody          = 1 << 20
	maxCiphertext    = 16 << 10
	maxRecordsInRoom = 20000
	maxPhonesInRoom  = 64
	maxRooms         = 256
	keepFor          = 14 * 24 * time.Hour
)

var (
	roomID  = regexp.MustCompile(`^[0-9a-f]{64}$`)
	shortID = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,80}$`)
)

// Record is one phone's save, sealed with the room key. Only the fields the relay needs to dedupe
// and to log stand outside the ciphertext.
type Record struct {
	OpID       string `json:"opId"`
	Device     string `json:"device"`
	OccurredAt string `json:"occurredAt"`
	Ciphertext string `json:"ciphertext"`
}

// Phone is what the relay knows of one phone: when it last called, and its sealed presence.
type Phone struct {
	Device   string `json:"device"`
	AgoSec   int64  `json:"agoSec"`
	Presence string `json:"presence"`
	last     time.Time
}

type room struct {
	records []Record
	byOp    map[string]struct{}
	phones  map[string]*Phone
	touched time.Time
}

// SyncIn is one phone's call: its presence, the records it holds that this relay may lack, and the
// position it has read up to.
type SyncIn struct {
	Device   string   `json:"device"`
	Presence string   `json:"presence"`
	Boot     string   `json:"boot"`
	Since    int      `json:"since"`
	Records  []Record `json:"records"`
}

// SyncOut answers with this relay's boot id (a new one means it lost what it held and the phone
// should send everything again), the records after `since`, and the phones in the room.
type SyncOut struct {
	Boot    string   `json:"boot"`
	Next    int      `json:"next"`
	Taken   int      `json:"taken"`
	Records []Record `json:"records"`
	Phones  []Phone  `json:"phones"`
}

type Relay struct {
	mu    sync.Mutex
	rooms map[string]*room
	boot  string
	now   func() time.Time
	log   func(event string, fields map[string]any)
}

func NewRelay(log func(string, map[string]any)) *Relay {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return &Relay{rooms: map[string]*room{}, boot: hex.EncodeToString(b), now: time.Now, log: log}
}

var errFull = errors.New("full")

func (r *Relay) sync(id string, in SyncIn) (SyncOut, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := r.now()
	r.prune(now)
	rm := r.rooms[id]
	if rm == nil {
		if len(r.rooms) >= maxRooms {
			return SyncOut{}, errFull
		}
		rm = &room{byOp: map[string]struct{}{}, phones: map[string]*Phone{}}
		r.rooms[id] = rm
	}
	rm.touched = now
	ph := rm.phones[in.Device]
	if ph == nil {
		if len(rm.phones) >= maxPhonesInRoom {
			return SyncOut{}, errFull
		}
		ph = &Phone{Device: in.Device}
		rm.phones[in.Device] = ph
		r.log("phone_new", map[string]any{"room": id[:8], "device": in.Device})
	}
	ph.last = now
	if in.Presence != "" {
		ph.Presence = in.Presence
	}
	taken := 0
	for _, rec := range in.Records {
		if _, dup := rm.byOp[rec.OpID]; dup {
			continue
		}
		if len(rm.records) >= maxRecordsInRoom {
			break
		}
		rm.byOp[rec.OpID] = struct{}{}
		rm.records = append(rm.records, rec)
		taken++
	}
	if taken > 0 {
		refill := in.Boot != r.boot
		r.log("records_in", map[string]any{"room": id[:8], "device": in.Device, "taken": taken, "refill": refill, "held": len(rm.records)})
	}
	since := in.Since
	if in.Boot != r.boot || since < 0 || since > len(rm.records) {
		since = 0
	}
	out := SyncOut{Boot: r.boot, Next: len(rm.records), Taken: taken, Records: append([]Record{}, rm.records[since:]...)}
	for _, p := range rm.phones {
		out.Phones = append(out.Phones, Phone{Device: p.Device, AgoSec: int64(now.Sub(p.last) / time.Second), Presence: p.Presence})
	}
	sort.Slice(out.Phones, func(i, j int) bool { return out.Phones[i].Device < out.Phones[j].Device })
	return out, nil
}

func (r *Relay) prune(now time.Time) {
	for id, rm := range r.rooms {
		if now.Sub(rm.touched) > keepFor {
			delete(r.rooms, id)
		}
	}
}

func valid(in SyncIn) bool {
	if !shortID.MatchString(in.Device) || len(in.Presence) > maxCiphertext || len(in.Boot) > 64 {
		return false
	}
	for _, rec := range in.Records {
		if !shortID.MatchString(rec.OpID) || !shortID.MatchString(rec.Device) || len(rec.OccurredAt) > 40 || len(rec.Ciphertext) > maxCiphertext {
			return false
		}
	}
	return true
}

// Handler serves /health and POST /v1/rooms/{room}/sync, with CORS for the app's origins.
func (r *Relay) Handler(origins []string) http.Handler {
	allowed := map[string]bool{}
	for _, o := range origins {
		allowed[o] = true
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, map[string]any{"hub": "company-app-site", "boot": r.boot})
	})
	mux.HandleFunc("POST /v1/rooms/{room}/sync", func(w http.ResponseWriter, req *http.Request) {
		id := req.PathValue("room")
		if !roomID.MatchString(id) {
			http.Error(w, "bad room", http.StatusBadRequest)
			return
		}
		var in SyncIn
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, maxBody)).Decode(&in); err != nil || !valid(in) {
			http.Error(w, "bad body", http.StatusBadRequest)
			return
		}
		out, err := r.sync(id, in)
		if err != nil {
			http.Error(w, "full", http.StatusInsufficientStorage)
			return
		}
		writeJSON(w, out)
	})
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		if o := req.Header.Get("Origin"); allowed[o] {
			h := w.Header()
			h.Set("Access-Control-Allow-Origin", o)
			h.Set("Vary", "Origin")
			h.Set("Access-Control-Allow-Methods", "GET, POST")
			h.Set("Access-Control-Allow-Headers", "Content-Type")
			// Chrome asks before a public page reaches a private address (Private Network Access).
			h.Set("Access-Control-Allow-Private-Network", "true")
			h.Set("Access-Control-Max-Age", "600")
		}
		if req.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		mux.ServeHTTP(w, req)
	})
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(v)
}

func splitList(s string) []string {
	var out []string
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
