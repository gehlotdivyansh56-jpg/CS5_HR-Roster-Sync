#!/usr/bin/env bash
# ==============================================================================
# HR Roster Sync - End-to-End Demonstration Script (Bash)
# Case Study 5: Enterprise Idempotent, Out-of-Order, Self-Healing Roster Engine
# ==============================================================================

set -e

BASE_URL="http://localhost:8080"
JWT_SECRET="MoveInSyncRosterSyncSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong"
TENANT_ID="11111111-1111-1111-1111-111111111111"

echo "=============================================================================="
echo "   MOVEINSYNC HR ROSTER SYNC - END-TO-END DEMO WALKTHROUGH (BASH)             "
echo "=============================================================================="
echo ""

# Helper to generate HMAC-SHA256 JWT using openssl
generate_jwt() {
    local subject="$1"
    local tenant_id="$2"
    local role="$3"
    
    local header_b64=$(echo -n '{"alg":"HS256","typ":"JWT"}' | openssl base64 -e | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    local now=$(date +%s)
    local exp=$((now + 86400))
    local payload_json="{\"sub\":\"${subject}\",\"tenant_id\":\"${tenant_id}\",\"role\":\"${role}\",\"iat\":${now},\"exp\":${exp}}"
    local payload_b64=$(echo -n "${payload_json}" | openssl base64 -e | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    
    local unsigned_token="${header_b64}.${payload_b64}"
    local signature=$(echo -n "${unsigned_token}" | openssl dgst -sha256 -hmac "${JWT_SECRET}" -binary | openssl base64 -e | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    
    echo "${unsigned_token}.${signature}"
}

HR_TOKEN=$(generate_jwt "hr-service" "${TENANT_ID}" "HR_SYSTEM")
TRANS_TOKEN=$(generate_jwt "trans-admin-demo" "${TENANT_ID}" "TRANSPORT_ADMIN")
OPERATOR_TOKEN=$(generate_jwt "operator-demo" "${TENANT_ID}" "OPERATOR")
ADMIN_TOKEN=$(generate_jwt "sys-admin-demo" "${TENANT_ID}" "ADMIN")

echo "[STEP 0] Checking Service Health at ${BASE_URL}/actuator/health..."
curl -s -X GET "${BASE_URL}/actuator/health" || echo "Note: Run 'docker compose up -d' and 'mvn spring-boot:run' first"
echo ""

echo "[STEP 1] Ingesting Real-Time Daytime Batch (HTTP 207 Multi-Status)..."
curl -s -X POST "${BASE_URL}/api/v1/sync/events" \
  -H "Authorization: Bearer ${HR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-bash-001" \
  -d '{
    "events": [
      {
        "source_employee_id": "EMP-BASH-01",
        "version": 1730300000000,
        "event_type": "CREATE",
        "name": "Aarav Sharma",
        "email": "aarav.sharma@corp.com",
        "phone": "+919876543210",
        "office_code": "BLR-01",
        "department": "Engineering",
        "shift": "DAY"
      }
    ]
  }'
echo ""
echo ""

echo "[STEP 2] Testing Stale Event Rejection (Sending older version V1000)..."
curl -s -X POST "${BASE_URL}/api/v1/sync/events" \
  -H "Authorization: Bearer ${HR_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      {
        "source_employee_id": "EMP-BASH-01",
        "version": 1000,
        "event_type": "UPDATE",
        "name": "Aarav Sharma Old",
        "email": "aarav.sharma@corp.com",
        "phone": "+919876543210",
        "office_code": "BLR-01",
        "department": "Engineering",
        "shift": "DAY"
      }
    ]
  }'
echo ""
echo ""

echo "[STEP 3] Updating Transport Coordinates (Transport Admin)..."
curl -s -X PUT "${BASE_URL}/api/v1/employees/EMP-BASH-01/transport-fields" \
  -H "Authorization: Bearer ${TRANS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "pickup_point": "Hebbal Flyover Bus Stop, Gate 2",
    "drop_point": "RMZ Infinity Campus",
    "special_assistance": "None"
  }'
echo ""
echo ""

echo "[STEP 4] Cache Administration & Prometheus Metrics Scrape..."
curl -s -X POST "${BASE_URL}/api/v1/admin/cache/invalidate?cacheName=employeeVersion" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
echo ""

curl -s -X GET "${BASE_URL}/actuator/prometheus" | grep -E '^(sync_events|cache_|outbox_)' | head -n 6
echo ""

echo "=============================================================================="
echo "   DEMONSTRATION WALKTHROUGH COMPLETE                                         "
echo "=============================================================================="
