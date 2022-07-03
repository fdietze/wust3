package webapp.atoms

import cats.syntax.all.*
import colibri.Observable
import colibri.reactive.*
import cps.*
import cps.monads.{given, *}
import formidable.{given, *}
import outwatch.*
import outwatch.dsl.*
import webapp.Page
import webapp.util.*

import scala.concurrent.Future
import scala.util.{Failure, Success}

object App {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi: api.Api = FirebaseApi

  def layout(focus: Option[api.AtomId] = None) = Owned[VNode] {
    val showCreateAtomForm = Var(false)
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
      showCreateAtomForm.map[VModifier] {
        case true  => newAtomForm()
        case false => focus.map(focusAtom)
      },
    )
  }

  val formModifiers = FormModifiers(
    inputModifiers = VModifier(cls := "input input-sm input-bordered"),
    checkboxModifiers = VModifier(cls := "checkbox checkbox-sm"),
    buttonModifiers = VModifier(cls := "btn btn-sm"),
  )

  def search(): VModifier = Owned {
    val targetState = Var[Either[String, api.SearchResult]](Left(""))

    div(
      VModifier.managedEval(
        targetState.observable.collect { case Right(result) => Page.Atoms.Atom(result.atom.id) }
          .unsafeForeach(Page.current.set),
      ),
      completionInput[api.SearchResult](
        resultState = targetState,
        search = query => dbApi.findAtoms(query),
        show = _.atom.toString,
        inputModifiers = VModifier(placeholder := "Search", cls := "input input-sm input-bordered"),
      ),
    ): VModifier
  }

  def focusAtom(atomId: api.AtomId): VModifier = Owned {
    div(
      dbApi
        .getAtom(atomId)
        .map(_.map { atom =>
          ResolvedAtom.from(atom).map { resolvedAtom =>
            val isEditing = Var(false)
            isEditing.map[VModifier] {
              case true =>
                import AtomForm.given
                val atomFormState = Var[AtomForm](AtomForm.from(resolvedAtom))
                div(
                  Form[AtomForm](atomFormState, formModifiers = formModifiers),
                  button(
                    "save",
                    cls := "btn btn-xs",
                    onClick.stopPropagation.doAction {
                      async[Future] {
                        await(saveAtom(atomFormState.now(), atom.id))
                        isEditing.set(false)
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
                    onClick.as(true) --> isEditing,
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
    ): VModifier
  }

  def onClickFocusAtom(atomId: api.AtomId) = VModifier(
    onClick.as(Page.Atoms.Atom(atomId)) --> Page.current,
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
      VModifier.ifTrue(atom.shape.nonEmpty)(
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

  def newAtomForm(): VModifier = Owned {
    import AtomForm.given

    val atomFormState = Form.state[AtomForm]
    atomFormState
      .map(_.shape.collect { case Right(searchResult) => searchResult })
      .foreach {
        _.map { result => addInheritedShapeFields(result.atom) }
      } // TODO: is this automatically cancelled by `Owned`?

    // TODO: is this called too often?
    def addInheritedShapeFields(shapeAtom: api.Atom): Unit = {
      atomFormState.set(
        atomFormState
          .now()
          .copy(targets =
            (atomFormState.now().targets ++
              shapeAtom.targets.map { case key -> value => AtomForm.TargetPair(key, Left("")) }.toSeq)
              .distinctBy(_.key),
          ),
      )

      shapeAtom.shape.traverse { atomId =>
        dbApi.getAtom(atomId).unsafeHeadFuture()
      }.foreach(_.flatten.foreach(addInheritedShapeFields))
    }

    div(
      Form[AtomForm](
        atomFormState,
        formModifiers = formModifiers,
      ),
      button(
        "Create",
        cls := "btn btn-sm",
        onClick.doAction(async[Future] {
          val atomId = await(createAtom(atomFormState.now()))
          Page.current.set(Page.Atoms.Atom(atomId))
        }.onComplete {
          case Success(_) => println("created atom")
          case Failure(e) => e.printStackTrace()
        }),
      ),
    ): VModifier
  }

}
