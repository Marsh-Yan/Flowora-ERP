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
    Request $admin POST '/api/v2/roles' @{code="R5K_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5K $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5k-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5K $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
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
      'flowora_inventory_balance_v2','flowora_workflow_task','flowora_workflow_instance','flowora_workflow_approval_task','flowora_activity_event','flowora_comment','flowora_notification','flowora_audit_event')
    $queries=$tables|ForEach-Object { "SELECT * FROM $_ WHERE organization_id='$org' ORDER BY id;" }
    return (@(Sql ($queries -join ' ')) -join "`n")
}
function Denied([scriptblock]$calls) {
    $before=Snapshot
    & $calls
    Check ((Snapshot) -ceq $before) 'Denied call changed a business row, balance, workflow or activity'
}
function ChangeRole($role,$scope,$permissions) {
    Request $admin PUT "/api/v2/roles/$($role.id)" @{name='R5K changed';active=$true;dataScope=$scope;permissions=$permissions;reason='Isolated live permission test'}|Out-Null
}
function Membership($user,$role,$active=$true) {
    Request $admin PUT "/api/v2/users/$($user.id)/membership" @{active=$active;roleIds=@($role.id);reason='Isolated live membership test'}|Out-Null
}
$admin=Client; $child=$null; $org=$null; $fixture=$null; $parent=$null
try {
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $parent=$identity.organizationId
    # A new organization guarantees the missing-template fallback, even if earlier
    # isolated rehearsals left a catch-all M2 template in the demo organization.
    $fixture=Request $admin POST '/api/v2/organizations' @{parentId=$parent;name="R5K trade $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated trade fixture'}
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
    $reader=User 'reader' (Role 'ALL' $view)
    $blind=User 'submit-only' (Role 'ALL' $submit)
    $selfRole=Role 'SELF' $full; $self=User 'self' $selfRole
    $deptA=Request $admin POST '/api/v2/departments' @{code="R5KA_$run";name='Native department A';reason='Isolated native matrix'}
    $deptB=Request $admin POST '/api/v2/departments' @{code="R5KB_$run";name='Native department B';reason='Isolated native matrix'}
    $deptRole=Role 'DEPARTMENT' $full; $dept=User 'department' $deptRole $deptA.id
    $ownerRole=Role 'ALL' $full
    $ownerA=User 'owner-a' $ownerRole $deptA.id; $ownerB=User 'owner-b' $ownerRole $deptB.id
    $createOnly=User 'create-only' (Role 'SELF' @('procurement:create','sales:create'))
    function Order($client,$module) {
        $body=@{warehouseId=$warehouse.id;currencyCode='USD';lines=@(@{itemId=$item.id;quantity=1;unitPrice=10;discountRate=0;taxRate=0},@{itemId=$item.id;quantity=2;unitPrice=5;discountRate=0;taxRate=0})}
        if($module -eq 'sales'){$body.customerId=$customer.id}else{$body.supplierId=$supplier.id}
        return Request $client POST "/api/v2/$module/orders" $body
    }
    function Allowed($client,$module,$owner=$client) {
        $order=Order $owner $module
        Check ($order.status -eq 'DRAFT' -and $order.lines.Count -eq 2) 'Native two-line draft differs'
        Request $client GET "/api/v2/$module/orders/$($order.id)"|Out-Null
        $confirmed=Request $client POST "/api/v2/$module/orders/$($order.id)/confirm?version=0" @{}
        Check ($confirmed.status -eq 'CONFIRMED' -and $confirmed.version -eq 1) 'Valid native confirm differs'
        $cancelled=Request $client POST "/api/v2/$module/orders/$($order.id)/cancel?version=1" @{}
        Check ($cancelled.status -eq 'CANCELLED' -and $cancelled.version -eq 2) 'Valid confirmed cancellation differs'
        $draft=Order $owner $module
        Check ((Request $client POST "/api/v2/$module/orders/$($draft.id)/cancel?version=0" @{}).status -eq 'CANCELLED') 'Valid draft cancellation differs'
    }
    function Rejected($client,$module,$owner=$admin,$readStatus=403) {
        $order=Order $owner $module
        Denied {
            Request $client GET "/api/v2/$module/orders/$($order.id)" $null $readStatus|Out-Null
            foreach($action in @('confirm','cancel')) { Request $client POST "/api/v2/$module/orders/$($order.id)/$($action)?version=0" @{} 403 }
        }
    }
    function Department($user,$role,$department) {
        Request $admin PUT "/api/v2/users/$($user.id)/membership" @{departmentId=$department;active=$true;roleIds=@($role.id);reason='Isolated native department change'}|Out-Null
    }
    Scenario 'NATIVE-01 custom ALL confirms and cancels accessible drafts and confirmed orders' {
        foreach($module in @('sales','procurement')) { Allowed $writer.client $module $admin }
    }
    Scenario 'NATIVE-02 read-only clients cannot confirm or cancel even readable orders' {
        foreach($module in @('sales','procurement')) { Rejected $reader.client $module $admin 200 }
    }
    Scenario 'NATIVE-03 submit without view cannot confirm or cancel organization orders' {
        foreach($module in @('sales','procurement')) { Rejected $blind.client $module }
    }
    Scenario 'NATIVE-04 SELF owners retain both native actions' {
        foreach($module in @('sales','procurement')) { Allowed $self.client $module }
    }
    Scenario 'NATIVE-05 SELF cannot act on another owner and leaves exact rows unchanged' {
        foreach($module in @('sales','procurement')) { Rejected $self.client $module $ownerA.client }
    }
    Scenario 'NATIVE-06 DEPARTMENT can act on an active same-department owner' {
        foreach($module in @('sales','procurement')) { Allowed $dept.client $module $ownerA.client }
    }
    Scenario 'NATIVE-07 DEPARTMENT cannot act across departments' {
        foreach($module in @('sales','procurement')) { Rejected $dept.client $module $ownerB.client }
    }
    Scenario 'NATIVE-08 DEPARTMENT without a department fails closed' {
        try {
            Department $dept $deptRole $null
            foreach($module in @('sales','procurement')) { Rejected $dept.client $module $ownerA.client }
        } finally { Department $dept $deptRole $deptA.id }
    }
    Scenario 'NATIVE-09 ASSIGNED without an order assignment policy cannot act even as owner' {
        try {
            ChangeRole $selfRole 'ASSIGNED' $full
            foreach($module in @('sales','procurement')) { Rejected $self.client $module $self.client }
        } finally { ChangeRole $selfRole 'SELF' $full }
    }
    Scenario 'NATIVE-10 ALL-to-SELF applies to an existing client before both actions' {
        try {
            ChangeRole $writerRole 'SELF' $full
            foreach($module in @('sales','procurement')) { Rejected $writer.client $module $ownerA.client }
        } finally { ChangeRole $writerRole 'ALL' $full }
    }
    Scenario 'NATIVE-11 current department changes deny old owners and allow new department owners' {
        try {
            Department $dept $deptRole $deptB.id
            foreach($module in @('sales','procurement')) { Rejected $dept.client $module $ownerA.client; Allowed $dept.client $module $ownerB.client }
        } finally { Department $dept $deptRole $deptA.id }
    }
    Scenario 'NATIVE-12 live view revocation denies an existing client before side effects' {
        try {
            ChangeRole $writerRole 'ALL' ($submit+@('sales:create','procurement:create'))
            foreach($module in @('sales','procurement')) { Rejected $writer.client $module $ownerA.client }
        } finally { ChangeRole $writerRole 'ALL' $full }
    }
    Scenario 'NATIVE-13 live submit revocation denies an existing reader before side effects' {
        try {
            ChangeRole $writerRole 'ALL' ($view+@('sales:create','procurement:create'))
            foreach($module in @('sales','procurement')) { Rejected $writer.client $module $ownerA.client 200 }
        } finally { ChangeRole $writerRole 'ALL' $full }
    }
    Scenario 'NATIVE-14 inactive current membership cannot act with an old session' {
        try {
            Membership $writer $writerRole $false
            foreach($module in @('sales','procurement')) { Rejected $writer.client $module $ownerA.client }
        } finally { Membership $writer $writerRole $true }
    }
    Scenario 'NATIVE-15 cross-organization IDs cannot mutate either organization' {
        $parentOrg=$org; $orders=@{}
        foreach($module in @('sales','procurement')) { $orders[$module]=Order $admin $module }
        $script:child=Request $admin POST '/api/v2/organizations' @{parentId=$org;name="R5K $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated native cross-organization matrix'}
        $before=Snapshot
        try {
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
            $script:org=$child.id; $childBefore=Snapshot
            foreach($module in @('sales','procurement')) {
                $id=$orders[$module].id
                Request $admin GET "/api/v2/$module/orders/$id" $null 404
                Request $admin POST "/api/v2/$module/orders/$id/confirm?version=0" @{} 409
                Request $admin POST "/api/v2/$module/orders/$id/cancel?version=0" @{} 404
            }
            Check ((Snapshot) -ceq $childBefore) 'Cross-org native action modified child rows'
        } finally {
            $script:org=$parentOrg
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null
        }
        Check ((Snapshot) -ceq $before) 'Cross-org native action modified parent rows'
    }
    Scenario 'NATIVE-16 accessible version conflicts roll back and existing confirm replay remains unchanged' {
        foreach($module in @('sales','procurement')) {
            $order=Order $writer.client $module
            Denied {
                Request $writer.client POST "/api/v2/$module/orders/$($order.id)/confirm?version=99" @{} 409
                Request $writer.client POST "/api/v2/$module/orders/$($order.id)/cancel?version=99" @{} 409
            }
            Request $writer.client POST "/api/v2/$module/orders/$($order.id)/confirm?version=0" @{}|Out-Null
            Denied {
                $replay=Request $writer.client POST "/api/v2/$module/orders/$($order.id)/confirm?version=99" @{}
                Check ($replay.status -eq 'CONFIRMED' -and $replay.version -eq 1) 'Existing confirmation replay changed'
                Request $writer.client POST "/api/v2/$module/orders/$($order.id)/cancel?version=0" @{} 409
            }
            Check ((Request $writer.client POST "/api/v2/$module/orders/$($order.id)/cancel?version=1" @{}).status -eq 'CANCELLED') 'Latest cancellation failed'
        }
    }
    Scenario 'NATIVE-17 legacy v1 writes remain read-only for authorized admins' {
        Denied {
            foreach($module in @('sales','procurement')) {
                foreach($action in @('confirm','cancel')) { Request $admin POST "/api/v1/$module/orders/unknown/$($action)?version=0" @{} 426 }
            }
        }
    }
    Scenario 'NATIVE-18 SELF create-only retains native drafts without implicit action authority' {
        foreach($module in @('sales','procurement')) { Rejected $createOnly.client $module $createOnly.client }
    }
    if($failures.Count){throw "Native order action regression failed: $($failures.Count) scenarios"}
    Write-Output 'Native order action HTTP regression complete: 18 scenarios; isolated endpoint only.'
} finally {
    if($child) {
        try { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null; Request $admin POST "/api/v2/organizations/$($child.id)/archive" @{reason='R5K fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic child archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null }
    }
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5K fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic trade fixture cleanup requires inspection'}}
    if($fixture) {
        try { Request $admin POST "/api/v2/organizations/$($fixture.id)/archive" @{reason='R5K trade fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic trade organization archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null }
    }
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
