[CmdletBinding()]
param(
  [ValidateSet('Prepare','Migrate','Verify')][string]$Stage = 'Prepare',
  [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database,
  [Parameter(Mandatory)][string]$MySqlExe,
  [Parameter(Mandatory)][string]$MySqlDumpExe,
  [Parameter(Mandatory)][string]$DefaultsFile,
  [Parameter(Mandatory)][string]$BackupDirectory,
  [string]$FlywayExe,
  [string]$FlywayConfig,
  [switch]$ConfirmMigration
)
$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backupRoot = [IO.Path]::GetFullPath($BackupDirectory)
if (-not (Test-Path -LiteralPath $MySqlExe -PathType Leaf)) { throw 'MySQL client was not found.' }
if (-not (Test-Path -LiteralPath $MySqlDumpExe -PathType Leaf)) { throw 'mysqldump was not found.' }
if (-not (Test-Path -LiteralPath $DefaultsFile -PathType Leaf)) { throw 'MySQL defaults file was not found.' }
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

function Invoke-Query([string]$SqlFile, [string]$OutputFile) {
  $result = & $MySqlExe "--defaults-extra-file=$DefaultsFile" --batch --raw $Database -e "source $($SqlFile.Replace('\','/'))" 2>&1
  if ($LASTEXITCODE -ne 0) { throw "Database check failed: $result" }
  $result | Set-Content -LiteralPath $OutputFile -Encoding utf8
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if ($Stage -eq 'Prepare') {
  $report = Join-Path $backupRoot "preflight-$stamp.tsv"
  Invoke-Query (Join-Path $scriptRoot 'preflight.sql') $report
  Write-Host "Preflight complete: $report"
  exit 0
}
if ($Stage -eq 'Migrate') {
  if (-not $ConfirmMigration) { throw 'Migration requires -ConfirmMigration.' }
  if (-not $FlywayExe -or -not (Test-Path -LiteralPath $FlywayExe -PathType Leaf)) { throw 'Flyway executable is required.' }
  if (-not $FlywayConfig -or -not (Test-Path -LiteralPath $FlywayConfig -PathType Leaf)) { throw 'External Flyway config is required.' }
  $backup = Join-Path $backupRoot "$Database-before-v15-$stamp.sql"
  & $MySqlDumpExe "--defaults-extra-file=$DefaultsFile" --single-transaction --routines --events --hex-blob --result-file=$backup $Database
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $backup)) { throw 'Pre-migration backup failed.' }
  & $FlywayExe "-configFiles=$FlywayConfig" validate
  if ($LASTEXITCODE -ne 0) { throw 'Flyway validation failed.' }
  & $FlywayExe "-configFiles=$FlywayConfig" migrate
  if ($LASTEXITCODE -ne 0) { throw 'Flyway migration failed. Keep the service stopped and follow the rollback runbook.' }
  Write-Host "Migration complete. Backup retained at: $backup"
}
$report = Join-Path $backupRoot "control-totals-$stamp.tsv"
Invoke-Query (Join-Path $scriptRoot 'control-totals.sql') $report
Write-Host "Verification report: $report"
