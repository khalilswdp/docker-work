#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_BOOTSTRAP_SERVERS:?Missing KAFKA_BOOTSTRAP_SERVERS}"
: "${KAFKA_CONFIG_FILE:=./client.properties}"

topics=(
  "com.example.events.CustomerCreated:3:3"
  "com.example.events.OrderCreated:3:3"
)

for topic_def in "${topics[@]}"; do
  IFS=":" read -r name partitions replication_factor <<< "$topic_def"
  kafka-topics \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --command-config "$KAFKA_CONFIG_FILE" \
    --create \
    --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor "$replication_factor"
done
