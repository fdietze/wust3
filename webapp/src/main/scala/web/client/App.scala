package web.client

import cats.effect.{IO, SyncIO}
import colibri.Subject
import outwatch.*
import outwatch.dsl.*
import web.util.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.*

object App {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def layout = div(
    pageHeader(),
    page(),
  )

  def page(): VNode =
    div(
      Page.current.map[VDomModifier] {
        case Page.Atoms.Home         => AtomsApp.newValueForm()
        case Page.Atoms.Atom(atomId) => AtomsApp.focusAtom(atomId)
        case _                       => div()
      },
    )

  val pageHeader = {
    def link(name: String, page: Page, selected: Page => Boolean) = {
      val styling = Page.current.map(selected).map {
        case true  => cls := "btn-neutral"
        case false => cls := "btn-ghost"
      }

      a(cls := "btn", name, page.href, styling)
    }

    header(
      cls := "navbar",
      div(
        cls := "space-x-2",
        link("Atoms", Page.Atoms.Home, _.isInstanceOf[Page.AtomsPage]),
        link("HK", Page.Home, _ => false),
      ),
    )
  }
}
