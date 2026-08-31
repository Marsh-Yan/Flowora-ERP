[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database,
  [Parameter(Mandatory)][string]$MySqlExe,
  [Parameter(Mandatory)][string]$DefaultsFile,
  [Parameter(Mandatory)][string]$DatabaseBackup,
  [switch]$ConfirmRestore
)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmRestore) { throw 'Restore is destructive and requires -ConfirmRestore.' }
if (-not (Test-Path -LiteralPath $DatabaseBackup -PathType Leaf)) { throw 'Database backup was not found.' }
$resolvedBackup = (Resolve-Path -LiteralPath $DatabaseBackup).Path
& $MySqlExe "--defaults-extra-file=$DefaultsFile" $Database -e "source $($resolvedBackup.Replace('\','/'))"
if ($LASTEXITCODE -ne 0) { throw 'Database restore failed.' }
Write-Host 'Database restore completed. Run Flyway validate and control totals before starting traffic.'
