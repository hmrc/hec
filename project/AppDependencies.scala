import play.core.PlayVersion
import sbt._

object AppDependencies {

  val akkaVersion      = "2.6.14"
  val bootstrapVersion = "5.16.0"
  val hmrcMongoVersion = "0.56.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"          % hmrcMongoVersion,
    "uk.gov.hmrc"             %% "domain"                      % "6.2.0-play-28",
    "ai.x"                    %% "play-json-extensions"        % "0.42.0",
    "org.typelevel"           %% "cats-core"                   % "2.6.1",
    "com.github.kxbmap"       %% "configs"                     % "0.6.1",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-28" % "0.37.0",
    "com.beachape"            %% "enumeratum"                  % "1.7.0",
    "com.miguno.akka"         %% "akka-mock-scheduler"         % "0.5.1" exclude ("com.typesafe.akka", "akka-actor")
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-27" % hmrcMongoVersion    % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.10"            % Test,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalamock"          %% "scalamock"               % "5.1.0"             % Test,
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2"            % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"             % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"            % akkaVersion         % Test,
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.1"             % Test
  )
}
