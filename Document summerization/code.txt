# Key in your AWS credentials here
myAWSsecret="8g010QeXxxIzzpP/40erBzzVfE0+4+DFAXHAj0Ym"
myAWSkey="AKIAT6DBARJD4EIHOUEU"
bucket = "bigdata-project-text"

from pyspark.sql import SparkSession
from pyspark import SparkConf, SparkContext
spark = SparkSession.builder.appName("Document Summerization") .getOrCreate()
sc = spark.sparkContext

import s3fs
import matplotlib.pyplot as plt
import boto3
from pyspark.sql.functions import trim
from pyspark.sql.functions import regexp_replace, col
from pyspark.sql.types import *
from pyspark.sql import SQLContext
from sklearn.feature_extraction.text import TfidfTransformer
from nltk.tokenize.punkt import PunktSentenceTokenizer
from sklearn.feature_extraction.text import CountVectorizer
import networkx as nx
import pandas
import nltk
from sklearn.model_selection import train_test_split
import bs4 as BeautifulSoup
import urllib.request
import rouge
import requests


# creating connections to s3 bucket to read and write files to s3 bucket
# make sure the created bucket and and the dataset uploaded are public
sc._jsc.hadoopConfiguration().set("fs.s3n.awsAccessKeyId", myAWSkey)
sc._jsc.hadoopConfiguration().set("fs.s3n.awsSecretAccessKey",myAWSsecret)
s3bo = boto3.resource('s3',aws_access_key_id=myAWSkey, aws_secret_access_key=myAWSsecret)
s3 = s3fs.S3FileSystem(key=myAWSkey, secret=myAWSsecret)

# lists that store the metrics after summeries are generated
my_metric_precision =[]
my_metric_recall =[]
my_metric_fstat = []

# tokenizes each line passed 
def getTokensForEachSentence(sentence):
    line = ' '.join(sentence.strip().split('\n'))
    tokenizer_line = PunktSentenceTokenizer()
    new_lines = tokenizer_line.tokenize(line)
    return new_lines
    
 # converts a collection of text documents to vectors of token counts
def applyCountVectorizerForLine(sentence):
    return CountVectorizer().fit_transform(sentence).toarray()
    
  # generates the tf-idf value for each term which reflect the importance of a term to a document in the corpus
def getTFIDFScores(elements):
    normalizedMatrix = TfidfTransformer().fit_transform(elements)
    return (normalizedMatrix * normalizedMatrix.T)
    
 # Creates a new graph from an adjacency matrix and applies page rank algorthim to it
def getPageRankScores(elements):
    mySparseGraph = nx.from_scipy_sparse_matrix(elements)
    ranks = nx.pagerank(mySparseGraph)
    return ranks
    
 # uses rouge and evaluates the performance of the summerization
def prepareScore(ulfa, abstractDF, n, outputDF):
    for agg in ['Avg', 'Best']:
        print('Analysis using {}'.format(agg))
        averageApply,bestApply= (agg == 'Avg'),(agg == 'Best')

        evalResult = rouge.Rouge(metrics=['rouge-n', 'rouge-l', 'rouge-w'], limit_length=True,max_n=3,length_limit_type='words',apply_avg=averageApply,alpha=.51,weight_factor=1.24,apply_best=bestApply,length_limit=100,stemming=True)

        assume=[]
        for _, _q in ulfa.iterrows():
            assume.append(_q['generated'])

        ref=[]
        for _, _q in abstractDF.iterrows():
            ref.append(_q['summary'])

        outputs = evalResult.get_scores(assume, ref)

        for m, r in sorted(outputs.items(), key=lambda a: a[0]):
            outputDF = outputDF.append({'Best/Average': agg, 'Metric':m, 'ModelNumber':n, 'Precision': 100 * r['p'], 'F-stat': 100 * r['f'],  'Recall': 100 * r['r'] }, ignore_index=True)
            if m == 'rouge-l' and agg == 'Avg':
                my_metric_precision.append(100 * r['p'])
                my_metric_recall.append(100 * r['r'])
                my_metric_fstat.append(100 * r['f'])
    return outputDF
    
    
    
 # read the input from s3 bucket
news = spark.read.format('csv').options(header='true', inferSchema='true', mode ="DROPMALFORMED").load('s3://'+bucket+'/news_summary.csv')
# drop the null values if any
news = news.dropna()
display(news)



news_subset = pandas.DataFrame(columns=['summary', 'text'])
news_pandas = news.toPandas()


# read the URL for each document and gets the respective data from the URL
for row in news_pandas.iterrows():
    url = row[1][3]
    text = row[1][4]
    try:
        request = requests.get(url)
        fetched_data = urllib.request.urlopen(url)
        article_read = fetched_data.read()
        article_parsed = BeautifulSoup.BeautifulSoup(article_read,'html.parser')
        paragraphs = article_parsed.find_all('p')
        article_content = ''
        for p in paragraphs:
            article_content += p.text
        news_subset = news_subset.append({'summary': text, 'text': article_content}, ignore_index=True)
    except:
        continue


# drop null values and extract 2 columns which we are going to work with
news = news_subset.dropna()
text = news['text']
reference = news['summary']


nltk.download('punkt')
frame = text.to_frame()
newsRDD = sc.parallelize(text).map(lambda x:(x,))
tokenDF = newsRDD.map(lambda sentence: getTokensForEachSentence(sentence[0]))
frame['sentence'] = tokenDF.collect()
cvDF = tokenDF.map(lambda token: applyCountVectorizerForLine(token))
frame['vectors'] = cvDF.collect()
graphDF = cvDF.map(lambda cv: getTFIDFScores(cv))
frame['tf_idf'] = graphDF.collect()
resultDF = graphDF.map(lambda graph: getPageRankScores(graph))
frame['scores'] = resultDF.collect()


c = 0
arr = []
for i, j in frame.iterrows():
    ordered = sorted(((j['scores'][i], s) for i, s in enumerate(j['sentence'])), reverse=True)
    temp= ""
    for _ in range(5):
        if _ < len(ordered):
            temp = temp + ordered[_][1]
    arr.append(temp)
frame['generated'] = arr

referenceDF = reference.to_frame()
generatedDF = frame['generated'].to_frame()


# create a dataframe with the summary generated and given reference summary
comparisonDF = pandas.concat([referenceDF,generatedDF],axis =1)
display(comparisonDF)

inputDF = news.to_csv(None).encode()
with s3.open(bucket+"/input.csv", 'wb') as f:
    f.write(inputDF)
link_to_input = "https://s3.us-east-2.amazonaws.com/" + bucket + "/input.csv"
s3bo.Bucket(bucket).Object('input.csv').Acl().put(ACL='public-read')


# write the output dataframe containing the reference and generated summary to S3
writeDF = comparisonDF.to_csv(None).encode()
with s3.open(bucket+"/output.csv", 'wb') as f:
    f.write(writeDF)
link_to_output ="https://s3.us-east-2.amazonaws.com/" + bucket + "/output.csv"
s3bo.Bucket(bucket).Object('output.csv').Acl().put(ACL='public-read')

my_metric_precision =[]
my_metric_recall = []
my_metric_fstat = []
analysis = pandas.DataFrame(columns = ['Best/Average', 'Metric', 'ModelNumber', 'Precision', 'F-stat', 'Recall'])

# computing the performace metrics
for num in range(1, 11, 2):
    outputDF = pandas.DataFrame(columns = ['Best/Average', 'Metric', 'ModelNumber', 'Precision', 'F-stat', 'Recall'])
    count = 0
    myIs = []
    for i, j in frame.iterrows():
        ordered = sorted(((j['scores'][i], s) for i, s in enumerate(j['sentence'])), reverse=True)
        temp= ""
        for k in range(num):
            if k < len(ordered):
                temp = temp + ordered[k][1]
        myIs.append(temp)
    frame['generated'] = myIs
    referenceDF = reference.to_frame()
    outputDF = prepareScore(frame, referenceDF, num, outputDF)
    analysis = analysis.append(outputDF)
    print(analysis)
    
# write the metrics to S3
analysisDF = analysis.to_csv(None).encode()
with s3.open(bucket+"/analysis.csv", 'wb') as f:
    f.write(analysisDF)
link_to_analysis = "https://s3.us-east-2.amazonaws.com/" + bucket + "/analysis.csv"
s3bo.Bucket(bucket).Object('analysis.csv').Acl().put(ACL='public-read')


# write the metrics to S3
analysisDF = analysis.to_csv(None).encode()
with s3.open(bucket+"/analysis.csv", 'wb') as f:
    f.write(analysisDF)
link_to_analysis = "https://s3.us-east-2.amazonaws.com/" + bucket + "/analysis.csv"
s3bo.Bucket(bucket).Object('analysis.csv').Acl().put(ACL='public-read')

# plotting the metrics on a scatterplot
plt.plot(Number_sentences, my_metric_precision, 'r--', label ='Precision')
plt.plot(Number_sentences, my_metric_recall, 'b--', label ='Recall')
plt.plot(Number_sentences, my_metric_fstat, 'g--', label ='Fstatistics')
axes = plt.gca()
axes.set_ylim([20,40])
plt.legend()
plt.ylabel('Precision,Recall,F-statistics')
plt.xlabel('Number of sentences Increases along X-axis')
plt.show()
plt.savefig('metric1.png')
display()

# observation: We can get a better by generating atleast 3 sentences for an article
plt.plot(Number_sentences, my_metric_precision)
plt.ylabel('Precision')
plt.xlabel('Number of sentences Increases along X-axis')
plt.show()
plt.savefig('metric2.png')
display()

# plotting the metrics on a barplot
plt.style.use('ggplot')
fig, axs = plt.subplots()
axs.bar(Number_sentences,my_metric_recall,label = "Recall")
axs.bar(Number_sentences,my_metric_fstat,label = "Fstatistics")
axs.bar(Number_sentences,my_metric_precision,label = "Precision")
plt.legend()
plt.ylabel('Precision,Recall,F-statistics')
plt.xlabel('Number of sentences Increases along x-axis')
plt.show()
plt.savefig('metric3.png')
display()

print(link_to_input)
print(link_to_analysis)
print(link_to_output)

# <<   THE END     >> 
    