param([string]$Username = 'admin@demo.flowora')
# Synthetic writes only to the authorized audit database/API; Redis SCARD is read-only.
$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18080'
if($env:DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/audit_flowora?serverTimezone=UTC'){throw 'Isolated database required'}
if($env:REDIS_HOST -ne '127.0.0.1' -or $env:REDIS_PORT -ne '16379' -or $env:REDIS_PASSWORD){throw 'Isolated Redis required'}
if(-not $env:FLOWORA_R5_HTTP_PASSWORD){throw 'Synthetic administrator password required'}
$run=[guid]::NewGuid().ToString('N').Substring(0,12)
$clients=[Collections.Generic.List[object]]::new();$users=[Collections.Generic.List[object]]::new();$failures=[Collections.Generic.List[string]]::new()
function Check($ok,$message){if(-not $ok){throw $message}}
function Scenario($id,[scriptblock]$test){try{& $test;Write-Output "PASS $id"}catch{$failures.Add($id);Write-Output "FAIL $id : $($_.Exception.Message)"}}
function Client{$c=[pscustomobject]@{Web=[Microsoft.PowerShell.Commands.WebRequestSession]::new()};$clients.Add($c);return $c}
function Request($client,$method,$path,$body=$null,$status=200,$code=$null){
 $args=@{Uri="$base$path";Method=$method;WebSession=$client.Web;SkipHttpErrorCheck=$true}
 if($method -ne 'GET'){
  $csrf=Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web
  $args.Headers=@{'X-XSRF-TOKEN'=$csrf.data.token;'Idempotency-Key'=[guid]::NewGuid().ToString()}
  $args.ContentType='application/json';$args.Body=$body|ConvertTo-Json -Depth 12
 }
 $response=Invoke-WebRequest @args
 Check ([int]$response.StatusCode -eq $status) "$method $path expected $status, received $($response.StatusCode)"
 $json=$response.Content|ConvertFrom-Json
 if($code){Check ($json.code -eq $code) 'Unexpected error code'}
 if($status -ge 400){return $json};return $json.data
}
function Pending($user){
 Check ($user.name -match "^r5i-[a-z0-9-]+-$run@audit\.invalid$") 'Unexpected synthetic Redis key'
 $key="flowora:session-login:pending:$($user.name)"
 $tcp=[Net.Sockets.TcpClient]::new();$tcp.ReceiveTimeout=2000;$tcp.SendTimeout=2000
 try{
  $tcp.Connect('127.0.0.1',16379);$stream=$tcp.GetStream()
  $message="*2`r`n"+'$5'+"`r`nSCARD`r`n"+'$'+[Text.Encoding]::UTF8.GetByteCount($key)+"`r`n$key`r`n"
  $bytes=[Text.Encoding]::UTF8.GetBytes($message);$stream.Write($bytes,0,$bytes.Length)
  $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$false,1024,$true)
  try{$line=$reader.ReadLine();Check ($line -match '^:[0-9]+$') 'Isolated Redis SCARD failed';return [int]$line.Substring(1)}finally{$reader.Dispose()}
 }finally{$tcp.Dispose()}
}
function NoPending($user){
 # Response bytes may arrive just before the outer filter finishes; allow at most 0.5s for request completion.
 for($i=0;$i -lt 50;$i++){if((Pending $user) -eq 0){return};Start-Sleep -Milliseconds 10}
 throw 'A completed login retained a pending slot'
}
function Login($client,$user,$status=200,$legacy=$false){
 $path=if($legacy){'/api/v1/auth/login'}else{'/api/v2/session/login'}
 $code=if($status -eq 409){'SESSION_LIMIT_REACHED'}else{$null}
 Request $client POST $path @{username=$user.name;password=$user.password} $status $code
}
function Logout($client,$legacy=$false){$path=if($legacy){'/api/v1/auth/logout'}else{'/api/v2/session/logout'};Request $client POST $path @{}|Out-Null}
function User($label){
 $name="r5i-$label-$run@audit.invalid";$temporary='Temp!'+[guid]::NewGuid().ToString('N');$password='Fresh!'+[guid]::NewGuid().ToString('N')
 $user=Request $admin POST '/api/v2/users' @{username=$name;displayName='R5I session regression';temporaryPassword=$temporary;roleIds=@($adminRole.id);reason='Isolated login reservation regression'}
 $result=[pscustomobject]@{id=$user.id;name=$name;password=$password};$users.Add($result)
 $client=Client;Request $client POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
 Request $client POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
 return $result
}
$admin=Client
try{
 $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
 $parent=$identity.organizationId;$roles=@(Request $admin GET '/api/v2/roles');$adminRole=@($roles|Where-Object code -eq 'ADMIN')[0]
 Check ($adminRole) 'Synthetic administrator role missing'
 Scenario 'SESSION-01 five immediate login, ID rotation and logout cycles leave no pending slots' {
  $user=User 'cycles';$clock=[Diagnostics.Stopwatch]::StartNew()
  for($i=0;$i -lt 5;$i++){
   $c=Client;Login $c $user|Out-Null;NoPending $user
   Request $c POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null
   Logout $c;NoPending $user
  }
  Check ($clock.Elapsed.TotalSeconds -lt 30) 'Cycles relied on the old reservation TTL'
 }
 Scenario 'SESSION-02 switching actual organizations rotates IDs without consuming another login slot' {
  $script:cross=User 'cross';$c=Client;Login $c $cross|Out-Null
  $script:child=Request $c POST '/api/v2/organizations' @{parentId=$parent;name="R5I $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated session switch matrix'}
  $clock=[Diagnostics.Stopwatch]::StartNew()
  for($i=0;$i -lt 4;$i++){
   $switched=Request $c POST '/api/v2/session/switch-organization' @{organizationId=$child.id}
   Check ($switched.organizationId -eq $child.id) 'Child switch failed'
   $active=@(Request $c GET '/api/v2/session/active');Check ($active.Count -eq 1) 'ID rotation duplicated the active session'
   Request $c POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null
   Logout $c;NoPending $cross;Login $c $cross|Out-Null
  }
  Check ($clock.Elapsed.TotalSeconds -lt 30) 'Cross-organization cycles relied on reservation TTL'
  Logout $c;NoPending $cross
 }
 Scenario 'SESSION-03 legacy v1 login and logout release slots without waiting' {
  $user=User 'legacy'
  for($i=0;$i -lt 5;$i++){$c=Client;Login $c $user 200 $true|Out-Null;NoPending $user;Logout $c $true;NoPending $user}
 }
 Scenario 'SESSION-04 three real sessions block a fourth and one logout immediately releases a slot' {
  $user=User 'limit';$active=@((Client),(Client),(Client));foreach($c in $active){Login $c $user|Out-Null;NoPending $user}
  $extra=Client;Login $extra $user 409|Out-Null;NoPending $user
  foreach($c in $active){Request $c GET '/api/v2/session/me'|Out-Null}
  Check (@(Request $active[0] GET '/api/v2/session/active').Count -eq 3) 'Real session cap changed'
  Logout $active[1];Login $extra $user|Out-Null;NoPending $user
  foreach($c in @($active[0],$active[2],$extra)){Logout $c}
 }
 Scenario 'SESSION-05 eight concurrent logins admit exactly three and rejected clients can retry after logout' {
  $user=User 'parallel';$requests=[Collections.Generic.List[object]]::new()
  for($i=0;$i -lt 8;$i++){
   $c=Client;$csrf=(Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $c.Web).data.token
   $requests.Add([pscustomobject]@{client=$c;csrf=$csrf;name=$user.name;password=$user.password})
  }
  $responses=@($requests|ForEach-Object -ThrottleLimit 8 -Parallel {
   $r=Invoke-WebRequest 'http://127.0.0.1:18080/api/v2/session/login' -Method Post -WebSession $_.client.Web -Headers @{'X-XSRF-TOKEN'=$_.csrf} -ContentType 'application/json' -Body (@{username=$_.name;password=$_.password}|ConvertTo-Json) -SkipHttpErrorCheck
   $json=$r.Content|ConvertFrom-Json
   [pscustomobject]@{client=$_.client;status=[int]$r.StatusCode;code=$json.code}
  })
  $accepted=@($responses|Where-Object status -eq 200);$rejected=@($responses|Where-Object status -eq 409)
  Check ($accepted.Count -eq 3 -and $rejected.Count -eq 5) 'Concurrent login capacity differs from three'
  Check (@($rejected|Where-Object code -ne 'SESSION_LIMIT_REACHED').Count -eq 0) 'Concurrency failure code differs'
  NoPending $user;Check (@(Request $accepted[0].client GET '/api/v2/session/active').Count -eq 3) 'Actual indexed concurrency differs'
  foreach($r in $accepted){Logout $r.client};Login $rejected[0].client $user|Out-Null;NoPending $user;Logout $rejected[0].client
 }
 Scenario 'SESSION-06 reauthentication in the same client rotates the ID without leaving pending slots' {
  $user=User 'reauth';$c=Client;Login $c $user|Out-Null;Login $c $user|Out-Null;NoPending $user
  Check (@(Request $c GET '/api/v2/session/active').Count -eq 1) 'Reauthentication duplicated a session';Logout $c
 }
 Scenario 'SESSION-07 failed credentials create no reservation or authenticated session' {
  $user=User 'failure';$c=Client
  Request $c POST '/api/v2/session/login' @{username=$user.name;password='Wrong!'+[guid]::NewGuid().ToString('N')} 401 'INVALID_CREDENTIALS'|Out-Null
  NoPending $user;Request $c GET '/api/v2/session/me' $null 401|Out-Null;Login $c $user|Out-Null;NoPending $user;Logout $c
 }
 Scenario 'SESSION-08 password change after organization switch revokes all sessions and permits immediate reauthentication' {
  Check ($cross -and $child.id) 'Cross-organization fixture missing'
  $one=Client;$two=Client;Login $one $cross|Out-Null;Login $two $cross|Out-Null
  Request $one POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
  $next='Changed!'+[guid]::NewGuid().ToString('N')
  Request $one POST '/api/v2/session/change-password' @{currentPassword=$cross.password;newPassword=$next}|Out-Null
  foreach($c in @($one,$two)){Request $c GET '/api/v2/session/me' $null 401|Out-Null}
  $cross.password=$next;NoPending $cross;Login $one $cross|Out-Null;NoPending $cross;Logout $one
 }
 if($failures.Count){throw "Session reservation regression failed: $($failures.Count) scenarios"}
 Write-Output 'Session reservation HTTP regression complete: 8 scenarios; isolated endpoint only.'
}finally{
 foreach($user in $users){try{Request $admin POST "/api/v2/users/$($user.id)/disable" @{reason='R5I fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic session fixture cleanup requires inspection'}}
 foreach($c in $clients){try{Logout $c}catch{}}
}
