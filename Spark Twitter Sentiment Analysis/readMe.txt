Kafka Enviroment Setup
		  Navigate to the kafka directory 
							  1. Start zookeeper server 
							  .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
							  2. Start kafka server
							  .\bin\windows\kafka-server-start.bat .\config\server.properties
							  3. Create a topic 
							  .\bin\windows\kafka-topics.bat --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic myTopic
							  4. Create a producer 
							  .\bin\windows\kafka-console-producer.bat --broker-list localhost:9092 --topic myTopic
							  5. Create a consumer
							  .\bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic myTopic --from-beginning
							  
Spark Enviroment Setup
		  Navigate to the spark installation directory
							  1. Run your jar
								  spark-submit  --class TwitterSentimentAnalysis <path_to_jar> <queryString> <topicName>

Visualization Environment Setup
		  Navigate to the elasticsearch directory
							  1.  Start ElasticSearch
									elasticsearch
		  Navigate to the kibana directory
							  1. Start kibana
									kibana
		  Navigate to the logstash directory
							  1. Edit the logstash.conf file => set the topicName
							  2. logstash -f logstash.conf

		  Open your browser and navigate to http://localhost:5601/app/timelion#
							give the filters
											.es(q='message.keyword:NEGATIVE').label('Negative'),
											.es(q='message.keyword:POSITIVE').label('Positive'),
											.es(q='message.keyword:NEUTRAL').label('Neutral')




