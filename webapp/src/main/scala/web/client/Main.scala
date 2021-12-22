package web.client

import cats.effect.{IO, SyncIO}
import colibri.{Observable, Subject}
import outwatch._
import outwatch.dsl.{value, _}

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
    Outwatch.renderInto[SyncIO]("#app", page()).unsafeRunSync()

  def page(): VNode =
    div(
      Page.page.map[VModifier] {
        case Page.Home         => newValueForm()
        case Page.Atom(atomId) => focusAtom(atomId)
      },
      div("search test:"),
      dbApi.findAtom("a").map(_.map(x => div(x.value))),
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
    val keySubject   = Subject.behavior("")
    val valueSubject = Subject.behavior("")
    div(
      syncedTextInput(keySubject),
      syncedTextInput(valueSubject),
      button(
        "new Target",
        onClick.doAsync(for {
          key         <- IO(keySubject.now())
          value       <- IO(valueSubject.now())
          valueAtomId <- IO(dbApi.newId())
          _           <- dbApi.setAtom(api.Atom(valueAtomId, Some(value), Map.empty))
          _           <- dbApi.setAtom(atom.copy(targets = atom.targets.updated(key, valueAtomId)))
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

  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value.<--[Observable](subject),
      onInput.value --> subject,
      cls := "border border-black",
    )

  def inlineEditable(rendered: VNode, value: String, onEdit: (String) => IO[Unit]): VModifier = {
    val isEditingSubject = Subject.behavior(false)
    isEditingSubject.map { isEditing =>
      if (isEditing) SyncIO {
        val newValueSubject = Subject.behavior(value)
        syncedTextInput(newValueSubject)(
          onChange.foreach {
            onEdit(newValueSubject.now()).unsafeRunSync()
            isEditingSubject.onNext(false)
          },
          onBlur.foreach(_ => isEditingSubject.onNext(false)),
        )
      }: VModifier
      else rendered(onClick.use(true) --> isEditingSubject)
    }
  }
}
