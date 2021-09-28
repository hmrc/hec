import play.core.PlayVersion
import sbt._

object AppDependencies {

  val akkaVersion = "2.6.14"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "5.11.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.52.0",
    "uk.gov.hmrc"       %% "mongo-lock"                % "7.0.0-play-28",
    "uk.gov.hmrc"       %% "mongo-caching"             % "7.0.0-play-28",
    "uk.gov.hmrc"       %% "domain"                    % "6.2.0-play-28",
    "ai.x"              %% "play-json-extensions"      % "0.42.0",
    "org.typelevel"     %% "cats-core"                 % "2.1.0",
    "com.github.kxbmap" %% "configs"                   % "0.4.4"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28" % "5.11.0"            % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"     % "5.0.0-play-28"     % Test,
    "org.scalatest"          %% "scalatest"              % "3.2.9"             % Test,
    "com.typesafe.play"      %% "play-test"              % PlayVersion.current % Test,
    "org.scalamock"          %% "scalamock"              % "5.1.0"             % Test,
    "com.vladsch.flexmark"    % "flexmark-all"           % "0.36.8"            % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"     % "5.1.0"             % "test, it",
    "com.typesafe.akka"      %% "akka-testkit"           % akkaVersion         % Test
  )
}
