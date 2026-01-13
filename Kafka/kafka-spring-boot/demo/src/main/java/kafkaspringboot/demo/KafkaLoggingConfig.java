package kafkaspringboot.demo;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

@Configuration
public class KafkaLoggingConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaLoggingConfig.class);

  @Bean
  public ProducerListener<Object, Object> producerListener() {
    return new ProducerListener<>() {
      @Override
      public void onSuccess(org.apache.kafka.clients.producer.ProducerRecord<Object, Object> producerRecord,
                            RecordMetadata recordMetadata) {
        log.info("PRODUCE_OK topic={} partition={} offset={} key={} valueSizeBytes={}",
            recordMetadata.topic(),
            recordMetadata.partition(),
            recordMetadata.offset(),
            producerRecord.key(),
            producerRecord.value() == null ? 0 : producerRecord.value().toString().length()
        );
      }

      @Override
      public void onError(org.apache.kafka.clients.producer.ProducerRecord<Object, Object> producerRecord,
                          RecordMetadata recordMetadata,
                          Exception exception) {
        log.error("PRODUCE_FAIL topic={} key={} error={}",
            producerRecord.topic(),
            producerRecord.key(),
            exception.toString(),
            exception
        );
      }
    };
  }
}
