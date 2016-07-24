val Version = "0.1.0"
val ScalaVersion = "2.11.8"

lazy val root = (project in file("core")).
  settings(
    name := "skaro",
    version := Version,
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq (
      "org.apache.avro" % "avro" % "1.8.1",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.1" % "test"
    )
  )
  .dependsOn(macro)

lazy val macro = (project in file("macro")).
  settings(
    name := "skaro-macro",
    version := Version,
    scalaVersion := ScalaVersion,
    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _),
    libraryDependencies ++= Seq (
      "org.apache.avro" % "avro" % "1.8.1"
    )
  )
