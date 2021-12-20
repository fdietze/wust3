package wust.client

import cats.effect.SyncIO
import colibri.Cancelable
import colibri.Observable
import colibri.Observer
import colibri.ProSubject
import colibri.Subject
import org.scalajs.dom.console
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.dsl.tags.extra.article
import cats.effect.IO

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.util.Try
import wust.client.Event.Literal
import wust.client.Page.Index

import scala.sys.process

@js.native
@JSImport("src/main/css/index.css", JSImport.Namespace)
object Css extends js.Object

@js.native
@JSImport("src/main/css/tailwind.css", JSImport.Namespace)
object TailwindCss extends js.Object

object Main {
  TailwindCss
  Css // load css

//  console.log("TESTENV")
//  console.log(js.Dynamic.global.TESTENV)

  val db = FirebaseDatabase

  def main(args: Array[String]): Unit =
    Outwatch.renderInto[SyncIO]("#app", page()).unsafeRunSync()

  def page(): VNode =
    div(
      Router.page.map {
        case Page.Index          => newTopicForm()
        case Page.Topic(topicId) => showTopic(topicId)
      },
    )

  def showTopic(topicId: Event.TopicId, compact: Boolean = false): VNode =
    if (compact) {
      span(
        db.getTopic(topicId)
          .map {
            case literal: Event.Literal =>
              div(
                literal.value,
                onClick.use(Page.Topic(topicId)) --> Router.page,
                cursor.pointer,
              )
            case binding: Event.Binding =>
              div(
                cls := "flex flex-row",
                "(",
                showTopic(binding.subject, compact),
                "-[",
                showTopic(binding.predicate, compact),
                "]",
                div(
                  "->",
                  onClick.use(Page.Topic(binding.id)) --> Router.page,
                  cursor.pointer,
                ),
                showTopic(binding.obj, compact),
                ")",
              )

          }
//          .handleErrorWith(error => IO(div(cls := "border-2 border-red-400", "Error: ", error.getMessage()))),
      )
    }
    else {
      div(
        cls := "border-2 border-blue-400 p-4",
        ul(
          db.getBindingsByObject(topicId).map { bindings =>
            bindings.map(topicId =>
              db.getTopic(topicId)
                .map(_.asInstanceOf[Event.Binding])
                .map { binding =>
                  li(
                    cls := "flex flex-row",
                    showTopic(binding.subject, compact = true),
                    div("->", onClick.use(Page.Topic(topicId)) --> Router.page, cursor.pointer),
                    b(showTopic(binding.predicate, compact = true)),
                  )
                },
            ): VModifier
          },
        ),
        db.getTopic(topicId)
          .map {
            case literal: Event.Literal =>
              div(literal.value, onClick.use(Page.Topic(topicId)) --> Router.page, cursor.pointer, cls := "text-2xl")
            case binding: Event.Binding =>
              div(
                cls := "flex flex-row",
                "(",
                showTopic(binding.subject, compact = true),
                "-[",
                showTopic(binding.predicate, compact = true),
                "]",
                div(
                  "->",
                  onClick.use(Page.Topic(binding.id)) --> Router.page,
                  cursor.pointer,
                ),
                showTopic(binding.obj, compact = true),
                ")",
              )

          },
        ul(
          db.getBindingsBySubject(topicId).map { bindings =>
            bindings.map(topicId =>
              db.getTopic(topicId)
                .map(_.asInstanceOf[Event.Binding])
                .map { binding =>
                  li(
                    cls := "flex flex-row",
                    b(showTopic(binding.predicate, compact = true)),
                    div("->", onClick.use(Page.Topic(topicId)) --> Router.page, cursor.pointer),
                    showTopic(binding.obj, compact = true),
                  )
                },
            ): VModifier
          },
          newBindingForm(subject = topicId),
        ),
      )
    }

  def newTopicForm() = {
    val fieldValue = Subject.behavior("")
    div(
      syncedTextInput(fieldValue),
      button(
        "new literal",
        onClick.doAsync[IO] {
          for {
            value   <- IO(fieldValue.now())
            topicId <- createLiteral(value)
            _       <- IO(Router.page.onNext(Page.Topic(topicId)))
          } yield ()
        },
      ),
    )
  }

  def newBindingForm(subject: Event.TopicId) = {
    val predicateValue = Subject.behavior("")
    val objectValue    = Subject.behavior("")
    div(
      cls := "flex flex-row",
      syncedTextInput(predicateValue),
      syncedTextInput(objectValue),
      button(
        "new binding",
        onClick.doAsync[IO] {
          for {
            predicateValue <- IO(predicateValue.now())
            objectValue    <- IO(objectValue.now())
            predicateId    <- createLiteral(predicateValue)
            objectId       <- createLiteral(objectValue)
            topicId        <- createBinding(subject, predicateId, objectId)
            _              <- IO(window.location.reload()),
          } yield ()
        },
      ),
    )
  }

  def createLiteral(value: String): IO[Event.TopicId] = for {
    topicId   <- IO(db.newId())
    versionId <- IO(db.newId())
    _         <- db.postEvent(
                   Event.Literal(
                     id = topicId,
                     version = versionId,
                     parent = None,
                     timestamp = db.now(),
                     value = value,
                   ),
                 )
    _         <- db.postEvent(
                   Event.Head(
                     topicId = topicId,
                     versionId = versionId,
                   ),
                 )
  } yield topicId

  def createBinding(subject: Event.TopicId, predicate: Event.TopicId, obj: Event.TopicId): IO[Event.TopicId] = for {
    topicId   <- IO(db.newId())
    versionId <- IO(db.newId())
    _         <- db.postEvent(
                   Event.Binding(
                     id = topicId,
                     version = versionId,
                     parent = None,
                     timestamp = db.now(),
                     subject = subject,
                     predicate = predicate,
                     obj = obj,
                   ),
                 )
    _         <- db.postEvent(
                   Event.Head(
                     topicId = topicId,
                     versionId = versionId,
                   ),
                 )
  } yield topicId

  def syncedTextInput(subject: Subject[String]) =
    input(
      tpe := "text",
      value.<--[Observable](subject),
      onInput.value --> subject,
      cls := "border border-black",
    )

}
