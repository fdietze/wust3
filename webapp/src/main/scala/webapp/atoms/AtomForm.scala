package webapp.atoms

import colibri._
import outwatch._
import outwatch.dsl._
import formidable.{given, *}
import webapp.util._
import scala.concurrent.Future
import colibri.reactive._
import cats.syntax.all._
import scala.concurrent.Promise
import scala.util.Success
import scala.util.Failure
import cps.*                 // async, await
import cps.monads.{given, *} // support for built-in monads (i.e. Future)

case class AtomForm(
  name: Option[String],
  shape: Seq[Either[String, api.SearchResult]],
  targets: Seq[AtomForm.TargetPair],
) {
  def toAtom(atomId: api.AtomId): api.Atom = {
    val targetMap = targets.map {
      case AtomForm.TargetPair(key, Left(str))           => key -> api.Field.Value(str)
      case AtomForm.TargetPair(key, Right(searchResult)) => key -> api.Field.AtomRef(searchResult.atom.id)
    }.toMap

    api.Atom(
      id = atomId,
      targets = targetMap,
      shape = shape.flatMap(_.map(_.atom.id).toOption).toVector,
      name = name,
    )
  }
}
object AtomForm {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi = App.dbApi

  case class TargetPair(key: String, value: Either[String, api.SearchResult])

  def from(atom: api.Atom): Future[AtomForm] = {
    ResolvedAtom.from(atom).map(from)
  }

  def from(atom: ResolvedAtom): AtomForm = {
    val formTargets = atom.targets.map {
      case key -> ResolvedField.Value(value) => TargetPair(key, Left(value))
      case key -> ResolvedField.Atom(atom)   => TargetPair(key, Right(api.SearchResult(atom, None)))
    }.toVector

    val formShapes = atom.shape.map { atom => Right(api.SearchResult(atom, None)) }.toVector

    AtomForm(
      name = atom.name,
      shape = formShapes,
      targets = formTargets,
    )
  }

  given Form[Either[String, api.SearchResult]] with {
    def default = Left("")
    def apply(
      state: Var[Either[String, api.SearchResult]],
      formModifiers: FormModifiers,
    )(using Owner): VNode = {
      completionInput[api.SearchResult](
        resultSubject = Subject.from(state.observer, state.observable),
        search = query => dbApi.findAtoms(query),
        show = _.atom.toString,
        inputModifiers = formModifiers.inputModifiers,
      )
    }
  }

  // given Form[TargetPair] with {
  //   def default = TargetPair("", Left(""))
  //   def apply(
  //     subject: Subject[TargetPair],
  //     formModifiers: FormModifiers,
  //   ): VNode = {
  //     val behaviorSubject = Subject.behavior(default)
  //     val keySubject      = behaviorSubject.lens(_.key)((tp, newKey) => tp.copy(key = newKey))
  //     div(
  //       cls := "flex",
  //       subject.map(_.toString),
  //       managedFunction(() => subject.distinctOnEquals.unsafeSubscribe(behaviorSubject)),
  //       managedFunction(() => behaviorSubject.drop(1).distinctOnEquals.unsafeSubscribe(subject)),
  //       syncedTextInput(keySubject)(formModifiers.inputModifiers),
  //     )
  //   }
  // }
}
