# Turns on the Windows Mobile Hotspot with $env:HUB_SSID / $env:HUB_PASS through the WinRT
# tethering API, which, unlike the Settings switch, starts with no internet. Prints one line:
# "state: ..." or "error: ...". Run by laptop_windows.go; parsed by its test on a Windows runner.
$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $ext = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 }
    $asOp = $ext | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
    $asAction = $ext | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' } | Select-Object -First 1
    function AwaitOp($op, [Type]$type) { $task = $asOp.MakeGenericMethod($type).Invoke($null, @($op)); $null = $task.Wait(60000); $task.Result }
    function AwaitAction($op) { $task = $asAction.Invoke($null, @($op)); $null = $task.Wait(60000) }
    $null = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType = WindowsRuntime]
    $null = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType = WindowsRuntime]
    $null = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringOperationResult, Windows.Networking.NetworkOperators, ContentType = WindowsRuntime]
    $conn = [Windows.Networking.Connectivity.NetworkInformation]::GetInternetConnectionProfile()
    $from = 'internet'
    if ($null -eq $conn) {
        $conn = [Windows.Networking.Connectivity.NetworkInformation]::GetConnectionProfiles() | Select-Object -First 1
        $from = 'no internet'
    }
    if ($null -eq $conn) { Write-Output 'error: Windows has no network to start the hotspot from: turn Wi-Fi on'; exit 3 }
    $tether = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager]::CreateFromConnectionProfile($conn)
    $conf = $tether.GetCurrentAccessPointConfiguration()
    if ($conf.Ssid -ne $env:HUB_SSID -or $conf.Passphrase -ne $env:HUB_PASS) {
        $conf.Ssid = $env:HUB_SSID
        $conf.Passphrase = $env:HUB_PASS
        AwaitAction ($tether.ConfigureAccessPointAsync($conf))
    }
    if ($tether.TetheringOperationalState -ne 'On') {
        $res = AwaitOp ($tether.StartTetheringAsync()) ([Windows.Networking.NetworkOperators.NetworkOperatorTetheringOperationResult])
        if ($res.Status -ne 'Success') { Write-Output ('error: Windows refused the hotspot: ' + $res.Status + ' ' + $res.AdditionalErrorMessage); exit 4 }
    }
    Write-Output ('state: ' + $tether.TetheringOperationalState + ', ' + $tether.ClientCount + ' joined, started with ' + $from)
} catch {
    Write-Output ('error: ' + $_.Exception.Message)
    exit 5
}
