"""Syslog Generator.

This script generates syslog messages and sends them to a Kafka topic.
The messages are generated from a list of F5 syslog messages.

Attributes:
    num_hosts (int): Number of hosts
    SEVERITIES (list[str]): List of syslog severities
    SEVERITY_WEIGHTS (list[int]): Weights for each severity
    HOSTS (list[str]): List of hosts
    HOST_WEIGHTS (list[float]): Weights for each host
    FILE_PATH (str): Path to the file containing F5 syslog messages
    LOGGER (logging.Logger): Logger object
    KAFKA_URL (str): Kafka Broker URL
    SCHEMA_REGISTRY_URL (str): Schema Registry URL
    KAFKA_TOPIC (str): Kafka Topic
    SYSLOG_SCHEMA_REGISTRY_SUBJECT (str): Schema Registry Subject for syslog messages
    SYSLOG_SCHEMA_STR (str): Schema string for syslog messages
"""

import logging
import logging.handlers
import random
import time

from confluent_kafka import SerializingProducer
from confluent_kafka.schema_registry import Schema, SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer

# Syslog message simulation configuration
NUM_HOSTS = 5
SEVERITIES = [
    "EMERG",
    "ALERT",
    "CRIT",
    "ERR",
    "WARNING",
    "NOTICE",
    "INFO",
    "DEBUG",
]
SEVERITY_WEIGHTS = [1, 1, 2, 3, 4, 5, 6, 7]
HOSTS = [f"host{i}.example.com" for i in range(NUM_HOSTS)]
HOST_WEIGHTS = [0.1, 0.2, 0.3, 0.2, 0.2]
FILE_PATH = "streams-demo/src/main/resources/logs/f5_syslog_messages.txt"
LOGGER = logging.getLogger("SyslogGenerator")
KAFKA_URL = "localhost:9092"
SCHEMA_REGISTRY_URL = "http://localhost:8081"
KAFKA_TOPIC = "syslog"
SYSLOG_SCHEMA_REGISTRY_SUBJECT = "syslog-value"

with open("streams-demo/src/main/avro/syslog_message.avsc", "r", encoding="utf8") as f:
    SYSLOG_SCHEMA_STR = f.read()


def get_schema_from_schema_registry(
    schema_registry_url: str, schema_registry_subject: str
):
    """Get the latest version of schema from schema registry.

    Args:
        schema_registry_url (str): Schema Registry URL
        schema_registry_subject (str): Schema Registry Subject
    """
    schema_reg = SchemaRegistryClient({"url": schema_registry_url})
    try:
        latest_version = schema_reg.get_latest_version(schema_registry_subject)
        return schema_reg, latest_version
    except Exception as e:
        LOGGER.info(
            "No existing schema found for %s. Error: %s",
            schema_registry_subject,
            e,
        )
        return schema_reg, None


def register(schema_registry_url: str, schema_registry_subject: str, schema_str: str):
    """Register schema to schema registry.

    Args:
        schema_registry_url (str): Schema Registry URL
        schema_registry_subject (str): Schema Registry Subject
        schema_str (str): Schema String
    """
    # Only register if it hasn't already been registered
    schema_reg = SchemaRegistryClient({"url": schema_registry_url})
    schema = Schema(schema_str, schema_type="AVRO")

    try:
        schema_id = schema_reg.get_schema_id_by_subject(
            subject_name=schema_registry_subject, schema=schema
        )
        return schema_id
    except Exception:
        LOGGER.info("Registering schema for %s", schema_registry_subject)
        schema_id = sr.register_schema(
            subject_name=schema_registry_subject, schema=schema
        )
        return schema_id


def delivery_report(errmsg: str, msg: dict):
    """Report callback called (from flush()) on successful/failed delivery.

    Args:
        errmsg (str): Error message
        msg (dict): Message
    """
    if errmsg is not None:
        LOGGER.error("Delivery failed for Message: %s : %s", msg.value(), errmsg)
        return

    LOGGER.debug(
        """Message: %d successfully produced to
        Topic: %s
        Partition: [%s] at offset {%d}""",
        msg.value(),
        msg.topic(),
        msg.partition(),
        msg.offset(),
    )


def avro_producer(
    kafka_url: str,
    records: list[str],
) -> None:
    """Create Avro Serializer and Kafka Producer.

    Args:
        kafka_url (str): Kafka Broker URL.
        records (list[str]): List of syslog messages.
    """
    # Register or update the schema
    schema_reg, latest_version = get_schema_from_schema_registry(
        SCHEMA_REGISTRY_URL, SYSLOG_SCHEMA_REGISTRY_SUBJECT
    )

    value_avro_serializer = AvroSerializer(
        schema_registry_client=schema_reg,
        schema_str=latest_version.schema.schema_str,
        # conf={"auto.register.schemas": True},
    )

    LOGGER.info("Created Avro Serializer")

    # Kafka Producer
    producer = SerializingProducer(
        {
            "bootstrap.servers": kafka_url,
            "security.protocol": "plaintext",
            "value.serializer": value_avro_serializer,
            "delivery.timeout.ms": 120000,  # set it to 2 mins
            "enable.idempotence": "true",
        }
    )

    LOGGER.info("Created Kafka Producer")

    while True:
        syslog_message = {
            "timestamp": int(time.time() * 1000),  # Current time in milliseconds
            "host": random.choices(HOSTS, weights=HOST_WEIGHTS, k=1)[0],
            "severity": random.choices(SEVERITIES, weights=SEVERITY_WEIGHTS, k=1)[0],
            "message": random.choice(records),
        }
        LOGGER.info(syslog_message)

        try:
            producer.produce(
                topic=KAFKA_TOPIC,
                value=syslog_message,
                on_delivery=delivery_report,
            )

            LOGGER.info("Produced message: %s", syslog_message.get("message"))

            events_processed = producer.poll(1)
            messages_in_queue = producer.flush(1)
            LOGGER.debug(
                "Messages in queue: %s, Events processed: %s",
                messages_in_queue,
                events_processed,
            )
        except Exception as e:
            LOGGER.error(e)

        time.sleep(random.uniform(5, 10))  # Random short delay between messages


def load_messages(file_path: str) -> list[str]:
    """Load messages from file.

    Args:
        file_path (str): File path

    Returns:
        list[str]: List of messages
    """
    with open(file_path, "r", encoding="utf8") as file:
        return [line.strip() for line in file.readlines()]


def main() -> None:
    """Entrypoint function to start the syslog generator.

    Returns:
        None
    """
    logging.basicConfig(
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
        level=logging.INFO,
    )

    f5_messages = load_messages(FILE_PATH)
    avro_producer(KAFKA_URL, f5_messages)


if __name__ == "__main__":
    main()
