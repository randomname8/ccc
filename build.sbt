name := "ccc"
scalaVersion := "2.12.4"

fork := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yinfer-argument-types", "-Yno-adapted-args", "-Xlint", "-Ypartial-unification", "-opt:_", "-opt-warnings:_", "-Ywarn-extra-implicit", "-Ywarn-inaccessible", "-Ywarn-infer-any", "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen")
scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:_", "-opt:_", "-Xlint")

libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.4.0",
  "com.atlassian.commonmark" % "commonmark" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-ins" % "0.11.0",
)

mainClass in reStart := Some("ccc.DevAppReloader")

//javaOptions in run ++= Seq("--patch-module", "javafx.controls=target/scala-2.12/classes")

//generate scalaized beans for certain classes
sourceGenerators in Compile += Def.task {
  val classpath = (dependencyClasspath in Compile).value.files
  val classpathHash = scala.util.hashing.MurmurHash3.stringHash(classpath.mkString(":") + file("build.sbt").lastModified)
  val destFile = (sourceManaged in Compile).value / "scala-beans" / s"beans-$classpathHash.scala"
  if (!destFile.exists) {
    if (destFile.getParentFile.exists) destFile.getParentFile.listFiles.foreach(_.delete())
    ScalaPropertiesForJavaGenerator.generate(destFile.toPath, classpath.map(_.toPath).toArray,
      patterns = Seq(
        "javafx/stage/[^/]+.class",
        "javafx/scene/[^/]+.class",
        "javafx/scene/control/[^/]+.class",
        "javafx/scene/canvas/[^/]+.class",
        "javafx/scene/layout/[^/]+.class",
        "javafx/scene/input/[^/]+.class",
        "javafx/scene/web/[^/]+.class",
        "tiled/core/[^/]+.class"))
  }
  Seq(destFile)
}.taskValue
