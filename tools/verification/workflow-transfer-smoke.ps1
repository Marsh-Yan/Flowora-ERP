param([string]$Username = 'admin@demo.flowora')
# Synthetic writes exclusively in the authorized audit database/API; SQL comparisons are read-only.
$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18080'
if($env:DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/audit_flowora?serverTimezone=UTC'){throw 'Isolated database required'}
if(-not $env:FLOWORA_R5_HTTP_PASSWORD){throw 'Synthetic administrator password required'}
$run=[guid]::NewGuid().ToString('N').Substring(0,12)
$clients=[Collections.Generic.List[object]]::new(); $users=[Collections.Generic.List[object]]::new(); $failures=[Collections.Generic.List[string]]::new()
function Check($ok,$message){if(-not $ok){throw $message}}
function Scenario($id,[scriptblock]$test){try{& $test; Write-Output "PASS $id"}catch{$failures.Add($id); Write-Output "FAIL $id : $($_.Exception.Message)"}}
function Client{$c=[pscustomobject]@{Web=[Microsoft.PowerShell.Commands.WebRequestSession]::new()};$clients.Add($c);return $c}
function Request($client,$method,$path,$body=$null,$status=200){
    $args=@{Uri="$base$path";Method=$method;WebSession=$client.Web;SkipHttpErrorCheck=$true}
    if($method -ne 'GET'){
        $csrf=Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $client.Web
        $args.Headers=@{'X-XSRF-TOKEN'=$csrf.data.token;'Idempotency-Key'=[guid]::NewGuid().ToString()}
        $args.ContentType='application/json';$args.Body=$body|ConvertTo-Json -Depth 12
    }
    $response=Invoke-WebRequest @args
    Check ([int]$response.StatusCode -eq $status) "$method $path expected $status, received $($response.StatusCode)"
    $json=$response.Content|ConvertFrom-Json
    if($status -ge 400){return $json}
    return $json.data
}
function Sql($query){
    $mysql=if($env:FLOWORA_MYSQL_CLIENT){$env:FLOWORA_MYSQL_CLIENT}else{'mysql'}
    $previous=$env:MYSQL_PWD
    try{$env:MYSQL_PWD=$env:DB_PASSWORD; $result=& $mysql --no-defaults --host=127.0.0.1 --port=13306 --user=root --database=audit_flowora --batch --skip-column-names --execute=$query; if($LASTEXITCODE -ne 0){throw 'Isolated SQL read failed'};return $result}
    finally{if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous}}
}
function SwitchOrg($org){Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null;$script:currentOrg=$org}
function Role($scope,$permissions){Request $admin POST '/api/v2/roles' @{code="R5H_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name='R5H transfer';dataScope=$scope;permissions=$permissions;reason='Isolated workflow target matrix'}}
function User($label,$role){
    $name="r5h-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5H $label";temporaryPassword=$temporary;roleIds=@($role.id);reason='Isolated workflow target matrix'}
    $users.Add([pscustomobject]@{id=$user.id;org=$currentOrg})
    $c=Client; Request $c POST '/api/v2/session/login' @{username=$name;password=$temporary}|Out-Null
    Request $c POST '/api/v2/session/change-password' @{currentPassword=$temporary;newPassword=$password}|Out-Null
    Request $c POST '/api/v2/session/login' @{username=$name;password=$password}|Out-Null
    return [pscustomobject]@{id=$user.id;client=$c;role=$role;username=$name}
}
function UpdateRole($user,$scope,$permissions,$active=$true){Request $admin PUT "/api/v2/roles/$($user.role.id)" @{name='R5H changed';dataScope=$scope;active=$active;permissions=$permissions;reason='Isolated live target change'}|Out-Null}
function Order($client){Request $client POST '/api/v2/procurement/orders' @{supplierId='supplier-demo-001';warehouseId='warehouse-demo-001';currencyCode='USD';note="R5H $run";lines=@(@{itemId='item-demo-001';quantity=1;unitPrice=12000;discountRate=0;taxRate=0})}}
function Task($source=$po.id){Request $admin POST '/api/v2/compat/workflow/tasks' @{resourceType='PURCHASE_ORDER';resourceId=$source;title="R5H-$run-$([guid]::NewGuid().ToString('N').Substring(0,8))";amount=12000;assigneeUserId=$delegate.id}}
function Snapshot($task){
    Check ($task.id -match '^[0-9a-fA-F-]{36}$' -and $task.resourceId -match '^[0-9a-fA-F-]{36}$' -and $task.title -match '^R5H-[a-z0-9-]+$') 'Unexpected fixture identifiers'
    Sql "SELECT CONCAT(status,':',COALESCE(assignee_user_id,''),':',COALESCE(assignee_role,''),':',version_no,':',(SELECT COUNT(*) FROM flowora_notification WHERE organization_id='$parent' AND message='$($task.title)'),':',(SELECT COUNT(*) FROM flowora_activity_event WHERE organization_id='$parent' AND resource_id='$($task.resourceId)'),':',(SELECT COUNT(*) FROM flowora_audit_event WHERE organization_id='$parent' AND resource_id='$($task.resourceId)'),':',(SELECT COUNT(*) FROM flowora_comment WHERE organization_id='$parent' AND resource_id='$($task.resourceId)'),':',(SELECT COUNT(*) FROM flowora_workflow_task WHERE organization_id='$parent' AND resource_id='$($task.resourceId)')) FROM flowora_workflow_task WHERE id='$($task.id)' AND organization_id='$parent';"
}
function Transfer($task,$target,$status=200,$client=$delegate.client){Request $client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='TRANSFER';transferToUserId=$target} $status}
function Rejected($target){
    $task=Task; $before=Snapshot $task
    $error=Transfer $task $target 409
    Check ($error.code -eq 'WORKFLOW_ASSIGNEE_INVALID') 'Rejected target error code differs'
    Check ((Snapshot $task) -eq $before) 'Rejected transfer persisted state, version, notification, activity, audit or comment'
    $error=Request $admin POST '/api/v2/compat/workflow/tasks' @{resourceType='PURCHASE_ORDER';resourceId=$po.id;title="R5H-$run-rejected";amount=12000;assigneeUserId=$target} 409
    Check ($error.code -eq 'WORKFLOW_ASSIGNEE_INVALID') 'Creation assignment used a different eligibility rule'
    Check ((Snapshot $task) -eq $before) 'Rejected creation persisted a task, activity or audit'
}
$admin=Client; $parent=$null
try{
    $identity=Request $admin POST '/api/v2/session/login' @{username=$Username;password=$env:FLOWORA_R5_HTTP_PASSWORD}
    $parent=$identity.organizationId;$currentOrg=$parent
    $temporary='Temp!'+[guid]::NewGuid().ToString('N');$password='Fresh!'+[guid]::NewGuid().ToString('N')
    $eligible=@('workflow:view','workflow:approve','procurement:view')
    $delegate=User 'delegate' (Role 'ALL' @('workflow:view','workflow:delegate','procurement:view'))
    $approver=User 'approver' (Role 'ALL' $eligible)
    $po=Order $admin
    Scenario 'WF-TARGET-01 nonexistent target rejected without side effects' {Rejected ([guid]::NewGuid().ToString())}
    Scenario 'WF-TARGET-02 foreign organization user rejected' {
        $script:child=Request $admin POST '/api/v2/organizations' @{parentId=$parent;name="R5H $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated target boundary'}
        try{SwitchOrg $child.id;$foreign=User 'foreign' (Role 'ALL' $eligible)}finally{SwitchOrg $parent}
        Rejected $foreign.id
    }
    Scenario 'WF-TARGET-03 disabled account rejected' {
        $target=User 'disabled' (Role 'ALL' $eligible)
        Request $admin POST "/api/v2/users/$($target.id)/disable" @{reason='Isolated disable'}|Out-Null; Rejected $target.id
    }
    Scenario 'WF-TARGET-04 disabled current membership rejected' {
        $target=User 'membership' (Role 'ALL' $eligible)
        Request $admin PUT "/api/v2/users/$($target.id)/membership" @{active=$false;roleIds=@($target.role.id);reason='Isolated membership revocation'}|Out-Null; Rejected $target.id
    }
    Scenario 'WF-TARGET-05 target with neither approval nor delegation rejected' {Rejected (User 'reader' (Role 'ALL' @('workflow:view','procurement:view'))).id}
    Scenario 'WF-TARGET-06 inactive target role rejected' {$target=User 'inactive-role' (Role 'ALL' $eligible);UpdateRole $target 'ALL' $eligible $false;Rejected $target.id}
    Scenario 'WF-TARGET-07 live target permission revocation applies despite existing login' {$target=User 'revoked' (Role 'ALL' $eligible);UpdateRole $target 'ALL' @('workflow:view','procurement:view');Rejected $target.id}
    Scenario 'WF-TARGET-08 target SELF scope does not gain someone elses source' {Rejected (User 'self-outside' (Role 'SELF' $eligible)).id}
    Scenario 'WF-TARGET-09 target lacking source module permission rejected' {Rejected (User 'no-module' (Role 'ALL' @('workflow:view','workflow:approve'))).id}
    Scenario 'WF-TARGET-10 valid custom approver receives one transfer and can approve' {
        $task=Task;$result=Transfer $task $approver.id
        Check ($result.task.assigneeUserId -eq $approver.id -and $result.task.status -eq 'OPEN') 'Valid transfer state incorrect'
        $notes=(Request $approver.client GET '/api/v2/compat/workflow/notifications?size=100').content
        Check (@($notes|Where-Object{$_.type -eq 'WORKFLOW_TRANSFER' -and $_.message -eq $task.title}).Count -eq 1) 'Transfer notification missing or duplicated'
        Request $delegate.client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='TRANSFER';transferToUserId=$delegate.id} 403|Out-Null
        $result=Request $approver.client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='APPROVE'}
        Check ($result.task.status -eq 'APPROVED') 'Eligible assignee could not approve'
    }
    Scenario 'WF-TARGET-11 delegate-only target retains routing without gaining approval' {
        $router=User 'router' (Role 'ALL' @('workflow:view','workflow:delegate','procurement:view'))
        $task=Task;Transfer $task $router.id|Out-Null
        Request $router.client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='APPROVE'} 403|Out-Null
        $result=Transfer $task $approver.id 200 $router.client
        Check ($result.task.assigneeUserId -eq $approver.id) 'Delegate chain failed'
    }
    Scenario 'WF-TARGET-12 active membership permits a user who also belongs to another organization' {
        Check ($child.id) 'Child fixture not available'
        try{SwitchOrg $child.id}finally{SwitchOrg $parent}
        Check (-not [string]::IsNullOrWhiteSpace($identity.id)) 'Administrator fixture identity missing'
        $task=Task;$result=Transfer $task $identity.id
        Check ($result.task.assigneeUserId -eq $identity.id) 'Multi-membership user rejected'
    }
    Scenario 'WF-TARGET-13 legitimate SELF source owner remains eligible' {
        $owner=User 'self-owner' (Role 'SELF' ($eligible+@('procurement:create')))
        $owned=Order $owner.client;$task=Task $owned.id;$result=Transfer $task $owner.id
        Check ($result.task.assigneeUserId -eq $owner.id) 'SELF owner rejected'
    }
    Scenario 'WF-TARGET-14 locked target account rejected' {
        $target=User 'locked' (Role 'ALL' $eligible);$c=Client
        for($i=0;$i -lt 5;$i++){Request $c POST '/api/v2/session/login' @{username=$target.username;password='Wrong!'+[guid]::NewGuid().ToString('N')} 401|Out-Null}
        Request $c POST '/api/v2/session/login' @{username=$target.username;password=$password} 423|Out-Null
        Rejected $target.id
    }
    Scenario 'WF-TARGET-15 target lacking workflow inbox permission rejected' {Rejected (User 'no-inbox' (Role 'ALL' @('workflow:approve','procurement:view'))).id}
    Scenario 'WF-TARGET-16 creation requires the capability matching pending or auto-approved state' {
        $task=Request $admin POST '/api/v2/compat/workflow/tasks' @{resourceType='PURCHASE_ORDER';resourceId=$po.id;title="R5H-$run-create";amount=12000;assigneeUserId=$approver.id}
        Check ($task.status -eq 'OPEN' -and $task.assigneeUserId -eq $approver.id) 'Eligible explicit pending assignment failed'
        $completer=User 'completer' (Role 'ALL' @('workflow:view','workflow:submit'))
        $task=Request $admin POST '/api/v2/compat/workflow/tasks' @{resourceType='GENERAL';resourceId=$po.id;title="R5H-$run-complete";amount=0;assigneeUserId=$completer.id}
        $result=Request $completer.client POST "/api/v2/compat/workflow/tasks/$($task.id)/actions" @{action='COMPLETE'}
        Check ($result.task.status -eq 'COMPLETED') 'Assigned auto-approved completer failed'
        $before=Sql "SELECT COUNT(*) FROM flowora_workflow_task WHERE organization_id='$parent' AND resource_id='$($po.id)';"
        $error=Request $admin POST '/api/v2/compat/workflow/tasks' @{resourceType='GENERAL';resourceId=$po.id;title="R5H-$run-bad-complete";amount=0;assigneeUserId=$approver.id} 409
        Check ($error.code -eq 'WORKFLOW_ASSIGNEE_INVALID') 'Auto-approved assignment did not require submit'
        Check ((Sql "SELECT COUNT(*) FROM flowora_workflow_task WHERE organization_id='$parent' AND resource_id='$($po.id)';") -eq $before) 'Rejected auto-approved creation persisted task'
    }
    Scenario 'WF-TARGET-17 live target scope reduction applies despite existing login' {$target=User 'scope-reduced' (Role 'ALL' $eligible);UpdateRole $target 'SELF' $eligible;Rejected $target.id}
    Scenario 'WF-TARGET-18 ASSIGNED purchase scope does not get widened by assignment' {Rejected (User 'assigned-outside' (Role 'ASSIGNED' $eligible)).id}
    if($failures.Count){throw "Workflow transfer regression failed: $($failures.Count) scenarios"}
    Write-Output 'Workflow assignee HTTP regression complete: 18 scenarios; isolated endpoint only.'
}finally{
    foreach($user in $users){try{SwitchOrg $user.org;Request $admin POST "/api/v2/users/$($user.id)/disable" @{reason='R5H fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic transfer fixture cleanup requires inspection'}}
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
