lazy val akkaVersion = "2.5.4"
lazy val akkaDynamoVersion = "1.1.0"
lazy val akkaHttpVersion = "10.0.10"
lazy val logbackVersion = "1.2.3"

lazy val dependencies = Seq(
  "ch.qos.logback"            %  "logback-classic"               % logbackVersion,
  "com.typesafe.akka"         %% "akka-actor"                    % akkaVersion,
  "com.typesafe.akka"         %% "akka-cluster"                  % akkaVersion,
  "com.typesafe.akka"         %% "akka-cluster-sharding"         % akkaVersion,
  "com.typesafe.akka"         %% "akka-slf4j"                    % akkaVersion,
  "com.typesafe.akka"         %% "akka-persistence"              % akkaVersion,
  "com.typesafe.akka"         %% "akka-contrib"                  % akkaVersion,
  "com.typesafe.akka"         %% "akka-persistence-dynamodb"     % akkaDynamoVersion,
  "com.typesafe.akka"         %% "akka-http"                     % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-http-spray-json"          % akkaHttpVersion
)

lazy val testDependencies = Seq(
  "com.typesafe.akka"         %% "akka-testkit"                  % akkaVersion,
  "com.typesafe.akka"         %% "akka-multi-node-testkit"       % akkaVersion
).map(_ % "test")

lazy val root = (project in file(".")).settings(
  name := "akka-saga-pattern",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.3",
  libraryDependencies ++= dependencies ++ testDependencies
)
