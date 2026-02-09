import scoverage.ScoverageKeys
val appName = "hec"

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|testonly|views).*;.*(BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    majorVersion := 1,
    scalaVersion := "3.3.6",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions := Seq(
      "-Wconf:src=html/.*:s",
      "-Wconf:src=routes/.*:s",
      "-deprecation",
      "-feature"
    ),
    Compile / doc / sources := Seq.empty
  )
  .settings(
    // To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always)
  )
  .settings(scoverageSettings: _*)
  .settings(scalafmtOnCompile := true)
  .settings(PlayKeys.playDefaultPort := 10105)
