package com.example;

import com.example.avro.UserEvent;
import com.example.json.JsonEvent;
import com.example.service.MigrationProducer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaMigrationIT {

  private static final Network network = Network.newNetwork();

  private static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
          .withNetwork(network)
          .withNetworkAliases("kafka");

  private static final GenericContainer<?> schemaRegistry =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.0"))
          .withNetwork(network)
          .withNetworkAliases("schema-registry")
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
          .dependsOn(kafka);

  static {
    kafka.start();
    schemaRegistry.start();
  }

  private static final String TOPIC_LEGACY = "topic_one";
  private static final String TOPIC_JSON = "topic_json";
  private static final String TOPIC_AVRO = "topic_avro";

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("app.kafka.bootstrapServers", kafka::getBootstrapServers);
    r.add("app.kafka.schemaRegistryUrl",
        () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));

    // tests: no SASL
    r.add("spring.kafka.properties.security.protocol", () -> "PLAINTEXT");
    r.add("spring.kafka.properties.sasl.mechanism", () -> "");
    r.add("spring.kafka.properties.sasl.jaas.config", () -> "");

    // Explicit opt-in only
    r.add("app.kafka.jsonTopics", () -> TOPIC_JSON);
    r.add("app.kafka.avroTopics", () -> TOPIC_AVRO);
  }

  @Autowired
  private MigrationProducer producer;

  @BeforeAll
  void createTopics() throws Exception {
    Properties p = new Properties();
    p.put("bootstrap.servers", kafka.getBootstrapServers());
    try (AdminClient admin = AdminClient.create(p)) {
      admin.createTopics(List.of(
          new NewTopic(TOPIC_LEGACY, 1, (short) 1),
          new NewTopic(TOPIC_JSON, 1, (short) 1),
          new NewTopic(TOPIC_AVRO, 1, (short) 1)
      )).all().get(30, TimeUnit.SECONDS);
    }
  }

  @Test
  void default_should_be_legacy_string_no_change() {
    producer.send(TOPIC_LEGACY, "k1", "HELLO_LEGACY");
    String msg = pollString(TOPIC_LEGACY, "g-legacy");
    Assertions.assertEquals("HELLO_LEGACY", msg);
  }

  @Test
  void json_topic_should_send_json() {
    producer.send(TOPIC_JSON, "id1", "CLICK");
    JsonEvent evt = pollJson(TOPIC_JSON, "g-json");
    Assertions.assertNotNull(evt);
    Assertions.assertEquals("CLICK", evt.action());
  }

  @Test
  void avro_topic_should_send_avro() {
    producer.send(TOPIC_AVRO, "id2", "LOGIN");
    UserEvent evt = pollAvro(TOPIC_AVRO, "g-avro");
    Assertions.assertNotNull(evt);
    Assertions.assertEquals("LOGIN", evt.getAction().toString());
  }

  // ---- helpers ----

  private String pollString(String topic, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
        (Map) props, new StringDeserializer(), new StringDeserializer()
    )) {
      consumer.subscribe(List.of(topic));
      var records = consumer.poll(Duration.ofSeconds(10));
      if (records.isEmpty()) return null;
      return records.iterator().next().value();
    }
  }

  private JsonEvent pollJson(String topic, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<JsonEvent> deser = new JsonDeserializer<>(JsonEvent.class);
    deser.addTrustedPackages("*");
    deser.setRemoveTypeHeaders(true);

    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, JsonEvent>(
        (Map) props, new StringDeserializer(), deser
    )) {
      consumer.subscribe(List.of(topic));
      var records = consumer.poll(Duration.ofSeconds(10));
      if (records.isEmpty()) return null;
      return records.iterator().next().value();
    }
  }

  private UserEvent pollAvro(String topic, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    props.put("schema.registry.url",
        "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, Object>(
        (Map) props, new StringDeserializer(), new KafkaAvroDeserializer()
    )) {
      consumer.subscribe(List.of(topic));
      var records = consumer.poll(Duration.ofSeconds(10));
      if (records.isEmpty()) return null;

      Object v = records.iterator().next().value();
      Assertions.assertTrue(v instanceof UserEvent, "Expected UserEvent but got " + v.getClass());
      return (UserEvent) v;
    }
  }
}