-- This relies on the schema being created in the streams app
-- Create a stream from the syslog topic
CREATE STREAM syslog_stream_ksql (
    host VARCHAR,
    severity VARCHAR
) WITH (
    KAFKA_TOPIC='syslog',
    VALUE_FORMAT='AVRO',
    KEY_FORMAT='KAFKA'
);

-- Create a cumulative aggregation table without windowing
CREATE TABLE severity_counts_ksql AS
SELECT
    host,
    COUNT(CASE WHEN severity IN ('EMERG', 'ALERT', 'CRIT') THEN 1 ELSE NULL END) AS high,
    COUNT(CASE WHEN severity IN ('ERR', 'WARNING') THEN 1 ELSE NULL END) AS medium,
    COUNT(CASE WHEN severity IN ('NOTICE', 'INFO', 'DEBUG') THEN 1 ELSE NULL END) AS low
FROM
    syslog_stream_ksql
GROUP BY
    host
EMIT CHANGES;
