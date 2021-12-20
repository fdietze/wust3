package web.client

import colibri.Subject
import colibri.router._

sealed trait Page
object Page {
  case object Index                        extends Page
  case class Topic(topicId: Event.TopicId) extends Page

  val page: Subject[Page] = Router.path.imapSubject[Page] {
    case Page.Index          => Root
    case Page.Topic(topicId) =>
      Root / "topic" / topicId
  } {
    case Root / "topic" / topicId => Page.Topic(topicId)
    case _                        => Page.Index
  }
}
