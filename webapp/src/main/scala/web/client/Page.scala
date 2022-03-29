package web.client

import colibri.Subject
import colibri.router._

sealed trait Page {
  final def href = outwatch.dsl.href := s"#${Page.toPath(this).pathString}"
}

object Page {
  case object Home extends Page

  sealed trait AtomsPage extends Page
  object Atoms {
    case object Home                     extends AtomsPage
    case class Atom(topicId: api.AtomId) extends AtomsPage
    object Paths {
      val Home = Root / "atoms"
      val Atom = Home / "atom"
    }
  }

  val fromPath: Path => Page = {
    case Atoms.Paths.Atom / atomId => Atoms.Atom(api.AtomId(atomId))
    case Atoms.Paths.Home / _      => Atoms.Home
    case Atoms.Paths.Home          => Atoms.Home
    case _                         => Page.Home
  }

  val toPath: Page => Path = {
    case Home               => Root
    case Atoms.Home         => Atoms.Paths.Home
    case Atoms.Atom(atomId) => Atoms.Paths.Atom / atomId.value
  }

  val current: Subject[Page] = Router.path
    .imapSubject[Page](Page.toPath)(Page.fromPath)
}
