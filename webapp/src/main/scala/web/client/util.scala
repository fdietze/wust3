package web
import cats.effect.{IO, SyncIO}
import colibri.{Observable, Subject}
import outwatch._
import outwatch.dsl._

package object util {
  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value.<--[Observable](subject),
      onInput.value --> subject,
      cls := "border border-black",
    )

  def inlineEditable(rendered: VNode, value: String, onEdit: (String) => IO[Unit]): VModifier = {
    val isEditingSubject = Subject.behavior(false)
    isEditingSubject.map { isEditing =>
      if (isEditing) SyncIO {
        val newValueSubject = Subject.behavior(value)
        syncedTextInput(newValueSubject)(
          onChange.foreach {
            onEdit(newValueSubject.now()).unsafeRunSync()
            isEditingSubject.onNext(false)
          },
          onBlur.foreach(_ => isEditingSubject.onNext(false)),
        )
      }: VModifier
      else rendered(onClick.use(true) --> isEditingSubject)
    }
  }

  def completionInput[T](
    resultSubject: Subject[Either[String, T]] = Subject.behavior[Either[String, T]](Left("")),
    search: String => IO[Seq[T]] = (x: String) => IO(Seq.empty),
    show: T => String = (x: T) => "",
  ): VNode = {
    val querySubject                      = Subject.behavior("")
    val searchResults: Observable[Seq[T]] =
      querySubject
        .debounceMillis(500)
        .switchMap(query => if (query.isEmpty) Observable(Seq.empty) else Observable.fromAsync(search(query)))

    div(
      cls := "relative inline-block",
      resultSubject.map {
        case Left(_)         =>
          VModifier(
            syncedTextInput(querySubject),
            div(
              searchResults.map(results =>
                VModifier(
                  results.map(result =>
                    div(
                      show(result),
                      onClick.stopPropagation.use(Right(result)) --> resultSubject,
                      cls := "cursor-pointer hover:bg-blue-200 p-2",
                    ),
                  ),
                  VModifier.ifTrue(results.nonEmpty)(cls := "absolute bg-blue-100"),
                ),
              ),
            ),
          )
        case Right(selected) =>
          div(
            show(selected),
            cls := "bg-blue-100",
            onClick.stopPropagation(querySubject).map(Left(_)) --> resultSubject,
          )
      },
    )
  }
}
