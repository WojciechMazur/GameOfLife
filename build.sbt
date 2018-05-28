name := "GameOfLife"

version := "0.1"

scalaVersion := "2.12.6"

val akkaVersion = "2.5.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"             % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"           % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream"            % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit"    % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
  "com.typesafe"      %  "config"                 % "1.3.2",
  "ch.qos.logback"    %  "logback-classic"        % "1.2.3",

  "org.scalafx"       %% "scalafx"                % "8.0.144-R12",

  "org.scalatest"     % "scalatest_2.12"          % "3.0.5" % "test"
)