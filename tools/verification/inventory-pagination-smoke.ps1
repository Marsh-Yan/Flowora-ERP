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
    Request $admin POST '/api/v2/roles' @{code="R5F_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5F $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5f-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5F $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
    $users.Add($user.id); $c=Client
    Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c}
}
$admin=Client
$parent=$null
$child=$null
try {
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $parent=$identity.organizationId
    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $before=Request $admin GET '/api/v2/compat/inventory/summary'
    $child=Request $admin POST '/api/v2/organizations' @{parentId=$parent;name="R5F inventory $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated inventory paging'}
    Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
    $reader=User 'inventory-reader' (Role 'ALL' @('inventory:view'))
    Scenario 'INV-PAGE-01 empty organization returns actual zero aggregates' {
        $empty=Request $reader.client GET '/api/v2/compat/inventory/summary'
        Check ($empty.inventoryValue -eq 0 -and $empty.balanceCount -eq 0 -and $empty.ledgerCount -eq 0) 'Empty summary is not zero'
    }
    # No public finance-setting provisioning endpoint currently exists. Seed only this owned fixture.
    Check ($child.id -match '^[0-9a-fA-F-]{36}$') 'Unexpected fixture organization ID'
    $mysql=if($env:FLOWORA_MYSQL_CLIENT){$env:FLOWORA_MYSQL_CLIENT}else{'mysql'}
    $previousMysqlPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$env:DB_PASSWORD
        & $mysql --no-defaults --host=127.0.0.1 --port=13306 --user=root --database=audit_flowora --execute="INSERT INTO flowora_finance_setting(organization_id,base_currency_code) SELECT id,base_currency_code FROM flowora_organization WHERE id='$($child.id)' AND name='R5F inventory $run';" | Out-Null
        if($LASTEXITCODE -ne 0){throw 'Owned isolated finance fixture setup failed'}
    } finally {
        if($null -eq $previousMysqlPassword){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previousMysqlPassword}
    }
    foreach($account in @(@{code='1400';name='Isolated inventory';type='ASSET'},@{code='5000';name='Isolated variance';type='EXPENSE'})) {
        Request $admin POST '/api/v2/masters/accounts' ($account+@{postingAllowed=$true;active=$true})|Out-Null
    }
    $warehouse=Request $admin POST '/api/v2/masters/warehouses' @{code="R5F_$run";name='Paging warehouse';active=$true}
    for($i=1;$i -le 51;$i++) {
        $item=Request $admin POST '/api/v2/masters/items' @{code="R5F_$run`_$i";name="Paging item $i";type='GOODS';unit='EA';salesPrice=$i;purchasePrice=$i;averageCost=0;taxRate=0;inventoryManaged=$true;active=$true}
        Request $admin POST '/api/v2/compat/inventory/counts' @{warehouseId=$warehouse.id;itemId=$item.id;countedQuantity=1;unitCost=$i}|Out-Null
    }
    Scenario 'INV-PAGE-02 full summary includes row 51 and is the same through both read aliases' {
        foreach($path in @('/api/v1/inventory/summary','/api/v2/compat/inventory/summary')) {
            $summary=Request $reader.client GET $path
            Check ($summary.inventoryValue -eq 1326 -and $summary.balanceCount -eq 51 -and $summary.ledgerCount -eq 51) 'Full aggregate differs from synthetic controls'
        }
    }
    Scenario 'INV-PAGE-03 all balance pages have no duplicates or missing value' {
        $first=Request $reader.client GET '/api/v2/compat/inventory/balances?size=50'
        $last=Request $reader.client GET '/api/v2/compat/inventory/balances?page=1&size=50'
        Check ($first.content.Count -eq 50 -and $last.content.Count -eq 1 -and $last.totalElements -eq 51) 'Balance page boundaries incorrect'
        $rows=@($first.content)+@($last.content)
        Check (@($rows.id|Select-Object -Unique).Count -eq 51) 'Balance pages duplicate or lose rows'
        Check (($rows|Measure-Object inventoryValue -Sum).Sum -eq 1326) 'Paged balance value differs from full summary'
    }
    Scenario 'INV-PAGE-04 all ledger pages have no duplicates or missing rows' {
        $first=Request $reader.client GET '/api/v2/compat/inventory/ledger?size=50'
        $last=Request $reader.client GET '/api/v2/compat/inventory/ledger?page=1&size=50'
        Check ($first.content.Count -eq 50 -and $last.content.Count -eq 1 -and $last.totalElements -eq 51) 'Ledger page boundaries incorrect'
        Check (@((@($first.content)+@($last.content)).id|Select-Object -Unique).Count -eq 51) 'Ledger pages duplicate or lose rows'
        Check ((Request $reader.client GET '/api/v2/compat/inventory/summary').ledgerCount -eq 51) 'Paging changed full summary'
    }
    Scenario 'INV-PAGE-05 summary requires inventory view and ALL scope' {
        $empty=User 'no-permission' (Role 'ALL' @('workflow:view'))
        foreach($path in @('/api/v1/inventory/summary','/api/v2/compat/inventory/summary')){Request $empty.client GET $path $null 403}
        foreach($scope in @('SELF','DEPARTMENT','ASSIGNED')) {
            $scoped=User $scope (Role $scope @('inventory:view'))
            foreach($path in @('/api/v1/inventory/summary','/api/v2/compat/inventory/summary')){Request $scoped.client GET $path $null 403}
        }
    }
    Scenario 'INV-PAGE-06 another organization summary excludes all new fixture rows' {
        Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null
        try {
            $after=Request $admin GET '/api/v2/compat/inventory/summary'
            Check ($after.inventoryValue -eq $before.inventoryValue -and $after.balanceCount -eq $before.balanceCount -and $after.ledgerCount -eq $before.ledgerCount) 'Other organization summary changed'
        } finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null }
    }
    if($failures.Count){throw "Inventory pagination regression failed: $($failures.Count) scenarios"}
    Write-Output 'Inventory pagination HTTP regression complete: 6 scenarios; isolated endpoint only.'
} finally {
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5F fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic inventory fixture cleanup requires inspection'}}
    if($parent){try{Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null}catch{}}
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
