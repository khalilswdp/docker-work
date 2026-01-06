package kafkaspringboot.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Component
public class MockProducerRunner implements CommandLineRunner {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final KafkaTopicRegistry topics;
  private final ObjectMapper mapper;
  private final EventBcefKafkaProperties props;
  private final Random rnd = new Random();

  public MockProducerRunner(
      KafkaTemplate<String, String> kafkaTemplate,
      KafkaTopicRegistry topics,
      ObjectMapper mapper,
      EventBcefKafkaProperties props
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.topics = topics;
    this.mapper = mapper;
    this.props = props;
  }

  @Override
  public void run(String... args) throws Exception {
    if (!props.getMock().isEnabled()) {
      return;
    }

    long interval = props.getMock().getIntervalMs();
    String topic = topics.topicName("topic-1");

    while (true) {
      MockEvent event = new MockEvent(
          UUID.randomUUID().toString(),
          "mock-event",
          Instant.now(),
          "spring-boot",
          rnd.nextInt(10_000)
      );

      kafkaTemplate.send(topic, event.id(), mapper.writeValueAsString(event));
      Thread.sleep(interval);
    }
  }
}
