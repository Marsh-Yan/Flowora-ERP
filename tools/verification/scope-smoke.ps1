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
    Request $admin POST '/api/v2/roles' @{code="R5C_$scope`_$run";name="R5C $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5c-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5C $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
    $users.Add($user.id); $c=Client
    Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c}
}
function Order($client,$label) {
    Request $client POST '/api/v2/sales/orders' @{customerId='customer-demo-001';warehouseId='warehouse-demo-001';currencyCode='USD';note="R5C $run $label";lines=@(@{itemId='item-demo-001';quantity=1;unitPrice=71;discountRate=0;taxRate=0})}
}
function Card($client,$code) { (Request $client GET '/api/v2/analytics/workspace').cards|Where-Object code -eq $code }
$admin=Client
try {
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $permissions=@('sales:view','sales:create','project:view','analytics:view','analytics:cross-org','analytics:export','attachment:view')
    $deptRole=Role 'DEPARTMENT' $permissions; $selfRole=Role 'SELF' $permissions; $assignedRole=Role 'ASSIGNED' $permissions; $allRole=Role 'ALL' $permissions
    $deptA=Request $admin POST '/api/v2/departments' @{code="R5CA_$run";name='R5C department A';reason='Isolated scope matrix'}
    $deptB=Request $admin POST '/api/v2/departments' @{code="R5CB_$run";name='R5C department B';reason='Isolated scope matrix'}
    $a=User 'dept-a' $deptRole $deptA.id; $b=User 'dept-b' $deptRole $deptB.id
    $none=User 'no-dept' $deptRole; $self=User 'self' $selfRole $deptA.id
    $assigned=User 'assigned' $assignedRole; $all=User 'all' $allRole
    $orderA=Order $a.client 'A'; $orderB=Order $b.client 'B'; $own=Order $self.client 'SELF'
    Scenario 'SCOPE-01 department list, detail and workspace agree' {
        $page=Request $a.client GET '/api/v2/compat/sales/orders?page=0&size=100'
        Check ($page.totalElements -eq 2) 'Department list count differs'
        Request $a.client GET "/api/v2/sales/orders/$($orderA.id)"|Out-Null
        Request $a.client GET "/api/v2/sales/orders/$($orderB.id)" $null 403
        Check ((Card $a.client 'OPEN_SALES').value -eq 2) 'Department workspace count differs'
    }
    Scenario 'SCOPE-02 missing department never falls back to ALL' {
        $page=Request $none.client GET '/api/v2/compat/sales/orders?page=0&size=100'
        Check ($page.totalElements -eq 0) 'Missing department list not empty'
        Check ((Card $none.client 'OPEN_SALES').value -eq 0) 'Missing department workspace leaked organization count'
    }
    Scenario 'SCOPE-03 SELF list and workspace agree' {
        $page=Request $self.client GET '/api/v2/compat/sales/orders?page=0&size=100'
        Check ($page.totalElements -eq 1 -and (Card $self.client 'OPEN_SALES').value -eq 1) 'SELF list/workspace differs'
        Request $self.client GET "/api/v2/sales/orders/$($orderA.id)" $null 403
    }
    Scenario 'SCOPE-04 ASSIGNED orders remain unavailable' {
        Check ((Request $assigned.client GET '/api/v2/compat/sales/orders').totalElements -eq 0) 'Assigned order list leaked'
        Check ((Card $assigned.client 'OPEN_SALES').value -eq 0) 'Assigned order workspace leaked'
        Request $assigned.client POST '/api/v2/exports' @{resourceType='SALES';filters=@{}} 403
    }
    Scenario 'SCOPE-05 DEPARTMENT export contains only permitted owners' {
        $job=Request $a.client POST '/api/v2/exports' @{resourceType='SALES';filters=@{};locale='en-US'}
        for($i=0;$i -lt 30 -and $job.status -in @('PENDING','RUNNING');$i++){Start-Sleep -Milliseconds 300;$job=Request $a.client GET "/api/v2/exports/$($job.id)"}
        Check ($job.status -eq 'COMPLETED' -and $job.rowCount -eq 2) 'Department export row count differs'
        $response=Invoke-WebRequest "$base/api/v2/exports/$($job.id)/download" -WebSession $a.client.Web
        $csv=if($response.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($response.Content)}else{$response.Content}
        Check ($csv.Contains($orderA.number) -and $csv.Contains($own.number) -and -not $csv.Contains($orderB.number)) 'Department export scope differs'
        Request $admin PUT "/api/v2/users/$($a.id)/membership" @{departmentId=$deptB.id;active=$true;roleIds=@($deptRole.id);reason='Isolated department change'}|Out-Null
        Request $a.client GET "/api/v2/exports/$($job.id)/download" $null 403
        Request $admin PUT "/api/v2/users/$($a.id)/membership" @{departmentId=$deptA.id;active=$true;roleIds=@($deptRole.id);reason='Restore isolated department'}|Out-Null
    }
    $date=Get-Date -Format yyyy-MM-dd
    Scenario 'SCOPE-06 analytics module boundaries never expose other domains' {
        $snapshot=Request $all.client GET "/api/v2/analytics/snapshot?from=$date&to=$date"
        foreach($point in $snapshot.trends){Check ($null -eq $point.purchases -and $null -eq $point.revenue -and $null -eq $point.expense -and $null -eq $point.grossProfit) 'Analytics exposed an inaccessible module'}
    }
    Scenario 'SCOPE-07 analytics respects SELF and missing department' {
        $snapshot=Request $self.client GET "/api/v2/analytics/snapshot?from=$date&to=$date"
        Check (($snapshot.trends.sales|Measure-Object -Sum).Sum -eq 71) 'SELF sales trend leaked another owner'
        Check (@((Request $none.client GET "/api/v2/analytics/snapshot?from=$date&to=$date").trends).Count -eq 0) 'Missing department trend leaked'
    }
    $project=Request $admin POST '/api/v2/compat/projects' @{name="R5C project $run";targetDate=$date;managerUserId=$b.id;budgetRevenue=0;budgetCost=0;currencyCode='USD'}
    Request $admin POST "/api/v2/projects/$($project.id)/billing-configuration" @{billingMode='TIME_MATERIAL';contractAmount=0;departmentId=$deptA.id}|Out-Null
    Request $admin POST "/api/v2/compat/projects/$($project.id)/status" @{status='AT_RISK'}|Out-Null
    Request $admin POST "/api/v2/projects/$($project.id)/members" @{userId=$assigned.id;projectRole='MEMBER'}|Out-Null
    Scenario 'SCOPE-08 project department follows project rather than manager department' {
        Request $a.client GET "/api/v2/compat/projects/$($project.id)/summary"|Out-Null
        Request $b.client GET "/api/v2/compat/projects/$($project.id)/summary" $null 403
        Check ((Card $a.client 'AT_RISK_PROJECTS').value -eq 1 -and (Card $b.client 'AT_RISK_PROJECTS').value -eq 0) 'Project department workspace differs from detail'
    }
    Scenario 'SCOPE-09 assigned project member matches list, detail and workspace' {
        Request $assigned.client GET "/api/v2/compat/projects/$($project.id)/summary"|Out-Null
        $page=Request $assigned.client GET '/api/v2/compat/projects'
        Check ($page.totalElements -eq 1 -and (Card $assigned.client 'AT_RISK_PROJECTS').value -eq 1) 'Assigned project workspace differs from list'
    }
    Scenario 'SCOPE-10 scoped users cannot request organization-wide cross-org aggregates' {
        Request $self.client GET '/api/v2/analytics/organizations' $null 403
        Request $assigned.client GET '/api/v2/analytics/organizations' $null 403
    }
    Scenario 'SCOPE-11 cross-org current membership retains module boundaries' {
        $rows=@(Request $all.client GET '/api/v2/analytics/organizations')
        Check ($rows.Count -eq 1 -and $null -eq $rows[0].receivables -and $null -eq $rows[0].payables -and $null -eq $rows[0].cash) 'Cross-org summary exposed inaccessible finance data'
    }
    if($failures.Count){throw "Scope matrix failed: $($failures -join ', ')"}
    Write-Output 'Scope HTTP regression complete: 11 scenarios; isolated endpoint only.'
} finally {
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5C fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic scope fixture cleanup requires inspection'}}
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
