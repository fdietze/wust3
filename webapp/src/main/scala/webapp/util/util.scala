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
      cls := "border border-black",
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
    // defaultTextFieldValue: String = "",
  )(implicit
    ec: scala.concurrent.ExecutionContext,
  ): VNode = {
    val querySubject: Subject[String] = resultSubject.transformSubject[String](
      _.contramap { x =>
        println(s"writing querysub $x"); Left(x)
      },
    )(
      _.collect { case Left(str) => str }.prepend(""),
    )
    val inputSize                     = cls := "w-40"

    div(
      cls := "relative inline-block",
      resultSubject.map {
        case Left(_)         =>
          VDomModifier(
            syncedTextInput(querySubject)(inputSize),
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
                        cls := "hover:bg-blue-200 p-2 cursor-pointer",
                      ),
                    ),
                    VDomModifier.ifTrue(results.nonEmpty)(cls := "absolute bg-blue-100 z-10"),
                  ),
                ),
            ),
          )
        case Right(selected) =>
          div(
            show(selected),
            cls := "bg-blue-100 px-2 cursor-pointer border border-blue-300",
            inputSize,
            onClick.stopPropagation(querySubject).map(Left(_)) --> resultSubject,
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
