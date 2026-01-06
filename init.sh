#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:19092}"

ENABLE_SASL="${ENABLE_SASL:-true}"
SASL_MECHANISM="${SASL_MECHANISM:-SCRAM-SHA-512}"
APP_USER="${APP_USER:-kafka_app}"
APP_PASS="${APP_PASS:-kafka_app_password}"

ENABLE_AUTHORIZER="${ENABLE_AUTHORIZER:-true}"
APPLY_ACLS="${APPLY_ACLS:-true}"
APP_GROUP="${APP_GROUP:-kafka_app_group}"

TOPIC_1="${TOPIC_1:-topic_one}"
TOPIC_2="${TOPIC_2:-topic_two}"
TOPIC_PARTITIONS="${TOPIC_PARTITIONS:-3}"
TOPIC_RF="${TOPIC_RF:-1}"

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

if [[ "${ENABLE_SASL}" == "true" ]]; then
  echo "Ensuring SCRAM user exists/updated: ${APP_USER} (${SASL_MECHANISM})"
  "${CONFIGS}" --bootstrap-server "${BOOTSTRAP}" \
    --alter \
    --add-config "${SASL_MECHANISM}=[password=${APP_PASS}]" \
    --entity-type users \
    --entity-name "${APP_USER}"
else
  echo "ENABLE_SASL=false -> skipping user creation"
fi

echo "Creating topics if missing: ${TOPIC_1}, ${TOPIC_2}"
"${TOPICS}" --bootstrap-server "${BOOTSTRAP}" \
  --create --if-not-exists --topic "${TOPIC_1}" \
  --partitions "${TOPIC_PARTITIONS}" --replication-factor "${TOPIC_RF}"

"${TOPICS}" --bootstrap-server "${BOOTSTRAP}" \
  --create --if-not-exists --topic "${TOPIC_2}" \
  --partitions "${TOPIC_PARTITIONS}" --replication-factor "${TOPIC_RF}"

echo "Topics:"
"${TOPICS}" --bootstrap-server "${BOOTSTRAP}" --list

if [[ "${ENABLE_AUTHORIZER}" == "true" && "${APPLY_ACLS}" == "true" ]]; then
  echo "Applying ACLs for User:${APP_USER}..."

  "${ACLS}" --bootstrap-server "${BOOTSTRAP}" \
    --add --allow-principal "User:${APP_USER}" \
    --operation Read --operation Write --operation Describe \
    --topic "${TOPIC_1}"

  "${ACLS}" --bootstrap-server "${BOOTSTRAP}" \
    --add --allow-principal "User:${APP_USER}" \
    --operation Read --operation Write --operation Describe \
    --topic "${TOPIC_2}"

  "${ACLS}" --bootstrap-server "${BOOTSTRAP}" \
    --add --allow-principal "User:${APP_USER}" \
    --operation Read --operation Describe \
    --group "${APP_GROUP}"

  echo "ACLs applied."
else
  echo "ACLs skipped (ENABLE_AUTHORIZER=${ENABLE_AUTHORIZER}, APPLY_ACLS=${APPLY_ACLS})"
fi

echo "Init complete."
