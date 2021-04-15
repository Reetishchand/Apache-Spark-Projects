import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

object GraphAnalysis {
  def main(args: Array[String]): Unit = {

    if (args.length != 2) {
      println("Input and output path required")
    }

    val input = args(0)
    val output_path = args(1)

    val sc = new SparkContext(new SparkConf().setAppName("Spark GraphAnalysis"))
    val parsedFile = sc.textFile(input)
    val edges: RDD[(VertexId, VertexId)] = parsedFile.map(line => line.split("\t")).map(line =>(line(0).toInt, line(1).toInt))
    val graph = Graph.fromEdgeTuples(edges, 1)


    val top_5_outdegree = graph.outDegrees.sortBy(-_._2).take(5)
    sc.parallelize(top_5_outdegree).coalesce(1).saveAsTextFile(output_path+"part_1.txt")

    val top_5_indegree =  graph.inDegrees.sortBy(-_._2).take(5)
    sc.parallelize(top_5_indegree).coalesce(1).saveAsTextFile(output_path+"part_2.txt")

    val top_5_rank = graph.pageRank(0.015).vertices.sortBy(-_._2).take(5)
    sc.parallelize(top_5_rank).coalesce(1).saveAsTextFile(output_path+"part_3.txt")

    val top_5_conn = graph.connectedComponents().vertices.sortBy(-_._2).take(5)
    sc.parallelize(top_5_conn).coalesce(1).saveAsTextFile(output_path+"part_4.txt")

    val top_5_triangle= graph.triangleCount().vertices.sortBy(-_._2).take(5)
    sc.parallelize(top_5_triangle).coalesce(1).saveAsTextFile(output_path+"part_5.txt")
  }
}
