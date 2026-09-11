param([switch]$Demo,[switch]$SkipTests)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Install Node.js 22+ before bootstrap.' }
$taskArgs = @('bootstrap')
if ($Demo) { $taskArgs += '--demo' }
if ($SkipTests) { $taskArgs += '--skip-tests' }
& node (Join-Path $PSScriptRoot 'platform.mjs') @taskArgs
exit $LASTEXITCODE
