//go:build windows

package main

import (
	_ "embed"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	"unicode/utf16"
	"unsafe"

	"golang.org/x/sys/windows"
	"golang.org/x/term"
)

//go:embed hotspot.ps1
var hotspotScript string

const (
	firewallRule = "CompanySiteHub"
	hostsMarker  = "# company site hub"
)

var (
	kernel32                = windows.NewLazySystemDLL("kernel32.dll")
	procSetThreadExecState  = kernel32.NewProc("SetThreadExecutionState")
	procGetSystemPowerState = kernel32.NewProc("GetSystemPowerStatus")
	hostsPath               = filepath.Join(os.Getenv("SystemRoot"), `System32\drivers\etc\hosts`)
)

type winPlatform struct{}

func (winPlatform) KeepAwake() error {
	const esSystemRequired = 0x00000001
	// Without ES_CONTINUOUS this only resets the idle timer, which is why it runs every 30 s; it
	// needs no thread of its own.
	if r, _, err := procSetThreadExecState.Call(esSystemRequired); r == 0 {
		return err
	}
	return nil
}

type systemPowerStatus struct {
	ACLineStatus        byte
	BatteryFlag         byte
	BatteryLifePercent  byte
	SystemStatusFlag    byte
	BatteryLifeTime     uint32
	BatteryFullLifeTime uint32
}

func (winPlatform) Battery() *Battery {
	var s systemPowerStatus
	if r, _, _ := procGetSystemPowerState.Call(uintptr(unsafe.Pointer(&s))); r == 0 {
		return nil
	}
	if s.BatteryFlag == 128 || s.BatteryFlag == 255 || s.BatteryLifePercent > 100 {
		return nil // no battery, or Windows doesn't know
	}
	return &Battery{Percent: int(s.BatteryLifePercent), Charging: s.ACLineStatus == 1}
}

func runHotspotScript(ssid, pass string) (string, error) {
	// -EncodedCommand, not "-Command -": PowerShell reads stdin line by line and drops a
	// multi-line try block without a word.
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encodePS(hotspotScript))
	cmd.Env = append(os.Environ(), "HUB_SSID="+ssid, "HUB_PASS="+pass)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: windows.CREATE_NO_WINDOW}
	out, _ := cmd.CombinedOutput()
	lines := strings.Split(strings.TrimSpace(string(out)), "\n")
	last := strings.TrimSpace(lines[len(lines)-1])
	switch {
	case strings.HasPrefix(last, "state: "):
		return strings.TrimPrefix(last, "state: "), nil
	case strings.HasPrefix(last, "error: "):
		return "", errors.New(strings.TrimPrefix(last, "error: "))
	}
	return "", fmt.Errorf("hotspot script said: %s", strings.TrimSpace(string(out)))
}

// encodePS is PowerShell's -EncodedCommand form: base64 of the script in UTF-16LE.
func encodePS(script string) string {
	u := utf16.Encode([]rune(script))
	b := make([]byte, 2*len(u))
	for i, c := range u {
		b[2*i], b[2*i+1] = byte(c), byte(c>>8)
	}
	return base64.StdEncoding.EncodeToString(b)
}

func (winPlatform) Hotspot(ssid, pass string) (string, error) { return runHotspotScript(ssid, pass) }

// netsh parses its own command line; a program path with spaces must reach it quoted as one word.
func netsh(args string) (string, error) {
	cmd := exec.Command("netsh.exe")
	cmd.SysProcAttr = &syscall.SysProcAttr{CmdLine: "netsh.exe " + args, HideWindow: true}
	out, err := cmd.CombinedOutput()
	return string(out), err
}

func firewallHas(rule, exe string) bool {
	out, err := netsh(fmt.Sprintf(`advfirewall firewall show rule name=%s verbose`, rule))
	return err == nil && strings.Contains(strings.ToLower(out), strings.ToLower(exe))
}

func firewallAdd(rule, exe string) error {
	_, _ = netsh(fmt.Sprintf(`advfirewall firewall delete rule name=%s`, rule))
	if out, err := netsh(fmt.Sprintf(`advfirewall firewall add rule name=%s dir=in action=allow program="%s" enable=yes profile=any`, rule, exe)); err != nil {
		return fmt.Errorf("firewall: %v: %s", err, strings.TrimSpace(out))
	}
	return nil
}

func hostsHas(path string) bool {
	b, err := os.ReadFile(path)
	return err == nil && strings.Contains(string(b), hostsMarker)
}

// hostsAdd maps hub.kiel.party to the hotspot address on the laptop itself, and for phones too
// when Windows' own DNS answers them instead of the hub (port 53 taken).
func hostsAdd(path string) error {
	if hostsHas(path) {
		return nil
	}
	b, _ := os.ReadFile(path)
	s := string(b)
	if s != "" && !strings.HasSuffix(s, "\n") {
		s += "\r\n"
	}
	s += fmt.Sprintf("%s %s %s\r\n", defaultHotspotIP, hubName, hostsMarker)
	return os.WriteFile(path, []byte(s), 0o644)
}

func (winPlatform) SetupDone(exe string) bool {
	return firewallHas(firewallRule, exe) && hostsHas(hostsPath)
}

// Setup starts this program again with admin rights (Windows shows its Yes/No box) and waits for
// the rule and the hosts line.
func (p winPlatform) Setup(exe string) error {
	verb, _ := windows.UTF16PtrFromString("runas")
	file, _ := windows.UTF16PtrFromString(exe)
	args, _ := windows.UTF16PtrFromString("-setup")
	if err := windows.ShellExecute(0, verb, file, args, nil, windows.SW_HIDE); err != nil {
		return fmt.Errorf("admin rights refused: %w", err)
	}
	for i := 0; i < 60; i++ {
		if p.SetupDone(exe) {
			return nil
		}
		time.Sleep(time.Second)
	}
	return errors.New("setup did not finish in a minute")
}

func setupElevated() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	if err := firewallAdd(firewallRule, exe); err != nil {
		return err
	}
	return hostsAdd(hostsPath)
}

// install copies the program to its own folder and puts a shortcut on the desktop, so later
// starts and the firewall rule use one path. It answers the path to run.
func install(exe, dir string) string {
	target := filepath.Join(dir, "company-hub.exe")
	if strings.EqualFold(exe, target) {
		return exe
	}
	if err := copyFile(exe, target); err != nil {
		return exe // the installed copy is running: keep using it from here
	}
	ps := fmt.Sprintf(`$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Company site hub.lnk');$s.TargetPath='%s';$s.WorkingDirectory='%s';$s.Save()`, target, dir)
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	_ = cmd.Run()
	return target
}

func copyFile(from, to string) error {
	src, err := os.Open(from)
	if err != nil {
		return err
	}
	defer src.Close()
	tmp := to + ".new"
	dst, err := os.Create(tmp)
	if err != nil {
		return err
	}
	if _, err := io.Copy(dst, src); err != nil {
		dst.Close()
		return err
	}
	dst.Close()
	return os.Rename(tmp, to)
}

func laptopMain() int {
	title, _ := windows.UTF16PtrFromString("Company site hub")
	_, _, _ = kernel32.NewProc("SetConsoleTitleW").Call(uintptr(unsafe.Pointer(title)))
	dir := stateDir()
	_ = os.MkdirAll(dir, 0o700)
	exe, _ := os.Executable()
	if target := install(exe, dir); !strings.EqualFold(target, exe) {
		cmd := exec.Command(target)
		cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: windows.CREATE_NEW_CONSOLE}
		if cmd.Start() == nil {
			return 0
		}
	}
	code := runLaptopWith(winPlatform{}, func() string {
		b, _ := term.ReadPassword(int(os.Stdin.Fd()))
		return string(b)
	})
	fmt.Println("Press Enter to close.")
	_, _ = newStdin().ReadString('\n')
	return code
}
