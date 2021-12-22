package web
import cats.effect.{IO, SyncIO}
import colibri.{Observable, Subject}
import outwatch.{VModifier, VNode}
import outwatch.dsl.{cls, input, onBlur, onChange, onClick, onInput, tpe, value}

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

}
