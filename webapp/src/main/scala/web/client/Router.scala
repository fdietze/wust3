package wust.client

import cats.effect.SyncIO
import colibri.Cancelable
import colibri.Observable
import colibri.Observer
import colibri.ProSubject
import colibri.Subject
import org.scalajs.dom.console
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.dsl.tags.extra.article

import scala.scalajs.js
import scala.scalajs.js.URIUtils.{encodeURI, decodeURI}
import scala.scalajs.js.annotation._
import scala.util.Try
import outwatch.router._

// TODO: inject router as environment
object Router {

  val locationHash = Subject
    .from[Observer, Observable, String](
      Observer.create(window.location.hash = _),
      Observable
        .create { (obs: Observer[String]) =>
          val handler: js.Function1[HashChangeEvent, Unit] = _ => {
            obs.onNext(window.location.hash)
          }
          window.addEventListener("hashchange", handler, false)
          Cancelable(() => window.removeEventListener("hashchange", handler, false))
        }
        .startWith(Seq(window.location.hash)),
    )
    .transformSubjectSource(_.distinctOnEquals) // TODO: transformSink distinctOnEquals

  val hashFragment = locationHash.imapSubject[String](s => s"#${s}")(_.tail)
  val path         = hashFragment.imapSubject[Path](_.pathString)(Path(_))

  val page = path.imapSubject[Page] {
    case Page.Index          => Root
    case Page.Topic(topicId) =>
      Root / "topic" / topicId
  } {
    case Root / "topic" / topicId => Page.Topic(topicId)
    case _                        => Page.Index
  }
}

sealed trait Page
object Page {
  case object Index                        extends Page
  case class Topic(topicId: Event.TopicId) extends Page
}
