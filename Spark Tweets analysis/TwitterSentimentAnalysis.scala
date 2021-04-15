import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession
import collection.mutable.Map
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}
import org.apache.spark.ml.feature.{HashingTF, Tokenizer}
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.feature.StopWordsRemover
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.classification.{RandomForestClassificationModel, RandomForestClassifier}
import org.apache.spark.ml.regression.{DecisionTreeRegressor, GBTRegressor}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.rdd.RDD

object TwitterSentimentAnalysis {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println("Please provide all the parameters")
    }

    val spark = SparkSession.builder.appName("Twitter Processing and Classification").master("local").getOrCreate()
    val sc = spark.sparkContext
    val sql = spark.sqlContext

    import spark.implicits._
    val data = spark.read.option("header","true").option("inferschema","true").csv(args(0)).filter($"text".isNotNull)
    val Array(train,test) = data.randomSplit(Array(0.6,0.4))
    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("tokens")
    val stopWordsRemover = new StopWordsRemover().setInputCol(tokenizer.getOutputCol).setOutputCol("withoutStopWords")
    val hashingTF = new HashingTF().setNumFeatures(1000).setInputCol(stopWordsRemover.getOutputCol).setOutputCol("rawfeatures")
    val indexer = new StringIndexer().setInputCol("airline_sentiment").setOutputCol("label")
    val lr = new LogisticRegression().setMaxIter(10).setLabelCol("label").setFeaturesCol("rawfeatures")
    val pipeline = new Pipeline().setStages(Array(tokenizer,stopWordsRemover, hashingTF,indexer,lr))
    val paramGrid = new ParamGridBuilder()
      .addGrid(hashingTF.numFeatures, Array(10, 100, 1000))
      .addGrid(lr.regParam, Array(0.1, 0.01))
      .build()
    val evaluator = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction")
    val crossValidator = new CrossValidator()
      .setEstimator(pipeline)
      .setEvaluator(evaluator)
      .setEstimatorParamMaps(paramGrid)
      .setNumFolds(3)

    val model = crossValidator.fit(train)
    val result = model.transform(test)
    val prediction = result.select("prediction", "label").rdd.map{case Row(prediction: Double, label: Double) => (prediction, label)}
    val metrics = new MulticlassMetrics(prediction)
    val labels = metrics.labels
    var outputText = "\t\t\t\t\t\t     Metrics for Logistic Regression \n"

    outputText+= "\n accuracy = " + metrics.accuracy.toString
    outputText+= "\n recall = "+metrics.weightedRecall.toString
    outputText+= "\n score = "+metrics.weightedFMeasure.toString
    outputText+= "\n Confusion Matrix : \n"+metrics.confusionMatrix.toString
    outputText+="\n Label wise Data"
    for ( i<- labels) {
      outputText+="\n \t Label :"+i.toString
      outputText+= "\n falsePositiveRate= "+metrics.falsePositiveRate(i).toString
      outputText+= "\n truePositiveRate = "+metrics.truePositiveRate(i).toString
      outputText+= "\n precision = "+metrics.precision(i).toString
    }
    outputText+= "\n\n  precision = "+metrics.weightedPrecision.toString
    outputText+= "\n falsePositiveRate = "+metrics.weightedFalsePositiveRate.toString
    outputText+= "\n truePositiveRate = "+metrics.weightedTruePositiveRate.toString
    sc.parallelize(List(outputText)).coalesce(1,true).saveAsTextFile(args(1))
  }
}