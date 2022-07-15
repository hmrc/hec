resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(
  Resolver.ivyStylePatterns
)
resolvers += Resolver.typesafeRepo("releases")
resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")

addSbtPlugin("uk.gov.hmrc"               % "sbt-auto-build"     % "3.7.0")
addSbtPlugin("uk.gov.hmrc"               % "sbt-distributables" % "2.1.0")
addSbtPlugin("com.typesafe.play"         % "sbt-plugin"         % "2.8.13")
addSbtPlugin("org.wartremover"           % "sbt-wartremover"    % "3.0.5")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"       % "2.4.6")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"      % "2.0.0")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"       % "0.9.34")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"       % "0.3.3")
