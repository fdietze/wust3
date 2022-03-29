package webapp.atoms

import colibri.Subject
import outwatch._
import outwatch.dsl._
import webapp.util._

import webapp.Page

import scala.concurrent.Future

object App {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi: api.Api = FirebaseApi

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
      onClick.use(Page.Atoms.Atom(atomId)) --> Page.current,
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
        onClick.foreach(for {
          key        <- Future.successful(keySubject.now())
          target     <- Future.successful(targetSubject.now())
          targetAtom <- Future.successful(target match {
                          case Left(value) => api.Atom(dbApi.newId(), Some(value), Map.empty);
                          case Right(atom) => atom
                        })
          _          <- dbApi.setAtom(targetAtom)
          _          <- dbApi.setAtom(atom.copy(targets = atom.targets.updated(key, targetAtom.id)))
          _          <- Future.successful(keySubject.onNext(""))
          _          <- Future.successful(targetSubject.onNext(Left("")))
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
        onClick.foreach(for {
          value  <- Future.successful(valueSubject.now())
          atomId <- Future.successful(dbApi.newId())
          _      <- dbApi.setAtom(api.Atom(atomId, Some(value), Map.empty))
          _      <- Future.successful(Page.current.onNext(Page.Atoms.Atom(atomId)))
        } yield ()),
      ),
    )
  }

}
