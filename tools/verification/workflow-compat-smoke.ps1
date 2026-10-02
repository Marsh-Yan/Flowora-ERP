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
    Request $admin POST '/api/v2/roles' @{code="R5E_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5E $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5e-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5E $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
    $users.Add($user.id); $c=Client
    Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c}
}
$admin=Client
try {
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $org=$identity.organizationId
    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $basePerms=@('workflow:view','procurement:view')
    $reader=User 'reader' (Role 'ALL' $basePerms)
    $submitter=User 'submitter' (Role 'ALL' ($basePerms+@('workflow:submit')))
    $approverRole=Role 'ALL' ($basePerms+@('workflow:approve'))
    $approver=User 'approver' $approverRole
    $delegate=User 'delegate' (Role 'ALL' ($basePerms+@('workflow:delegate')))
    $outsider=User 'outsider' (Role 'ALL' ($basePerms+@('workflow:approve')))
    $po=Request $admin POST '/api/v2/procurement/orders' @{supplierId='supplier-demo-001';warehouseId='warehouse-demo-001';currencyCode='USD';note="R5E $run";lines=@(@{itemId='item-demo-001';quantity=1;unitPrice=12000;discountRate=0;taxRate=0})}
    function Task($assignee,$client=$admin,$general=$false) {
        $resource=if($general){'GENERAL'}else{'PURCHASE_ORDER'}
        Request $client POST '/api/v2/compat/workflow/tasks' @{resourceType=$resource;resourceId=$po.id;title="R5E $run";description='Synthetic authorization task';amount=12000;assigneeUserId=$assignee}
    }
    function Act($client,$task,$action,$status=200,$target=$null,$comment=$null) {
        $resourcePath="/api/v2/compat/workflow/resources/$($task.resourceType)/$($task.resourceId)"
        if($status -ge 400) {
            $eventsBefore=(Request $admin GET "$resourcePath/activities").totalElements
            $commentsBefore=(Request $admin GET "$resourcePath/comments").totalElements
        }
        $result=Request $client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action=$action;transferToUserId=$target;comment=$comment} $status
        if($status -ge 400) {
            Check ((Request $admin GET "$resourcePath/activities").totalElements -eq $eventsBefore) 'Rejected action persisted activity'
            Check ((Request $admin GET "$resourcePath/comments").totalElements -eq $commentsBefore) 'Rejected action persisted comment'
        }
        return $result
    }
    function Unchanged($user,$task,$expected) {
        $page=Request $user.client GET '/api/v2/compat/workflow/tasks?size=100'
        $row=$page.content|Where-Object id -eq $task.id
        Check ($row.status -eq $expected) 'Rejected action changed task status'

    }
    Scenario 'WF-COMPAT-01 custom submit permission replaces fixed role names' {
        $task=Task $submitter.id $submitter.client $true
        Check ($task.status -eq 'APPROVED') 'Custom submitter could not create task'
        Act $submitter.client $task 'COMPLETE'|Out-Null
    }
    Scenario 'WF-COMPAT-02 assigned reader cannot approve, reject or transfer' {
        $task=Task $reader.id
        foreach($action in @('APPROVE','REJECT','TRANSFER')){Act $reader.client $task $action 403 $approver.id}
        Unchanged $reader $task 'OPEN'
    }
    Scenario 'WF-COMPAT-03 assigned custom approver succeeds and unassigned approver is denied' {
        $task=Task $approver.id
        Act $outsider.client $task 'APPROVE' 403
        $result=Act $approver.client $task 'APPROVE'
        Check ($result.task.status -eq 'APPROVED') 'Assigned custom approval failed'
        Act $approver.client $task 'COMPLETE' 403
    }
    Scenario 'WF-COMPAT-04 delegate permission does not grant approval' {
        $task=Task $delegate.id
        Act $delegate.client $task 'APPROVE' 403
        $result=Act $delegate.client $task 'TRANSFER' 200 $approver.id
        Check ($result.task.assigneeUserId -eq $approver.id) 'Delegation did not change assignee'
        Act $delegate.client $task 'TRANSFER' 403 $delegate.id
        Act $approver.client $task 'REJECT'|Out-Null
    }
    Scenario 'WF-COMPAT-05 live approval revocation applies to the existing session' {
        $task=Task $approver.id
        Request $admin PUT "/api/v2/roles/$($approverRole.id)" @{name='R5E revoked';dataScope='ALL';active=$true;permissions=$basePerms;reason='Isolated revocation'}|Out-Null
        Act $approver.client $task 'APPROVE' 403
        Unchanged $approver $task 'OPEN'
    }
    Scenario 'WF-COMPAT-06 approval comment cannot bypass collaboration permission' {
        $task=Task $outsider.id
        Act $outsider.client $task 'APPROVE' 403 $null 'Must not persist'
        Unchanged $outsider $task 'OPEN'
    }
    Scenario 'WF-COMPAT-07 creation requires submit and linked resource access' {
        Request $reader.client POST '/api/v2/compat/workflow/tasks' @{resourceType='GENERAL';resourceId=$po.id;title='Denied';amount=0} 403
        Request $submitter.client POST '/api/v2/compat/workflow/tasks' @{resourceType='PURCHASE_ORDER';resourceId='absent-resource';title='Missing source';amount=12000} 404
        $task=Task $submitter.id $submitter.client $true
        Act $reader.client $task 'CANCEL' 403
        $result=Act $submitter.client $task 'CANCEL'
        Check ($result.task.status -eq 'CANCELLED') 'Requester cancellation failed'
    }
    Scenario 'WF-COMPAT-08 cross-organization task ID cannot be acted on' {
        $task=Task $outsider.id
        $child=Request $admin POST '/api/v2/organizations' @{parentId=$org;name="R5E isolated $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated workflow boundary'}
        try {
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
            Request $admin POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='APPROVE'} 404
            Check ((Request $admin GET '/api/v2/compat/workflow/tasks').totalElements -eq 0) 'Cross-organization inbox leak'
        } finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null }
        Unchanged $outsider $task 'OPEN'
    }
    Scenario 'WF-COMPAT-09 actual custom administrator can cancel without implicit approval' {
        $administrator=User 'administrator' (Role 'ALL' ($basePerms+@('workflow:admin')))
        $task=Task $outsider.id
        Act $administrator.client $task 'APPROVE' 403
        $result=Act $administrator.client $task 'CANCEL'
        Check ($result.task.status -eq 'CANCELLED') 'Custom administrative cancellation failed'
    }
    Scenario 'WF-COMPAT-10 authorized approval comment persists with its decision' {
        $task=Task $outsider.id
        $comment="Authorized R5E $run"
        $result=Act $admin $task 'APPROVE' 200 $null $comment
        Check ($result.task.status -eq 'APPROVED') 'Authorized comment blocked approval'
        $comments=(Request $admin GET "/api/v2/compat/workflow/resources/PURCHASE_ORDER/$($po.id)/comments").content
        Check (@($comments|Where-Object body -eq $comment).Count -eq 1) 'Authorized approval comment missing or duplicated'
    }
    if($failures.Count){throw "Compatibility workflow regression failed: $($failures.Count) scenarios"}
    Write-Output 'Compatibility workflow HTTP regression complete: 10 scenarios; isolated endpoint only.'
} finally {
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5E fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic workflow fixture cleanup requires inspection'}}
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
