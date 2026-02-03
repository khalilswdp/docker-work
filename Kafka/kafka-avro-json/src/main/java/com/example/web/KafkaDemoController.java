package com.example.web;

import com.example.avro.UserEvent;
import com.example.producer.TopicRouterProducer;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/kafka")
public class KafkaDemoController {

  private final TopicRouterProducer producer;

  public KafkaDemoController(TopicRouterProducer producer) {
    this.producer = producer;
  }

  @PostMapping("/legacy/{topic}")
  public String legacy(@PathVariable String topic, @RequestParam String key, @RequestBody String value) {
    producer.sendLegacy(topic, key, value);
    return "OK legacy";
  }

  @PostMapping("/json/{topic}")
  public String json(@PathVariable String topic, @RequestParam String key, @RequestBody Map<String, Object> payload) {
    producer.sendJson(topic, key, payload);
    return "OK json";
  }

  @PostMapping("/avro/{topic}")
  public String avro(@PathVariable String topic, @RequestParam String key, @RequestBody String action) {
    UserEvent evt = UserEvent.newBuilder()
        .setUserId(key)
        .setAction(action)
        .setTs(Instant.now().toEpochMilli())
        .build();
    producer.sendAvro(topic, key, evt);
    return "OK avro";
  }

  @PostMapping("/auto/{topic}")
  public String auto(@PathVariable String topic, @RequestParam String key, @RequestBody String payload) {
    producer.sendAuto(topic, key, payload);
    return "OK auto";
  }
}