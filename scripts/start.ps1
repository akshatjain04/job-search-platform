param([switch]$Demo)
$taskArgs = @('start'); if ($Demo) { $taskArgs += '--demo' }
& node (Join-Path $PSScriptRoot 'platform.mjs') @taskArgs
exit $LASTEXITCODE
