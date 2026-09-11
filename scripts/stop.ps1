param([switch]$Demo)
$taskArgs = @('stop'); if ($Demo) { $taskArgs += '--demo' }
& node (Join-Path $PSScriptRoot 'platform.mjs') @taskArgs
exit $LASTEXITCODE
