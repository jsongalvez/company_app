package main

import (
	"errors"
	"io"
	"net"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/dns/dnsmessage"
)

// Names answers DNS for phones on the laptop's hotspot. Windows hands those phones the laptop as
// their DNS server, and the laptop is the only one who can say where hub.kiel.party is with no
// internet. It also answers "no such name" for iCloud Private Relay's names, Apple's documented
// way for a network to turn Relay off, so an iPhone uses this answer instead of Apple's.
type Names struct {
	Hub string
	IP  net.IP
	// Upstream servers (host:port) asked for every other name; none means offline: answer
	// SERVFAIL at once.
	Upstream []string
	Log      func(event string, fields map[string]any)

	mu       sync.Mutex
	answered map[string]int
}

var relayNames = []string{"mask.icloud.com", "mask-h2.icloud.com"}

func (d *Names) count(kind string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.answered == nil {
		d.answered = map[string]int{}
	}
	d.answered[kind]++
}

// Counts returns how many questions were answered, by kind (hub, relay-off, forwarded, failed).
func (d *Names) Counts() map[string]int {
	d.mu.Lock()
	defer d.mu.Unlock()
	out := map[string]int{}
	for k, v := range d.answered {
		out[k] = v
	}
	return out
}

// Listen serves UDP and TCP on addr and returns the UDP address bound and a stop function.
func (d *Names) Listen(addr string) (string, func(), error) {
	pc, err := net.ListenPacket("udp4", addr)
	if err != nil {
		return "", nil, err
	}
	bound := pc.LocalAddr().String()
	ln, err := net.Listen("tcp4", bound)
	if err != nil {
		pc.Close()
		return "", nil, err
	}
	go func() {
		buf := make([]byte, 1500)
		for {
			n, from, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			q := append([]byte{}, buf[:n]...)
			go func() {
				if out := d.answer(q); out != nil {
					_, _ = pc.WriteTo(out, from)
				}
			}()
		}
	}()
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go d.serveTCP(c)
		}
	}()
	return bound, func() { pc.Close(); ln.Close() }, nil
}

func (d *Names) serveTCP(c net.Conn) {
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(10 * time.Second))
	var l [2]byte
	if _, err := io.ReadFull(c, l[:]); err != nil {
		return
	}
	q := make([]byte, int(l[0])<<8|int(l[1]))
	if _, err := io.ReadFull(c, q); err != nil {
		return
	}
	out := d.answer(q)
	if out == nil {
		return
	}
	_, _ = c.Write(append([]byte{byte(len(out) >> 8), byte(len(out))}, out...))
}

func (d *Names) answer(raw []byte) []byte {
	var p dnsmessage.Parser
	h, err := p.Start(raw)
	if err != nil || h.Response {
		return nil
	}
	q, err := p.Question()
	if err != nil {
		return nil
	}
	name := strings.TrimSuffix(strings.ToLower(q.Name.String()), ".")
	reply := func(code dnsmessage.RCode, answers ...dnsmessage.Resource) []byte {
		m := dnsmessage.Message{
			Header:    dnsmessage.Header{ID: h.ID, Response: true, Authoritative: code != dnsmessage.RCodeServerFailure, RecursionDesired: h.RecursionDesired, RecursionAvailable: len(d.Upstream) > 0, RCode: code},
			Questions: []dnsmessage.Question{q},
			Answers:   answers,
		}
		b, _ := m.Pack()
		return b
	}
	switch {
	case name == strings.ToLower(d.Hub):
		d.count("hub")
		if q.Type != dnsmessage.TypeA {
			return reply(dnsmessage.RCodeSuccess)
		}
		var a [4]byte
		copy(a[:], d.IP.To4())
		return reply(dnsmessage.RCodeSuccess, dnsmessage.Resource{
			Header: dnsmessage.ResourceHeader{Name: q.Name, Type: dnsmessage.TypeA, Class: dnsmessage.ClassINET, TTL: 60},
			Body:   &dnsmessage.AResource{A: a},
		})
	case isRelayName(name):
		d.count("relay-off")
		return reply(dnsmessage.RCodeNameError)
	}
	for _, up := range d.Upstream {
		if out, err := forward(up, raw); err == nil {
			d.count("forwarded")
			return out
		}
	}
	d.count("failed")
	return reply(dnsmessage.RCodeServerFailure)
}

func isRelayName(name string) bool {
	for _, r := range relayNames {
		if name == r {
			return true
		}
	}
	return false
}

var errShort = errors.New("short answer")

func forward(up string, q []byte) ([]byte, error) {
	c, err := net.DialTimeout("udp", up, 2*time.Second)
	if err != nil {
		return nil, err
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(2 * time.Second))
	if _, err := c.Write(q); err != nil {
		return nil, err
	}
	buf := make([]byte, 4096)
	n, err := c.Read(buf)
	if err != nil {
		return nil, err
	}
	if n < 12 {
		return nil, errShort
	}
	return buf[:n], nil
}
