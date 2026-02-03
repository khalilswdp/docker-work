package com.example.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JsonListener {

  @KafkaListener(
      topics = {"topic_json"},
      containerFactory = "jsonListenerFactory"
  )
  public void onMessage(Object value) {
    System.out.println("[JSON] " + value);
  }
}