//go:build windows

package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Runs on CI's Windows runner (hub.yml): the Windows calls the laptop program depends on load
// and answer, so a broken build is caught before it reaches the owner's laptop. The runner has no
// Wi-Fi, so the hotspot must fail with Windows' own words, not a script or type error.

func TestKeepAwakeAndBattery(t *testing.T) {
	if err := (winPlatform{}).KeepAwake(); err != nil {
		t.Fatal(err)
	}
	t.Logf("battery: %+v", (winPlatform{}).Battery())
}

func TestHotspotScriptRuns(t *testing.T) {
	line, err := runHotspotScript("Company Site Hub", "test-pass-1234")
	t.Logf("line=%q err=%v", line, err)
	if err != nil {
		for _, bad := range []string{"Unable to find type", "ParserError", "hotspot script said", "null-valued", "cannot be found"} {
			if strings.Contains(err.Error(), bad) {
				t.Fatalf("script broken: %v", err)
			}
		}
	}
}

func TestFirewallRuleWithSpacesInPath(t *testing.T) {
	exe := `C:\Program Files\Company Hub Test\company-hub.exe`
	rule := "CompanySiteHubTest"
	defer netsh("advfirewall firewall delete rule name=" + rule)
	if err := firewallAdd(rule, exe); err != nil {
		t.Fatal(err)
	}
	if !firewallHas(rule, exe) {
		t.Fatal("rule not found after adding")
	}
}

func TestHostsLineAddedOnce(t *testing.T) {
	p := filepath.Join(t.TempDir(), "hosts")
	_ = os.WriteFile(p, []byte("127.0.0.1 localhost"), 0o644)
	for i := 0; i < 2; i++ {
		if err := hostsAdd(p); err != nil {
			t.Fatal(err)
		}
	}
	b, _ := os.ReadFile(p)
	if strings.Count(string(b), hubName) != 1 || !strings.Contains(string(b), "localhost\r\n") {
		t.Fatalf("hosts: %q", b)
	}
}
