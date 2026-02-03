package com.example;

import com.example.avro.UserEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class KafkaSendOnlyIT {

  @Autowired KafkaTemplate<String, String> legacyKafkaTemplate;
  @Autowired KafkaTemplate<String, Object> jsonKafkaTemplate;
  @Autowired KafkaTemplate<String, UserEvent> avroKafkaTemplate;

  @Test
  void send_only_legacy_topics_should_succeed() throws Exception {
    legacyKafkaTemplate.send("topic_one", "k1", "legacy-send-only" + UUID.randomUUID())
        .get(10, TimeUnit.SECONDS);

    legacyKafkaTemplate.send("topic_two", "k2", "legacy-send-only" + UUID.randomUUID())
        .get(10, TimeUnit.SECONDS);
  }

  @Test
  void send_only_json_topic_should_succeed() throws Exception {
    Map<String, Object> payload = Map.of(
        "message", "hello-json-send-only",
        "n", 123,
        "ts", Instant.now().toEpochMilli()
    );

    jsonKafkaTemplate.send("topic_json", "u-json", payload)
        .get(10, TimeUnit.SECONDS);
  }

  @Test
  void send_only_avro_topic_should_succeed_and_register_schema() throws Exception {
    UserEvent evt = UserEvent.newBuilder()
        .setUserId("u-avro")
        .setAction("LOGIN Send only")
        .setTs(Instant.now().toEpochMilli())
        .build();

    avroKafkaTemplate.send("topic_avro", "u-avro", evt)
        .get(10, TimeUnit.SECONDS);
  }
}