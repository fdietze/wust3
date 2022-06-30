package webapp

import colibri._
import scala.concurrent.Future
import cats.effect.SyncIO
import colibri.{BehaviorSubject, Observable, Subject}
import outwatch._
import outwatch.dsl._
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext

package object util {
  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value <-- subject,
      onInput.value --> subject,
    )

  def inlineEditable(rendered: VNode, value: String, onEdit: String => Future[Unit])(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VModifier = {
    val isEditingSubject = Subject.behavior(false)
    isEditingSubject.map { isEditing =>
      if (isEditing) SyncIO {
        val newValueSubject = Subject.behavior(value)
        syncedTextInput(newValueSubject)(
          onChange.doAction {
            onEdit(newValueSubject.now())
            isEditingSubject.unsafeOnNext(false)
          },
          onBlur.foreach(_ => isEditingSubject.unsafeOnNext(false)),
        )
      }: VModifier
      else rendered(onClick.as(true) --> isEditingSubject)
    }
  }

  def completionInput[T](
    resultSubject: Subject[Either[String, T]] = Subject.behavior[Either[String, T]](Left("")),
    search: String => Future[Seq[T]] = (_: String) => Future.successful(Seq.empty),
    show: T => String = (_: T) => "",
    inputModifiers: VModifier = VModifier.empty,
  )(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VNode = {
    val querySubject: Subject[String] = resultSubject.transformSubject[String](
      _.contramap(Left(_)),
    )(
      _.collect { case Left(str) => str }.prepend(""),
    )

    var lastInput = ""

    val inputSize = cls := "w-40"

    div(
      cls := "relative inline-block",
      VModifier.managedEval(querySubject.unsafeForeach(lastInput = _)),
      resultSubject.map {
        case Left(_) =>
          VModifier(
            syncedTextInput(querySubject)(inputSize, inputModifiers),
            div(
              querySubject
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
                        onClick.stopPropagation.as(Right(result)) --> resultSubject,
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
            onClick.stopPropagation.as(Left(lastInput)) --> resultSubject,
          )
      },
    )
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

  extension [T](obs: Observable[T]) {
    def first(using ExecutionContext) = observableFirstFuture(obs)
  }

  def observableFirstFuture[T](obs: Observable[T])(using ExecutionContext): Future[T] = {
    val promise    = Promise[T]
    val future     = promise.future
    val cancelable = obs.take(1).attempt.unsafeForeach(value => promise.complete(value.toTry))
    future.onComplete(_ => cancelable.unsafeCancel())
    future
  }

}
