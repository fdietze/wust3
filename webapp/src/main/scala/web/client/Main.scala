package web.client

import cats.effect.{IO, SyncIO}
import colibri.Subject
import outwatch._
import outwatch.dsl._
import web.util._

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

  val dbApi: api.Api = FirebaseApi

  def main(args: Array[String]): Unit =
    OutWatch.renderInto[SyncIO]("#app", page()).unsafeRunSync()

  def page(): VNode =
    div(
      Page.page.map[VDomModifier] {
        case Page.Home         => newValueForm()
        case Page.Atom(atomId) => focusAtom(atomId)
      },
    )

  def focusAtom(atomId: api.AtomId): VNode =
    div(
      dbApi
        .getAtom(atomId)
        .map(_.map { atom =>
          div(
            div(
              inlineEditable(
                span(atom.value),
                atom.value.getOrElse(""),
                newValue => dbApi.setAtom(atom.copy(value = Some(newValue))),
              ),
            ),
            atom.targets.map { case (key, valueAtomId) =>
              div(cls := "ml-4", key, " = ", showAtomValue(valueAtomId))
            }.toSeq,
            newTargetForm(atom).apply(cls := "ml-4"),
          )
        }),
    )

  def showAtomValue(atomId: api.AtomId): VNode =
    span(
      dbApi.getAtom(atomId).map(_.map(_.value)),
      onClick.use(Page.Atom(atomId)) --> Page.page,
      cursor.pointer,
    )

  def newTargetForm(atom: api.Atom): VNode = {
    val keySubject    = Subject.behavior("")
    val targetSubject = Subject.behavior[Either[String, api.Atom]](Left(""))
    div(
      syncedTextInput(keySubject),
      completionInput[api.Atom](
        resultSubject = targetSubject,
        search = query => dbApi.findAtom(query),
        show = x => x.value.getOrElse("[no value]"),
      ),
      button(
        "new Target",
        onClick.doAsync(for {
          key        <- IO(keySubject.now())
          target     <- IO(targetSubject.now())
          targetAtom <- IO(target match {
                          case Left(value) => api.Atom(dbApi.newId(), Some(value), Map.empty); case Right(atom) => atom
                        })
          _          <- dbApi.setAtom(targetAtom)
          _          <- dbApi.setAtom(atom.copy(targets = atom.targets.updated(key, targetAtom.id)))
          _          <- IO(keySubject.onNext(""))
          _          <- IO(targetSubject.onNext(Left("")))
        } yield ()),
      ),
    )
  }

  def newValueForm(): VNode = {
    val valueSubject = Subject.behavior("")
    div(
      syncedTextInput(valueSubject),
      button(
        "new Value",
        onClick.doAsync(for {
          value  <- IO(valueSubject.now())
          atomId <- IO(dbApi.newId())
          _      <- dbApi.setAtom(api.Atom(atomId, Some(value), Map.empty))
          _      <- IO(Page.page.onNext(Page.Atom(atomId)))
        } yield ()),
      ),
    )
  }

}
