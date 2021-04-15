import java.util.Properties
import Sentiment.Sentiment
import org.apache.spark.SparkConf
import org.apache.spark.streaming.twitter.TwitterUtils
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext
import scala.collection.convert.wrapAll._

object TwitterSentimentAnalysis {
  val properties = new Properties()
  properties.setProperty("annotators", "tokenize, ssplit, parse, sentiment")
  val pipeline: StanfordCoreNLP = new StanfordCoreNLP(properties)

  def main(args: Array[String]) {

    if (args.length != 2) {
      println("Invalid arguments passed.")
      println("Usage: spark-submit --class <class_name> <path_to_jar> <term> <topic> \n  Try again")
      System.exit(1)
    }

    val queryString = Seq(args(0))
    val topic = args(1)
    val apiKey = "uYqzfFceYwIdzZ8pY84gYMWjL"
    val apiSecretKey  = "AsjDEsDUuVlNmOTRYA3tbzv2yjOQNjUGT2CVqwSsvQgIAXpMFI"
    val accessToken = "876865368114855936-XCbMSBrxlXWi4wU5JhXBGvWz5VpR3lz"
    val accessSecretToken = "APJriAXpM1aKnzMdSPZluZ9EyHxHpCKKR6HhzAVwddLlc"

    System.setProperty("twitter4j.oauth.consumerKey", apiKey)
    System.setProperty("twitter4j.oauth.consumerSecret", apiSecretKey)
    System.setProperty("twitter4j.oauth.accessToken", accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", accessSecretToken)

    val sparkConf = new SparkConf().setAppName("Spark Streaming")
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[*]")
    }

    val streamingContext = new StreamingContext(sparkConf, Seconds(60))
    val myTwitterStream = TwitterUtils.createStream(streamingContext, None, queryString)
    val sentimentForEachTag = myTwitterStream.map(status =>status.getText()).map{hashTag=> (hashTag,getSentiment(hashTag))}

    sentimentForEachTag.cache().foreachRDD(sentimentRDD =>
      sentimentRDD.foreachPartition(partition =>
        partition.foreach { x => {
          val sparkSerializer = "org.apache.kafka.common.serialization.StringSerializer"
          val properties = new Properties()
          properties.put("bootstrap.servers", "localhost:9092")
          properties.put("key.serializer", sparkSerializer)
          properties.put("value.serializer", sparkSerializer)
          val producer = new KafkaProducer[String, String](properties)
          val consumerData = new ProducerRecord[String, String](topic, x._1.toString,x._2.toString)
          println(x)
          producer.send(consumerData)
          producer.close()
        }}
      ))
    streamingContext.start()
    streamingContext.awaitTermination()
  }


  private def getSentiment(text: String): Sentiment = {
    val list = findSentiments(text)
    val (_, sentiment) = if(list.nonEmpty) list
      .maxBy { case (sentence, _) => sentence.length }
    else findSentiments("key").maxBy{case (sentence, _) => sentence.length }
    sentiment
  }

  def stringToSentiment(input: String): Sentiment = Option(input) match {
    case Some(text) if text.nonEmpty => getSentiment(text)
    case _ => throw new IllegalArgumentException("Not a valid input")
  }

  def stringToList(input: String): List[(String, Sentiment)] = Option(input) match {
    case Some(text) if text.nonEmpty => findSentiments(text)
    case _ => throw new IllegalArgumentException("Not a valid input")
  }

  private def findSentiments(text: String): List[(String, Sentiment)] = {
    val annotation: Annotation = pipeline.process(text)
    val sentences = annotation.get(classOf[CoreAnnotations.SentencesAnnotation])
    sentences.map(sentence => (sentence, sentence.get(classOf[SentimentCoreAnnotations.SentimentAnnotatedTree])))
      .map { case (sentence, tree) => (sentence.toString, Sentiment.toSentiment(RNNCoreAnnotations.getPredictedClass(tree))) }
      .toList
  }
}

object Sentiment extends Enumeration {
  type Sentiment = Value
  val POSITIVE, NEGATIVE, NEUTRAL = Value

  def toSentiment(sentiment: Int): Sentiment = sentiment match {
    case x if x == 0 || x == 1 => Sentiment.NEGATIVE
    case 2 => Sentiment.NEUTRAL
    case x if x == 3 || x == 4 => Sentiment.POSITIVE
  }
}