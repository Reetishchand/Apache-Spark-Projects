Steps to execute on AWS cluster:
	- create a cluster using AWS EMR 
	- once the cluster is up and running add a step with the following arguments:
	spark-submit --deploy-mode client --class TwitterSentimentAnalysis s3://gprc.cs6350spring2020/twitter-sentiment-analysis_2.11-0.1.jar s3://gprc.cs6350spring2020/Tweets.csv s3://gprc.cs6350spring2020/Task_2'
	- after adding the step the cluster will execute the files the status changes to completed 
	- go the the destination path i.e. 3rd argument and check the file generated which contains the metrics of the logistic regression performed.