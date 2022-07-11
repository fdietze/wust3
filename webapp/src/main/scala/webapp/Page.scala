package webapp

import colibri.router.*
import colibri.reactive._
import colibri.reactive.Owner.unsafeImplicits.unsafeGlobalOwner

sealed trait Page {
  final def href = outwatch.dsl.href := s"#${Page.toPath(this).pathString}"
}

object Page {
  case object Home extends Page

  sealed trait AtomsPage extends Page
  object Atoms {
    import atoms.*
    case object Create                   extends AtomsPage
    case object Home                     extends AtomsPage
    case class Atom(topicId: api.AtomId) extends AtomsPage
    object Paths {
      val Home   = Root / "atoms"
      val Atom   = Home / "atom"
      val Create = Home / "create"
    }
  }

  sealed trait HkPage extends Page
  object Hk {
    import hk.*
    case object Home                       extends HkPage
    case class Topic(topicId: api.TopicId) extends HkPage
    object Paths {
      val Home  = Root / "hk"
      val Topic = Home / "topic"
    }
  }

  val fromPath: Path => Page = {
    case Atoms.Paths.Atom / atomId => Atoms.Atom(atoms.api.AtomId(atomId))
    case Atoms.Paths.Create        => Atoms.Create
    case Atoms.Paths.Home          => Atoms.Home
    case Atoms.Paths.Home / _      => Atoms.Home

    case Hk.Paths.Topic / topicId => Hk.Topic(topicId)
    case Hk.Paths.Home            => Hk.Home
    case Hk.Paths.Home / _        => Hk.Home

    case _ => Page.Home
  }

  val toPath: Page => Path = {
    case Home => Root

    case Atoms.Create       => Atoms.Paths.Create
    case Atoms.Home         => Atoms.Paths.Home
    case Atoms.Atom(atomId) => Atoms.Paths.Atom / atomId.value

    case Hk.Home           => Hk.Paths.Home
    case Hk.Topic(topicId) => Hk.Paths.Topic / topicId
  }

  val current: Var[Page] =
    // Router.pathVar
    Var
      .combine[Path](Rx.observableSync(Router.path), RxWriter.observer(Router.path))
      .imap[Page](Page.toPath)(Page.fromPath)
}
