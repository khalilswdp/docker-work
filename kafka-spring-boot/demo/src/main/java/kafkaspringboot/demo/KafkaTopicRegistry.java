package kafkaspringboot.demo;

import org.springframework.stereotype.Component;

@Component
public class KafkaTopicRegistry {

  private final EventBcefKafkaProperties props;

  public KafkaTopicRegistry(EventBcefKafkaProperties props) {
    this.props = props;
  }

  public String topicName(String key) {
    var topic = props.getTopics().get(key);
    if (topic == null) {
      throw new IllegalArgumentException("Unknown Kafka topic key: " + key);
    }
    return topic.getName();
  }
}
