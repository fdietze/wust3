package webapp.atoms

import cats.syntax.all.*
import colibri.{Observable, Subject}
import cps.*
import cps.monads.{given, *}
import formidable.{given, *}
import outwatch.*
import outwatch.dsl.*
import webapp.util.*

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success} // support for built-in monads (i.e. Future)

sealed trait ResolvedField

object ResolvedField {
  case class Atom(atom: api.Atom) extends ResolvedField
  case class Value(value: String) extends ResolvedField
}

case class ResolvedAtom(
  id: api.AtomId,
  targets: Map[String, ResolvedField],
  shape: Vector[api.Atom],
  name: Option[String],
)

object ResolvedAtom {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val dbApi                                          = App.dbApi

  def from(atom: api.Atom): Future[ResolvedAtom] = async[Future] {
    val resolvedTargets = await(atom.targets.toSeq.traverse {
      case key -> api.Field.Value(value) => Future.successful(Some(key -> ResolvedField.Value(value)))
      case key -> api.Field.AtomRef(atomId) =>
        dbApi.getAtom(atomId).unsafeHeadFuture().map(_.map(atom => key -> ResolvedField.Atom(atom)))
    }).flatten.toMap

    val resolvedShapes = await(atom.shape.traverse { atomId =>
      dbApi.getAtom(atomId).unsafeHeadFuture()
    }).flatten

    ResolvedAtom(
      atom.id,
      targets = resolvedTargets,
      shape = resolvedShapes,
      name = atom.name,
    )
  }
}
