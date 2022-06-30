package webapp

import cats.effect.{IO, SyncIO}
import colibri.Subject
import outwatch.*
import outwatch.dsl.*
import webapp.util.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("src/main/css/index.css", JSImport.Namespace)
object Css extends js.Object

@js.native
@JSImport("src/main/css/tailwind.css", JSImport.Namespace)
object TailwindCss extends js.Object

object Main {
  TailwindCss
  Css // load css

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def main(args: Array[String]): Unit =
    Outwatch.renderInto[SyncIO]("#app", App.layout).unsafeRunSync()
}
