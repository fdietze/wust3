package webapp

import cats.effect.SyncIO
import colibri.reactive.*
import colibri.*
import outwatch.*
import outwatch.dsl.*

import scala.concurrent.{ExecutionContext, Future, Promise}

package object util {
  def syncedTextInput(state: Var[String]): VNode =
    input(
      tpe := "text",
      value <-- state,
      onInput.stopPropagation.value --> state,
    )

  def inlineEditable(rendered: VNode, value: String, onEdit: String => Future[Unit])(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VModifier = Owned[VModifier] {
    val isEditingState = Var(false)
    isEditingState.map { isEditing =>
      if (isEditing) SyncIO {
        val newValueState = Var(value)
        syncedTextInput(newValueState)(
          onChange.doAction {
            onEdit(newValueState.now())
            isEditingState.set(false)
          },
          onBlur.foreach(_ => isEditingState.set(false)),
        )
      }: VModifier
      else rendered(onClick.as(true) --> isEditingState)
    }
  }

  def completionInput[T](
    resultState: Var[Either[String, T]] = Var[Either[String, T]](Left("")),
    search: String => Future[Seq[T]] = (_: String) => Future.successful(Seq.empty),
    show: T => String = (_: T) => "",
    inputModifiers: VModifier = VModifier.empty,
  )(using ExecutionContext): VModifier = Owned {
    val queryState: Var[String] = resultState.transformVar[String](
      // write every input field value into the resultState
      _.contramap(Left(_)),
    ) { // only write strings back on the Left case
      _.collect { case Left(str) =>
        str
      }("")
    }

    var lastInput = ""
    queryState.foreach(lastInput = _)

    val inputSize = cls := "w-40"

    div(
      cls := "relative inline-block",
      resultState.map {
        case Left(_) =>
          VModifier(
            syncedTextInput(queryState)(inputSize, inputModifiers),
            div(
              queryState.observable
                .debounceMillis(300)
                .switchMap { query =>
                  if (query.isEmpty) Observable(Seq.empty)
                  else Observable.fromFuture(search(query))
                }
                .map(results =>
                  VModifier(
                    results.map(result =>
                      div(
                        show(result),
                        onClick.stopPropagation.as(Right(result)) --> resultState,
                        cls := "whitespace-nowrap overflow-x-hidden text-ellipsis",
                        cls := "hover:bg-blue-200 hover:dark:bg-blue-800 p-2 cursor-pointer",
                      ),
                    ),
                    VModifier
                      .ifTrue(results.nonEmpty)(cls := "absolute bg-blue-100 dark:bg-blue-900 dark:text-white z-10"),
                  ),
                ),
            ),
          )
        case Right(selected) =>
          div(
            span(
              show(selected),
              cls := "whitespace-nowrap overflow-x-hidden text-ellipsis",
            ),
            cls := "h-full px-2 bg-blue-100 dark:bg-blue-900 dark:text-white cursor-pointer rounded",
            cls := "flex items-center",
            inputSize,
            onClick.stopPropagation.as(Left(lastInput)) --> resultState,
          )
      },
    ): VModifier
  }

  def intersperse[T](s: Seq[T], sep: T): Seq[T] = {
    val b = Seq.newBuilder[T]
    var i = 0
    for (x <- s) {
      if (i > 0) b += sep
      b            += x
      i            += 1
    }
    b.result()
  }

}
