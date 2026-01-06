package kafkaspringboot.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockProducerRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MockProducerRunner.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final KafkaTopicRegistry topics;
  private final ObjectMapper mapper;
  private final EventBcefKafkaProperties props;

  private final Random rnd = new Random();
  private final AtomicLong sent = new AtomicLong();


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
  public void run(ApplicationArguments args) throws Exception {
    log.info("MockProducerRunner starting. enabled={}, bootstrapServers={}",
            props.getMock().isEnabled(), props.getBootstrapServers());

    if (!props.getMock().isEnabled()) {
      log.warn("Mock producer disabled by config (event.bcef.kafka.mock.enabled=false). Exiting runner.");
      return;
    }

    String topic = topics.topicName("topic-1");
    long intervalMs = props.getMock().getIntervalMs() == null ? 1000L : props.getMock().getIntervalMs();

    log.info("Producing to topicKey=topic-1 resolvedTopic={} intervalMs={}", topic, intervalMs);

    // Heartbeat thread so you ALWAYS see output
    Thread heartbeat = new Thread(() -> {
      while (true) {
        try {
          Thread.sleep(5000);
          log.info("Heartbeat: still producing. sentCount={}", sent.get());
        } catch (InterruptedException e) {
          return;
        }
      }
    }, "producer-heartbeat");
    heartbeat.setDaemon(true);
    heartbeat.start();

    while (true) {
      MockEvent event = new MockEvent(
              UUID.randomUUID().toString(),
              "mock-event",
              Instant.now(),
              "spring-boot",
              rnd.nextInt(10_000)
      );

      String key = event.id();
      String payload = mapper.writeValueAsString(event);

      log.debug("PRODUCE_ATTEMPT topic={} key={} payload={}", topic, key, payload);

      kafkaTemplate.send(topic, key, payload).whenComplete((res, ex) -> {
        if (ex != null) {
          log.error("PRODUCE_FAIL topic={} key={} error={}", topic, key, ex.toString(), ex);
        } else {
          sent.incrementAndGet();
          log.info("PRODUCE_OK topic={} partition={} offset={} key={}",
                  res.getRecordMetadata().topic(),
                  res.getRecordMetadata().partition(),
                  res.getRecordMetadata().offset(),
                  key
          );
        }
      });

      // Flush occasionally so you see results quickly even with batching
      if (sent.get() % 50 == 0) {
        log.info("Flushing producer (sentCount={})", sent.get());
        kafkaTemplate.flush();
      }

      Thread.sleep(intervalMs);
    }
  }
}
