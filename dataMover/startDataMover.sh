#!/bin/bash
kafka/bin/zookeeper-server-start.sh kafka/config/zookeeper.properties &

kafka/bin/kafka-server-start.sh kafka/config/server.properties &

inotifywait.sh

kafka/bin/kafka-file-consumer.sh --zookeeper localhost:2181 --topic test --outut-file kafka/logs/file-receiver-log.log
