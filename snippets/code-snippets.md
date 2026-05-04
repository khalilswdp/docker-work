```
backend:
  labels:
    env: "dev"
    laas_dmzr_env: dev

  vault:
    certificates:
      - vault_path: "dev/A100789/commun/certificats"
        vault_key: "truststore_jks"
        file_name: "truststore.jks"

      - vault_path: "dev/A100789/kafka-ui/certificats"
        vault_key: "kafka.keystore"
        file_name: "kafka.keystore.jks"

  config:
    env:
      SPRING_PROFILES_ACTIVE: kafka-ui

    raw:
      application-kafka-ui.yaml: |+
        spring:
          config:
            import:
              - "vault://secret/dev/A100789/kafka-ui/secrets"
              - "optional:vault://"

          cloud:
            vault:
              fail-fast: false
              enabled: true
              ssl:
                trust-store: "file:/applis/vault/truststore.jks"
                trust-store-type: "JKS"
                trust-store-password: ${app.truststore-password}
              bootstrap.enabled: true
              uri: https://hvault-b.staging.echonet
              connection-timeout: 5000
              read-timeout: 15000
              namespace: A100789
              authentication: KUBERNETES
              kubernetes:
                kubernetes-path: kubernetes_oc002i000669_dev
                role: ns002i013263_default
                service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
              kv.enabled: false
              generic.enabled: false
              config.lifecycle:
                enabled: false
              session.lifecycle:
                enabled: false

          ssl:
            bundle:
              jks:
                appbundle:
                  truststore:
                    location: /applis/vault/truststore.jks
                    password: ${app.truststore-password}
                  keystore:
                    location: /applis/vault/kafka.keystore.jks
                    password: ${app.keystore-password}
                    private-key-password: ${app.key-password}

        logging:
          level:
            root: INFO
            com.provectus: INFO
            org.springframework.web: INFO
            org.apache.kafka: WARN
            io.confluent: INFO

        server:
          port: 8080

        kafka:
          clusters:
            - name: secured-cluster
              bootstrapServers: ${kafka.bootstrap-servers}

              properties:
                security.protocol: SASL_SSL
                sasl.mechanism: PLAIN
                sasl.jaas.config: >
                  org.apache.kafka.common.security.plain.PlainLoginModule required
                  username="${kafka.username}"
                  password="${kafka.password}";

                ssl.protocol: TLS
                ssl.enabled.protocols: TLSv1.2,TLSv1.3

                ssl.truststore.location: /applis/vault/truststore.jks
                ssl.truststore.password: ${app.truststore-password}
                ssl.truststore.type: JKS

                ssl.keystore.location: /applis/vault/kafka.keystore.jks
                ssl.keystore.password: ${app.keystore-password}
                ssl.keystore.type: JKS
                ssl.key.password: ${app.key-password}

              schemaRegistry: ${schema.registry.url}

              schemaRegistryAuth:
                username: ${schema.registry.username}
                password: ${schema.registry.password}

              ssl:
                truststore-location: /applis/vault/truststore.jks
                truststore-password: ${app.truststore-password}
                verify-ssl: true

              defaultKeySerde: String
              defaultValueSerde: SchemaRegistry

  routes:
    enabled: true
    routesList:
      - url: kafka-ui.dev.echonet
        portName: http
        tlsTermination: edge
      - url: kafka-ui.dev.echonet.net.intra
        portName: http
        tlsTermination: edge
      - url: kafka-ui.dev.adc2-hp.fr.net.intra
        portName: http
        tlsTermination: edge
        
        
```
MR / PR summary

Enhanced unit tests for EstreemApiGatewayAdapter and ApiFlowProcessorStrategyImpl to improve readability, maintainability, and branch coverage.

Main changes
•	aligned tests with the current token validation rules:
•	ISS mandatory
•	SUB optional
•	regex updated to the new generic format
•	replaced outdated invalid token samples with cases that truly fail the new regex
•	added positive coverage for SUB = null
•	added coverage for request/response payload empty and null branches
•	added assertions on mapped ApiFlow content in adapter tests
•	added coverage for adapter delegation methods
•	updated strategy tests to match current config validation behavior:
•	authorizedCodeAp == null || empty now raises CORE_CONFIG_MALFORMED
•	simplified strategy test setup by using real domain fixtures instead of mocking all model objects
•	strengthened assertions on transformation contexts and call ordering
•	reduced duplication through helper methods and parameterized scenarios

Benefits
•	better alignment between tests and current implementation
•	improved branch coverage
•	clearer test intent
•	easier future maintenance and refactoring

⸻

5) Suggested commit message

A few options depending on style.





