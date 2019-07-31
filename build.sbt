name := "ccc"
scalaVersion := "2.13.0"

fork := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xlint", "-opt:_", "-opt-warnings:_", "-opt:l:inline", "-opt-inline-from:scala.**,tangerine.**")
scalacOptions in (Compile, console) --= Seq("-opt:_", "-Xlint")

resolvers ++= Seq(
  "jcenter bintray" at "http://jcenter.bintray.com",
)


dependsOn(RootProject(file("../tangerine")))

lazy val jfxVersion = "12.0.1"
lazy val jfxClassifier = settingKey[String]("jfxClassifier")
jfxClassifier := {
  if (scala.util.Properties.isWin) "win"
  else if (scala.util.Properties.isLinux) "linux"
  else if (scala.util.Properties.isMac) "mac"
  else throw new IllegalStateException(s"Unknown OS: ${scala.util.Properties.osName}")
}
libraryDependencies ++= Seq(
  "org.openjfx" % "javafx-graphics" % jfxVersion classifier jfxClassifier.value,
  "org.openjfx" % "javafx-controls" % jfxVersion classifier jfxClassifier.value,
  "org.openjfx" % "javafx-base" % jfxVersion classifier jfxClassifier.value,
  "org.openjfx" % "javafx-fxml" % jfxVersion classifier jfxClassifier.value,
  "org.openjfx" % "javafx-web" % jfxVersion classifier jfxClassifier.value,
  "org.openjfx" % "javafx-media" % jfxVersion classifier jfxClassifier.value,
  "com.atlassian.commonmark" % "commonmark" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-gfm-strikethrough" % "0.11.0",
  "com.atlassian.commonmark" % "commonmark-ext-ins" % "0.11.0",
  "com.univocity" % "univocity-parsers" % "2.5.9",
  "uk.co.caprica" % "vlcj" % "3.10.1",
)

javaOptions ++= {
  val attributedJars = (Compile/dependencyClasspathAsJars).value.filterNot(_.metadata.get(moduleID.key).exists(_.organization == "org.scala-lang"))
  val modules = attributedJars.flatMap { aj =>
    try {
      val module = java.lang.module.ModuleFinder.of(aj.data.toPath).findAll().iterator.next.descriptor
      Some(aj -> module).filter(!_._2.modifiers.contains(java.lang.module.ModuleDescriptor.Modifier.AUTOMATIC))
    } catch { case _: java.lang.module.FindException => None }
  }
  Seq(
    "--add-modules=" + modules.map(_._2.name).mkString(","),
    "--module-path=" + modules.map(_._1.data.getAbsolutePath).mkString(java.io.File.pathSeparator)
  )
}

(reStart/mainClass) := Some("tangerine.DevAppReloader")
(reStart/javaOptions) ++= Seq(
  "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
  "--add-opens=javafx.graphics/javafx.scene.image=ALL-UNNAMED",
)
