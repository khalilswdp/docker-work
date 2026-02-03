package com.example.consumer;

import com.example.avro.UserEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AvroListener {

  @KafkaListener(
      topics = {"topic_avro"},
      containerFactory = "avroListenerFactory"
  )
  public void onMessage(UserEvent value) {
    System.out.println("[AVRO] userId=" + value.getUserId()
        + " action=" + value.getAction()
        + " ts=" + value.getTs());
  }
}