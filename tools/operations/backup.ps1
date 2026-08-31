[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database,
  [Parameter(Mandatory)][string]$MySqlDumpExe,
  [Parameter(Mandatory)][string]$DefaultsFile,
  [Parameter(Mandatory)][string]$Destination,
  [Parameter(Mandatory)][string]$AttachmentRoot
)
$ErrorActionPreference = 'Stop'
$destinationRoot = [IO.Path]::GetFullPath($Destination)
$attachmentPath = [IO.Path]::GetFullPath($AttachmentRoot)
if (-not (Test-Path -LiteralPath $MySqlDumpExe -PathType Leaf)) { throw 'mysqldump was not found.' }
if (-not (Test-Path -LiteralPath $DefaultsFile -PathType Leaf)) { throw 'MySQL defaults file was not found.' }
if (-not (Test-Path -LiteralPath $attachmentPath -PathType Container)) { throw 'Attachment root was not found.' }
New-Item -ItemType Directory -Path $destinationRoot -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$databaseFile = Join-Path $destinationRoot "$Database-$stamp.sql"
$attachmentFile = Join-Path $destinationRoot "attachments-$stamp.zip"
& $MySqlDumpExe "--defaults-extra-file=$DefaultsFile" --single-transaction --routines --events --hex-blob --result-file=$databaseFile $Database
if ($LASTEXITCODE -ne 0) { throw 'Database backup failed.' }
Compress-Archive -LiteralPath $attachmentPath -DestinationPath $attachmentFile -CompressionLevel Optimal
Get-FileHash -Algorithm SHA256 -LiteralPath $databaseFile,$attachmentFile | Format-Table Path,Hash
