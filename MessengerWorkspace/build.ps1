param(
    [ValidateSet('Debug','Release')] [string]$Configuration = 'Release',
    [ValidateSet('x64')] [string]$Platform = 'x64',
    [switch]$SkipInstaller
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Solution = Join-Path $Root 'MessengerWorkspace.sln'
$ProjectOut = Join-Path $Root "src\MessengerWorkspace\bin\$Platform\$Configuration"
$Dist = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

if (-not (Get-Command nuget.exe -ErrorAction SilentlyContinue)) {
    $nuget = Join-Path $Root '.tools\nuget.exe'
    if (-not (Test-Path $nuget)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $nuget) | Out-Null
        Invoke-WebRequest -UseBasicParsing https://dist.nuget.org/win-x86-commandline/latest/nuget.exe -OutFile $nuget
    }
} else { $nuget = 'nuget.exe' }

& $nuget restore $Solution -PackagesDirectory (Join-Path $Root 'packages')

$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (Test-Path $vswhere) {
    $msbuild = & $vswhere -latest -products * -requires Microsoft.Component.MSBuild -find MSBuild\**\Bin\MSBuild.exe | Select-Object -First 1
}
if (-not $msbuild) { $msbuild = (Get-Command msbuild.exe -ErrorAction SilentlyContinue).Source }
if (-not $msbuild) { throw 'MSBuild.exe not found. Install Visual Studio Build Tools with .NET desktop workload.' }

& $msbuild $Solution /m /t:Rebuild /p:Configuration=$Configuration /p:Platform=$Platform /p:RestorePackages=false
if (-not (Test-Path (Join-Path $ProjectOut 'MessengerWorkspace.exe'))) { throw 'Build did not produce MessengerWorkspace.exe' }

Copy-Item (Join-Path $ProjectOut '*') $Dist -Recurse -Force

if (-not $SkipInstaller) {
    $makensis = (Get-Command makensis.exe -ErrorAction SilentlyContinue).Source
    if (-not $makensis) { throw 'makensis.exe not found. Install NSIS 3.x or run with -SkipInstaller.' }
    & $makensis "/DBUILD_DIR=$ProjectOut" "/DOUT_DIR=$Dist" (Join-Path $Root 'Installer\MessengerWorkspace.nsi')
    if (-not (Test-Path (Join-Path $Dist 'MessengerWorkspaceSetup.exe'))) { throw 'Installer was not created.' }
}
Write-Host "Build completed. Output: $Dist"
