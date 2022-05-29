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

  val formModifiers = FormModifiers(
    inputModifiers = VDomModifier(cls := "input input-sm input-bordered"),
    checkboxModifiers = VDomModifier(cls := "checkbox checkbox-sm"),
    buttonModifiers = VDomModifier(cls := "btn btn-sm"),
  )

  def search(): VNode = {
    val targetSubject = Subject.behavior[Either[String, api.SearchResult]](Left(""))
    div(
      managedFunction(() =>
        targetSubject.collect { case Right(result) => Page.Atoms.Atom(result.atom.id) }.subscribe(Page.current),
      ),
      completionInput[api.SearchResult](
        resultSubject = targetSubject,
        search = query => dbApi.findAtoms(query),
        show = _.atom.toString,
        inputModifiers = VDomModifier(placeholder := "Search", cls := "input input-sm input-bordered"),
      ),
    )
  }

  def focusAtom(atomId: api.AtomId): VNode = {
    div(
      dbApi
        .getAtom(atomId)
        .map(_.map { atom =>
          ResolvedAtom.from(atom).map { resolvedAtom =>
            val isEditing = Subject.behavior(false)
            isEditing.map[VDomModifier] {
              case true =>
                import AtomForm.given
                val subject = Subject.behavior[AtomForm](AtomForm.from(resolvedAtom))
                div(
                  Form[AtomForm](subject, formModifiers = formModifiers),
                  button(
                    "save",
                    cls := "btn btn-xs",
                    onClick(subject).foreach { formValue =>
                      async[Future] {
                        await(saveAtom(formValue, atom.id))
                        isEditing.onNext(false)
                      }
                    },
                  ),
                )
              case false =>
                div(
                  showResolvedAtom(resolvedAtom),
                  button(
                    "edit",
                    cls := "btn btn-xs",
                    onClick.use(true) --> isEditing,
                  ),
                )
            }
          }
        }),
      hr(),
      "Usages:",
      dbApi.getUsages(atomId).map {
        _.map { reference =>
          div(reference.key, ": ", showAtomValue(reference.atom))
        }
      },
    )
  }

  def onClickFocusAtom(atomId: api.AtomId) = VDomModifier(
    onClick.use(Page.Atoms.Atom(atomId)) --> Page.current,
    cursor.pointer,
  )

  def showAtomValue(atomId: api.AtomId): VNode =
    span(
      dbApi.getAtom(atomId).map(_.map(_.toString)),
      onClickFocusAtom(atomId),
    )

  def showAtomValue(atom: api.Atom): VNode =
    span(
      atom.toString,
      onClickFocusAtom(atom.id),
    )

  def showResolvedAtom(atom: ResolvedAtom): VNode =
    div(
      div("id: ", atom.id.value, cls := "text-xs text-gray-500"),
      div(atom.name, cls             := "text-xl"),
      VDomModifier.ifTrue(atom.shape.nonEmpty)(
        div(
          "shape: ",
          atom.shape.map(atom => div(atom.toString, cls := "badge", onClickFocusAtom(atom.id))),
        ),
      ),
      atom.targets.map { case key -> field =>
        div(
          cls := "ml-8 flex items-center",
          div(key, ":", cls := "mr-4"),
          field match {
            case ResolvedField.Value(value) => div(value, cls := "font-mono")
            case ResolvedField.Atom(atom)   => div(showAtomValue(atom), cls := "underline")
          },
        )
      }.toVector,
    )

  def createAtom(formData: AtomForm): Future[api.AtomId] = async[Future] {
    val atomId = dbApi.newId()
    await(dbApi.setAtom(formData.toAtom(atomId)))
    atomId
  }

  def saveAtom(formData: AtomForm, atomId: api.AtomId): Future[Unit] = {
    dbApi.setAtom(formData.toAtom(atomId))
  }

  def newAtomForm(): VNode = {
    import AtomForm.given

    val subject = Form.subject[AtomForm]
    subject
      .map(_.shape.collect { case Right(searchResult) => searchResult })
      .distinctOnEquals
      .delayMillis(100)                                                     // TODO: why is this needed?
      .foreach { _.map { result => addInheritedShapeFields(result.atom) } } // TODO: handle cancelable

    // TODO: is this called too often?
    def addInheritedShapeFields(shapeAtom: api.Atom): colibri.Cancelable = {
      subject.onNext(
        subject
          .now()
          .copy(targets =
            (subject.now().targets ++
              shapeAtom.targets.map { case (key -> value) => AtomForm.TargetPair(key, Left("")) }.toSeq)
              .distinctBy(_.key),
          ),
      )

      shapeAtom.shape.traverse { atomId =>
        dbApi.getAtom(atomId)
      }.foreach(_.flatten.foreach(addInheritedShapeFields))
    }

    div(
      Form[AtomForm](
        subject,
        formModifiers = formModifiers,
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
