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
    Request $admin POST '/api/v2/roles' @{code="R5J_$scope`_$run`_$([guid]::NewGuid().ToString('N').Substring(0,6))";name="R5J $scope";dataScope=$scope;permissions=$permissions;reason='Isolated scope matrix'}
}
function User($label,$role,$department=$null) {
    $name="r5j-$label-$run@audit.invalid"
    $user=Request $admin POST '/api/v2/users' @{username=$name;displayName="R5J $label";temporaryPassword=$temporary;departmentId=$department;roleIds=@($role.id);reason='Isolated scope matrix'}
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
    Request $admin PUT "/api/v2/roles/$($role.id)" @{name='R5J changed';active=$true;dataScope=$scope;permissions=$permissions;reason='Isolated live permission test'}|Out-Null
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
    $fixture=Request $admin POST '/api/v2/organizations' @{parentId=$parent;name="R5J trade $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated trade fixture'}
    Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$fixture.id}|Out-Null
    $org=$fixture.id
    $supplier=Request $admin POST '/api/v2/masters/suppliers' @{code="S_$run";name='Trade supplier';currencyCode='USD';paymentTermsDays=30;active=$true}
    $customer=Request $admin POST '/api/v2/masters/customers' @{code="C_$run";name='Trade customer';currencyCode='USD';paymentTermsDays=30;active=$true}
    $warehouse=Request $admin POST '/api/v2/masters/warehouses' @{code="W_$run";name='Trade warehouse';active=$true}
    $item=Request $admin POST '/api/v2/masters/items' @{code="I_$run";name='Trade item';type='GOODS';unit='EA';salesPrice=10;purchasePrice=10;averageCost=0;taxRate=0;inventoryManaged=$true;active=$true}
    foreach($account in @(@{code='1000';type='ASSET'},@{code='1100';type='ASSET'},@{code='1400';type='ASSET'},@{code='2000';type='LIABILITY'},@{code='4000';type='REVENUE'},@{code='5000';type='EXPENSE'})) {
        Request $admin POST '/api/v2/masters/accounts' ($account+@{name='Trade account';postingAllowed=$true;active=$true})|Out-Null
    }
    $temporary='Temp!'+[guid]::NewGuid().ToString('N'); $password='Fresh!'+[guid]::NewGuid().ToString('N')
    $view=@('procurement:view','sales:view')
    $all=$view+@('procurement:create','procurement:submit','sales:create','sales:submit','workflow:view','workflow:submit')
    # Use this run's new BUSINESS role to exercise a cached ROLE_BUSINESS authority.
    Request $admin POST '/api/v2/roles' @{code='BUSINESS';name='Synthetic legacy business';dataScope='ALL';permissions=($all+@('inventory:post','finance:post','workflow:approve'));reason='Isolated cached role fixture'}|Out-Null
    $reader=User 'reader'  (Role 'ALL' $view)
    $writerRole=Role 'ALL' $all; $writer=User 'writer' $writerRole
    $createOnly=User 'create-only' (Role 'ALL' @('procurement:create','sales:create'))
    $blindBuyer=User 'blind-buyer' (Role 'ALL' @('procurement:create','procurement:submit'))
    $postRole=Role 'ALL' @('inventory:post','finance:post'); $poster=User 'poster' $postRole
    $selfRole=Role 'SELF' ($view+@('procurement:create','procurement:submit')); $self=User 'self' $selfRole
    $poBody=@{supplierId=$supplier.id;warehouseId=$warehouse.id;itemId=$item.id;quantity=1;unitPrice=10;taxRate=0;note="R5J $run"}
    $soBody=@{customerId=$customer.id;warehouseId=$warehouse.id;itemId=$item.id;quantity=1;unitPrice=10;discountRate=0;taxRate=0;currencyCode='USD';note="R5J $run"}
    $requestBody=@{supplierId=$supplier.id;warehouseId=$warehouse.id;itemId=$item.id;quantity=1;estimatedUnitCost=10;note="R5J $run"}
    $quoteBody=@{customerId=$customer.id;itemId=$item.id;quantity=1;unitPrice=12000;discountRate=0;taxRate=0;currencyCode='USD';validUntil=(Get-Date).AddDays(10).ToString('yyyy-MM-dd');note="R5J $run"}
    $po=Request $writer.client POST '/api/v2/compat/procurement/orders' $poBody
    $so=Request $writer.client POST '/api/v2/compat/sales/orders' $soBody
    $ar=@((Request $admin GET '/api/v2/compat/sales/receivables?size=100').content|Where-Object salesOrderId -eq $so.id)[0]
    Check ($null -ne $ar) 'Synthetic sale has no receivable'
    $delivery=@{salesOrderId=$so.id;salesOrderLineId=$so.lineId;warehouseId=$warehouse.id;quantity=1}
    $payment=@{receivableId=$ar.id;amount=1;method='BANK';paymentDate=(Get-Date -Format 'yyyy-MM-dd');reference="R5J $run"}
    $ownPo=Request $self.client POST '/api/v2/compat/procurement/orders' $poBody

    Scenario 'TRADE-01 custom create capabilities allow direct approved purchase and confirmed sales with receivable and journal' {
        Check ($po.status -eq 'APPROVED' -and $so.status -eq 'CONFIRMED' -and $ar.totalAmount -eq 10) 'Custom order state differs'
        Check ([int](Sql "SELECT COUNT(*) FROM flowora_journal_entry WHERE organization_id='$org' AND source_id='$($so.id)' AND status='POSTED' AND total_debit=total_credit;") -eq 1) 'Synthetic sale did not post one balanced journal'
    }
    Scenario 'TRADE-02 all-scope readers can read both aliases without fixed roles or master permission' {
        foreach($alias in @('/api/v1','/api/v2/compat')) {
            foreach($path in @('procurement/requests','procurement/orders','sales/quotes','sales/orders','sales/deliveries','sales/receivables',"sales/receivables/$($ar.id)/payments")) {
                Request $reader.client GET "$alias/$path"|Out-Null
            }
        }
    }
    Scenario 'TRADE-03 readers cannot create, cancel, approve, deliver or pay and leave exact rows unchanged' {
        Denied {
            Request $reader.client POST '/api/v2/compat/procurement/requests' $requestBody 403
            Request $reader.client POST '/api/v2/compat/procurement/orders' $poBody 403
            Request $reader.client DELETE "/api/v2/compat/procurement/orders/$($po.id)" @{} 403
            Request $reader.client POST '/api/v2/compat/sales/quotes' $quoteBody 403
            foreach($action in @('approve','reject')) { Request $reader.client POST "/api/v2/compat/sales/quotes/unknown/$action" @{} 403 }
            Request $reader.client POST '/api/v2/compat/sales/orders' $soBody 403
            Request $reader.client POST '/api/v2/compat/sales/deliveries' $delivery 403
            Request $reader.client POST '/api/v2/compat/sales/payments' $payment 403
        }
    }
    Scenario 'TRADE-04 create alone cannot implicitly submit requests, quotes or confirmed sales' {
        Denied {
            Request $createOnly.client POST '/api/v2/compat/procurement/orders' $poBody 403
            Request $createOnly.client POST '/api/v2/compat/procurement/requests' $requestBody 403
            Request $createOnly.client POST '/api/v2/compat/sales/quotes' $quoteBody 403
            Request $createOnly.client POST '/api/v2/compat/sales/orders' $soBody 403
        }
    }
    Scenario 'TRADE-05 implicit workflows require workflow view and submit together' {
        foreach($missing in @('workflow:view','workflow:submit')) {
            $incomplete=User ($missing.Replace(':','-')) (Role 'ALL' @($all|Where-Object {$_ -ne $missing}))
            Denied {
                Request $incomplete.client POST '/api/v2/compat/procurement/requests' $requestBody 403
                Request $incomplete.client POST '/api/v2/compat/sales/quotes' $quoteBody 403
            }
        }
    }
    $purchaseRequest=Request $writer.client POST '/api/v2/compat/procurement/requests' $requestBody
    $quote=Request $writer.client POST '/api/v2/compat/sales/quotes' $quoteBody
    Scenario 'TRADE-06 custom submission creates supported request and quote workflows' {
        Check ($purchaseRequest.status -in @('APPROVED','SUBMITTED')) 'Unexpected request status'
        Check ($quote.status -eq 'SUBMITTED' -and $quote.workflowTaskId) 'High-value quote did not create a compatibility approval task'
    }
    $approver=User 'approver' (Role 'ALL' @('sales:view','workflow:view','workflow:approve'))
    $outsider=User 'unassigned' (Role 'ALL' @('sales:view','workflow:view','workflow:approve'))
    Scenario 'TRADE-07 unassigned custom approvers cannot approve or reject and do not change rows' {
        Denied { foreach($action in @('approve','reject')) { Request $outsider.client POST "/api/v2/compat/sales/quotes/$($quote.id)/$action" @{} 403 } }
    }
    Scenario 'TRADE-08 assigned custom approver succeeds without MANAGEMENT role' {
        Request $admin POST "/api/v2/compat/workflow/tasks/$($quote.workflowTaskId)/actions" @{action='TRANSFER';transferToUserId=$approver.id}|Out-Null
        $approved=Request $approver.client POST "/api/v2/compat/sales/quotes/$($quote.id)/approve" @{}
        Check ($approved.status -eq 'APPROVED') 'Assigned custom approval failed'
    }
    Scenario 'TRADE-09 quote sources require sales read even with direct-create and submit permission' {
        $blind=User 'blind-sale' (Role 'ALL' @('sales:create','sales:submit'))
        $linked=$soBody.Clone(); $linked.quoteId=$quote.id
        Denied { Request $blind.client POST '/api/v2/compat/sales/orders' $linked 403 }
    }
    Scenario 'TRADE-10 purchase sources require organization read and reject SELF or blind creators' {
        $linked=$poBody.Clone(); $linked.purchaseRequestId=$purchaseRequest.id
        Denied {
            Request $self.client POST '/api/v2/compat/procurement/orders' $linked 403
            Request $blindBuyer.client POST '/api/v2/compat/procurement/orders' $linked 403
        }
    }
    Scenario 'TRADE-11 self order reads and cancellation preserve owner boundaries on both aliases' {
        foreach($alias in @('/api/v1','/api/v2/compat')) {
            Check ((Request $self.client GET "$alias/procurement/orders?query=$($po.number)").totalElements -eq 0) 'SELF exposed another buyer'
            Check ((Request $self.client GET "$alias/procurement/orders?query=$($ownPo.number)").totalElements -eq 1) 'SELF hid own order'
        }
        Denied { Request $self.client DELETE "/api/v2/compat/procurement/orders/$($po.id)" @{} 403 }
        Request $self.client DELETE "/api/v2/compat/procurement/orders/$($ownPo.id)" @{}|Out-Null
        Check ((Request $admin GET "/api/v2/procurement/orders/$($ownPo.id)").status -eq 'CANCELLED') 'Own order cancellation failed'
    }
    foreach($scope in @('SELF','DEPARTMENT','ASSIGNED')) {
        $scoped=User "shared-$scope" (Role $scope ($all+@('inventory:post','finance:post','workflow:approve')))
        Scenario "TRADE-12-$scope shared reads and confirmed writes reject unsupported scope without side effects" {
            Denied {
                foreach($alias in @('/api/v1','/api/v2/compat')) {
                    foreach($path in @('procurement/requests','sales/quotes','sales/deliveries','sales/receivables',"sales/receivables/$($ar.id)/payments")) {
                        Request $scoped.client GET "$alias/$path" $null 403
                    }
                    Request $scoped.client GET "$alias/procurement/orders"|Out-Null
                    Request $scoped.client GET "$alias/sales/orders"|Out-Null
                }
                Request $scoped.client POST '/api/v2/compat/procurement/requests' $requestBody 403
                Request $scoped.client POST '/api/v2/compat/sales/quotes' $quoteBody 403
                Request $scoped.client POST '/api/v2/compat/sales/orders' $soBody 403
                Request $scoped.client POST '/api/v2/compat/sales/deliveries' $delivery 403
                Request $scoped.client POST '/api/v2/compat/sales/payments' $payment 403
                Request $scoped.client POST "/api/v2/compat/sales/quotes/$($quote.id)/approve" @{} 403
            }
        }
    }
    Scenario 'TRADE-13 existing cached BUSINESS and ADMIN sessions lose actual write permissions immediately' {
        $roles=Request $admin GET '/api/v2/roles'
        foreach($code in @('BUSINESS','ADMIN')) {
            $legacy=User "cached-$code" @($roles|Where-Object code -eq $code)[0]
            Membership $legacy (Role 'ALL' $view)
            Denied {
                Request $legacy.client POST '/api/v2/compat/procurement/orders' $poBody 403
                Request $legacy.client POST '/api/v2/compat/sales/orders' $soBody 403
                Request $legacy.client POST '/api/v2/compat/sales/deliveries' $delivery 403
                Request $legacy.client POST '/api/v2/compat/sales/payments' $payment 403
                Request $legacy.client POST "/api/v2/compat/sales/quotes/$($quote.id)/approve" @{} 403
            }
        }
    }
    Scenario 'TRADE-14 ALL-to-SELF reduction applies to old procurement and sales sessions and shared reads' {
        $reduceRole=Role 'ALL' $view; $reduce=User 'reduce' $reduceRole
        Check ((Request $reduce.client GET "/api/v2/compat/sales/orders?query=$($so.number)").totalElements -eq 1) 'ALL starting scope differs'
        ChangeRole $reduceRole 'SELF' $view
        foreach($alias in @('/api/v1','/api/v2/compat')) {
            Check ((Request $reduce.client GET "$alias/procurement/orders?query=$($po.number)").totalElements -eq 0) 'Cached purchase scope leaked'
            Check ((Request $reduce.client GET "$alias/sales/orders?query=$($so.number)").totalElements -eq 0) 'Cached sales scope leaked'
            Request $reduce.client GET "$alias/procurement/requests" $null 403
            Request $reduce.client GET "$alias/sales/receivables" $null 403
        }
    }
    Scenario 'TRADE-15 inactive membership blocks existing compatibility session' {
        $memberRole=Role 'ALL' $view; $member=User 'inactive' $memberRole
        Membership $member $memberRole $false
        foreach($alias in @('/api/v1','/api/v2/compat')) {
            Request $member.client GET "$alias/procurement/orders" $null 403
            Request $member.client GET "$alias/sales/orders" $null 403
        }
    }
    Scenario 'TRADE-16 custom inventory and finance post capabilities fulfill and pay without sales view' {
        # Build and receive stock for this run; do not depend on pre-existing balances.
        $stockPo=Request $admin POST '/api/v2/procurement/orders' @{supplierId=$supplier.id;warehouseId=$warehouse.id;currencyCode='USD';note="R5J stock $run";lines=@(@{itemId=$item.id;quantity=1;unitPrice=10;discountRate=0;taxRate=0})}
        Request $admin POST "/api/v2/procurement/orders/$($stockPo.id)/confirm?version=0" @{}|Out-Null
        Request $admin POST '/api/v2/compat/inventory/receipts' @{purchaseOrderId=$stockPo.id;purchaseOrderLineId=$stockPo.lines[0].id;warehouseId=$warehouse.id;quantity=1;unitCost=10}|Out-Null
        $posted=Request $poster.client POST '/api/v2/compat/sales/deliveries' $delivery
        Check ($posted.status -eq 'POSTED') 'Custom warehouse posting failed'
        $paid=Request $poster.client POST '/api/v2/compat/sales/payments' $payment
        Check ($paid.amount -eq 1) 'Custom finance payment failed'
        $balance=@((Request $admin GET '/api/v2/compat/sales/receivables?size=100').content|Where-Object id -eq $ar.id)[0]
        Check ($balance.paidAmount -eq 1 -and $balance.outstandingAmount -eq 9) 'Payment did not change the synthetic balance exactly once'
    }
    Scenario 'TRADE-17 live inventory and finance posting revocation blocks old sessions before side effects' {
        ChangeRole $postRole 'ALL' @('workflow:view')
        Denied {
            Request $poster.client POST '/api/v2/compat/sales/deliveries' $delivery 403
            Request $poster.client POST '/api/v2/compat/sales/payments' $payment 403
        }
    }
    Scenario 'TRADE-18 cross-organization source IDs cannot be cancelled, fulfilled, paid or approved' {
        $script:child=Request $admin POST '/api/v2/organizations' @{parentId=$org;name="R5J $run";baseCurrencyCode='USD';timezone='UTC';fiscalYearStartMonth=1;amountScale=2;priceScale=4;quantityScale=4;taxRoundingMode='HALF_UP';reservationTtlMinutes=60;expiryWarningDays=30;defaultApprovalPolicy='REQUIRED';reason='Isolated cross-organization matrix'}
        $before=Snapshot
        try {
            Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null
            Request $admin DELETE "/api/v2/compat/procurement/orders/$($po.id)" @{} 404
            Request $admin POST '/api/v2/compat/sales/deliveries' $delivery 404
            Request $admin POST '/api/v2/compat/sales/payments' $payment 404
            Request $admin POST "/api/v2/compat/sales/quotes/$($quote.id)/approve" @{} 404
        } finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null }
        Check ((Snapshot) -ceq $before) 'Cross-organization call modified parent business rows'
    }
    Scenario 'TRADE-19 legacy v1 writes remain read-only even for an authorized administrator' {
        Denied {
            Request $admin POST '/api/v1/procurement/orders' $poBody 426
            Request $admin POST '/api/v1/sales/orders' $soBody 426
            Request $admin POST '/api/v1/sales/payments' $payment 426
        }
    }
    if($failures.Count){throw "Compatibility trade regression failed: $($failures.Count) scenarios"}
    Write-Output 'Compatibility trade HTTP regression complete: 21 scenarios; isolated endpoint only.'
} finally {
    if($child) {
        try { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$child.id}|Out-Null; Request $admin POST "/api/v2/organizations/$($child.id)/archive" @{reason='R5J fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic child archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$org}|Out-Null }
    }
    foreach($id in $users){try{Request $admin POST "/api/v2/users/$id/disable" @{reason='R5J fixture cleanup'}|Out-Null}catch{Write-Warning 'Synthetic trade fixture cleanup requires inspection'}}
    if($fixture) {
        try { Request $admin POST "/api/v2/organizations/$($fixture.id)/archive" @{reason='R5J trade fixture cleanup'}|Out-Null }
        catch { Write-Warning 'Synthetic trade organization archive requires inspection' }
        finally { Request $admin POST '/api/v2/session/switch-organization' @{organizationId=$parent}|Out-Null }
    }
    foreach($client in $clients){try{Request $client POST '/api/v2/session/logout' @{}|Out-Null}catch{}}
}
