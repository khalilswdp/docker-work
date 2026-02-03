package com.example.producer;

import com.example.avro.UserEvent;
import com.example.config.KafkaTopicsProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class TopicRouterProducer {

  private final KafkaTopicsProperties topics;
  private final KafkaTemplate<String, String> legacyTemplate;
  private final KafkaTemplate<String, Object> jsonTemplate;
  private final KafkaTemplate<String, UserEvent> avroTemplate;

  public TopicRouterProducer(
          KafkaTopicsProperties topics,
          KafkaTemplate<String, String> legacyTemplate,
          KafkaTemplate<String, Object> jsonTemplate,
          KafkaTemplate<String, UserEvent> avroTemplate
  ) {
    this.topics = topics;
    this.legacyTemplate = legacyTemplate;
    this.jsonTemplate = jsonTemplate;
    this.avroTemplate = avroTemplate;
  }

  public void sendLegacy(String topic, String key, String value) {
    legacyTemplate.send(topic, key, value);
  }

  public void sendJson(String topic, String key, Object payload) {
    jsonTemplate.send(topic, key, payload);
  }

  public void sendAvro(String topic, String key, UserEvent record) {
    avroTemplate.send(topic, key, record);
  }

  /**
   * Routing policy:
   * - jsonTopics => JSON
   * - legacyTopics => String
   * - otherwise => Avro (DEFAULT)
   */
  public void sendAuto(String topic, String key, String payload) {
    if (topics.jsonTopics().contains(topic)) {
      sendJson(topic, key, Map.of("message", payload, "ts", Instant.now().toEpochMilli()));
      return;
    }
    if (topics.legacyTopics().contains(topic)) {
      sendLegacy(topic, key, payload);
      return;
    }

    // Default = Avro
    UserEvent evt = UserEvent.newBuilder()
            .setUserId(key == null ? "unknown" : key)
            .setAction(payload)
            .setTs(Instant.now().toEpochMilli())
            .build();
    sendAvro(topic, key, evt);
  }
}