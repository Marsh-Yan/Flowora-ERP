param([string]$Username = 'admin@demo.flowora')
# Synthetic business writes; only the explicitly authorized audit_flowora API.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:18080'
if ($env:DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/audit_flowora?serverTimezone=UTC') { throw 'Scope regression requires the isolated database' }
if (-not $env:FLOWORA_R5_HTTP_PASSWORD) { throw 'Synthetic administrator password required' }
$run = [guid]::NewGuid().ToString('N').Substring(0,12)
$clients = [Collections.Generic.List[object]]::new()
$users = [Collections.Generic.List[string]]::new()
$failures = [Collections.Generic.List[string]]::new()
function Check($ok, $message) { if (-not $ok) { throw $message } }
function Scenario($id, [scriptblock]$test) {
    try { & $test; Write-Output "PASS $id" }
    catch { $failures.Add($id); Write-Output "FAIL $id : $($_.Exception.Message)" }
}
function Client { $c = [pscustomobject]@{Web=[Microsoft.PowerShell.Commands.WebRequestSession]::new()}; $clients.Add($c); return $c }
function Request($client,$method,$path,$body=$null,$status=200) {
    $args=@{Uri="$base$path";Method=$method;WebSession=$client.Web;SkipHttpErrorCheck=$true}
    if($method -ne 'GET') {
        $csrf=Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web
        $args.Headers=@{'X-XSRF-TOKEN'=$csrf.data.token;'Idempotency-Key'=[guid]::NewGuid().ToString()}
        $args.ContentType='application/json'; $args.Body=$body|ConvertTo-Json -Depth 12
    }
    $response=Invoke-WebRequest @args
    Check ([int]$response.StatusCode -eq $status) "$method $path expected $status, received $($response.StatusCode)"
    if($status -ge 400){return}
    return ($response.Content|ConvertFrom-Json).data
}
function Role($scope,$permissions) {
    Request $admin POST '/api/v2/roles' @{code="R5D_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5D $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5d-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5D $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
    $users.Add($user.id); $c=Client
    Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c}
}
$admin=Client
try {
    Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}|Out-Null
    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $view=@('inventory:view','finance:view')
    $post=@('inventory:view','inventory:post','finance:view','finance:post')
    $reader=User 'reader' (Role 'ALL' $view)
    $empty=User 'empty' (Role 'ALL' @('workflow:view'))
    $writerRole=Role 'ALL' $post
    $writer=User 'writer' $writerRole
    $creator=User 'creator' (Role 'ALL' @('inventory:create','finance:create'))
    $manual=@{entryDate=(Get-Date -Format 'yyyy-MM-dd');memo="R5D $run";currencyCode='USD';lines=@(@{accountCode='1000';description='Isolated debit';debit=1;credit=0},@{accountCode='1100';description='Isolated credit';debit=0;credit=1})}
    $balance=(Request $admin GET '/api/v2/compat/inventory/balances?warehouseId=warehouse-demo-001&size=100').content|Where-Object itemId -eq 'item-demo-001'|Select-Object -First 1
    Check ($null -ne $balance) 'Required synthetic balance absent'
    $count=@{warehouseId='warehouse-demo-001';itemId='item-demo-001';countedQuantity=$balance.quantity;unitCost=0}
    $adjustment=@{warehouseId='warehouse-demo-001';itemId='item-demo-001';quantityDelta=1;unitCost=0;reason='Permission rejection only'}
    $reads=@('/api/v1/inventory/balances','/api/v2/compat/inventory/ledger','/api/v2/inventory/availability','/api/v2/inventory/trace?itemId=item-demo-001','/api/v1/finance/journals','/api/v2/compat/finance/periods','/api/v2/finance/invoices','/api/v2/finance/payments','/api/v2/finance/bank/statements',('/api/v2/finance/dashboard?from='+(Get-Date -Format 'yyyy-MM-01')+'&to='+(Get-Date -Format 'yyyy-MM-dd')))
    Scenario 'MODULE-01 ALL readers can read their modules without master or procurement permissions' {
        foreach($path in $reads|Where-Object {$_ -notlike '*trace*'}){Request $reader.client GET $path|Out-Null}
        Request $reader.client GET '/api/v2/inventory/trace?itemId=item-demo-001' $null 403
    }
    Scenario 'MODULE-02 no module permission rejects compatibility and native reads' {
        foreach($path in $reads){Request $empty.client GET $path $null 403}
    }
    foreach($scope in @('SELF','DEPARTMENT','ASSIGNED')) {
        $scoped=User $scope (Role $scope @('inventory:view','inventory:trace','inventory:post','finance:view','finance:post'))
        Scenario "MODULE-03-$scope organization-wide reads and posts reject unsupported row scopes" {
            foreach($path in $reads){Request $scoped.client GET $path $null 403}
            Request $scoped.client POST '/api/v2/compat/finance/journals/manual' $manual 403
            Request $scoped.client POST '/api/v2/compat/inventory/counts' $count 403
        }
    }
    Scenario 'MODULE-04 view and create permissions cannot directly post journals or stock' {
        foreach($client in @($reader.client,$creator.client)){
            Request $client POST '/api/v2/compat/finance/journals/manual' $manual 403
            Request $client POST '/api/v2/compat/inventory/counts' $count 403
            Request $client POST '/api/v2/compat/inventory/adjustments' $adjustment 403
        }
    }
    Scenario 'MODULE-05 custom post permission works without fixed FINANCE or WAREHOUSE role names' {
        $entry=Request $writer.client POST '/api/v2/compat/finance/journals/manual' $manual
        Check ($entry.status -eq 'POSTED' -and $entry.totalDebit -eq 1 -and $entry.totalCredit -eq 1) 'Custom finance posting failed'
        $result=Request $writer.client POST '/api/v2/compat/inventory/counts' $count
        Check ($result.variance -eq 0) 'No-op count changed stock'
    }
    Scenario 'MODULE-06 live permission revocation applies to compatibility writes' {
        Request $admin PUT "/api/v2/roles/$($writerRole.id)" @{name='R5D revoked';dataScope='ALL';active=$true;permissions=$view;reason='Isolated revocation'}|Out-Null
        Request $writer.client POST '/api/v2/compat/finance/journals/manual' $manual 403
        Request $writer.client POST '/api/v2/compat/inventory/counts' $count 403
        Request $writer.client GET '/api/v2/finance/invoices'|Out-Null
    }
    Scenario 'MODULE-07 live scope reduction rejects existing module sessions' {
        Request $admin PUT "/api/v2/roles/$($writerRole.id)" @{name='R5D restricted';dataScope='SELF';active=$true;permissions=$view;reason='Isolated scope reduction'}|Out-Null
        Request $writer.client GET '/api/v2/finance/invoices' $null 403
        Request $writer.client GET '/api/v2/compat/inventory/balances' $null 403
    }
    if($failures.Count){throw "Module regression failed: $($failures.Count) scenarios"}
    Write-Output 'Module HTTP regression complete: 9 scenarios; isolated endpoint only.'
} finally {
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5D fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic module fixture cleanup requires inspection'}}
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}

