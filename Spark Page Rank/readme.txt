Steps to execute on AWS cluster:
	- create a cluster using AWS EMR 
	- once the cluster is up and running add a step with the following arguments which includes all the 3 input parameters required for the .jar file to run:
	spark-submit --deploy-mode client --class PageRank s3://gprc.cs6350spring2020/pagerank_2.11-0.1.jar s3://gprc.cs6350spring2020/airlines.csv 50 s3://gprc.cs6350spring2020/Task_1' 
	- after adding the step the cluster will execute the files the status changes to completed 
	- go the the destination path i.e. 3rd argument and check the .csv file generated which contains the airport code and rank 
	