//go:build windows

package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"golang.org/x/sys/windows"
	"golang.org/x/term"
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

func TestConsoleChildHelper(t *testing.T) {
	outPath := os.Getenv("HUB_CONSOLE_TEST_OUT")
	if outPath == "" {
		return
	}
	inTTY := term.IsTerminal(int(os.Stdin.Fd()))
	outTTY := term.IsTerminal(int(os.Stdout.Fd()))
	line := "stdin=false stdout=false"
	if inTTY && outTTY {
		line = "stdin=true stdout=true"
	} else if inTTY {
		line = "stdin=true stdout=false"
	} else if outTTY {
		line = "stdin=false stdout=true"
	}
	if err := os.WriteFile(outPath, []byte(line), 0o600); err != nil {
		t.Fatal(err)
	}
}

// Guards the self-install relaunch: starting the copied exe in a new console must give the child
// real console handles (so prompts appear and ReadPassword works), not NUL.
func TestStartInNewConsoleHasLiveConsole(t *testing.T) {
	outPath := filepath.Join(t.TempDir(), "console.txt")
	t.Setenv("HUB_CONSOLE_TEST_OUT", outPath)
	pi, err := startInNewConsole(os.Args[0], t.TempDir(), "-test.run=^TestConsoleChildHelper$")
	if err != nil {
		t.Fatal(err)
	}
	defer windows.CloseHandle(pi.Thread)
	defer windows.CloseHandle(pi.Process)

	ev, err := windows.WaitForSingleObject(pi.Process, 15_000)
	if err != nil || ev != windows.WAIT_OBJECT_0 {
		t.Fatalf("child did not finish: ev=%v err=%v", ev, err)
	}
	var exitCode uint32
	if err := windows.GetExitCodeProcess(pi.Process, &exitCode); err != nil || exitCode != 0 {
		t.Fatalf("child exit=%d err=%v", exitCode, err)
	}
	got, err := os.ReadFile(outPath)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "stdin=true stdout=true" {
		t.Fatalf("relaunched console handles broken: %q", string(got))
	}
}
