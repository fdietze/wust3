package web.client

import cats.effect.{IO, SyncIO}
import colibri.{Observable, Subject}
import outwatch._
import outwatch.dsl._
import web.client.Event.TopicId
import web.client.api.Api

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("src/main/css/index.css", JSImport.Namespace)
object Css extends js.Object

@js.native
@JSImport("src/main/css/tailwind.css", JSImport.Namespace)
object TailwindCss extends js.Object

object Main {
  TailwindCss
  Css // load css

  val api: Api = FirebaseApi

  def main(args: Array[String]): Unit =
    Outwatch.renderInto[SyncIO]("#app", page()).unsafeRunSync()

  def page(): VNode =
    div(
      Page.page.map[VModifier] {
        case Page.Home         => newAtomForm()
        case Page.Atom(atomId) => focusAtom(atomId)
      },
    )

  def focusAtom(atomId: api.AtomID) =
    div()

  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value.<--[Observable](subject),
      onInput.value --> subject,
      cls := "border border-black",
    )

}
