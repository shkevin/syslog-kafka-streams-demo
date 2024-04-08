package io.confluent.kafka.streams;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.confluent.kafka.streams.schemas.SeverityCount;
import io.confluent.kafka.streams.schemas.SyslogMessage;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class SeverityCountApp {
        private static final Logger logger = LogManager.getLogger(SyslogProducer.class);

        public static void main(String[] args) {

                // Set up the configuration
                logger.debug("Starting SeverityCountApp");
                final String BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS") != null
                                ? System.getenv("BOOTSTRAP_SERVERS")
                                : "localhost:9092";
                final String SCHEMA_REGISTRY_URL = System.getenv("SCHEMA_REGISTRY_URL") != null
                                ? System.getenv("SCHEMA_REGISTRY_URL")
                                : "http://localhost:8081";

                logger.info("BOOTSTRAP_SERVERS: {}", BOOTSTRAP_SERVERS);
                logger.info("SCHEMA_REGISTRY_URL: {}", SCHEMA_REGISTRY_URL);
                System.out.println("BOOTSTRAP_SERVERS: " + BOOTSTRAP_SERVERS);
                final String inputTopic = "syslog";
                final String outputTopic = "severity-counts-topic";

                logger.debug("Creating Kafka Streams configuration");
                Properties streamsConfiguration = new Properties();
                streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "severity-count-app");
                streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
                streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                                Serdes.String().getClass());
                streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                                SpecificAvroSerde.class);
                streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                streamsConfiguration.put("auto.register.schemas", false);

                StreamsBuilder builder = new StreamsBuilder();

                // Configure the SpecificAvroSerdes
                Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url",
                                SCHEMA_REGISTRY_URL);

                // Create the Serdes for the SyslogMessage and SeverityCount classes
                final Serde<SyslogMessage> syslogMessageSerde = new SpecificAvroSerde<>();
                syslogMessageSerde.configure(serdeConfig, false);
                // severityMessageSerde.configure(serdeConfig, true);

                final Serde<SeverityCount> severityCountSerde = new SpecificAvroSerde<>();
                severityCountSerde.configure(serdeConfig, false);
                // severityCountSerde.configure(serdeConfig, true);

                // Build the stream topology
                KStream<String, SyslogMessage> sourceStream = builder.stream(inputTopic,
                                Consumed.with(Serdes.String(), syslogMessageSerde));

                // Group by host and aggregate the severity counts
                logger.debug("Creating the KTable for the severity counts");
                KTable<String, SeverityCount> severityCounts = sourceStream
                                .groupBy((key, value) -> value.getHost(),
                                                Grouped.with(Serdes.String(), syslogMessageSerde))
                                .aggregate(SeverityCount::new, // Initializer
                                                (host, message, aggValue) -> updateSeverityCount(
                                                                aggValue, message), // Aggregator
                                                Named.as("severityCountAggregator"),
                                                Materialized.<String, SeverityCount, KeyValueStore<Bytes, byte[]>>as(
                                                                "severity-counts-store")
                                                                .withKeySerde(Serdes.String())
                                                                .withValueSerde(severityCountSerde));

                // Write the aggregated severity counts to the output topic
                logger.debug("Writing the aggregated severity counts to the output topic");
                severityCounts.toStream().to(outputTopic,
                                Produced.with(Serdes.String(), severityCountSerde));

                logger.info("Starting the Kafka Streams application");
                KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
                streams.start();

                // Shutdown hook to correctly close the streams application
                Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        }

        /**
         * Update the severity count based on the severity level of the Syslog message.
         * 
         * @param aggValue the current severity count
         * @param message the Syslog message
         * @return the updated severity count
         */
        private static SeverityCount updateSeverityCount(SeverityCount aggValue,
                        SyslogMessage message) {
                // Ensure aggValue is not null
                if (aggValue == null) {
                        aggValue = new SeverityCount();
                        aggValue.setLow(0L);
                        aggValue.setMedium(0L);
                        aggValue.setHigh(0L);
                }

                // Increment the appropriate severity count
                logger.debug("Updating the severity count for message: {}", message);
                switch (message.getSeverity().toString()) {
                        case "EMERG":
                        case "ALERT":
                        case "CRIT":
                                aggValue.setHigh(aggValue.getHigh() + 1);
                                break;
                        case "ERR":
                        case "WARNING":
                                aggValue.setMedium(aggValue.getMedium() + 1);
                                break;
                        case "NOTICE":
                        case "INFO":
                        case "DEBUG":
                                aggValue.setLow(aggValue.getLow() + 1);
                                break;
                        default:
                                // Handle unexpected severity level, if necessary
                                break;
                }
                return aggValue;
        }
}
