/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.hec.controllers

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api._
import uk.gov.hmrc.hec.config.AppConfig

import scala.reflect.ClassTag

trait ControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with MockFactory {

  def overrideBindings: List[GuiceableModule] = List.empty[GuiceableModule]

  def additionalConfig: Configuration = Configuration()

  def buildFakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString(
            """
              | microservice.metrics.graphite.enabled = false 
              | metrics.jvm = false
              | metrics.enabled = false
              | """.stripMargin
          )
        ).withFallback(additionalConfig)
      )
      .disable[uk.gov.hmrc.hec.repos.HECTaxCheckStore]
      .overrides(overrideBindings: _*)
      .build()

  lazy val fakeApplication: Application = buildFakeApplication()

  lazy val appConfig: AppConfig = instanceOf[AppConfig]

  lazy implicit val mat: Materializer = instanceOf[Materializer]

  abstract override def beforeAll(): Unit = {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  def instanceOf[A : ClassTag]: A = fakeApplication.injector.instanceOf[A]

}
