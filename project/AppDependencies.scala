import AppDependencies.playVersion
import play.core.PlayVersion
import sbt._

object AppDependencies {
  val playVersion      = s"-play-28"
  val akkaVersion      = "2.6.14"
  val bootstrapVersion = "5.16.0"
  val hmrcMongoVersion = "0.56.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"           % hmrcMongoVersion,
    "uk.gov.hmrc"             %% "domain"                            % "6.2.0-play-28",
    "uk.gov.hmrc"             %% s"internal-auth-client$playVersion" % "1.0.0",
    "ai.x"                    %% "play-json-extensions"              % "0.42.0",
    "org.typelevel"           %% "cats-core"                         % "2.6.1",
    "com.github.kxbmap"       %% "configs"                           % "0.6.1",
    "uk.gov.hmrc.objectstore" %% s"object-store-client$playVersion"  % "0.37.0",
    "com.beachape"            %% "enumeratum"                        % "1.7.0",
    "com.miguno.akka"         %% "akka-mock-scheduler"               % "0.5.1" exclude ("com.typesafe.akka", "akka-actor")
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion    % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.10"            % Test,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalamock"          %% "scalamock"               % "5.1.0"             % Test,
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2"            % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"             % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"            % akkaVersion         % Test,
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.1"             % Test
  )
}
