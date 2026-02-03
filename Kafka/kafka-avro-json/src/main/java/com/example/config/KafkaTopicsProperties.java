package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicsProperties(
    String bootstrapServers,
    String schemaRegistryUrl,
    String username,
    String password,
    String saslMechanism,
    Set<String> legacyTopics,
    Set<String> jsonTopics,
    Set<String> avroTopics
) {
  public Set<String> legacyTopics() { return legacyTopics != null ? legacyTopics : new HashSet<>(); }
  public Set<String> jsonTopics() { return jsonTopics != null ? jsonTopics : new HashSet<>(); }
  public Set<String> avroTopics() { return avroTopics != null ? avroTopics : new HashSet<>(); }
}