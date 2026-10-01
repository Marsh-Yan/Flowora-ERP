param([string]$Username='admin@demo.flowora')
# Business writes are restricted to the disposable loopback API and synthetic demo data.
$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18080'
$password=$env:FLOWORA_R3_HTTP_PASSWORD
if (-not $password) { throw 'Set FLOWORA_R3_HTTP_PASSWORD for a synthetic test account' }
$web=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$token=(Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $web).data.token
Invoke-RestMethod "$base/api/v2/session/login" -Method Post -WebSession $web -Headers @{'X-XSRF-TOKEN'=$token} -ContentType 'application/json' -Body (@{username=$Username;password=$password}|ConvertTo-Json)|Out-Null
$token=(Invoke-RestMethod "$base/api/v2/session/csrf" -WebSession $web).data.token
function Call($path,$body=$null,$key=[guid]::NewGuid().ToString()) {
    if($null -eq $body){return (Invoke-RestMethod "$base$path" -WebSession $web).data}
    return (Invoke-RestMethod "$base$path" -Method Post -WebSession $web -Headers @{'X-XSRF-TOKEN'=$token;'Idempotency-Key'=$key} -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 12)).data
}
function Check($condition,$message){if(-not $condition){throw $message}}
function Status($path,$body,$expected){try{Call $path $body|Out-Null;throw 'Unexpected success'}catch{if(-not $_.Exception.Response){throw};Check ([int]$_.Exception.Response.StatusCode -eq $expected) "Wrong rejection status for $path"}}
function Invoice($type,$party,$rate,$price,$sources=@(),$count=1) {
    $rows=@();for($i=0;$i -lt $count;$i++){$rows+=@{description='R3 isolated browser invoice';quantity=1;unitPrice=$price;discountRate=0;taxRate=0;sources=$sources}}
    return Call '/api/v2/finance/invoices' @{documentType=$type;partyId=$party;businessDate=$date;accountingDate=$date;dueDate=$date;exchangeRateDate=$date;currencyCode='EUR';exchangeRate=$rate;lines=$rows}
}
try {
    $date=Get-Date -Format 'yyyy-MM-dd'
    Status '/api/v2/finance/dashboard' $null 400
    Status '/api/v2/finance/dashboard?from=bad&to=bad' $null 400
    Status '/api/v2/finance/unknown-audit-path' $null 404
    $po=Call '/api/v2/procurement/orders' @{supplierId='supplier-demo-001';warehouseId='warehouse-demo-003';currencyCode='EUR';note='R3 source smoke';lines=@(@{itemId='item-demo-001';quantity=1;unitPrice=10;discountRate=0;taxRate=0})}
    $po=Call "/api/v2/procurement/orders/$($po.id)/confirm?version=0" @{}
    $receipt=Call '/api/v2/compat/inventory/receipts' @{purchaseOrderId=$po.id;purchaseOrderLineId=$po.lines[0].id;warehouseId=$po.warehouseId;quantity=1;unitCost=10}
    $movement=@((Call '/api/v2/inventory/trace?itemId=item-demo-001').movements|Where-Object{$_.sourceId -eq $receipt.id})[0]
    $source=@{sourceType='PURCHASE_RECEIPT_LINE';sourceId=$receipt.id;sourceLineId=$movement.lines[0].sourceLineId;quantity=1;amount=10}
    $supplier=Invoice 'SUPPLIER_INVOICE' 'supplier-demo-001' 1 10 @($source)
    Check ((Call "/api/v2/finance/invoices/$($supplier.id)/post?version=0" @{}).status -eq 'POSTED') 'Receipt supplier invoice did not post'
    $tiny=Invoice 'SALES_INVOICE' 'customer-demo-001' 0.5 0.0001 @() 2
    Status "/api/v2/finance/invoices/$($tiny.id)/post?version=0" @{} 409
    Check ((Call "/api/v2/finance/invoices/$($tiny.id)").status -eq 'DRAFT') 'Rounding failure changed invoice'
    $invoice=Invoice 'SALES_INVOICE' 'customer-demo-001' 1 100
    $invoice=Call "/api/v2/finance/invoices/$($invoice.id)/post?version=0" @{}
    $payment=Call '/api/v2/finance/payments' @{paymentType='RECEIPT';partyId='customer-demo-001';businessDate=$date;accountingDate=$date;exchangeRateDate=$date;currencyCode='EUR';exchangeRate=2;amount=100;reference='R3 FX smoke'}
    $payment=Call "/api/v2/finance/payments/$($payment.id)/post?version=0" @{}
    $allocation=Call "/api/v2/finance/payments/$($payment.id)/allocations" @{invoiceId=$invoice.id;amount=100}
    $key=[guid]::NewGuid().ToString()
    Call "/api/v2/finance/allocations/$($allocation.id)/reverse" @{reason='R3 synthetic reversal'} $key|Out-Null
    Call "/api/v2/finance/allocations/$($allocation.id)/reverse" @{reason='R3 synthetic reversal'} $key|Out-Null
    $journals=@(Call "/api/v2/finance/journals?from=$date&to=$date")
    $receiptJournal=@($journals|Where-Object{$_.sourceType -eq 'PURCHASE_RECEIPT' -and $_.sourceId -eq $receipt.id})[0]
    $supplierJournal=@($journals|Where-Object{$_.sourceType -eq 'SUPPLIER_INVOICE' -and $_.sourceId -eq $supplier.id})[0]
    $accrual=@($receiptJournal.lines|Where-Object{$_.credit -gt 0})[0].accountCode
    Check (@($supplierJournal.lines|Where-Object{$_.debit -gt 0 -and $_.accountCode -eq $accrual}).Count -eq 1) 'Supplier invoice did not clear the actual receipt accrual'
    $fx=@($journals|Where-Object{$_.sourceType -eq 'REALIZED_EXCHANGE' -and $_.sourceId -eq $allocation.id})[0]
    $reverse=@($journals|Where-Object{$_.reversalOfId -eq $fx.id})
    Check ($reverse.Count -eq 1) 'FX reversal missing or duplicated'
    foreach($journal in @($fx,$reverse[0])) {
        $dr=($journal.lines|Measure-Object baseDebit -Sum).Sum;$cr=($journal.lines|Measure-Object baseCredit -Sum).Sum
        Check ($dr -eq $cr) 'Base journal not balanced'
    }
    Check ($fx.lines[0].baseDebit -eq $reverse[0].lines[0].baseCredit) 'FX reversal did not offset original'
    Check ((Call "/api/v2/finance/invoices/$($invoice.id)").allocatedAmount -eq 0) 'Invoice allocation not restored'
    $summary=Call "/api/v2/finance/dashboard?from=$date&to=$date"
    Check ($summary.trialDebit -eq $summary.trialCredit) 'Dashboard trial totals disagree'
    $compat=Call "/api/v2/compat/finance/reports/trial-balance?from=$date&to=$date"
    Check ($compat.totalDebit -eq $summary.trialDebit -and $compat.totalCredit -eq $summary.trialCredit) 'Compatibility trial balance differs from canonical ledger'
    $income=Call "/api/v2/compat/finance/reports/income-statement?from=$date&to=$date"
    Check ($income.total -eq $summary.netIncome) 'Compatibility and canonical net income disagree'
    Write-Output "PASS: supplier source, rounding rejection, FX reversal/replay, 400/404 contract, finance dashboard. Browser fixture: $($invoice.number)"
} finally { Call '/api/v2/session/logout' @{}|Out-Null }
