import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession
import collection.mutable.Map

object PageRank {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("Please provide all the parameters")
    }
    val spark = SparkSession.builder.appName("Page Rank Implementation").master("local").getOrCreate()
    val sc = spark.sparkContext
    val sql = spark.sqlContext
    import spark.implicits._
    val pr = 10.0;
    val airpots = sc.textFile(args(0)).map(x => x.split(",")).map(x => (x(0), x(1)));
    val headers = airpots.first
    val data = airpots.filter(x => x != headers)
    val iteration_count = args(1).toInt;
    val destination = args(2).toString;
    val allUniqueAirports = (data.map { case (x, y) => x }.distinct() ++ data.map { case (x, y) => y }.distinct()).distinct().collect();
    val outLinks = data.groupByKey().map(x => (x._1, x._2.size)).collect().map(x => (x._1, x._2)).toMap;
    val rank = Map() ++ allUniqueAirports.map(x => (x, pr)).toMap;
    for (i <- 1 to iteration_count) {
      val calc = Map() ++ allUniqueAirports.map(x => (x, 0.0)).toMap
      rank.keys.foreach((id) => rank(id) = rank(id) / outLinks(id))
      for ((key, value) <- data.collect()) {
        calc.put(value, calc(value) + rank(key))
      }
      val pr_out = collection.mutable.Map() ++ calc.map(x => (x._1, ((0.15 / allUniqueAirports.size) + 0.85 * x._2)))
      pr_out.keys.foreach((id) => rank(id) = pr_out(id))
    }
    val results = rank.toSeq.sortBy(-_._2).map{case(x:String,h:Double)=>(x.slice(1,x.length-1),h)}.toDF("Airport", "Rank")
    results.write.option("header","true").csv(destination)

  }
}