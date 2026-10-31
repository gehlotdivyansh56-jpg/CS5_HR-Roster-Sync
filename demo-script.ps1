# ==============================================================================
# HR Roster Sync - End-to-End Demonstration Script (PowerShell)
# Case Study 5: Enterprise Idempotent, Out-of-Order, Self-Healing Roster Engine
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$JwtSecret = "MoveInSyncRosterSyncSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong"
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "   MOVEINSYNC HR ROSTER SYNC - END-TO-END DEMO WALKTHROUGH                    " -ForegroundColor Yellow
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host ""

# Helper to generate HMAC-SHA256 JWT in PowerShell without external dependencies
function New-DemoJwtToken {
    param(
        [string]$Subject,
        [string]$TenantId,
        [string]$Role
    )
    $header = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}')).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $now + 86400
    $payloadJson = "{`"sub`":`"$Subject`",`"tenant_id`":`"$TenantId`",`"role`":`"$Role`",`"iat`":$now,`"exp`":$exp}"
    $payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payloadJson)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $toSign = "$header.$payload"

    $hmac = New-Object Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($JwtSecret)
    $sigBytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($toSign))
    $sig = [Convert]::ToBase64String($sigBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return "$toSign.$sig"
}

$TENANT_ID = "11111111-1111-1111-1111-111111111111"
$HR_TOKEN = New-DemoJwtToken -Subject "hr-service" -TenantId $TENANT_ID -Role "HR_SYSTEM"
$TRANS_TOKEN = New-DemoJwtToken -Subject "trans-admin-demo" -TenantId $TENANT_ID -Role "TRANSPORT_ADMIN"
$OPERATOR_TOKEN = New-DemoJwtToken -Subject "operator-demo" -TenantId $TENANT_ID -Role "OPERATOR"
$ADMIN_TOKEN = New-DemoJwtToken -Subject "sys-admin-demo" -TenantId $TENANT_ID -Role "ADMIN"

# Step 0: Health Check
Write-Host "[STEP 0] Checking Service Health at $BaseUrl/actuator/health..." -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 5
    Write-Host " Service is healthy: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host " Service not reachable at $BaseUrl. Please start it using 'mvn spring-boot:run'!" -ForegroundColor Red
    Write-Host " Continuing demo payload preview..." -ForegroundColor Gray
}

# Step 1: Real-Time Daytime Sync (Batch of 2 events)
Write-Host ""
Write-Host "[STEP 1] Ingesting Real-Time Daytime Batch (HTTP 207 Multi-Status)..." -ForegroundColor Cyan
$batchPayload = @{
    events = @(
        @{
            source_employee_id = "EMP-DEMO-01"
            version = 1730300000000
            event_type = "CREATE"
            name = "Aarav Sharma"
            email = "aarav.sharma@corp.com"
            phone = "+919876543210"
            office_code = "BLR-01"
            department = "Engineering"
            shift = "DAY"
        },
        @{
            source_employee_id = "EMP-DEMO-02"
            version = 1730300000000
            event_type = "CREATE"
            name = "Diya Patel"
            email = "diya.patel@corp.com"
            phone = "+919876543211"
            office_code = "BLR-01"
            department = "Product"
            shift = "DAY"
        }
    )
} | ConvertTo-Json -Depth 5

$hrHeaders = @{
    "Authorization" = "Bearer $HR_TOKEN"
    "Content-Type" = "application/json"
    "X-Correlation-Id" = "demo-corr-001"
}

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sync/events" -Method Post -Headers $hrHeaders -Body $batchPayload
    Write-Host " Batch Response:" -ForegroundColor Green
    Write-Host ($resp | ConvertTo-Json -Depth 5) -ForegroundColor DarkGreen
} catch {
    Write-Host " Request failed: $_" -ForegroundColor Yellow
}

# Step 2: Stale / Out-of-Order Event Rejection
Write-Host ""
Write-Host "[STEP 2] Testing Stale Event Rejection (Sending older version V1000 vs stored V1730300000000)..." -ForegroundColor Cyan
$stalePayload = @{
    events = @(
        @{
            source_employee_id = "EMP-DEMO-01"
            version = 1000 # Stale Version!
            event_type = "UPDATE"
            name = "Aarav Sharma Old"
            email = "aarav.sharma@corp.com"
            phone = "+919876543210"
            office_code = "BLR-01"
            department = "Engineering"
            shift = "DAY"
        }
    )
} | ConvertTo-Json -Depth 5

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sync/events" -Method Post -Headers $hrHeaders -Body $stalePayload
    Write-Host " Result (Status shows IGNORED_STALE):" -ForegroundColor Green
    Write-Host ($resp | ConvertTo-Json -Depth 5) -ForegroundColor DarkGreen
} catch {
    Write-Host " Request failed: $_" -ForegroundColor Yellow
}

# Step 3: Transport Admin Coordinate Assignment
Write-Host ""
Write-Host "[STEP 3] Updating Transport Coordinates (Transport Admin updating pickup and drop points)..." -ForegroundColor Cyan
$transportPayload = @{
    pickup_point = "Hebbal Flyover Bus Stop, Gate 2"
    drop_point = "RMZ Infinity Campus"
    special_assistance = "None"
} | ConvertTo-Json

try {
    $tHeaders = @{
        "Authorization" = "Bearer $TRANS_TOKEN"
        "Content-Type" = "application/json"
    }
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-DEMO-01/transport-fields" -Method Put -Headers $tHeaders -Body $transportPayload
    Write-Host " Transport Update Response:" -ForegroundColor Green
    Write-Host ($resp | ConvertTo-Json -Depth 5) -ForegroundColor DarkGreen
} catch {
    Write-Host " Request failed: $_" -ForegroundColor Yellow
}

# Step 4: Field Ownership Integrity (HR Sync must NOT overwrite Transport fields)
Write-Host ""
Write-Host "[STEP 4] Verifying Field Ownership Protection (HR Sync bumping department without wiping transport fields)..." -ForegroundColor Cyan
$hrBumpPayload = @{
    events = @(
        @{
            source_employee_id = "EMP-DEMO-01"
            version = 1730300001000
            event_type = "UPDATE"
            name = "Aarav Sharma"
            email = "aarav.sharma@corp.com"
            phone = "+919876543210"
            office_code = "BLR-01"
            department = "Core Architecture"
            shift = "DAY"
        }
    )
} | ConvertTo-Json -Depth 5

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sync/events" -Method Post -Headers $hrHeaders -Body $hrBumpPayload
    Write-Host " HR Sync Bumping Department (Notice transport fields remain intact):" -ForegroundColor Green
    Write-Host ($resp | ConvertTo-Json -Depth 5) -ForegroundColor DarkGreen
} catch {
    Write-Host " Request failed: $_" -ForegroundColor Yellow
}

# Step 5: Nightly Batch Reconciliation with Safety Brake Trigger
Write-Host ""
Write-Host "[STEP 5] Uploading Truncated Nightly CSV (>10% Missing -> Triggers Safety Brake)..." -ForegroundColor Cyan
$tempCsv = [System.IO.Path]::GetTempFileName() + ".csv"
@"
sourceEmployeeId,name,email,phone,officeCode,department,status,timestamp
EMP-DEMO-01,Aarav Sharma,aarav.sharma@corp.com,+919876543210,BLR-01,Core Architecture,ACTIVE,2026-10-30T00:00:00Z
"@ | Out-File -FilePath $tempCsv -Encoding utf8

try {
    $opHeaders = @{ "Authorization" = "Bearer $OPERATOR_TOKEN" }
    if ($PSVersionTable.PSVersion.Major -ge 7) {
        $form = @{ file = Get-Item $tempCsv }
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/upload" -Method Post -Headers $opHeaders -Form $form
        Write-Host " Reconciliation Response: $($resp | ConvertTo-Json -Compress)" -ForegroundColor Green
    } else {
        # Fallback for Windows PowerShell 5.1 using curl.exe
        $curlOutput = & curl.exe -s -w "%{http_code}" -X POST "$BaseUrl/api/v1/reconciliation/upload" -H "Authorization: Bearer $OPERATOR_TOKEN" -F "file=@$tempCsv"
        $httpCode = $curlOutput.Substring($curlOutput.Length - 3)
        $respBody = $curlOutput.Substring(0, $curlOutput.Length - 3)
        if ($httpCode -eq "422") {
            Write-Host " Safety Brake Triggered (Expected 422 Unprocessable Entity): $respBody" -ForegroundColor Yellow
        } else {
            Write-Host " Reconciliation Response ($httpCode): $respBody" -ForegroundColor Green
        }
    }
} catch {
    Write-Host " Safety Brake Triggered (Expected 422 Unprocessable Entity): $_" -ForegroundColor Yellow
} finally {
    if (Test-Path $tempCsv) { Remove-Item $tempCsv }
}

# Step 6: Cache Administration & Metrics Scrape
Write-Host ""
Write-Host "[STEP 6] Invalidate Version Cache & Inspect Prometheus Metrics..." -ForegroundColor Cyan
try {
    $adminHeaders = @{ "Authorization" = "Bearer $ADMIN_TOKEN" }
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/cache/invalidate?cacheName=employeeVersion" -Method Post -Headers $adminHeaders
    Write-Host " Cache Invalidation: $($resp | ConvertTo-Json -Compress)" -ForegroundColor Green

    $metrics = Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" -Method Get
    $metricLines = ($metrics -split "`n") | Where-Object { $_ -match "^(sync_events|cache_|outbox_)" } | Select-Object -First 6
    Write-Host " Sample Prometheus Metrics:" -ForegroundColor Cyan
    $metricLines | ForEach-Object { Write-Host "   $_" -ForegroundColor Gray }
} catch {
    Write-Host " Metrics scrape preview skipped: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "   DEMONSTRATION WALKTHROUGH COMPLETE                                         " -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan
