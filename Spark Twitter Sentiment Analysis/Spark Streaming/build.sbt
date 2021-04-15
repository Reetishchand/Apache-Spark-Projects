name := "TwitterSentimentAnalysis"

version := "0.1"

scalaVersion := "2.11.12"

lazy val root = (project in file(".")).
  settings(
    name := "TwitterSentimentAnalysis",
    version := "1.0",
    scalaVersion := "2.11.12",
    mainClass in Compile := Some("TwitterSentimentAnalysis")
  )

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-streaming_2.11" % "1.6.3",
  "org.apache.kafka" % "kafka-clients" % "0.10.0.0",
  "org.apache.spark" %% "spark-streaming-twitter" % "1.6.3",
  "org.apache.bahir" %% "spark-streaming-twitter" % "2.4.0" % "provided",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.5.2" artifacts (Artifact("stanford-corenlp", "models"), Artifact("stanford-corenlp")
))

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

