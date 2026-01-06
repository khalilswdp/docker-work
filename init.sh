#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:19092}"

ENABLE_SASL="${ENABLE_SASL:-true}"
ENABLE_AUTHORIZER="${ENABLE_AUTHORIZER:-true}"
APPLY_ACLS="${APPLY_ACLS:-true}"

SASL_MECHANISM="${SASL_MECHANISM:-SCRAM-SHA-512}"
APP_USER="${APP_USER:-kafka_app}"
APP_PASS="${APP_PASS:-kafka_app_password}"

TOPIC_1="${TOPIC_1:-topic_one}"
TOPIC_2="${TOPIC_2:-topic_two}"
TOPIC_PARTITIONS="${TOPIC_PARTITIONS:-3}"
TOPIC_RF="${TOPIC_RF:-1}"

APP_GROUP="${APP_GROUP:-kafka_app_group}"

KAFKA_BIN="/opt/kafka/bin"
TOPICS="${KAFKA_BIN}/kafka-topics.sh"
CONFIGS="${KAFKA_BIN}/kafka-configs.sh"
ACLS="${KAFKA_BIN}/kafka-acls.sh"

echo "Waiting for Kafka at ${BOOTSTRAP}..."
for i in {1..60}; do
  if "${TOPICS}" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; then
    echo "Kafka reachable."
    break
  fi
  sleep 2
done

# ------------------------------------------------------------
# SCRAM user: create/update deterministically
# ------------------------------------------------------------
if [[ "${ENABLE_SASL}" == "true" ]]; then
  echo "Ensuring SCRAM user exists: ${APP_USER} (${SASL_MECHANISM})"
  "${CONFIGS}" --bootstrap-server "${BOOTSTRAP}" \
    --alter \
    --add-config "${SASL_MECHANISM}=[password=${APP_PASS}]" \
    --entity-type users \
    --entity-name "${APP_USER}"
else
  echo "ENABLE_SASL=false -> skipping user creation"
fi

# ------------------------------------------------------------
# Topics
# ------------------------------------------------------------
echo "Creating topics if missing: ${TOPIC_1}, ${TOPIC_2}"
"${TOPICS}"
