package io.confluent.kafka.streams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.schemas.Severity;
import io.confluent.kafka.streams.schemas.SyslogMessage;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class SyslogProducer {

    private static final Logger logger = LogManager.getLogger(SyslogProducer.class);

    private static String BOOTSTRAP_SERVERS;
    private static String SCHEMA_REGISTRY_URL;
    private static final String KAFKA_TOPIC = "syslog";
    private static final String FILE_PATH = "/logs/f5_syslog_messages.txt";
    private static final String[] HOSTS = {"host1.example.com", "host2.example.com",
            "host3.example.com", "host4.example.com", "host5.example.com"};
    private static final Random random = new Random();
    private static final List<String> facilities = List.of("auth", "cron", "daemon", "kern", "mail",
            "syslog", "lpr", "news", "user", "local0");

    public static void main(String[] args) {
        BOOTSTRAP_SERVERS =
                System.getenv("BOOTSTRAP_SERVERS") != null ? System.getenv("BOOTSTRAP_SERVERS")
                        : "localhost:9092";
        SCHEMA_REGISTRY_URL =
                System.getenv("SCHEMA_REGISTRY_URL") != null ? System.getenv("SCHEMA_REGISTRY_URL")
                        : "http://localhost:8081";
        // BOOTSTRAP_SERVERS = "broker:29092";
        // SCHEMA_REGISTRY_URL = "http://schema-registry:8081";

        logger.info("Starting SyslogProducer");
        List<String> records = loadMessages();
        produceAvroMessages(BOOTSTRAP_SERVERS, records);
    }

    private static List<String> loadMessages() {
        logger.info("Loading messages from file: {}", FILE_PATH);
        List<String> messages = new ArrayList<>();
        try (InputStream inputStream = SyslogProducer.class.getResourceAsStream(FILE_PATH);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                messages.add(line.trim());
            }
        } catch (IOException e) {
            logger.error("Error loading messages from file: {}", FILE_PATH, e);
        }
        return messages;
    }

    public static void produceAvroMessages(String kafkaUrl, List<String> records) {
        logger.debug("Producing Avro messages to topic: {}", KAFKA_TOPIC);
        logger.info("BOOTSTRAP_SERVERS: {}", BOOTSTRAP_SERVERS);
        logger.info("SCHEMA_REGISTRY_URL: {}", SCHEMA_REGISTRY_URL);

        Properties props = new Properties();
        props.put("auto.register.schemas", false);
        Map<String, String> serdeConfig =
                Collections.singletonMap("schema.registry.url", SCHEMA_REGISTRY_URL);
        Serde<SyslogMessage> syslogMessageSerde = new SpecificAvroSerde<>();
        syslogMessageSerde.configure(serdeConfig, true);

        props.put("bootstrap.servers", kafkaUrl);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", syslogMessageSerde.serializer().getClass().getName());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);

        KafkaProducer<String, SyslogMessage> producer = new KafkaProducer<>(props);

        records.forEach(record -> {
            SyslogMessage syslogMessage = createSyslogMessage(record);
            ProducerRecord<String, SyslogMessage> producerRecord =
                    new ProducerRecord<>(KAFKA_TOPIC, null, syslogMessage);
            producer.send(producerRecord, (RecordMetadata metadata, Exception exception) -> {
                logger.info("Sending message to topic {}", metadata);
                logger.info(syslogMessage.toString());
                if (exception != null) {
                    System.err.println("Error producing message to topic " + KAFKA_TOPIC);
                    exception.printStackTrace();
                } else {
                    logger.info("Message sent to topic {} partition {} offset {}", metadata.topic(),
                            metadata.partition(), metadata.offset());
                }
            });
            try {
                Thread.sleep(random.nextInt(5000) + 500);
            } catch (InterruptedException e) {
                logger.error("Producer thread was interrupted");
                Thread.currentThread().interrupt();
            }
        });

        logger.debug("Closing producer");
        producer.close();
        syslogMessageSerde.close();
    }

    private static SyslogMessage createSyslogMessage(String logMessage) {
        logger.debug("Creating SyslogMessage");
        SyslogMessage syslogMessage = new SyslogMessage();

        long timestamp = System.currentTimeMillis();
        String host = HOSTS[random.nextInt(HOSTS.length)];
        Severity severity = Severity.values()[random.nextInt(Severity.values().length)];

        // Set the properties of the SyslogMessage
        syslogMessage.setTimestamp(timestamp);
        syslogMessage.setHost(host.toString());
        syslogMessage.setFacility(facilities.get(random.nextInt(facilities.size())));
        syslogMessage.setEventId(UUID.randomUUID().toString());
        syslogMessage.setSeverity(severity);
        syslogMessage.setMessage(logMessage);

        return syslogMessage;
    }
}
