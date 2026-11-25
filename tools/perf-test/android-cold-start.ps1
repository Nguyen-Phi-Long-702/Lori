# android-cold-start.ps1: do cold start cua app Lori tren thiet bi/AVD dang noi adb (ke hoach Tuan 17, Ngay 4-5).
# Cach chay: xem INFRA.md muc 40. Moi lan do: tat han tien trinh app roi mo SplashActivity, roi doc
#   SplashMs = TotalTime cua `am start -W` (den khung hinh dau tien cua Splash, dinh nghia cold start cua Android);
#   HomeMs   = SplashMs + khoang tu luc Splash hien den luc MainActivity hien (chenh lech gio logcat "Displayed").
# Yeu cau: da dang nhap san de Splash chuyen thang vao MainActivity.
param(
    [int]$Runs = 10,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$package = "com.example.lori"

function Get-DisplayedTime($log, $activity) {
    $line = $log | Select-String "^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}).*Displayed\s+$package/\.$activity" | Select-Object -First 1
    if (-not $line) { return $null }
    # logcat -v time khong co nam; nam gia 2000 chi de tinh chenh lech trong cung mot lan do
    return [datetime]::ParseExact("2000-" + $line.Matches[0].Groups[1].Value, "yyyy-MM-dd HH:mm:ss.fff", [cultureinfo]::InvariantCulture)
}

function Get-TopResumedTime($eventsLog, $activity) {
    $line = $eventsLog | Select-String "^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}).*am_on_top_resumed_gained_called.*\[0,$package\.$activity," | Select-Object -First 1
    if (-not $line) { return $null }
    return [datetime]::ParseExact("2000-" + $line.Matches[0].Groups[1].Value, "yyyy-MM-dd HH:mm:ss.fff", [cultureinfo]::InvariantCulture)
}

$usedEventFallback = $false
$rows = for ($i = 1; $i -le $Runs; $i++) {
    & $Adb shell am force-stop $package | Out-Null
    Start-Sleep -Seconds 3
    & $Adb logcat -c
    & $Adb logcat -b events -c
    $launch = & $Adb shell am start -W -n "$package/.SplashActivity"
    Start-Sleep -Seconds 8
    $log = & $Adb logcat -d -v time

    $totalMatch = $launch | Select-String "TotalTime:\s*(\d+)"
    $splashShown = Get-DisplayedTime $log "SplashActivity"
    $mainShown = Get-DisplayedTime $log "MainActivity"
    if (-not $mainShown) {
        $eventsLog = & $Adb logcat -b events -d -v time
        $mainShown = Get-TopResumedTime $eventsLog "MainActivity"
        if ($mainShown) { $usedEventFallback = $true }
    }
    if (-not $totalMatch -or -not $splashShown -or -not $mainShown) {
        throw "Lan $i : khong doc duoc TotalTime hoac dong Displayed/top_resumed cua SplashActivity/MainActivity (da dang nhap chua?). Ket qua am start: $launch"
    }
    $splashMs = [int]$totalMatch.Matches[0].Groups[1].Value
    [pscustomobject]@{
        Run      = $i
        SplashMs = $splashMs
        HomeMs   = $splashMs + [int]($mainShown - $splashShown).TotalMilliseconds
    }
}

if ($usedEventFallback) {
    "Luu y: khong co dong Displayed cho MainActivity (hanh vi Android 10 API 29), da dung moc am_on_top_resumed_gained_called lam can duoi cho HomeMs."
}

foreach ($row in $rows) {
    "Lan {0}: Splash {1} ms, den Home {2} ms" -f $row.Run, $row.SplashMs, $row.HomeMs
}
foreach ($column in "SplashMs", "HomeMs") {
    $values = @($rows | ForEach-Object { $_.$column } | Sort-Object)
    $median = $values[[math]::Ceiling($values.Count / 2) - 1]
    "{0}: trung vi {1} ms, trung binh {2:N0} ms, lon nhat {3} ms" -f $column, $median, ($values | Measure-Object -Average).Average, $values[-1]
}
