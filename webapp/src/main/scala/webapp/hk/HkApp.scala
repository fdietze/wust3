package webapp.hk

import cats.effect.{IO, SyncIO}
import colibri.Subject
import org.scalajs.dom.console
import outwatch.*
import outwatch.dsl.*
import webapp.util.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.*

object App {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val dbApi: api.Api = FirebaseApi

  def newValueForm(): VNode =
    div(
      button(
        "New Value",
        onClick.foreach { e =>
          console.log("clicked")
          dbApi.addFrame(
            api.Frame(
              type_id = api.CastingKey.N(api.Name("rdf:Object")),
              targets = Map(
                api.CastingKey.N(api.Name("eg:test"))  -> api.CastingValue.N(api.Name("Some name")),
                api.CastingKey.N(api.Name("eg:test2")) ->
                  api.CastingValue.L(api.Literal.StringLiteral("Some literal", "en")),
                api.CastingKey.N(api.Name("eg:test3")) -> api.CastingValue.T("topic_id"),
              ),
            ),
          )
        },
      ),
      button(
        "Search",
        onClick.foreach { e =>
          dbApi.findNames("eg:").foreach(x => console.log(x.toArray))
          dbApi.findFrames("eg:test2").foreach(x => console.log(x.toString))
        },
      ),
      div(
        dbApi.findFrames("eg:test2").map(x => ul(x.map(f => li(showFrame(f))))),
      ),
    )

  def newFrameForm(): VNode = {
    val frameSubject = Subject.behavior[Either[String, api.Topic]](Left(""))
    val castings     =
      Subject.behavior[Seq[(Either[String, api.Topic], Either[String, api.Topic])]](
        Seq((Left(""), Left(""))),
      )
    div(
      completionInput[api.Topic](
        resultSubject = frameSubject,
        search = query =>
          dbApi
            .findTopics(query),
        show = x => x.toString,
      ),
      div(
        "castings:",
        castings.map(_.toString),
      ),
      div(
        castings.sequence
          .map(
            _.zipWithIndex.map { case (keyValueSubject, i) =>
              val keySubject   = keyValueSubject.lens(_._1)((oldKv, newKey) => oldKv.copy(_1 = newKey))
              val valueSubject = keyValueSubject.lens(_._2)((oldKv, newValue) => oldKv.copy(_2 = newValue))
              div(
                cls := "flex",
                completionInput[api.Topic](
                  resultSubject = keySubject,
                  search = query =>
                    dbApi
                      .findTopics(query),
                  show = x => x.toString,
                ),
                completionInput[api.Topic](
                  resultSubject = valueSubject,
                  search = query =>
                    dbApi
                      .findTopics(query),
                  show = x => x.toString,
                ),
                button(
                  "remove",
                  onClick.stopPropagation.foreach { _ =>
                    castings.onNext(castings.now().patch(i, Nil, 1))
                  },
                ),
              ),
            },
          ),
        button(
          "add",
          onClick.foreach { _ =>
            castings.onNext(castings.now() :+ (Left(""), Left("")))
          },
        ),
      ),
      button(
        "new Target",
        onClick.foreach { _ =>
          val frameType = frameSubject.now() match {
            case Left(str) if str.isEmpty => None
            case Left(str)                => Some(api.CastingKey.N(api.Name(str)))
            case Right(topic)             => api.CastingKey.fromTopic(topic)
          }
          val keys      = castings
            .now()
            .map(_._1)
            .map(_ match {
              case Left(str) if str.isEmpty => None
              case Left(str)                => Some(api.CastingKey.N(api.Name(str)))
              case Right(topic)             => api.CastingKey.fromTopic(topic)
            })
          val values    = castings
            .now()
            .map(_._2)
            .map(_ match {
              case Left(str) if str.isEmpty => None
              case Left(str)                => Some(api.CastingValue.N(api.Name(str)))
              case Right(topic)             => api.CastingValue.fromTopic(topic)
            })

          if (frameType.isDefined && keys.forall(_.isDefined) && values.forall(_.isDefined))
            dbApi.addFrame(
              api.Frame(
                type_id = frameType.get,
                targets = keys
                  .zip(values)
                  .collect { case (Some(k), Some(v)) =>
                    (k, v)
                  }
                  .toMap,
              ),
            )
          else console.log("invalid frame")
        },
      ),
    )
  }

  def showFrame(frame: api.Frame): VNode =
    span(
      "[a ",
      span(frame.type_id.toString),
      "; ",
      intersperse[VDomModifier](
        frame.targets.toList
          .sortBy(_._1.id)
          .map { case (k, v) =>
            span(
              k.toString, // assume no frame here
              ": ",
              v match {
                case api.CastingValue.N(n) =>
                case api.CastingValue.L(n) =>
                  n.toString
                case api.CastingValue.F(t) =>
                  span(
                    "<...>",
                    idAttr := t,
                    color  := "blue",
                    onClick.foreach { e =>
                      dbApi.getTopic(t).map(_.toString).foreach(console.log(_))
                    // dbApi.getFrame(t).foreach(x => console.log(x.toString))
                    },
                  )
                case api.CastingValue.T(t) =>
                  span(
                    "<?...>",
                    idAttr := t,
                    color  := "blue",
                    onClick.foreach { e =>
                      dbApi.getTopic(t).map(_.toString).foreach(console.log(_))
                    // dbApi.getFrame(t).foreach(x => console.log(x.toString))
                    },
                  )
              },
            )
          },
        "; ",
      ),
      "]",
    )

}
