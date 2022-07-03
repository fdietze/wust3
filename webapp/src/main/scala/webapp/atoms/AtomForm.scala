package webapp.atoms

import cats.syntax.all.*
import colibri.*
import colibri.reactive.*
import cps.*
import cps.monads.{given, *}
import formidable.{given, *}
import outwatch.*
import outwatch.dsl.*
import webapp.util.*

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

case class AtomForm(
  shape: Seq[Either[String, api.SearchResult]],
  label: Option[String],
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
      name = label,
    )
  }
}
object AtomForm {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi: api.Api = App.dbApi

  case class TargetPair(key: String, value: Either[String, api.SearchResult])

  def from(atom: api.Atom): Future[AtomForm] = {
    ResolvedAtom.from(atom).map(from)
  }

  def from(atom: ResolvedAtom): AtomForm = {
    val formTargets = atom.targets.map {
      case key -> ResolvedField.Value(value) => TargetPair(key, Left(value))
      case key -> ResolvedField.Atom(atom)   => TargetPair(key, Right(api.SearchResult(atom, None)))
    }.toVector

    val formShapes = atom.shape.map { atom => Right(api.SearchResult(atom, None)) }

    AtomForm(
      label = atom.name,
      shape = formShapes,
      targets = formTargets,
    )
  }

  given Form[Either[String, api.SearchResult]] with {
    def default: Either[String, api.SearchResult] = Left("")
    def apply(
      state: Var[Either[String, api.SearchResult]],
      formModifiers: FormModifiers,
    )(using Owner): VModifier = {
      completionInput[api.SearchResult](
        resultState = state,
        search = query => dbApi.findAtoms(query),
        show = _.atom.toString,
        inputModifiers = formModifiers.inputModifiers,
      )
    }
  }

  given Form[TargetPair] with {
    def default: TargetPair = TargetPair("", Left(""))
    def apply(
      state: Var[TargetPair],
      formModifiers: FormModifiers,
    )(using Owner): VModifier = {
      val keyState: Var[String] = state.lens(_.key)((tp, newKey) => tp.copy(key = newKey))
      val valueState: Var[Either[String, api.SearchResult]] =
        state.lens(_.value)((tp, newValue) => tp.copy(value = newValue))
      div(
        cls := "flex",
        syncedTextInput(keyState)(formModifiers.inputModifiers),
        Form[Either[String, api.SearchResult]](valueState),
      )
    }
  }
}
