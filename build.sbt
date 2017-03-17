name := "api_rabota"

version := "1.0"

lazy val `api_rabota` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(jdbc, cache, ws,
  "com.typesafe.akka" %% "akka-remote" % "2.4.16",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.1",
  "org.jsoup" % "jsoup" % "1.10.2",
  specs2 % Test)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"  