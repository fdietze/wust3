package fun.web.client

import cats.effect.SyncIO
import colibri.Cancelable
import colibri.Observable
import colibri.Observer
import colibri.ProSubject
import colibri.Subject
import org.scalajs.dom.console
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.dsl.tags.extra.article
import cats.effect.IO

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.util.Try

@js.native
@JSImport("../../../../src/main/css/index.css", JSImport.Namespace)
object Css extends js.Object

@js.native
@JSImport("../../../../src/main/css/tailwind.css", JSImport.Namespace)
object TailwindCss extends js.Object

object Main {
  TailwindCss
  Css // load css

  def main(args: Array[String]): Unit = {

    Outwatch.renderInto[SyncIO]("#app", div("Hello Wust.")).unsafeRunSync()
  }
}
