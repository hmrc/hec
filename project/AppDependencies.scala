import AppDependencies.playVersion
import play.core.PlayVersion
import sbt._

object AppDependencies {
  val playVersion      = "-play-30"
  val bootstrapVersion = "8.5.0"
  val hmrcMongoVersion = "1.7.0"
  val PekkoVersion = "1.0.2"

  val compile = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"           % hmrcMongoVersion,
    "uk.gov.hmrc"             %% s"domain$playVersion"               % "9.0.0",
    "uk.gov.hmrc"             %% s"internal-auth-client$playVersion" % "1.10.0",
    "ai.x"                    %% "play-json-extensions"              % "0.42.0",
    "org.typelevel"           %% "cats-core"                         % "2.8.0",
    "uk.gov.hmrc.objectstore" %% s"object-store-client$playVersion"  % "1.3.0",
    "com.beachape"            %% "enumeratum"                        % "1.7.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test$playVersion"  % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion    % Test,
    "org.scalamock"          %% "scalamock"                    % "5.2.0"             % Test,
    "org.apache.pekko"       %% "pekko-stream-testkit"         % PekkoVersion        % Test,
    "com.github.pjfanning"   %% "pekko-mock-scheduler"         % "0.6.0"             % Test
  )
}
