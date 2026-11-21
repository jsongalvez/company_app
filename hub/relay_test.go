package main

import (
	"strings"
	"testing"
	"time"
)

// Guards the relay's two promises: a record sent twice is held once (phones resend everything after a
// restart), and a room's size is capped (a phone can't fill the laptop's memory).

const room1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

func quiet(string, map[string]any) {}

func TestResentRecordsAreHeldOnce(t *testing.T) {
	r := NewRelay(quiet)
	rec := Record{OpID: "1-2", Device: "a", OccurredAt: "2026-11-21T08:00:00Z", Ciphertext: "x"}
	out, _ := r.sync(room1, SyncIn{Device: "a", Records: []Record{rec}})
	if out.Taken != 1 || out.Next != 1 {
		t.Fatalf("first send: %+v", out)
	}
	out, _ = r.sync(room1, SyncIn{Device: "b", Boot: "old", Records: []Record{rec, rec}})
	if out.Taken != 0 || out.Next != 1 || len(out.Records) != 1 {
		t.Fatalf("resend: %+v", out)
	}
	out, _ = r.sync(room1, SyncIn{Device: "b", Boot: out.Boot, Since: out.Next})
	if len(out.Records) != 0 || len(out.Phones) != 2 {
		t.Fatalf("read after: %+v", out)
	}
}

func TestRoomIsCapped(t *testing.T) {
	r := NewRelay(quiet)
	recs := make([]Record, maxRecordsInRoom+5)
	for i := range recs {
		recs[i] = Record{OpID: strings.Repeat("x", 1) + time.Duration(i).String(), Device: "a", Ciphertext: "x"}
	}
	out, _ := r.sync(room1, SyncIn{Device: "a", Records: recs})
	if out.Next != maxRecordsInRoom {
		t.Fatalf("held %d", out.Next)
	}
	if valid(SyncIn{Device: "a", Presence: strings.Repeat("x", maxCiphertext+1)}) {
		t.Fatal("oversized presence accepted")
	}
}

func TestOldRoomsAreDropped(t *testing.T) {
	r := NewRelay(quiet)
	start := time.Now()
	r.now = func() time.Time { return start }
	_, _ = r.sync(room1, SyncIn{Device: "a"})
	r.now = func() time.Time { return start.Add(keepFor + time.Hour) }
	other := strings.Repeat("b", 64)
	_, _ = r.sync(other, SyncIn{Device: "a"})
	if _, ok := r.rooms[room1]; ok {
		t.Fatal("room older than keepFor kept")
	}
}

// The Site panel warns at 15%: the laptop's battery must reach the phones in every answer.
func TestSyncCarriesTheLaptopsBattery(t *testing.T) {
	r := NewRelay(quiet)
	out, _ := r.sync(room1, SyncIn{Device: "a"})
	if out.Battery != nil {
		t.Fatalf("no battery source, got %+v", out.Battery)
	}
	r.battery = func() *Battery { return &Battery{Percent: 14, Charging: false} }
	out, _ = r.sync(room1, SyncIn{Device: "a"})
	if out.Battery == nil || out.Battery.Percent != 14 || out.Battery.Charging {
		t.Fatalf("battery: %+v", out.Battery)
	}
}
