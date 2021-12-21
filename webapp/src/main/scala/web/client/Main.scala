package web.client

import cats.effect.{IO, SyncIO}
import colibri.{Observable, Subject}
import outwatch._
import outwatch.dsl._
import web.client.Event.TopicId

import scala.scalajs.js
import scala.scalajs.js.annotation._

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

  val api: Api = FirebaseDatabase

  def main(args: Array[String]): Unit =
    Outwatch.renderInto[SyncIO]("#app", page()).unsafeRunSync()

  def page(): VNode =
    div(
      Page.page.map[VModifier] {
        case Page.Home           => newLiteralForm()
        case Page.Topic(topicId) => focusTopic(topicId)
      },
    )

  def focusTopic(topicId: Event.TopicId, seen: Set[Event.TopicId] = Set.empty): VNode =
    if (seen(topicId)) div("recursion", cls := "text-gray-300")
    else
      div(
        cls                                 := "flex flex-col",
        cls                                 := "border-4 border-sky-100 p-2 mr-2",
        api.getTopic(topicId).map {
          case Some(literal: Event.Literal) => focusLiteral(literal)
          case Some(binding: Event.Binding) => focusBinding(binding)
          case None                         => VModifier.empty
        },
        topicContext(topicId, seen + topicId),
      )

  def focusLiteral(literal: Event.Literal) =
    div(
      literal.value,
      onClick.use(Page.Topic(literal.id)) --> Page.page,
      cursor.pointer,
      cls := "font-bold",
    )

  def focusBinding(binding: Event.Binding) =
    span(
      span(binding.subject),
      span(binding.predicate),
      span(binding.obj),
    )

  def topicContext(topicId: Event.TopicId, seen: Set[Event.TopicId] = Set.empty): VNode =
    div(
      cls := "flex flex-row",
      div(
        api
          .getBindingsByObject(topicId)
          .map { topics =>
            val bindings =
              topics.collect { case binding: Event.Binding =>
                div(cls := "flex flex-row", focusTopic(binding.subject, seen), focusTopic(binding.predicate, seen))
              }
            if (bindings.nonEmpty) {
              div(
                div("object for"),
                bindings,
              )
            }
            else VModifier.empty
          },
      ),
      div(
        api
          .getBindingsByObject(topicId)
          .map { topics =>
            val bindings =
              topics.collect { case binding: Event.Binding =>
                div(cls := "flex flex-row", focusTopic(binding.subject, seen), focusTopic(binding.obj, seen))
              }
            if (bindings.nonEmpty) {
              div(
                div("predicate for"),
                bindings,
              )
            }
            else VModifier.empty
          },
      ),
      div(
        api
          .getBindingsBySubject(topicId)
          .map { topics =>
            val bindings =
              topics.collect { case binding: Event.Binding =>
                div(cls := "flex flex-row", focusTopic(binding.predicate, seen), focusTopic(binding.obj, seen))
              }
            if (bindings.nonEmpty) {
              div(
                div("subject for"),
                bindings,
              )
            }
            else VModifier.empty
          },
      ),
    )

  def showTopic(topicId: Event.TopicId, compact: Boolean): VNode =
    if (compact) {
      span(
        api.getTopic(topicId).map {
          case Some(literal: Event.Literal) => compactLiteralValue(literal)
          case Some(binding: Event.Binding) => compactBindingValue(binding)
          case None                         => VModifier.empty
        },
      )
    }
    else {
      div(
        cls := "border-2 border-sky-400 p-4",
        reverseBindingsDetailed(topicId),
        api.getTopic(topicId).map {
          case Some(literal: Event.Literal) => largeLiteralValue(literal)
          case Some(binding: Event.Binding) => largeBindingWithCompactDetails(binding)
        },
        attachedBindingsDetailed(topicId),
      )
    }

  private def attachedBindingsDetailed(topicId: TopicId) =
    ul(
      api.getBindingsBySubject(topicId).map { bindings =>
        bindings.collect { case binding: Event.Binding =>
          li(
            cls := "flex flex-row",
            showTopic(binding.predicate, compact = true),
            div(" = ", onClick.use(Page.Topic(binding.id)) --> Page.page, cursor.pointer),
            showTopic(binding.obj, compact = true),
          )
        }
      },
      newBindingForm(subject = topicId),
    )

  private def largeBindingWithCompactDetails(binding: Event.Binding) =
    div(
      cls := "flex flex-row hover:bg-blue-100",
      "(",
      showTopic(binding.subject, compact = true),
      "-[",
      showTopic(binding.predicate, compact = true),
      "]",
      div(
        "->",
        onClick.use(Page.Topic(binding.id)) --> Page.page,
        cursor.pointer,
      ),
      showTopic(binding.obj, compact = true),
      ")",
    )

  private def largeLiteralValue(literal: Event.Literal) =
    div(literal.value, onClick.use(Page.Topic(literal.id)) --> Page.page, cursor.pointer, cls := "text-2xl")

  private def reverseBindingsDetailed(topicId: TopicId) =
    ul(
      cls := "text-gray-400",
      api
        .getBindingsByObject(topicId)
        .map(bindings =>
          bindings.collect { case binding: Event.Binding =>
            li(
              cls := "flex flex-row",
              showTopic(binding.subject, compact = true),
              div(".", onClick.use(Page.Topic(binding.id)) --> Page.page, cursor.pointer),
              b(showTopic(binding.predicate, compact = true)),
              div("=", onClick.use(Page.Topic(binding.obj)) --> Page.page, cursor.pointer),
            )
          },
        ): VModifier,
    )

  private def compactBindingValue(binding: Event.Binding) =
    div(
      cls := "flex flex-row",
      "(",
      showTopic(binding.subject, compact = true),
      "-[",
      showTopic(binding.predicate, compact = true),
      "]",
      div(
        "->#",
        onClick.use(Page.Topic(binding.id)).foreach(println),
        cursor.pointer,
      ),
      showTopic(binding.obj, compact = true),
      ")",
    )

  private def compactLiteralValue(literal: Event.Literal) =
    div(
      literal.value,
      onClick.use(Page.Topic(literal.id)) --> Page.page,
      cursor.pointer,
    )

  def newLiteralForm(): VNode = {
    val fieldValue = Subject.behavior("")
    div(
      syncedTextInput(fieldValue),
      button(
        "new literal",
        onClick.doAsync[IO] {
          for {
            value   <- IO(fieldValue.now())
            _       <- IO(println("ui"))
            topicId <- createLiteral(value)
            _       <- IO(Page.page.onNext(Page.Topic(topicId)))
          } yield ()
        },
      ),
    )
  }

  def newBindingForm(subject: Event.TopicId): VNode = {
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
          } yield ()
        },
      ),
    )
  }

  def createLiteral(value: String): IO[Event.TopicId] = for {
    topicId   <- IO(api.newId())
    versionId <- IO(api.newId())
    _         <- api.writeTopic(
                   Event.Literal(
                     id = topicId,
                     value = value,
                   ),
                 )
  } yield topicId

  def createBinding(subject: Event.TopicId, predicate: Event.TopicId, obj: Event.TopicId): IO[Event.TopicId] = for {
    topicId   <- IO(api.newId())
    versionId <- IO(api.newId())
    _         <- api.writeTopic(
                   Event.Binding(
                     id = topicId,
                     subject = subject,
                     predicate = predicate,
                     obj = obj,
                   ),
                 )
  } yield topicId

  def syncedTextInput(subject: Subject[String]): VNode =
    input(
      tpe := "text",
      value.<--[Observable](subject),
      onInput.value --> subject,
      cls := "border border-black",
    )

}
