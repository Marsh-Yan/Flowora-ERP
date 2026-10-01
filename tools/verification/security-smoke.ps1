param([string]$Username = 'admin@demo.flowora')
# Fixed loopback endpoint; use only with DB_URL=127.0.0.1:13306/audit_flowora.
# Fresh synthetic users are disabled on exit. No credentials/cookies/MFA secrets are logged.
# Requires PowerShell 7 (also available on the CI runner).
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:18080'
if ($env:DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:13306/audit_flowora\?') {
    throw 'Security regression requires the explicitly isolated audit_flowora database'
}
if (-not $env:FLOWORA_R5_HTTP_PASSWORD) { throw 'Set FLOWORA_R5_HTTP_PASSWORD for a synthetic administrator' }
$sessions = [System.Collections.Generic.List[object]]::new()
$users = [System.Collections.Generic.List[string]]::new()
$results = [System.Collections.Generic.List[string]]::new()
$run = [guid]::NewGuid().ToString('N').Substring(0, 12)
function Check($ok, $message) { if (-not $ok) { throw $message } }
function Pass($id) { $results.Add($id); Write-Output "PASS $id" }
function New-Client {
    $client = [pscustomobject]@{ Web = [Microsoft.PowerShell.Commands.WebRequestSession]::new() }
    $sessions.Add($client)
    return $client
}
function Request($client, $method, $path, $body = $null, $expected = 200, $code = $null) {
    $args = @{ Uri = "$base$path"; Method = $method; WebSession = $client.Web; SkipHttpErrorCheck = $true }
    if ($method -ne 'GET') {
        $csrf = Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web
        $args.Headers = @{ 'X-XSRF-TOKEN' = $csrf.data.token; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
        $args.ContentType = 'application/json'
        $args.Body = $body | ConvertTo-Json -Depth 12
    }
    $response = Invoke-WebRequest @args
    Check ([int]$response.StatusCode -eq $expected) "$method $path expected $expected, received $($response.StatusCode)"
    $json = $response.Content | ConvertFrom-Json
    if ($code) { Check ($json.code -eq $code) "$method $path returned an unexpected error code" }
    if ($expected -ge 400) { return $json }
    return $json.data
}
function Login($client, $name, $password, $mfa = $null, $expected = 200, $legacy = $false, $code = $null) {
    $path = if ($legacy) { '/api/v1/auth/login' } else { '/api/v2/session/login' }
    Request $client POST $path @{ username = $name; password = $password; mfaCode = $mfa } $expected $code
}
function Totp($secret, [long]$step = [Math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 30)) {
    # RFC 4648 Base32 and RFC 6238 SHA-1, matching the configured 30-second factor.
    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bytes = [System.Collections.Generic.List[byte]]::new()
    $buffer = 0; $bits = 0
    foreach ($char in $secret.ToUpperInvariant().TrimEnd('=').ToCharArray()) {
        $value = $alphabet.IndexOf($char)
        Check ($value -ge 0) 'Invalid synthetic factor encoding'
        $buffer = ($buffer -shl 5) -bor $value; $bits += 5
        if ($bits -ge 8) { $bits -= 8; $bytes.Add([byte](($buffer -shr $bits) -band 255)); $buffer = $buffer -band ((1 -shl $bits) - 1) }
    }
    $counter = [BitConverter]::GetBytes($step)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($counter) }
    $hmac = [System.Security.Cryptography.HMACSHA1]::new($bytes.ToArray())
    try { $hash = $hmac.ComputeHash($counter) } finally { $hmac.Dispose() }
    $offset = $hash[19] -band 15
    $value = (([int]$hash[$offset] -band 127) -shl 24) -bor ([int]$hash[$offset + 1] -shl 16) -bor ([int]$hash[$offset + 2] -shl 8) -bor [int]$hash[$offset + 3]
    return ($value % 1000000).ToString('D6')
}
function Upload($client, $path, $filename, $type, $expected = 200) {
    $csrf = (Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web).data.token
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = $client.Web.Cookies
    $http = [System.Net.Http.HttpClient]::new($handler)
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        $file = [System.Net.Http.ByteArrayContent]::new([Text.Encoding]::UTF8.GetBytes('R5 synthetic attachment'))
        $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new($type)
        $form.Add($file, 'file', $filename)
        $http.DefaultRequestHeaders.Add('X-XSRF-TOKEN', $csrf)
        $response = $http.PostAsync("$base$path", $form).GetAwaiter().GetResult()
        try {
            Check ([int]$response.StatusCode -eq $expected) "Attachment upload expected $expected, received $([int]$response.StatusCode)"
            $json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            if ($expected -eq 200) { return $json.data }
        } finally { $response.Dispose() }
    } finally { $form.Dispose(); $http.Dispose() }
}
$admin = New-Client
try {
    Check ((Totp 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ' 1) -eq '287082') 'RFC TOTP vector failed before business writes'
    $identity = Login $admin $Username $env:FLOWORA_R5_HTTP_PASSWORD
    $org = $identity.organizationId
    $roles = @(Request $admin GET '/api/v2/roles')
    $adminRole = @($roles | Where-Object { $_.code -eq 'ADMIN' })[0]
    Check ($null -ne $adminRole) 'Synthetic administrator role missing'
    $restrictedRole = Request $admin POST '/api/v2/roles' @{ code = "R5_$run"; name = 'R5 isolated limited role'; dataScope = 'ALL'; permissions = @('inventory:view'); reason = 'R5 regression fixture' }
    $temporary = 'Temp!' + [guid]::NewGuid().ToString('N')
    $password = 'Fresh!' + [guid]::NewGuid().ToString('N')
    $name = "r5-$run@audit.invalid"
    $user = Request $admin POST '/api/v2/users' @{ username = $name; displayName = 'R5 synthetic user'; temporaryPassword = $temporary; roleIds = @($adminRole.id); reason = 'R5 regression fixture' }
    $users.Add($user.id)
    $client = New-Client
    $me = Login $client $name $temporary
    Check $me.mustChangePassword 'New account did not require password change'
    Request $client GET '/api/v2/roles' $null 403 | Out-Null
    Request $client POST '/api/v2/session/change-password' @{ currentPassword = $temporary; newPassword = 'short' } 400 | Out-Null
    Request $client POST '/api/v2/session/change-password' @{ currentPassword = $temporary; newPassword = $password } | Out-Null
    Request $client GET '/api/v2/session/me' $null 401 | Out-Null
    Pass 'SEC-01 forced password change and post-change reauthentication'

    Login $client $name $password | Out-Null
    $second = New-Client; $third = New-Client; $fourth = New-Client
    Login $second $name $password | Out-Null
    Login $third $name $password | Out-Null
    Login $fourth $name $password $null 409 $false 'SESSION_LIMIT_REACHED' | Out-Null
    Request $second POST '/api/v2/session/logout' @{} | Out-Null
    Login $fourth $name $password | Out-Null
    $activeSessions = @(Request $client GET '/api/v2/session/active')
    Check ($activeSessions.Count -eq 3) 'Active session listing differs from login limit'
    Pass 'SEC-02 active session listing, three-session limit and slot release'
    $changed = 'Changed!' + [guid]::NewGuid().ToString('N')
    Request $client POST '/api/v2/session/change-password' @{ currentPassword = $password; newPassword = $changed } | Out-Null
    foreach ($active in @($client, $third, $fourth)) { Request $active GET '/api/v2/session/me' $null 401 | Out-Null }
    $password = $changed
    Login $client $name $password | Out-Null
    Request $client POST '/api/v2/session/change-password' @{ currentPassword = $password; newPassword = $temporary } 400 'PASSWORD_REUSED' | Out-Null
    Pass 'SEC-03 password history and revocation of every existing session'

    $factor = Request $client POST '/api/v2/session/mfa/enroll' @{}
    $codes = Request $client POST '/api/v2/session/mfa/confirm' @{ code = (Totp $factor.secret) }
    Request $client POST '/api/v2/session/logout' @{} | Out-Null
    foreach ($legacy in @($false, $true)) {
        $unauthenticated = New-Client
        Login $unauthenticated $name $password $null 401 $legacy 'MFA_REQUIRED' | Out-Null
        Request $unauthenticated GET '/api/v2/session/me' $null 401 | Out-Null
        Login $unauthenticated $name $password 'invalid' 401 $legacy 'MFA_CODE_INVALID' | Out-Null
        Login $unauthenticated $name $password (Totp $factor.secret) 200 $legacy | Out-Null
        Request $unauthenticated POST '/api/v2/session/logout' @{} | Out-Null
    }
    Pass 'SEC-04 v1 and v2 MFA enforcement with no session on failure'
    Login $client $name $password (Totp $factor.secret) | Out-Null
    Request $client POST '/api/v2/session/mfa/enroll' @{} 401 | Out-Null
    Request $client POST '/api/v2/session/mfa/enroll' @{ currentCode = (Totp $factor.secret) } | Out-Null
    Request $client POST '/api/v2/session/mfa/confirm' @{ code = 'invalid' } 401 | Out-Null
    Request $client POST '/api/v2/session/mfa/cancel' @{} | Out-Null
    Request $client POST '/api/v2/session/logout' @{} | Out-Null
    Login $client $name $password (Totp $factor.secret) | Out-Null
    Request $client POST '/api/v2/session/logout' @{} | Out-Null
    Login $client $name $password $codes.codes[0] | Out-Null
    Request $client POST '/api/v2/session/logout' @{} | Out-Null
    Login $client $name $password $codes.codes[0] 401 $false 'MFA_CODE_INVALID' | Out-Null
    Pass 'SEC-05 cancelled replacement preserves old factor and recovery code is single-use'

    Login $client $name $password (Totp $factor.secret) | Out-Null
    Request $admin POST "/api/v2/users/$($user.id)/disable" @{ reason = 'R5 fixture cleanup and revocation test' } | Out-Null
    Request $client GET '/api/v2/session/me' $null 401 | Out-Null
    Pass 'SEC-06 account disable revokes active sessions'

    $limitedName = "r5-limited-$run@audit.invalid"
    $limited = Request $admin POST '/api/v2/users' @{ username = $limitedName; displayName = 'R5 limited synthetic user'; temporaryPassword = $temporary; roleIds = @($restrictedRole.id); reason = 'R5 regression fixture' }
    $users.Add($limited.id)
    $reader = New-Client
    Login $reader $limitedName $temporary | Out-Null
    Request $reader POST '/api/v2/session/change-password' @{ currentPassword = $temporary; newPassword = $password } | Out-Null
    Login $reader $limitedName $password | Out-Null
    foreach ($path in @('/api/v2/compat/sales/orders', '/api/v2/compat/projects', '/api/v2/exports')) { Request $reader GET $path $null 403 | Out-Null }
    $search = Request $reader GET '/api/v2/search?query=demo'
    Check (@($search.results).Count -eq 0) 'Search leaked records from inaccessible modules'
    Pass 'SEC-07 compatibility, export and search module permissions'

    $order = Request $admin POST '/api/v2/sales/orders' @{ customerId = 'customer-demo-001'; warehouseId = 'warehouse-demo-001'; currencyCode = 'USD'; note = 'R5 attachment fixture'; lines = @(@{ itemId = 'item-demo-001'; quantity = 1; unitPrice = 10; discountRate = 0; taxRate = 0 }) }
    $attachmentPath = "/api/v2/collaboration/resources/SALES_ORDER/$($order.id)/attachments"
    $attachment = Upload $admin $attachmentPath 'synthetic.txt' 'text/plain'
    Check ($attachment.sizeBytes -eq 23) 'Attachment byte count differs'
    $download = Invoke-WebRequest "$base/api/v2/collaboration/attachments/$($attachment.id)/content" -WebSession $admin.Web
    $content = if ($download.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($download.Content) } else { $download.Content }
    Check ($content -eq 'R5 synthetic attachment' -and $download.Headers['X-Content-Type-Options'] -contains 'nosniff') 'Attachment download bytes or nosniff header differ'
    Upload $admin $attachmentPath 'dangerous.html' 'text/plain' 400
    Upload $admin $attachmentPath 'synthetic.txt' 'application/octet-stream' 400
    Upload $reader $attachmentPath 'synthetic.txt' 'text/plain' 403
    Request $reader GET "/api/v2/collaboration/attachments/$($attachment.id)/content" $null 403 | Out-Null

    $child = Request $admin POST '/api/v2/organizations' @{ parentId = $org; name = "R5 isolated $run"; baseCurrencyCode = 'USD'; timezone = 'UTC'; fiscalYearStartMonth = 1; amountScale = 2; priceScale = 4; quantityScale = 4; taxRoundingMode = 'HALF_UP'; reservationTtlMinutes = 60; expiryWarningDays = 30; defaultApprovalPolicy = 'REQUIRED'; reason = 'R5 regression fixture' }
    Request $reader POST '/api/v2/session/switch-organization' @{ organizationId = $child.id } 403 | Out-Null
    $organizations = @(Request $reader GET '/api/v2/session/organizations')
    Check (@($organizations | Where-Object { $_.id -eq $child.id }).Count -eq 0) 'Membership listing leaked inaccessible organization'
    $switched = Request $admin POST '/api/v2/session/switch-organization' @{ organizationId = $child.id }
    Check ($switched.organizationId -eq $child.id -and $switched.roles -contains 'ADMIN') 'Creator cannot administer created organization'
    Request $admin GET "/api/v2/collaboration/attachments/$($attachment.id)/content" $null 404 | Out-Null
    Request $admin GET $attachmentPath $null 404 | Out-Null
    Request $admin POST '/api/v2/session/switch-organization' @{ organizationId = $org } | Out-Null
    Pass 'SEC-08 organization membership and creator administration'
    Pass 'SEC-09 attachment bytes, types, module permission and cross-organization boundary'

    Request $admin POST "/api/v2/users/$($limited.id)/reset-password" @{ temporaryPassword = '123456789012'; reason = 'R5 weak reset regression' } 400 | Out-Null
    $reset = 'Reset!' + [guid]::NewGuid().ToString('N')
    Request $admin POST "/api/v2/users/$($limited.id)/reset-password" @{ temporaryPassword = $reset; reason = 'R5 reset regression' } | Out-Null
    Request $reader GET '/api/v2/session/me' $null 401 | Out-Null
    $resetIdentity = Login $reader $limitedName $reset
    Check $resetIdentity.mustChangePassword 'Reset account did not require password change'
    Request $reader GET '/api/v2/inventory/summary' $null 403 | Out-Null
    Pass 'SEC-10 administrator reset policy, revocation and forced password change'

    $preflight = Invoke-WebRequest "$base/api/v2/workflows/tasks" -Method Options -SkipHttpErrorCheck -Headers @{ Origin = 'http://127.0.0.1:15173'; 'Access-Control-Request-Method' = 'POST'; 'Access-Control-Request-Headers' = 'If-Match,X-XSRF-TOKEN,Idempotency-Key' }
    Check ($preflight.StatusCode -eq 200 -and ($preflight.Headers['Access-Control-Allow-Headers'] -join ',') -match '(?i)If-Match') 'Workflow preflight omitted If-Match'
    $denied = Invoke-WebRequest "$base/api/v2/workflows/tasks" -Method Options -SkipHttpErrorCheck -Headers @{ Origin = 'https://untrusted.example.invalid'; 'Access-Control-Request-Method' = 'POST' }
    Check ($denied.StatusCode -eq 403) 'CORS accepted an unlisted origin'
    Pass 'SEC-11 workflow CORS preflight and origin boundary'

    $selfRole = Request $admin POST '/api/v2/roles' @{ code = "R5_SELF_$run"; name = 'R5 isolated self role'; dataScope = 'SELF'; permissions = @('sales:view', 'sales:create', 'analytics:export', 'attachment:view'); reason = 'R5 scope regression' }
    $selfName = "r5-self-$run@audit.invalid"
    $selfUser = Request $admin POST '/api/v2/users' @{ username = $selfName; displayName = 'R5 self synthetic user'; temporaryPassword = $temporary; roleIds = @($selfRole.id); reason = 'R5 scope regression' }
    $users.Add($selfUser.id)
    $self = New-Client
    Login $self $selfName $temporary | Out-Null
    Request $self POST '/api/v2/session/change-password' @{ currentPassword = $temporary; newPassword = $password } | Out-Null
    Login $self $selfName $password | Out-Null
    $owned = Request $self POST '/api/v2/sales/orders' @{ customerId = 'customer-demo-001'; warehouseId = 'warehouse-demo-001'; currencyCode = 'USD'; note = 'R5 SELF fixture'; lines = @(@{ itemId = 'item-demo-001'; quantity = 1; unitPrice = 10; discountRate = 0; taxRate = 0 }) }
    Request $self GET "/api/v2/sales/orders/$($owned.id)" | Out-Null
    Request $self GET "/api/v2/sales/orders/$($order.id)" $null 403 | Out-Null
    Request $self GET "/api/v2/collaboration/attachments/$($attachment.id)/content" $null 403 | Out-Null
    $page = Request $self GET '/api/v2/compat/sales/orders?page=0&size=100'
    Check ($page.totalElements -eq 1 -and @($page.content).Count -eq 1 -and $page.content[0].id -eq $owned.id) 'SELF list or pagination total leaked another owner'
    $job = Request $self POST '/api/v2/exports' @{ resourceType = 'SALES'; locale = 'en-US'; filters = @{} }
    for ($attempt = 0; $attempt -lt 30 -and $job.status -in @('PENDING', 'RUNNING'); $attempt++) {
        Start-Sleep -Milliseconds 300
        $job = Request $self GET "/api/v2/exports/$($job.id)"
    }
    Check ($job.status -eq 'COMPLETED' -and $job.rowCount -eq 1) 'SELF export did not complete with exactly one owned row'
    $csv = Invoke-WebRequest "$base/api/v2/exports/$($job.id)/download" -WebSession $self.Web
    $csvText = if ($csv.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($csv.Content) } else { $csv.Content }
    Check ($csvText.Contains($owned.number) -and -not $csvText.Contains($order.number)) 'SELF export leaked another owner'
    Request $admin PUT "/api/v2/roles/$($selfRole.id)" @{ name = 'R5 revoked export'; dataScope = 'SELF'; active = $true; permissions = @('sales:view', 'attachment:view'); reason = 'R5 export permission revocation' } | Out-Null
    Request $self GET "/api/v2/exports/$($job.id)/download" $null 403 | Out-Null
    Pass 'SEC-12 SELF detail, pagination, attachment and export scope with download revocation'
    Write-Output "Security HTTP regression complete: $($results.Count) scenarios; isolated endpoint only."
} finally {
    foreach ($id in $users) {
        try { Request $admin POST "/api/v2/users/$id/disable" @{ reason = 'R5 fixture cleanup' } | Out-Null } catch { Write-Warning 'Could not disable an R5 synthetic fixture; inspect the isolated database.' }
    }
    foreach ($session in $sessions) {
        try { Request $session POST '/api/v2/session/logout' @{} | Out-Null } catch { # Already revoked/anonymous sessions are expected.
        }
    }
}
