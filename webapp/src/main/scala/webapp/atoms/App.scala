package webapp.atoms

import colibri.Subject
import outwatch._
import outwatch.dsl._
import webapp.util._
import cats.syntax.all._

import cps.*                 // async, await
import cps.monads.{given, *} // support for built-in monads (i.e. Future)

import webapp.Page

import scala.concurrent.Future
import formidable.{given, *}
import scala.util.Success
import scala.util.Failure

object App {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi: api.Api = FirebaseApi

  def layout(focus: Option[api.AtomId] = None): VNode = {
    val showCreateAtomForm = Subject.behavior(false)
    div(
      div(
        cls := "flex items-center",
        button(
          "Create Atom",
          cls := "btn btn-xs btn-ghost",
          onClick(showCreateAtomForm).map(!_) --> showCreateAtomForm,
        ),
        search(),
      ),
      hr(),
      showCreateAtomForm.map[VDomModifier] {
        case true  => newAtomForm()
        case false => focus.map(focusAtom)
      },
    )
  }

  def search(): VNode = {
    val targetSubject = Subject.behavior[Either[String, api.SearchResult]](Left(""))
    div(
      managedFunction(() =>
        targetSubject.collect { case Right(result) => Page.Atoms.Atom(result.atom.id) }.subscribe(Page.current),
      ),
      completionInput[api.SearchResult](
        resultSubject = targetSubject,
        search = query => dbApi.findAtoms(query),
        show = _.toString,
        inputModifiers = VDomModifier(placeholder := "Search", cls := "input input-sm input-bordered"),
      ),
    )
  }

  def focusAtom(atomId: api.AtomId): VNode = {
    def removeButton(atom: api.Atom, key: String) = {
      button(
        "remove",
        cls := "btn btn-xs btn-ghost",
        onClick.foreach {
          dbApi.setAtom(atom.copy(targets = atom.targets - key))
        },
      )
    }

    div(
      dbApi
        .getAtom(atomId)
        .map(_.map { atom =>
          div(
            atom.shape.map(atomId => showAtomValue(atomId)(cls := "badge")),
            atom.targets.map { case (key, target) =>
              div(
                cls := "ml-4",
                key,
                " = ",
                target match {
                  case api.Field.Value(value)    => value
                  case api.Field.AtomRef(atomId) => showAtomValue(atomId)
                },
                removeButton(atom, key),
              )
            }.toSeq,
            newTargetForm(atom).apply(cls := "ml-4"),
          )
        }),
      "References:",
      dbApi.getReferences(atomId).map {
        _.map { reference =>
          div(reference.key, ": ", showAtomValue(reference.atomId))
        }
      },
    )
  }

  def showAtomValue(atomId: api.AtomId): VNode =
    span(
      dbApi.getAtom(atomId).map(_.map(_.toString)),
      onClick.use(Page.Atoms.Atom(atomId)) --> Page.current,
      cursor.pointer,
    )

  def newTargetForm(atom: api.Atom): VNode = {
    // TODO: share  targetform for create and edit atom forms
    val keySubject    = Subject.behavior("")
    val targetSubject = Subject.behavior[Either[String, api.SearchResult]](Left(""))
    div(
      cls := "flex h-8",
      syncedTextInput(keySubject)(cls := "input input-sm input-bordered", placeholder := "key"),
      completionInput[api.SearchResult](
        resultSubject = targetSubject,
        search = query => dbApi.findAtoms(query),
        show = atom => atom.toString,
        inputModifiers = VDomModifier(cls := "input input-sm input-bordered", placeholder := "value"),
      ).append(cls := "h-full ml-1"),
      button(
        cls := "btn btn-sm btn-ghost",
        "add",
        onClick.foreach(async[Future] {
          val key    = keySubject.now()
          val target = targetSubject.now()
          val targetValue: api.Field = target match {
            case Left(value)   => api.Field.Value(value)
            case Right(result) => api.Field.AtomRef(result.atom.id)
          }
          await(dbApi.setAtom(atom.copy(targets = atom.targets + (key -> targetValue))))
          keySubject.onNext("")
          targetSubject.onNext(Left(""))
        }.onComplete {
          case Success(_) => println("created target")
          case Failure(e) => e.printStackTrace()
        }),
      ),
    )
  }

  def newAtomForm(): VNode = {
    case class TargetPair(key: String, value: Either[String, api.Atom])
    case class AtomForm(
      name: Option[String],
      shape: Seq[Either[String, api.Atom]],
      targets: Seq[TargetPair],
    )

    given Form[Either[String, api.SearchResult]] with {
      def default = Left("")
      def apply(
        subject: Subject[Either[String, api.SearchResult]],
        formModifiers: FormModifiers,
      ): VNode = {
        completionInput[api.SearchResult](
          resultSubject = subject,
          search = query => dbApi.findAtoms(query),
          show = atom => atom.toString,
          inputModifiers = formModifiers.inputModifiers,
        )
      }
    }

    val subject = Form.subject[AtomForm]
    subject
      .map(_.shape.collect { case Right(shapeAtom) => shapeAtom })
      .distinctOnEquals
      .delayMillis(100)                     // TODO: why is this needed?
      .foreach { _.map { addShapeFields } } // TODO: handle cancelable

    def addShapeFields(shapeAtom: api.Atom): colibri.Cancelable = {
      subject.onNext(
        subject
          .now()
          .copy(targets =
            (subject.now().targets ++
              shapeAtom.targets.map { case (key -> value) => TargetPair(key, Left("")) }.toSeq).distinctBy(_.key),
          ),
      )

      shapeAtom.shape.traverse { atomId =>
        dbApi.getAtom(atomId)
      }.foreach(_.flatten.foreach(addShapeFields))
    }

    def createAtom(formData: AtomForm): Future[api.AtomId] = async[Future] {
      val atomId = dbApi.newId()

      // val targets: Map[String, api.AtomId] = await(Future.sequence(formData.targets.collect {
      //   case TargetPair(key, Right(atom)) =>
      //     Future.successful(key -> atom.id)
      // })).toMap
      val targets = formData.targets.map {
        case TargetPair(key, Left(str))   => key -> api.Field.Value(str)
        case TargetPair(key, Right(atom)) => key -> api.Field.AtomRef(atom.id)
      }.toMap

      await(
        dbApi.setAtom(
          api.Atom(
            atomId,
            targets = targets,
            shape = formData.shape.flatMap(_.map(_.id).toOption).toVector,
            formData.name,
          ),
        ),
      )

      atomId
    }

    div(
      Form[AtomForm](
        subject,
        formModifiers = FormModifiers(
          inputModifiers = VDomModifier(cls := "input input-sm input-bordered"),
          checkboxModifiers = VDomModifier(cls := "checkbox checkbox-sm"),
          buttonModifiers = VDomModifier(cls := "btn btn-sm"),
        ),
      ),
      button(
        "Create",
        cls := "btn btn-sm",
        onClick.foreach(async[Future] {
          val atomId = await(createAtom(subject.now()))
          Page.current.onNext(Page.Atoms.Atom(atomId))
        }.onComplete {
          case Success(_) => println("created atom")
          case Failure(e) => e.printStackTrace()
        }),
      ),
    )
  }

}
