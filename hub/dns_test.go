package main

import (
	"net"
	"testing"
	"time"

	"golang.org/x/net/dns/dnsmessage"
)

// Guards the laptop's name answers: phones on its hotspot have no other way to find
// hub.kiel.party, and an iPhone with iCloud Private Relay only uses the local answer once the
// network says the relay names don't exist.

func ask(t *testing.T, addr, name string, typ dnsmessage.Type) dnsmessage.Message {
	t.Helper()
	q := dnsmessage.Message{Header: dnsmessage.Header{ID: 7, RecursionDesired: true}, Questions: []dnsmessage.Question{{Name: dnsmessage.MustNewName(name), Type: typ, Class: dnsmessage.ClassINET}}}
	b, err := q.Pack()
	if err != nil {
		t.Fatal(err)
	}
	c, err := net.Dial("udp", addr)
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(3 * time.Second))
	if _, err := c.Write(b); err != nil {
		t.Fatal(err)
	}
	buf := make([]byte, 1500)
	n, err := c.Read(buf)
	if err != nil {
		t.Fatal(err)
	}
	var m dnsmessage.Message
	if err := m.Unpack(buf[:n]); err != nil {
		t.Fatal(err)
	}
	if m.ID != 7 {
		t.Fatalf("id %d", m.ID)
	}
	return m
}

func TestNamesAnswered(t *testing.T) {
	d := &Names{Hub: "hub.kiel.party", IP: net.IPv4(192, 168, 137, 1)}
	addr, stop, err := d.Listen("127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer stop()

	m := ask(t, addr, "HUB.kiel.party.", dnsmessage.TypeA)
	if m.RCode != dnsmessage.RCodeSuccess || len(m.Answers) != 1 {
		t.Fatalf("hub A: %+v", m)
	}
	if a, ok := m.Answers[0].Body.(*dnsmessage.AResource); !ok || net.IP(a.A[:]).String() != "192.168.137.1" {
		t.Fatalf("hub A body: %+v", m.Answers[0].Body)
	}
	if m := ask(t, addr, "hub.kiel.party.", dnsmessage.TypeAAAA); m.RCode != dnsmessage.RCodeSuccess || len(m.Answers) != 0 {
		t.Fatalf("hub AAAA must be empty, not an error: %+v", m)
	}
	for _, n := range []string{"mask.icloud.com.", "mask-h2.icloud.com."} {
		if m := ask(t, addr, n, dnsmessage.TypeA); m.RCode != dnsmessage.RCodeNameError {
			t.Fatalf("%s: want NXDOMAIN, got %v", n, m.RCode)
		}
	}
	// Offline (no upstream): anything else fails at once instead of hanging the phone.
	if m := ask(t, addr, "example.com.", dnsmessage.TypeA); m.RCode != dnsmessage.RCodeServerFailure {
		t.Fatalf("other name offline: %v", m.RCode)
	}
}

func TestOtherNamesForwardedWhenOnline(t *testing.T) {
	up := &Names{Hub: "example.com", IP: net.IPv4(10, 0, 0, 9)}
	upAddr, stopUp, err := up.Listen("127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer stopUp()
	d := &Names{Hub: "hub.kiel.party", IP: net.IPv4(192, 168, 137, 1), Upstream: []string{upAddr}}
	addr, stop, err := d.Listen("127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer stop()
	m := ask(t, addr, "example.com.", dnsmessage.TypeA)
	if m.RCode != dnsmessage.RCodeSuccess || len(m.Answers) != 1 {
		t.Fatalf("forwarded: %+v", m)
	}
}
