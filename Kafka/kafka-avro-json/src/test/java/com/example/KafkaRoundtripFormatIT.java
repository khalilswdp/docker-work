package com.example;

import com.example.avro.UserEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class KafkaRoundtripFormatIT {

  @Autowired KafkaTemplate<String, String> legacyKafkaTemplate;
  @Autowired KafkaTemplate<String, Object> jsonKafkaTemplate;
  @Autowired KafkaTemplate<String, UserEvent> avroKafkaTemplate;

  // If you have your KafkaTopicsProperties class, use it.
  // Otherwise replace these with constants matching your application.yml.
  private final String bootstrap = "localhost:29092";
  private final String username  = "kafka_app";
  private final String password  = "kafka_app_password";
  private final String mechanism = "SCRAM-SHA-512";
  private final String schemaRegistryUrl = "http://localhost:8081";

  private Properties baseConsumerProps(String groupId) {
    Properties p = new Properties();
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    p.put("security.protocol", "SASL_PLAINTEXT");
    p.put(SaslConfigs.SASL_MECHANISM, mechanism);
    p.put(SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"" + username + "\" "
            + "password=\"" + password + "\";");
    p.put("schema.registry.url", schemaRegistryUrl);
    p.put("specific.avro.reader", true);

    p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return p;
  }

  /** Assign partitions directly and seekToEnd so we only read records produced after this call. */
  private static <K, V> void assignAndSeekToEnd(KafkaConsumer<K, V> c, String topic) {
    var partsInfo = c.partitionsFor(topic, Duration.ofSeconds(5));
    if (partsInfo == null || partsInfo.isEmpty()) {
      throw new IllegalStateException("No partitions for topic " + topic + " (topic exists?)");
    }
    List<TopicPartition> tps = partsInfo.stream()
        .map(pi -> new TopicPartition(topic, pi.partition()))
        .toList();

    c.assign(tps);
    c.seekToEnd(tps);
  }

  private static <K, V> ConsumerRecord<K, V> pollOne(KafkaConsumer<K, V> c, Duration maxWait) {
    long deadline = System.currentTimeMillis() + maxWait.toMillis();
    while (System.currentTimeMillis() < deadline) {
      ConsumerRecords<K, V> recs = c.poll(Duration.ofMillis(250));
      for (ConsumerRecord<K, V> r : recs) return r;
    }
    throw new AssertionError("No record received within " + maxWait);
  }

  @Test
  void legacy_roundtrip_topic_one_should_be_raw_string() throws Exception {
    String topic = "topic_one";
    String key = "k1";
    String value = "legacy-raw-" + UUID.randomUUID();

    Properties p = baseConsumerProps("it-legacy-1-" + UUID.randomUUID());
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    try (KafkaConsumer<String, String> c = new KafkaConsumer<>(p)) {
      assignAndSeekToEnd(c, topic);

      legacyKafkaTemplate.send(topic, key, value).get(10, TimeUnit.SECONDS);

      ConsumerRecord<String, String> r = pollOne(c, Duration.ofSeconds(10));
      assertThat(r.key()).isEqualTo(key);
      assertThat(r.value()).isEqualTo(value);
    }
  }

  @Test
  void legacy_roundtrip_topic_two_should_be_raw_string() throws Exception {
    String topic = "topic_two";
    String key = "k2";
    String value = "legacy-raw-" + UUID.randomUUID();

    Properties p = baseConsumerProps("it-legacy-2-" + UUID.randomUUID());
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    try (KafkaConsumer<String, String> c = new KafkaConsumer<>(p)) {
      assignAndSeekToEnd(c, topic);

      legacyKafkaTemplate.send(topic, key, value).get(10, TimeUnit.SECONDS);

      ConsumerRecord<String, String> r = pollOne(c, Duration.ofSeconds(10));
      assertThat(r.key()).isEqualTo(key);
      assertThat(r.value()).isEqualTo(value);
    }
  }

  @Test
  void json_roundtrip_topic_json_should_be_valid_json_object() throws Exception {
    String topic = "topic_json";
    String key = "u-json";
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("message", "hello-json");
    payload.put("n", 123);
    payload.put("ts", Instant.now().toEpochMilli());

    Properties p = baseConsumerProps("it-json-" + UUID.randomUUID());
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
    p.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

    try (KafkaConsumer<String, Object> c = new KafkaConsumer<>(p)) {
      assignAndSeekToEnd(c, topic);

      jsonKafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);

      ConsumerRecord<String, Object> r = pollOne(c, Duration.ofSeconds(10));
      assertThat(r.key()).isEqualTo(key);
      assertThat(r.value()).isInstanceOf(Map.class);

      @SuppressWarnings("unchecked")
      Map<String, Object> v = (Map<String, Object>) r.value();

      assertThat(v.get("message")).isEqualTo("hello-json");
      assertThat(((Number) v.get("n")).intValue()).isEqualTo(123);
      assertThat(v).containsKey("ts");
    }
  }

  @Test
  void avro_roundtrip_topic_avro_should_be_specific_record() throws Exception {
    String topic = "topic_avro";
    String key = "u-avro";

    UserEvent evt = UserEvent.newBuilder()
        .setUserId(key)
        .setAction("LOGIN")
        .setTs(Instant.now().toEpochMilli())
        .build();

    Properties p = baseConsumerProps("it-avro-" + UUID.randomUUID());
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());

    try (KafkaConsumer<String, UserEvent> c = new KafkaConsumer<>(p)) {
      assignAndSeekToEnd(c, topic);

      avroKafkaTemplate.send(topic, key, evt).get(10, TimeUnit.SECONDS);

      ConsumerRecord<String, UserEvent> r = pollOne(c, Duration.ofSeconds(10));
      assertThat(r.key()).isEqualTo(key);
      assertThat(r.value()).isNotNull();
      assertThat(r.value().getUserId()).isEqualTo(key);
      assertThat(r.value().getAction()).isEqualTo("LOGIN");
    }
  }
}