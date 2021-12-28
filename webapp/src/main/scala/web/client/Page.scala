package web.client

import colibri.Subject
import colibri.router._

sealed trait Page
object Page {
  case object Home                     extends Page
  case class Atom(topicId: api.AtomId) extends Page

  val page: Subject[Page] = Router.path.imapSubject[Page] {
    case Page.Home         => Root
    case Page.Atom(atomId) => Root / "atom" / atomId.value
  } {
    case Root / "atom" / atomId => Page.Atom(api.AtomId(atomId))
    case _                      => Page.Home
  }
}
