name := "ccc"
scalaVersion := "2.12.6"

fork := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yno-adapted-args", "-Xlint", "-Ypartial-unification", "-opt:_", "-opt-warnings:_", "-Ywarn-extra-implicit", "-Ywarn-inaccessible", "-Ywarn-infer-any", "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen")
scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:_", "-opt:_", "-Xlint")

resolvers += "jcenter bintray" at "http://jcenter.bintray.com"

libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.4.0",
  "com.atlassian.commonmark" % "commonmark" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-ins" % "0.11.0",
  "com.univocity" % "univocity-parsers" % "2.5.9",
  "uk.co.caprica" % "vlcj" % "3.10.1",
)

mainClass in reStart := Some("ccc.DevAppReloader")

//javaOptions in run ++= Seq("--patch-module", "javafx.controls=target/scala-2.12/classes")
