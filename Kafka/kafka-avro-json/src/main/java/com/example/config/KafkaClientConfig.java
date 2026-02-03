package com.example.config;

import com.example.avro.UserEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaClientConfig {

  private Map<String, Object> baseProps(KafkaTopicsProperties p) {
    Map<String, Object> m = new HashMap<>();
    m.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, p.bootstrapServers());

    // SASL/SCRAM
    m.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
    m.put(SaslConfigs.SASL_MECHANISM, p.saslMechanism());
    m.put(SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"" + p.username() + "\" "
            + "password=\"" + p.password() + "\";");

    // Schema Registry (harmless for non-avro clients)
    m.put("schema.registry.url", p.schemaRegistryUrl());
    m.put("specific.avro.reader", true);

    return m;
  }

  // =========================
  // PRODUCERS
  // =========================

  @Bean
  public KafkaTemplate<String, String> legacyKafkaTemplate(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public KafkaTemplate<String, Object> jsonKafkaTemplate(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    // Avoid type headers if you want interoperability with non-Spring consumers
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public KafkaTemplate<String, UserEvent> avroKafkaTemplate(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    // auto.register.schemas=true in application.yml
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  // =========================
  // CONSUMERS + LISTENER FACTORIES
  // =========================

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> legacyListenerFactory(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "legacy-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
    ConcurrentKafkaListenerContainerFactory<String, String> f = new ConcurrentKafkaListenerContainerFactory<>();
    f.setConsumerFactory(cf);
    return f;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> jsonListenerFactory(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "json-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<Object> jd = new JsonDeserializer<>(Object.class);
    jd.addTrustedPackages("*");

    ConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jd);
    ConcurrentKafkaListenerContainerFactory<String, Object> f = new ConcurrentKafkaListenerContainerFactory<>();
    f.setConsumerFactory(cf);
    return f;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserEvent> avroListenerFactory(KafkaTopicsProperties p) {
    Map<String, Object> props = baseProps(p);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "avro-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    ConsumerFactory<String, UserEvent> cf = new DefaultKafkaConsumerFactory<>(props);
    ConcurrentKafkaListenerContainerFactory<String, UserEvent> f = new ConcurrentKafkaListenerContainerFactory<>();
    f.setConsumerFactory(cf);
    return f;
  }
}