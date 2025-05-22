import play.core.PlayVersion
import sbt.*

object AppDependencies {
  val playVersion      = "-play-30"
  val bootstrapVersion = "9.12.0"
  val hmrcMongoVersion = "2.6.0"
  val pekkoVersion = "1.0.3" //version has to match already existing pekko version in play

  val compile = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-backend$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"           % hmrcMongoVersion,
    "uk.gov.hmrc"             %% s"domain$playVersion"               % "11.0.0",
    "uk.gov.hmrc"             %% s"internal-auth-client$playVersion" % "4.0.0",
    "org.typelevel"           %% "cats-core"                         % "2.13.0",
    "uk.gov.hmrc.objectstore" %% s"object-store-client$playVersion"  % "2.2.0",
    "com.beachape"            %% "enumeratum-play"                   % "1.8.2"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test$playVersion"  % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion    % Test,
    "org.scalamock"          %% "scalamock"                    % "6.1.1"             % Test,
    "org.apache.pekko"       %% "pekko-stream-testkit"         % pekkoVersion        % Test,
    "com.github.pjfanning"   %% "pekko-mock-scheduler"         % "0.6.0"             % Test
  )
}