Steps to execute on AWS cluster:
	- create a cluster using AWS EMR 
	- once the cluster is up and running add a step with the following arguments:
	spark-submit --deploy-mode client --class GraphAnalysis s3://cs6350rgaphanalysis/graphanalysis_2.11-0.1.jar s3://cs6350rgaphanalysis/com-amazon.ungraph.txt s3://cs6350rgaphanalysis/outputs/
	- after adding the step the cluster will execute the files the status changes to completed 
	- go the the destination path i.e. 2nd argument and check 5 files generated which contains the output for each part of the question