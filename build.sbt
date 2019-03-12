name := "akka-streams-to-http-example"

version := "0.1"
organizationName := "NuvolaTech LLC - Roberto Congiu"
startYear := Some(2019)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

scalaVersion := "2.12.8"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.21",
  "com.typesafe.akka" %% "akka-http"   % "10.1.7",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.7")

mainClass := Some("com.nuvola_tech.tests.BackPressureTest")