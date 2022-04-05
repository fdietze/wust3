package webapp.atoms

import colibri.Subject
import outwatch._
import outwatch.dsl._
import webapp.util._

import cps.*                 // async, await
import cps.monads.{given, *} // support for built-in monads (i.e. Future)

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
        onClick.foreach(async[Future] {
          val key        = keySubject.now()
          val target     = targetSubject.now()
          val targetAtom = target match {
            case Left(value) => api.Atom(dbApi.newId(), Some(value), Map.empty);
            case Right(atom) => atom
          }
          await(dbApi.setAtom(targetAtom))
          await(dbApi.setAtom(atom.copy(targets = atom.targets.updated(key, targetAtom.id))))
          keySubject.onNext("")
          targetSubject.onNext(Left(""))
        }),
      ),
    )
  }

  def newValueForm(): VNode = {
    import formidable.{given, *}

    case class AtomForm(
      // id: AtomId,
      // ab: Int | String,
//    _type: AtomID,
      value: Option[String],
      targets: Map[String, Either[String, api.Atom]],
//    shape: Option[AtomId],
    )

    given Form[Either[String, api.Atom]] with {
      def default                                          = Left("")
      def form(subject: Subject[Either[String, api.Atom]]) = {

        completionInput[api.Atom](
          resultSubject = subject,
          search = query => dbApi.findAtom(query),
          show = x => x.value.getOrElse("[no value]"),
        )
      }
    }

    case class Address(street: String, city: Option[String])

    sealed trait MyType
    case class Person(name: String, age: Int, address: Seq[Address]) extends MyType
    case object Bla                                                  extends MyType

    type T = AtomForm

    val subject = Subject.behavior(summon[Form[T]].default)

    div(
      summon[Form[T]].form(subject),
      div(subject.map(_.toString)),
      // summon[Form[T]].form(subject),
    )
//    val valueSubject = Subject.behavior("")
//    div(
//      syncedTextInput(valueSubject)(cls := "input input-sm"),
//      button(
//        "new Value",
//        cls                             := "btn",
//        onClick.foreach(async[Future] {
//          val value  = valueSubject.now()
//          val atomId = dbApi.newId()
//          await(dbApi.setAtom(api.Atom(atomId, Some(value), Map.empty)))
//          Page.current.onNext(Page.Atoms.Atom(atomId))
//        }),
//      ),
//    )
  }

}
