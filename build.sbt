name := "web-search"

version := "1.0"

scalaVersion := "2.10.0"

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-feature"

seq(webSettings: _*)

resolvers ++= Seq(
  "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases",
  "Typesafe Repository Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"
)
  
libraryDependencies ++= {
  val liftVersion = "2.5-M4"
  Seq(
  "net.liftweb" %% "lift-webkit" % liftVersion % "compile->default",
  "net.liftweb" %% "lift-mapper" % liftVersion % "compile->default",
  "net.liftweb" %% "lift-wizard" % liftVersion % "compile->default",
  "net.liftmodules" %% "widgets" % "2.5-SNAPSHOT-1.2-SNAPSHOT" % "compile->default"
  )
}

libraryDependencies ++= Seq(
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.1",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.1",
  "nu.validator.htmlparser" % "htmlparser" % "1.2.1"
)

libraryDependencies ++= Seq(
  "cc.co.scala-reactive" %% "reactive-web" % "latest.integration",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "container", // For Jetty, add scope test to make jetty avl. for tests
  "junit" % "junit" % "latest.integration" % "test->default", // For JUnit 4 testing
  "org.specs2" %% "specs2" % "latest.integration" % "test", // For specs.org tests
  "javax.servlet" % "servlet-api" % "2.5" % "provided->default",
  "javax.servlet.jsp" % "jsp-api" % "2.0",
  "com.h2database" % "h2" % "latest.integration", // In-process database, useful for development systems
  "ch.qos.logback" % "logback-classic" % "0.9.26", // Logging
  "com.amazonaws" % "aws-java-sdk" % "1.2.12" 
)

