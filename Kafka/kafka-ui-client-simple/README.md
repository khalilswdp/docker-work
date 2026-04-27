# Simple Kafka UI client for an existing secure Kafka cluster

This project does not run Kafka or Schema Registry. It only runs Kafka UI and connects to your existing Kafka cluster and Confluent Schema Registry.

## Files

```text
.
├── docker-compose.yml
├── .env.example
├── kafka-ui/application.yml
├── certs/
│   ├── kafka.truststore.jks
│   └── kafka.keystore.jks
├── app-config/application.yml
├── app-config/application-ssl-bundle.yml
├── topics/topics.example.yml
└── scripts/create-topics.sh
```

## Setup

```bash
cp .env.example .env
```

Edit `.env`, then place these files in `certs/`:

```text
certs/kafka.truststore.jks
certs/kafka.keystore.jks
```

Start Kafka UI:

```bash
docker compose --env-file .env up -d
```

Open:

```text
http://localhost:8080
```

## IntelliJ Big Data Tools equivalent

Kafka properties:

```properties
bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
ssl.protocol=TLSv1.2
ssl.enabled.protocols=TLSv1.2,TLSv1.3
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";
ssl.truststore.location=/path/to/kafka.truststore.jks
ssl.truststore.password=${KAFKA_TRUSTSTORE_PASSWORD}
ssl.keystore.location=/path/to/kafka.keystore.jks
ssl.keystore.password=${KAFKA_KEYSTORE_PASSWORD}
ssl.key.password=${KAFKA_KEY_PASSWORD}
```

Schema Registry properties:

```properties
schema.registry.url=${SCHEMA_REGISTRY_URL}
basic.auth.credentials.source=USER_INFO
basic.auth.user.info=${SCHEMA_REGISTRY_USERNAME}:${SCHEMA_REGISTRY_PASSWORD}
```

## Spring Boot producer config

See:

- `app-config/application.yml`
- `app-config/application-ssl-bundle.yml`

The producer uses:

```yaml
key-serializer: org.apache.kafka.common.serialization.StringSerializer
value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
acks: all
retries: 3
batch-size: 16384
linger-ms: 5
buffer-memory: 33554432
```
