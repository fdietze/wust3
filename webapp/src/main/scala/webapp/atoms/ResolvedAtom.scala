package webapp.atoms

import colibri.Subject
import outwatch._
import outwatch.dsl._
import formidable.{given, *}
import webapp.util._
import scala.concurrent.Future
import colibri.Observable
import cats.syntax.all._
import scala.concurrent.Promise
import scala.util.Success
import scala.util.Failure
import cps.*                 // async, await
import cps.monads.{given, *} // support for built-in monads (i.e. Future)

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
        observableFirstFuture(dbApi.getAtom(atomId)).map(_.map(atom => key -> ResolvedField.Atom(atom)))
    }).flatten.toMap

    val resolvedShapes = await(atom.shape.traverse { atomId =>
      observableFirstFuture(dbApi.getAtom(atomId))
    }).flatten

    ResolvedAtom(
      atom.id,
      targets = resolvedTargets,
      shape = resolvedShapes,
      name = atom.name,
    )
  }
}
