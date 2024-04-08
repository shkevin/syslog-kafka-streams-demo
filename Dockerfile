FROM confluentinc/cp-kafka-connect-base:latest

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-prometheus-metrics:2.0.0
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:14.0.14
