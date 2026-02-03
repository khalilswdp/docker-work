package com.example.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LegacyStringListener {

  @KafkaListener(
      topics = {"topic_one", "topic_two"},
      containerFactory = "legacyListenerFactory"
  )
  public void onMessage(String value) {
    System.out.println("[LEGACY] " + value);
  }
}