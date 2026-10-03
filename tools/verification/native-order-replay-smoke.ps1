param([string]$Username = 'admin@demo.flowora')
# Synthetic business writes; only the explicitly authorized audit_flowora API.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:18080'
if ($env:DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/audit_flowora?serverTimezone=UTC') { throw 'Native replay regression requires the isolated database' }
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
function Request($client,$method,$path,$body=$null,$status=200,$key=$null) {
    $args=@{Uri="$base$path";Method=$method;WebSession=$client.Web;SkipHttpErrorCheck=$true}
    if($method -ne 'GET') {
        $csrf=Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web
        $args.Headers=@{'X-XSRF-TOKEN'=$csrf.data.token;'Idempotency-Key'=$(if($key){$key}else{[guid]::NewGuid().ToString()})}
        $args.ContentType='application/json'; $args.Body=$body|ConvertTo-Json -Depth 12
    }
    $response=Invoke-WebRequest @args
    Check ([int]$response.StatusCode -eq $status) "$method $path expected $status, received $($response.StatusCode)"
    if($status -ge 400){
        if($status -eq 409){
            $errorBody=$response.Content|ConvertFrom-Json
            Check ($errorBody.code -eq 'IDEMPOTENCY_CONFLICT') 'Unexpected replay conflict code'
            Check (@($errorBody.args.PSObject.Properties).Count -eq 0 -and $errorBody.fieldErrors.Count -eq 0 -and $null -eq $errorBody.data) 'Replay conflict disclosed result data'
        }
        return
    }
    return ($response.Content|ConvertFrom-Json).data
}
function Role($scope,$permissions) {
    Request $admin POST '/api/v2/roles' @{code="R5L_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5L $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5l-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5L $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
    $users.Add($user.id); $c=Client
    Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c}
}
function Sql($query) {
    $mysql=if($env:FLOWORA_MYSQL_CLIENT){$env:FLOWORA_MYSQL_CLIENT}else{'mysql'}
    $previousMysqlPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$env:DB_PASSWORD
        $result = & $mysql --no-defaults --host=127.0.0.1 --port=13306 --user=root --database=audit_flowora --batch --skip-column-names --execute=$query
        if($LASTEXITCODE -ne 0){throw 'Isolated database read failed'}
        return $result
    } finally {
        if($null -eq $previousMysqlPassword){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previousMysqlPassword}
    }
}

# Compare exact ordered rows, not just HTTP status or counts. SQL remains read-only.
function Snapshot {
    Check ($org -match '^[a-zA-Z0-9_-]+$') 'Unexpected organization ID'
    $tables=@('flowora_purchase_request','flowora_purchase_request_line','flowora_purchase_order','flowora_purchase_order_line',
      'flowora_sales_quote','flowora_sales_quote_line','flowora_sales_order','flowora_sales_order_line','flowora_sales_delivery','flowora_sales_delivery_line',
      'flowora_receivable_document','flowora_payment','flowora_journal_entry','flowora_journal_line','flowora_stock_movement','flowora_stock_movement_line',
      'flowora_inventory_balance_v2','flowora_workflow_task','flowora_workflow_instance','flowora_workflow_approval_task','flowora_activity_event','flowora_comment','flowora_notification','flowora_audit_event','flowora_idempotency_record','flowora_trade_source_line_link','flowora_stock_reservation')
    $queries=$tables|ForEach-Object { "SELECT * FROM $_ WHERE organization_id='$org' ORDER BY id;" }
    return (@(Sql ($queries -join ' ')) -join "`n")
}
function Denied([scriptblock]$calls) {
    $before=Snapshot
    & $calls
    Check ((Snapshot) -ceq $before) 'Denied call changed a business row, balance, workflow or activity'
}
function ChangeRole($role,$scope,$permissions) {
    Request $admin PUT "/api/v2/roles/$($role.id)" @{name='R5L changed';active=$true;dataScope=$scope;permissions=$permissions;reason='Isolated live permission test'}|Out-Null
}
function Membership($user,$role,$active=$true) {
    Request $admin PUT "/api/v2/users/$($user.id)/membership" @{active=$active;roleIds=@($role.id);reason='Isolated live membership test'}|Out-Null
}
$admin=Client; $child=$null; $org=$null; $fixture=$null; $parent=$null
try {
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $parent=$identity.organizationId
    # All users, keys and business rows belong to a fresh synthetic organization.
    $fixture=Request $admin POST '/api/v2/organizations' @{parentId=$parent;name="R5L trade $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated trade fixture'}
    Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$fixture.id}|Out-Null
    $org=$fixture.id
    $supplier=Request $admin POST '/api/v2/masters/suppliers' @{code="S_$run";name='Trade supplier';currencyCode='USD';paymentTermsDays=30;active=$true}
    $customer=Request $admin POST '/api/v2/masters/customers' @{code="C_$run";name='Trade customer';currencyCode='USD';paymentTermsDays=30;active=$true}
    $warehouse=Request $admin POST '/api/v2/masters/warehouses' @{code="W_$run";name='Trade warehouse';active=$true}
    $item=Request $admin POST '/api/v2/masters/items' @{code="I_$run";name='Trade item';type='GOODS';unit='EA';salesPrice=10;purchasePrice=10;averageCost=0;taxRate=0;inventoryManaged=$true;active=$true}

    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $view=@('procurement:view','sales:view'); $submit=@('procurement:submit','sales:submit')
    $full=$view+$submit+@('procurement:create','sales:create')
    $writerRole=Role 'ALL' $full; $writer=User 'writer' $writerRole
    $selfRole=Role 'SELF' $full; $self=User 'self' $selfRole
    $deptA=Request $admin POST '/api/v2/departments' @{code="R5LA_$run";name='Native department A';reason='Isolated native matrix'}
    $deptB=Request $admin POST '/api/v2/departments' @{code="R5LB_$run";name='Native department B';reason='Isolated native matrix'}
    $deptRole=Role 'DEPARTMENT' $full; $dept=User 'department' $deptRole $deptA.id
    $ownerRole=Role 'ALL' $full
    $ownerA=User 'owner-a' $ownerRole $deptA.id; $ownerB=User 'owner-b' $ownerRole $deptB.id
    $createOnly=User 'create-only' (Role 'SELF' @('procurement:create','sales:create'))
    function Body($module) {
        $body=@{warehouseId=$warehouse.id;currencyCode='USD';lines=@(@{itemId=$item.id;quantity=1;unitPrice=10;discountRate=0;taxRate=0},@{itemId=$item.id;quantity=2;unitPrice=5;discountRate=0;taxRate=0})}
        if($module -eq 'sales'){$body.customerId=$customer.id}else{$body.supplierId=$supplier.id}
        return $body
    }
    function Order($client,$module,$key,$status=200) { Request $client POST "/api/v2/$module/orders" (Body $module) $status $key }
    function Pair($client,$module) {
        $key='r5l-'+[guid]::NewGuid().ToString('N')
        $order=Order $client $module $key
        Check ($order.status -eq 'DRAFT' -and $order.lines.Count -eq 2) 'Two-line draft differs'
        return @{key=$key;order=$order}
    }
    function Replay($client,$module,$pair) {
        Denied {
            $again=Order $client $module $pair.key
            Check (($again|ConvertTo-Json -Depth 12 -Compress) -ceq ($pair.order|ConvertTo-Json -Depth 12 -Compress)) 'Replay changed order representation'
        }
    }
    function Collision($client,$module,$owner=$ownerA.client,$readStatus=403) {
        $pair=Pair $owner $module
        Denied {
            Request $client GET "/api/v2/$module/orders/$($pair.order.id)" $null $readStatus|Out-Null
            Order $client $module $pair.key 409
        }
        Replay $owner $module $pair
        $fresh=Pair $client $module
        Check ($fresh.order.id -ne $pair.order.id) 'Fresh key returned another creator order'
    }
    Scenario 'REPLAY-01 ALL creators retry the same key without writes' {
        foreach($module in @('sales','procurement')) { $pair=Pair $writer.client $module; Replay $writer.client $module $pair }
    }
    Scenario 'REPLAY-02 SELF create-only creators retry without acquiring view or submit' {
        foreach($module in @('sales','procurement')) {
            $pair=Pair $createOnly.client $module
            Denied {Request $createOnly.client GET "/api/v2/$module/orders/$($pair.order.id)" $null 403}
            Replay $createOnly.client $module $pair
        }
    }
    Scenario 'REPLAY-03 SELF cannot replay another creator key' {
        foreach($module in @('sales','procurement')) { Collision $self.client $module }
    }
    Scenario 'REPLAY-04 different department cannot replay another creator key' {
        foreach($module in @('sales','procurement')) { Collision $dept.client $module $ownerB.client }
    }
    Scenario 'REPLAY-05 missing department cannot replay another creator key' {
        try {
            Request $admin PUT "/api/v2/users/$($dept.id)/membership" @{active=$true;roleIds=@($deptRole.id);reason='Isolated missing department'}|Out-Null
            foreach($module in @('sales','procurement')) { Collision $dept.client $module }
        } finally {
            Request $admin PUT "/api/v2/users/$($dept.id)/membership" @{departmentId=$deptA.id;active=$true;roleIds=@($deptRole.id);reason='Isolated restore department'}|Out-Null
        }
    }
    Scenario 'REPLAY-06 ASSIGNED has creator replay but cannot claim another creator key' {
        try {
            ChangeRole $selfRole 'ASSIGNED' $full
            foreach($module in @('sales','procurement')) { Collision $self.client $module; $pair=Pair $self.client $module; Replay $self.client $module $pair }
        } finally {ChangeRole $selfRole 'SELF' $full}
    }
    Scenario 'REPLAY-07 ALL can read but cannot take another creator idempotency key' {
        foreach($module in @('sales','procurement')) { Collision $writer.client $module $ownerA.client 200 }
    }
    Scenario 'REPLAY-08 create-only cannot retrieve another creator response' {
        foreach($module in @('sales','procurement')) { Collision $createOnly.client $module }
    }
    Scenario 'REPLAY-09 existing client scope changes preserve creator binding' {
        try {
            $pairs=@{};foreach($module in @('sales','procurement')) {$pairs[$module]=Pair $writer.client $module}
            ChangeRole $writerRole 'SELF' $full
            foreach($module in @('sales','procurement')) { Collision $writer.client $module; Replay $writer.client $module $pairs[$module] }
        } finally {ChangeRole $writerRole 'ALL' $full}
    }
    Scenario 'REPLAY-10 live create revocation denies fresh and replay requests before writes' {
        $pairs=@{};foreach($module in @('sales','procurement')) {$pairs[$module]=Pair $writer.client $module}
        try {
            ChangeRole $writerRole 'ALL' ($view+$submit)
            Denied {foreach($module in @('sales','procurement')) {Order $writer.client $module $pairs[$module].key 403;Order $writer.client $module ([guid]::NewGuid().ToString()) 403}}
        } finally {ChangeRole $writerRole 'ALL' $full}
    }
    Scenario 'REPLAY-11 inactive membership cannot replay with an old session' {
        $pairs=@{};foreach($module in @('sales','procurement')) {$pairs[$module]=Pair $writer.client $module}
        try {
            Membership $writer $writerRole $false
            Denied {foreach($module in @('sales','procurement')) {Order $writer.client $module $pairs[$module].key 403}}
        } finally {Membership $writer $writerRole $true}
    }
    Scenario 'REPLAY-12 parent organization keys do not select a parent response from a child' {
        $parentOrg=$org; $keys=@{}
        foreach($module in @('sales','procurement')) {$keys[$module]=(Pair $admin $module).key}
        $script:child=Request $admin POST '/api/v2/organizations' @{parentId=$org;name="R5L child $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated replay organization boundary'}
        $before=Snapshot
        try {
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
            $script:org=$child.id
            Denied {foreach($module in @('sales','procurement')) {Order $admin $module $keys[$module] 404}}
        } finally {
            $script:org=$parentOrg
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null
        }
        Check ((Snapshot) -ceq $before) 'Child request changed parent order or key'
    }
    Scenario 'REPLAY-13 same text key is independent across sales and purchase operations' {
        $key='r5l-'+[guid]::NewGuid().ToString('N')
        $sale=Order $self.client 'sales' $key; $purchase=Order $ownerA.client 'procurement' $key
        Check ($sale.id -ne $purchase.id) 'Operation keys collided'
        Replay $self.client 'sales' @{key=$key;order=$sale}
        Replay $ownerA.client 'procurement' @{key=$key;order=$purchase}
    }
    Scenario 'REPLAY-14 key normalization and current confirmed or cancelled state remain compatible' {
        foreach($module in @('sales','procurement')) {
            $pair=Pair $writer.client $module
            $pair.order=Request $writer.client POST "/api/v2/$module/orders/$($pair.order.id)/confirm?version=0" @{}
            $pair.key='  '+$pair.key+'  '; Replay $writer.client $module $pair
            $pair.order=Request $writer.client POST "/api/v2/$module/orders/$($pair.order.id)/cancel?version=1" @{}
            Replay $writer.client $module $pair
        }
    }
    if($failures.Count){throw "Native order replay regression failed: $($failures.Count) scenarios"}
    Write-Output 'Native order replay HTTP regression complete: 14 scenarios; isolated endpoint only.'
} finally {
    if($child) {
        try { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null; Request $admin POST "/api/v2/organizations/$($child.id)/archive" @{reason='R5L fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic child archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null }
    }
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5L fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic trade fixture cleanup requires inspection'}}
    if($fixture) {
        try { Request $admin POST "/api/v2/organizations/$($fixture.id)/archive" @{reason='R5L trade fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic trade organization archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null }
    }
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
