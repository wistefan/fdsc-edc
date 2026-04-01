#!/usr/bin/env bash
#
# Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

###############################################################################
# init-tmforum.sh — Populate a TMForum instance with the test data required
#                    for DSP TCK conformance testing.
#
# This script creates:
#   - ProductSpecifications (mapped to EDC assets)
#   - ProductOfferings      (mapped to EDC contract definitions)
#   - Agreements            (mapped to EDC contract agreements)
#
# Usage:
#   TMF_BASE_URL=http://tmforum:8632 ./scripts/init-tmforum.sh
#
# Environment variables:
#   TMF_BASE_URL    Base URL of the TMForum API (default: http://tmforum:8632)
#   PARTICIPANT_ID  EDC participant ID (default: urn:connector:fdsc-edc)
#   MAX_RETRIES     Max retries waiting for TMForum to be ready (default: 60)
#   RETRY_INTERVAL  Seconds between retries (default: 2)
###############################################################################

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
readonly TMF_BASE_URL="${TMF_BASE_URL:-http://tmforum:8632}"
readonly PARTICIPANT_ID="${PARTICIPANT_ID:-urn:connector:fdsc-edc}"
readonly MAX_RETRIES="${MAX_RETRIES:-60}"
readonly RETRY_INTERVAL="${RETRY_INTERVAL:-2}"

readonly CATALOG_API="${TMF_BASE_URL}/tmf-api/productCatalogManagement/v4"
readonly AGREEMENT_API="${TMF_BASE_URL}/tmf-api/agreementManagement/v4"

# TCK participant ID used in agreements
readonly TCK_PARTICIPANT="TCK_PARTICIPANT"
# Policy ID matching DataAssembly.POLICY_ID
readonly POLICY_ID="P123"
# Contract definition ID matching DataAssembly.CONTRACT_DEFINITION_ID
readonly CONTRACT_DEF_ID="CD123"

# Asset IDs from DataAssembly.ASSET_IDS
readonly ASSET_IDS=(
  ACN0101 ACN0102 ACN0103 ACN0104
  ACN0201 ACN0202 ACN0203 ACN0204 ACN0205 ACN0206 ACN0207
  ACN0301 ACN0302 ACN0303 ACN0304
  CAT0101 CAT0102
)

# Provider-side agreement IDs (connector is provider, TCK is consumer)
readonly PROVIDER_AGREEMENT_IDS=(
  ATP0101 ATP0102 ATP0103 ATP0104 ATP0105
  ATP0201 ATP0202 ATP0203 ATP0204 ATP0205
  ATP0301 ATP0302 ATP0303 ATP0304 ATP0305 ATP0306
)

# Consumer-side agreement IDs (connector is consumer, TCK is provider)
readonly CONSUMER_AGREEMENT_IDS=(
  ATPC0101 ATPC0102 ATPC0103 ATPC0104 ATPC0105
  ATPC0201 ATPC0202 ATPC0203 ATPC0204 ATPC0205
  ATPC0301 ATPC0302 ATPC0303 ATPC0304 ATPC0305 ATPC0306
)

# Signing timestamp for agreements
readonly SIGNING_DATE=$(date +%s)

# ---------------------------------------------------------------------------
# ODRL Policy (expanded JSON-LD format)
# ---------------------------------------------------------------------------
# This policy matches the permissive "use" policy created by DataAssembly.
# The uid field maps to the policy ID used by the TMFEdcMapper.
# ---------------------------------------------------------------------------
read -r -d '' ODRL_POLICY <<'POLICY_EOF' || true
{
  "@type": "http://www.w3.org/ns/odrl/2/Set",
  "http://www.w3.org/ns/odrl/2/uid": "P123",
  "http://www.w3.org/ns/odrl/2/permission": [
    {
      "http://www.w3.org/ns/odrl/2/action": [
        {"@id": "http://www.w3.org/ns/odrl/2/use"}
      ]
    }
  ]
}
POLICY_EOF

# ---------------------------------------------------------------------------
# Functions
# ---------------------------------------------------------------------------

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

die() {
  echo "[ERROR] $*" >&2
  exit 1
}

## Wait for TMForum API to become available.
wait_for_tmforum() {
  log "Waiting for TMForum API at ${TMF_BASE_URL}..."
  local attempt=0
  while [ "$attempt" -lt "$MAX_RETRIES" ]; do
    if curl -sf "${CATALOG_API}/productSpecification?limit=1" >/dev/null 2>&1; then
      log "TMForum API is ready."
      return 0
    fi
    attempt=$((attempt + 1))
    sleep "$RETRY_INTERVAL"
  done
  die "TMForum API did not become ready within $((MAX_RETRIES * RETRY_INTERVAL))s"
}

## Create a ProductSpecification (maps to an EDC Asset).
create_product_spec() {
  local asset_id="$1"
  local response
  response=$(curl -sf -X POST "${CATALOG_API}/productSpecification" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${asset_id}\",
      \"externalId\": \"${asset_id}\",
      \"productSpecCharacteristic\": [
        {
          \"id\": \"upstreamAddress\",
          \"name\": \"upstreamAddress\",
          \"valueType\": \"upstreamAddress\",
          \"productSpecCharacteristicValue\": [
            {\"value\": {\"value\": \"http://test-upstream.local\"}, \"isDefault\": true}
          ]
        },
        {
          \"id\": \"endpointUrl\",
          \"name\": \"endpointUrl\",
          \"valueType\": \"endpointUrl\",
          \"productSpecCharacteristicValue\": [
            {\"value\": {\"value\": \"http://test-upstream.local/data\"}, \"isDefault\": true}
          ]
        }
      ]
    }" 2>&1) || {
    log "WARNING: Failed to create ProductSpecification for asset ${asset_id}: ${response}"
    return 1
  }
  return 0
}

## Create a ProductOffering (maps to an EDC ContractDefinition).
create_product_offering() {
  local def_id="$1"
  local response
  response=$(curl -sf -X POST "${CATALOG_API}/productOffering" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${def_id}\",
      \"externalId\": \"${def_id}\",
      \"productOfferingTerm\": [
        {
          \"name\": \"edc:contractDefinition\",
          \"contractPolicy\": ${ODRL_POLICY},
          \"accessPolicy\": ${ODRL_POLICY}
        }
      ]
    }" 2>&1) || {
    log "WARNING: Failed to create ProductOffering for ${def_id}: ${response}"
    return 1
  }
  return 0
}

## Create a TMForum Agreement (maps to an EDC ContractAgreement).
create_agreement() {
  local agreement_id="$1"
  local provider_id="$2"
  local consumer_id="$3"
  local response
  response=$(curl -sf -X POST "${AGREEMENT_API}/agreement" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${agreement_id}\",
      \"externalId\": \"${agreement_id}\",
      \"agreementType\": \"dspContract\",
      \"status\": \"agreed\",
      \"characteristic\": [
        {\"name\": \"signing-date\", \"value\": ${SIGNING_DATE}},
        {\"name\": \"provider-id\", \"value\": \"${provider_id}\"},
        {\"name\": \"consumer-id\", \"value\": \"${consumer_id}\"},
        {\"name\": \"asset-id\", \"value\": \"${agreement_id}\"},
        {\"name\": \"policy\", \"value\": ${ODRL_POLICY}}
      ],
      \"engagedParty\": [
        {\"id\": \"${provider_id}\", \"name\": \"provider\", \"role\": \"provider\"},
        {\"id\": \"${consumer_id}\", \"name\": \"consumer\", \"role\": \"consumer\"}
      ]
    }" 2>&1) || {
    log "WARNING: Failed to create Agreement for ${agreement_id}: ${response}"
    return 1
  }
  return 0
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
log "=== TMForum Test Data Initialization ==="
log "TMForum API: ${TMF_BASE_URL}"
log "Participant ID: ${PARTICIPANT_ID}"

# Wait for TMForum to be ready
wait_for_tmforum

# Create ProductSpecifications (assets)
log "Creating ${#ASSET_IDS[@]} ProductSpecifications (assets)..."
success=0
for asset_id in "${ASSET_IDS[@]}"; do
  if create_product_spec "$asset_id"; then
    success=$((success + 1))
  fi
done
log "Created ${success}/${#ASSET_IDS[@]} ProductSpecifications."

# Create ProductOffering (contract definition)
log "Creating ProductOffering (contract definition: ${CONTRACT_DEF_ID})..."
if create_product_offering "$CONTRACT_DEF_ID"; then
  log "ProductOffering created successfully."
else
  log "WARNING: ProductOffering creation failed."
fi

# Create provider-side agreements (connector = provider, TCK = consumer)
log "Creating ${#PROVIDER_AGREEMENT_IDS[@]} provider-side Agreements..."
success=0
for agreement_id in "${PROVIDER_AGREEMENT_IDS[@]}"; do
  if create_agreement "$agreement_id" "$PARTICIPANT_ID" "$TCK_PARTICIPANT"; then
    success=$((success + 1))
  fi
done
log "Created ${success}/${#PROVIDER_AGREEMENT_IDS[@]} provider-side Agreements."

# Create consumer-side agreements (connector = consumer, TCK = provider)
log "Creating ${#CONSUMER_AGREEMENT_IDS[@]} consumer-side Agreements..."
success=0
for agreement_id in "${CONSUMER_AGREEMENT_IDS[@]}"; do
  if create_agreement "$agreement_id" "$TCK_PARTICIPANT" "$PARTICIPANT_ID"; then
    success=$((success + 1))
  fi
done
log "Created ${success}/${#CONSUMER_AGREEMENT_IDS[@]} consumer-side Agreements."

log "=== TMForum Test Data Initialization Complete ==="
