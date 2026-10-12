param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$FunctionsOnly
)

# Run against an already installed Tempo debug build. Only a temporary QA plan is changed.
$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path -Parent $PSScriptRoot
$taskArtifacts = Join-Path $taskWorkspace 'artifacts'
$taskPackage = 'io.github.leychan.tempo'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null

function Invoke-Device([string[]]$DeviceArguments) {
    $taskOutput = & $AdbPath -s $Serial @DeviceArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($taskOutput -join "`n") }
    return ($taskOutput -join "`n")
}

function Read-Ui {
    $null = Invoke-Device @('shell', 'uiautomator', 'dump', '/sdcard/tempo-qa-ui.xml')
    $null = Invoke-Device @('pull', '/sdcard/tempo-qa-ui.xml', (Join-Path $taskArtifacts 'device-ui.xml'))
    return [xml](Get-Content -LiteralPath (Join-Path $taskArtifacts 'device-ui.xml') -Raw)
}

function Tap-Node($Node) {
    if ($null -eq $Node) { throw 'Expected control was not found' }
    $taskMatches = [regex]::Match($Node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    $taskX = [int](([int]$taskMatches.Groups[1].Value + [int]$taskMatches.Groups[3].Value) / 2)
    $taskY = [int](([int]$taskMatches.Groups[2].Value + [int]$taskMatches.Groups[4].Value) / 2)
    $null = Invoke-Device @('shell', 'input', 'touchscreen', 'tap', "$taskX", "$taskY")
}

function Tap-Text([string]$Text) {
    $taskUi = Read-Ui
    Tap-Node ($taskUi.SelectNodes('//node') | Where-Object { $_.text -eq $Text } | Select-Object -First 1)
}

function Tap-Description([string]$Description) {
    $taskUi = Read-Ui
    Tap-Node ($taskUi.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Description } | Select-Object -First 1)
}

function Read-AppState {
    $taskRaw = Invoke-Device @('shell', 'run-as', $taskPackage, 'cat', 'shared_prefs/tempo.xml')
    [xml]$taskPrefs = $taskRaw
    return ($taskPrefs.map.string | Where-Object { $_.name -eq 'state' }).'#text' | ConvertFrom-Json
}

function Assert-Ui([string]$Text) {
    $taskUi = Read-Ui
    if (-not ($taskUi.SelectNodes('//node') | Where-Object { $_.text -eq $Text })) {
        throw "Missing expected UI text: $Text"
    }
}

function Set-Number([string]$Label, [string]$Value) {
    Tap-Description $Label
    if ($Label -in @('周期数', '循环数')) {
        Set-Wheel '数量' ([int]$Value)
    } else {
        Set-Wheel '分钟' ([int][math]::Floor([int]$Value / 60))
        Set-Wheel '秒' ([int]$Value % 60)
    }
    Tap-Text '确定'
}

function Set-Wheel([string]$Label, [int]$Target) {
    for ($taskAttempt = 0; $taskAttempt -lt 100; $taskAttempt++) {
        $taskUi = Read-Ui
        $taskWheel = $taskUi.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Label } | Select-Object -First 1
        if ($null -eq $taskWheel) {
            if ($taskAttempt -ge 3) { throw "Picker wheel not found: $Label" }
            Start-Sleep -Milliseconds 200
            continue
        }
        $taskValueText = ($taskUi.SelectNodes('//node') | Where-Object {
            $_.text -match '^\d{2}:\d{2}(:\d{2})?$' -or $_.text -match '^\d+ [次组]$'
        } | Select-Object -First 1).text
        if ($Label -eq '数量') { $taskValue = [int]($taskValueText -split ' ')[0] }
        else {
            $taskParts = $taskValueText -split ':'
            $taskValue = if ($Label -eq '秒') { [int]$taskParts[-1] }
                elseif ($taskParts.Count -eq 3) { [int]$taskParts[0] * 60 + [int]$taskParts[1] }
                else { [int]$taskParts[0] }
        }
        if ($taskValue -eq $Target) { return }
        $taskBounds = [regex]::Match($taskWheel.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        $taskX = [int](([int]$taskBounds.Groups[1].Value + [int]$taskBounds.Groups[3].Value) / 2)
        $taskTop = [int]$taskBounds.Groups[2].Value
        $taskHeight = [int]$taskBounds.Groups[4].Value - $taskTop
        $taskY = [int]($taskTop + $taskHeight * $(if ($Target -gt $taskValue) { 5 / 6 } else { 1 / 6 }))
        $null = Invoke-Device @('shell', 'input', 'touchscreen', 'tap', "$taskX", "$taskY")
    }
    throw "Could not select $Target on picker wheel $Label"
}

function Save-Screenshot([string]$Name) {
    $null = Invoke-Device @('shell', 'screencap', '-p', '/sdcard/tempo-qa-preview.png')
    $null = Invoke-Device @('pull', '/sdcard/tempo-qa-preview.png', (Join-Path $taskArtifacts $Name))
}

function Wait-AppStatus([string]$Expected) {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $taskCurrent = Read-AppState
        if ($taskCurrent.session.status -eq $Expected) { return $taskCurrent }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    throw "Expected session status $Expected, got $($taskCurrent.session.status)"
}

function Wait-AppState([scriptblock]$Condition) {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $taskCurrent = Read-AppState
        if (& $Condition $taskCurrent) { return $taskCurrent }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    throw 'The app did not persist the expected state within five seconds'
}

if ($FunctionsOnly) { return }

$taskChecks = [System.Collections.Generic.List[string]]::new()
$taskInitial = Read-AppState
if ($taskInitial.session.status -in @('RUNNING', 'PAUSED')) {
    throw 'An active training session exists; finish or reset it before running device QA.'
}
if ($taskInitial.plans.name -contains 'QA Short') { throw 'A QA Short plan already exists; refusing to modify it.' }

# The test starts on the training tab, opened through the App navigation.
$null = Invoke-Device @('shell', 'am', 'start', '-W', '--user', '0', '-n', "$taskPackage/.MainActivity")
Assert-Ui '间歇训练'
Assert-Ui '下一阶段'
$taskUi = Read-Ui
if ($taskUi.SelectNodes('//node[@text="TEMPO"]').Count -gt 0) { throw 'TEMPO is still visible' }
Tap-Text '我的计划'
Tap-Description '新增训练计划'
Assert-Ui '新建计划'
Tap-Text '保存计划'
Assert-Ui '请输入计划名称'
$taskChecks.Add('Empty plan name is rejected')

$taskUi = Read-Ui
Tap-Node ($taskUi.SelectNodes('//node[@class="android.widget.EditText"]') | Select-Object -First 1)
$null = Invoke-Device @('shell', 'input', 'text', 'QA%sShort')
$null = Invoke-Device @('shell', 'input', 'keyevent', '4')
Set-Number '准备时间' '0'
Tap-Description '锻炼时间'
Assert-Ui '滑动选择时间 · 最少 1 秒，最多 60 分钟'
Tap-Text '取消'
$taskChecks.Add('Workout duration picker enforces a one-second minimum')
Set-Number '锻炼时间' '4'
Set-Number '休息时间' '1'
Set-Number '周期数' '2'
Set-Number '循环数' '2'
Set-Number '循环间休息' '1'
Tap-Text '保存计划'
Assert-Ui '3 个计划 · 保存在本机'
$taskSaved = Read-AppState
$taskQa = $taskSaved.plans | Where-Object { $_.name -eq 'QA Short' }
if ($null -eq $taskQa -or $taskQa.work -ne 4 -or $taskQa.cycles -ne 2 -or $taskQa.roundRest -ne 1) {
    throw 'Edited plan values were not persisted correctly'
}
$taskChecks.Add('New plan parameters are saved locally and selected')

Tap-Text '训练'
Assert-Ui '2 周期 × 2 循环 · 总时长 00:19'
Tap-Text '开始训练'
Start-Sleep -Milliseconds 600
$null = Invoke-Device @('shell', 'input', 'keyevent', '3')
$taskPaused = Wait-AppStatus 'PAUSED'
if ($taskPaused.session.status -ne 'PAUSED' -or $taskPaused.session.elapsedMillis -le 0) {
    throw 'Background navigation did not pause the session'
}
Start-Sleep -Milliseconds 1200
$taskStillPaused = Read-AppState
if ($taskStillPaused.session.elapsedMillis -ne $taskPaused.session.elapsedMillis) {
    throw 'The paused session continued counting down'
}
$taskChecks.Add('Returning to Home pauses and preserves elapsed milliseconds')

$null = Invoke-Device @('shell', 'am', 'force-stop', '--user', '0', $taskPackage)
$null = Invoke-Device @('shell', 'am', 'start', '-W', '--user', '0', '-n', "$taskPackage/.MainActivity")
Assert-Ui '继续训练'
$taskRestored = Read-AppState
if ($taskRestored.session.elapsedMillis -ne $taskPaused.session.elapsedMillis) {
    throw 'The paused position changed after process recreation'
}
Save-Screenshot 'paused.png'
$taskChecks.Add('Process restart restores the plan and a paused session')

Tap-Text '继续训练'
Start-Sleep -Seconds 20
Assert-Ui '训练完成'
Assert-Ui '再练一次'
$taskFinished = Read-AppState
if ($taskFinished.session.status -ne 'COMPLETED' -or $taskFinished.session.elapsedMillis -ne 19000) {
    throw 'The complete two-round session did not end at 19 seconds'
}
Save-Screenshot 'completed.png'
$taskChecks.Add('A two-round workout completes automatically at the correct total duration')

# Delete only the plan created by this script and restore the original selection.
Tap-Description '重置训练'
Tap-Text '我的计划'
$taskUi = Read-Ui
if (-not ($taskUi.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq '删除QA Short' })) {
    $null = Invoke-Device @('shell', 'input', 'touchscreen', 'swipe', '720', '2600', '720', '1200', '350')
}
Tap-Description '删除QA Short'
Assert-Ui '删除这个计划？'
Tap-Text '删除'
$taskCleaned = Wait-AppState { param($snapshot) $snapshot.plans.Count -eq $taskInitial.plans.Count }
if ($taskCleaned.plans.name -contains 'QA Short' -or $taskCleaned.plans.Count -ne $taskInitial.plans.Count) {
    throw 'Temporary QA plan cleanup failed'
}
$taskChecks.Add('Plan deletion requires confirmation and removes only the selected plan')

Tap-Text '训练'
$taskChecks | Set-Content -LiteralPath (Join-Path $taskArtifacts 'device-checks.txt') -Encoding utf8
Write-Output ($taskChecks -join "`n")
