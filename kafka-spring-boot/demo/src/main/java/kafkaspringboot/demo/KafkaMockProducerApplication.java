package kafkaspringboot.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@SpringBootApplication
public class KafkaMockProducerApplication implements CommandLineRunner {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final Random random = new Random();

  @Value("${app.topic:topic_one}")
  private String topic;

  @Value("${app.interval-ms:1000}")
  private long intervalMs;

  public KafkaMockProducerApplication(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  public static void main(String[] args) {
    SpringApplication.run(KafkaMockProducerApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println("Producing mock events to Kafka topic: " + topic);

    while (true) {
      MockEvent event = new MockEvent(
          UUID.randomUUID().toString(),
          "mock-event",
          Instant.now().toString(),
          "spring-boot",
          random.nextInt(10_000)
      );

      String key = event.eventId();
      String payload = objectMapper.writeValueAsString(event);

      kafkaTemplate.send(topic, key, payload)
          .whenComplete((res, ex) -> {
            if (ex != null) {
              System.err.println("Send failed: " + ex.getMessage());
            } else {
              System.out.printf("Sent key=%s partition=%d offset=%d%n",
                  key,
                  res.getRecordMetadata().partition(),
                  res.getRecordMetadata().offset());
            }
          });

      Thread.sleep(intervalMs);
    }
  }
}
