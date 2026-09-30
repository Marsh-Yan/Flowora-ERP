param([string]$BaseUrl='http://127.0.0.1:18080',[string]$Username='admin@demo.flowora')
# Destructive business smoke: run only against a disposable audit_flowora API with demo fixtures.
$ErrorActionPreference='Stop'
$base=$BaseUrl
if ($base -ne 'http://127.0.0.1:18080') { throw 'This smoke test only permits the isolated loopback API on port 18080' }
if (-not $env:FLOWORA_R2_HTTP_PASSWORD) { throw 'Set FLOWORA_R2_HTTP_PASSWORD for a synthetic test account' }
$web=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$token=(Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $web).data.token
Invoke-RestMethod "$base/api/v2/session/login" -Method Post -WebSession $web -Headers @{'X-XSRF-TOKEN'=$token} -ContentType 'application/json' -Body (@{username=$Username;password=$env:FLOWORA_R2_HTTP_PASSWORD}|ConvertTo-Json) | Out-Null
$token=(Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $web).data.token
function Call($path,$body=$null) {
    if($null -eq $body){return (Invoke-RestMethod "$base$path" -WebSession $web).data}
    return (Invoke-RestMethod "$base$path" -Method Post -WebSession $web -Headers @{'X-XSRF-TOKEN'=$token;'Idempotency-Key'=[guid]::NewGuid().ToString()} -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 12)).data
}
function Check($condition,$message){if(-not $condition){throw $message}}
function Status($path,$body,$expected){try{Call $path $body|Out-Null; throw 'Unexpected success'}catch{if(-not $_.Exception.Response){throw};Check ([int]$_.Exception.Response.StatusCode -eq $expected) 'Wrong rejection status'}}
try {
$warehouse='warehouse-demo-003';$item='item-demo-001'
$before=@(Call "/api/v2/inventory/availability?warehouseId=$warehouse&itemId=$item")[0].onHand
$traceBefore=@((Call "/api/v2/inventory/trace?itemId=$item").movements).Count
$lines=@(@{itemId=$item;quantity=2;unitPrice=10;discountRate=0;taxRate=0},@{itemId=$item;quantity=3;unitPrice=10;discountRate=0;taxRate=0})
$po=Call '/api/v2/procurement/orders' @{supplierId='supplier-demo-001';warehouseId=$warehouse;currencyCode='USD';note='R2 HTTP multi-line';lines=$lines}
$receipt=@{purchaseOrderId=$po.id;purchaseOrderLineId=$po.lines[0].id;warehouseId=$warehouse;quantity=$po.lines[0].orderedQuantity;unitCost=10}
Status '/api/v2/compat/inventory/receipts' $receipt 409
$draft=Call "/api/v2/procurement/orders/$($po.id)"
Check ($draft.status -eq 'DRAFT' -and $draft.version -eq 0) 'Rejected receipt changed the order state or version'
Check (@($draft.lines|Where-Object{$_.fulfilledQuantity -ne 0}).Count -eq 0) 'Rejected receipt changed line fulfillment'
Check (@((Call "/api/v2/inventory/trace?itemId=$item").movements).Count -eq $traceBefore) 'Rejected receipt posted a movement'
$po=Call "/api/v2/procurement/orders/$($po.id)/confirm?version=0" @{}
$firstReceipt=Call '/api/v2/compat/inventory/receipts' $receipt
Check ((Call "/api/v2/procurement/orders/$($po.id)").status -eq 'PARTIALLY_RECEIVED') 'First line incorrectly completed entire purchase order'
$receipt.purchaseOrderLineId=$po.lines[1].id;$receipt.quantity=$po.lines[1].orderedQuantity
Call '/api/v2/compat/inventory/receipts' $receipt|Out-Null
Check ((Call "/api/v2/procurement/orders/$($po.id)").status -eq 'RECEIVED') 'Purchase order not complete'
$so=Call '/api/v2/sales/orders' @{customerId='customer-demo-001';warehouseId=$warehouse;currencyCode='USD';note='R2 HTTP multi-line';lines=$lines}
$so=Call "/api/v2/sales/orders/$($so.id)/confirm?version=0" @{}
$delivery=@{salesOrderId=$so.id;salesOrderLineId=$so.lines[0].id;warehouseId=$warehouse;quantity=$so.lines[0].orderedQuantity}
$firstDelivery=Call '/api/v2/compat/sales/deliveries' $delivery
Check ((Call "/api/v2/sales/orders/$($so.id)").status -eq 'PARTIALLY_FULFILLED') 'First line incorrectly completed entire sales order'
$delivery.salesOrderLineId=$so.lines[1].id;$delivery.quantity=$so.lines[1].orderedQuantity
Call '/api/v2/compat/sales/deliveries' $delivery|Out-Null
Check ((Call "/api/v2/sales/orders/$($so.id)").status -eq 'FULFILLED') 'Sales order not complete'
$trace=Call "/api/v2/inventory/trace?itemId=$item"
$receiptMovement=@($trace.movements|Where-Object{$_.sourceId -eq $firstReceipt.id})[0]
$deliveryMovement=@($trace.movements|Where-Object{$_.sourceId -eq $firstDelivery.id})[0]
Check ($receiptMovement.lines[0].sourceLineId -ne $firstReceipt.id) 'Receipt movement points to header'
Check ($deliveryMovement.lines[0].sourceLineId -ne $firstDelivery.id) 'Delivery movement points to header'
Call '/api/v2/procurement/returns' @{sourceDocumentId=$firstReceipt.id;sourceMovementId=$receiptMovement.id;disposition='SELLABLE';lines=@(@{movementLineId=$receiptMovement.lines[0].id;quantity=1})}|Out-Null
Call '/api/v2/sales/returns' @{sourceDocumentId=$firstDelivery.id;sourceMovementId=$deliveryMovement.id;disposition='SELLABLE';lines=@(@{movementLineId=$deliveryMovement.lines[0].id;quantity=1})}|Out-Null
$available=@(Call "/api/v2/inventory/availability?warehouseId=$warehouse&itemId=$item")[0]
Check ($available.onHand -eq $before) 'Mixed entry stock balance drift'
$summary=@((Call '/api/v2/compat/inventory/balances?size=100').content|Where-Object{$_.warehouseId -eq $warehouse -and $_.itemId -eq $item})[0]
Check ($summary.quantity -eq $available.onHand) 'Compatibility summary and availability disagree'
$job=Call '/api/v2/exports' @{resourceType='INVENTORY';locale='en-US'}
for($i=0;$i -lt 30 -and $job.status -notin @('COMPLETED','FAILED');$i++){Start-Sleep -Milliseconds 300;$job=Call "/api/v2/exports/$($job.id)"}
Check ($job.status -eq 'COMPLETED') 'Inventory export failed'
$csv=Invoke-RestMethod "$base/api/v2/exports/$($job.id)/download" -WebSession $web
$exportQuantities=@($csv|ConvertFrom-Csv|ForEach-Object{[decimal]$_.quantity}|Sort-Object)
$summaryQuantities=@((Call '/api/v2/compat/inventory/balances?size=100').content|ForEach-Object{[decimal]$_.quantity}|Sort-Object)
Check ($exportQuantities.Count -eq $summaryQuantities.Count) 'Export row count differs from inventory summary'
for($i=0;$i -lt $summaryQuantities.Count;$i++){Check ($exportQuantities[$i] -eq $summaryQuantities[$i]) 'Export quantities differ from inventory summary'}
} finally { Call '/api/v2/session/logout' @{}|Out-Null }
Write-Output 'PASS: draft rejection, multi-line compatibility receiving/shipping, native returns, canonical summary, inventory export'
