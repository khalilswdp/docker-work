package kafkaspringboot.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "event.bcef.kafka")
public class EventBcefKafkaProperties {

  private String bootstrapServers;
  private String clientId;
  private String clientRack;

  private Security security = new Security();
  private Network network = new Network();
  private Producer producer = new Producer();
  private Publishing publishing = new Publishing();
  private Map<String, Topic> topics;
  private Mock mock = new Mock();

  /* getters / setters omitted for brevity */

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientRack() {
    return clientRack;
  }

  public void setClientRack(String clientRack) {
    this.clientRack = clientRack;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  public Network getNetwork() {
    return network;
  }

  public void setNetwork(Network network) {
    this.network = network;
  }

  public Producer getProducer() {
    return producer;
  }

  public void setProducer(Producer producer) {
    this.producer = producer;
  }

  public Publishing getPublishing() {
    return publishing;
  }

  public void setPublishing(Publishing publishing) {
    this.publishing = publishing;
  }

  public Map<String, Topic> getTopics() {
    return topics;
  }

  public void setTopics(Map<String, Topic> topics) {
    this.topics = topics;
  }

  public Mock getMock() {
    return mock;
  }

  public void setMock(Mock mock) {
    this.mock = mock;
  }
  // ==========================
  // Nested classes
  // ==========================

  public static class Security {
    private String protocol;
    private Sasl sasl = new Sasl();
    private Ssl ssl = new Ssl();
    /* getters/setters */

    public String getProtocol() {
      return protocol;
    }

    public void setProtocol(String protocol) {
      this.protocol = protocol;
    }

    public Sasl getSasl() {
      return sasl;
    }

    public void setSasl(Sasl sasl) {
      this.sasl = sasl;
    }

    public Ssl getSsl() {
      return ssl;
    }

    public void setSsl(Ssl ssl) {
      this.ssl = ssl;
    }

    public static class Sasl {
      private String mechanism;
      private String jaasConfig;
      /* getters/setters */

      public String getMechanism() {
        return mechanism;
      }

      public void setMechanism(String mechanism) {
        this.mechanism = mechanism;
      }

      public String getJaasConfig() {
        return jaasConfig;
      }

      public void setJaasConfig(String jaasConfig) {
        this.jaasConfig = jaasConfig;
      }
    }

    public static class Ssl {
      private boolean enabled;
      private String endpointIdentificationAlgorithm;
      /* getters/setters */

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(boolean enabled) {
        this.enabled = enabled;
      }

      public String getEndpointIdentificationAlgorithm() {
        return endpointIdentificationAlgorithm;
      }

      public void setEndpointIdentificationAlgorithm(String endpointIdentificationAlgorithm) {
        this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
      }
    }
  }

  public static class Network {
    private String clientDnsLookup;
    private Integer requestTimeoutMs;
    private Integer reconnectBackoffMs;
    private Integer reconnectBackoffMaxMs;
    private Integer metadataMaxAgeMs;
    /* getters/setters */

    public String getClientDnsLookup() {
      return clientDnsLookup;
    }

    public void setClientDnsLookup(String clientDnsLookup) {
      this.clientDnsLookup = clientDnsLookup;
    }

    public Integer getRequestTimeoutMs() {
      return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
      this.requestTimeoutMs = requestTimeoutMs;
    }

    public Integer getReconnectBackoffMs() {
      return reconnectBackoffMs;
    }

    public void setReconnectBackoffMs(Integer reconnectBackoffMs) {
      this.reconnectBackoffMs = reconnectBackoffMs;
    }

    public Integer getReconnectBackoffMaxMs() {
      return reconnectBackoffMaxMs;
    }

    public void setReconnectBackoffMaxMs(Integer reconnectBackoffMaxMs) {
      this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
    }

    public Integer getMetadataMaxAgeMs() {
      return metadataMaxAgeMs;
    }

    public void setMetadataMaxAgeMs(Integer metadataMaxAgeMs) {
      this.metadataMaxAgeMs = metadataMaxAgeMs;
    }
  }

  public static class Producer {
    private String keySerializer;
    private String valueSerializer;
    private String acks;
    private boolean enableIdempotence;
    private Integer maxInFlightRequestsPerConnection;
    private Integer retries;
    private Integer lingerMs;
    private Integer batchSize;
    private Long bufferMemory;
    private String compressionType;
    private Integer maxRequestSize;
    private String transactionIdPrefix;
    /* getters/setters */

    public String getKeySerializer() {
      return keySerializer;
    }

    public void setKeySerializer(String keySerializer) {
      this.keySerializer = keySerializer;
    }

    public String getValueSerializer() {
      return valueSerializer;
    }

    public void setValueSerializer(String valueSerializer) {
      this.valueSerializer = valueSerializer;
    }

    public String getAcks() {
      return acks;
    }

    public void setAcks(String acks) {
      this.acks = acks;
    }

    public boolean isEnableIdempotence() {
      return enableIdempotence;
    }

    public void setEnableIdempotence(boolean enableIdempotence) {
      this.enableIdempotence = enableIdempotence;
    }

    public Integer getMaxInFlightRequestsPerConnection() {
      return maxInFlightRequestsPerConnection;
    }

    public void setMaxInFlightRequestsPerConnection(Integer maxInFlightRequestsPerConnection) {
      this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    public Integer getRetries() {
      return retries;
    }

    public void setRetries(Integer retries) {
      this.retries = retries;
    }

    public Integer getLingerMs() {
      return lingerMs;
    }

    public void setLingerMs(Integer lingerMs) {
      this.lingerMs = lingerMs;
    }

    public Integer getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
      this.batchSize = batchSize;
    }

    public Long getBufferMemory() {
      return bufferMemory;
    }

    public void setBufferMemory(Long bufferMemory) {
      this.bufferMemory = bufferMemory;
    }

    public String getCompressionType() {
      return compressionType;
    }

    public void setCompressionType(String compressionType) {
      this.compressionType = compressionType;
    }

    public Integer getMaxRequestSize() {
      return maxRequestSize;
    }

    public void setMaxRequestSize(Integer maxRequestSize) {
      this.maxRequestSize = maxRequestSize;
    }

    public String getTransactionIdPrefix() {
      return transactionIdPrefix;
    }

    public void setTransactionIdPrefix(String transactionIdPrefix) {
      this.transactionIdPrefix = transactionIdPrefix;
    }
  }

  public static class Topic {
    private String name;
    private Integer partitions;
    private Integer replicationFactor;
    // NEW: optional per-topic overrides
    private ProducerOverrides producerOverrides = new ProducerOverrides();

    public static class ProducerOverrides {
      // Maps from YAML: compression-type
      private String compressionType;

      public String getCompressionType() { return compressionType; }
      public void setCompressionType(String compressionType) { this.compressionType = compressionType; }
    }

    /* getters/setters */

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getPartitions() {
      return partitions;
    }

    public void setPartitions(Integer partitions) {
      this.partitions = partitions;
    }

    public Integer getReplicationFactor() {
      return replicationFactor;
    }

    public void setReplicationFactor(Integer replicationFactor) {
      this.replicationFactor = replicationFactor;
    }

    public ProducerOverrides getProducerOverrides() {
      return producerOverrides;
    }

    public void setProducerOverrides(ProducerOverrides producerOverrides) {
      this.producerOverrides = producerOverrides;
    }
  }

  public static class Publishing {
    private Map<String, String> defaultHeaders;
    private Routing routing = new Routing();
    /* getters/setters */


    public Map<String, String> getDefaultHeaders() {
      return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
      this.defaultHeaders = defaultHeaders;
    }

    public Routing getRouting() {
      return routing;
    }

    public void setRouting(Routing routing) {
      this.routing = routing;
    }

    public static class Routing {
      private Map<String, String> map;
      /* getters/setters */

      public Routing() {
        // no-args constructor REQUIRED for Spring binding
      }

      public Map<String, String> getMap() {
        return map;
      }

      public void setMap(Map<String, String> map) {
        this.map = map;
      }

      public Routing(Map<String, String> map) {
        this.map = map;
      }
    }
  }

  public static class Mock {
    private boolean enabled;
    private Long intervalMs;
    /* getters/setters */

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Long getIntervalMs() {
      return intervalMs;
    }

    public void setIntervalMs(Long intervalMs) {
      this.intervalMs = intervalMs;
    }
  }
}
