package kafkaspringboot.demo;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(EventBcefKafkaProperties.class)
public class KafkaProducerConfig {

  @Bean
  public ProducerFactory<String, String> producerFactory(EventBcefKafkaProperties p) {
    Map<String, Object> props = new HashMap<>();

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, p.getBootstrapServers());
    props.put(ProducerConfig.CLIENT_ID_CONFIG, p.getClientId());

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    var prod = p.getProducer();
    props.put(ProducerConfig.ACKS_CONFIG, prod.getAcks());
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, prod.isEnableIdempotence());
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, prod.getMaxInFlightRequestsPerConnection());
    props.put(ProducerConfig.RETRIES_CONFIG, prod.getRetries());
    props.put(ProducerConfig.LINGER_MS_CONFIG, prod.getLingerMs());
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, prod.getBatchSize());
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, prod.getBufferMemory());
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, prod.getCompressionType());
    props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, prod.getMaxRequestSize());

    if (prod.getTransactionIdPrefix() != null && !prod.getTransactionIdPrefix().isBlank()) {
      props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, prod.getTransactionIdPrefix());
    }

    // Security
    props.put("security.protocol", p.getSecurity().getProtocol());
    props.put(SaslConfigs.SASL_MECHANISM, p.getSecurity().getSasl().getMechanism());
    props.put(SaslConfigs.SASL_JAAS_CONFIG, p.getSecurity().getSasl().getJaasConfig());


    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
    return new KafkaTemplate<>(pf);
  }
}
