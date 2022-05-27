package webapp

import colibri._
import scala.concurrent.Future
import cats.effect.SyncIO
import colibri.{BehaviorSubject, Observable, Subject}
import outwatch._
import outwatch.dsl._

package object util {
  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value <-- subject,
      onInput.value --> subject,
    )

  def inlineEditable(rendered: VNode, value: String, onEdit: (String) => Future[Unit])(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VDomModifier = {
    val isEditingSubject = Subject.behavior(false)
    isEditingSubject.map { isEditing =>
      if (isEditing) SyncIO {
        val newValueSubject = Subject.behavior(value)
        syncedTextInput(newValueSubject)(
          onChange.foreach {
            onEdit(newValueSubject.now())
            isEditingSubject.onNext(false)
          },
          onBlur.foreach(_ => isEditingSubject.onNext(false)),
        )
      }: VDomModifier
      else rendered(onClick.use(true) --> isEditingSubject)
    }
  }

  def completionInput[T](
    resultSubject: Subject[Either[String, T]] = Subject.behavior[Either[String, T]](Left("")),
    search: String => Future[Seq[T]] = (x: String) => Future.successful(Seq.empty),
    show: T => String = (x: T) => "",
    inputModifiers: VDomModifier = VDomModifier.empty,
  )(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VNode = {
    val querySubject: Subject[String] = resultSubject.transformSubject[String](
      _.contramap(Left(_)),
    )(
      _.collect { case Left(str) => str }.prepend(""),
    )
    val inputSize = cls := "w-40"

    div(
      cls := "relative inline-block",
      resultSubject.map {
        case Left(_) =>
          VDomModifier(
            syncedTextInput(querySubject)(inputSize, inputModifiers),
            div(
              querySubject
                .debounceMillis(300)
                .switchMap { query =>
                  if (query.isEmpty) Observable(Seq.empty)
                  else Observable.fromFuture(search(query))
                }
                .map(results =>
                  VDomModifier(
                    results.map(result =>
                      div(
                        show(result),
                        onClick.stopPropagation.use(Right(result)) --> resultSubject,
                        cls := "hover:bg-blue-200 hover:dark:bg-blue-800 p-2 cursor-pointer",
                      ),
                    ),
                    VDomModifier
                      .ifTrue(results.nonEmpty)(cls := "absolute bg-blue-100 dark:bg-blue-900 dark:text-white z-10"),
                  ),
                ),
            ),
          )
        case Right(selected) =>
          div(
            span(show(selected)),
            cls := "h-full px-2 bg-blue-100 dark:bg-blue-900 dark:text-white cursor-pointer rounded",
            cls := "flex items-center",
            inputSize,
            onClick.stopPropagation.use(Left(show(selected))) --> resultSubject,
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

}
