package web.client
import cats.effect.{ContextShift, IO}
import colibri.firebase.{circeConverter, docObservable, docSubject, queryObservable}
import colibri.{Observable, Subject}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.scalajs.dom.window
import web.client.Event.TopicId

import scala.scalajs.js

trait Api {
  def getTopic(topicId: Event.TopicId): Observable[Option[Event.Topic]]
  def getBindingsBySubject(subjectId: Event.TopicId): Observable[js.Array[Event.Topic]]
  def getBindingsByPredicate(predicateId: Event.TopicId): Observable[js.Array[Event.Topic]]
  def getBindingsByObject(objectId: Event.TopicId): Observable[js.Array[Event.Topic]]
  def writeTopic(topic: Event.Topic): IO[Unit]
  def newId(): String
}

object Event {
  type TopicId = String

  // Annotation is required when using semi-automatic derivation with configuration
  // https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala
  @ConfiguredJsonCodec
  sealed trait Topic {
    def id: TopicId
  }

  case class Literal(
    id: TopicId,
    value: String,
  ) extends Topic

  case class Binding(
    id: TopicId,
    subject: TopicId,
    predicate: TopicId,
    obj: TopicId,
  ) extends Topic

  object Topic {
    implicit val genDevConfig: Configuration =
      Configuration.default.withDiscriminator("_type")

    implicit val decoder: Decoder[Topic] = deriveDecoder
    implicit val encoder: Encoder[Topic] = deriveEncoder
  }
}

object FirebaseDatabase extends Api {
  import typings.firebaseApp.mod.{initializeApp, FirebaseOptions}
  import typings.firebaseFirestore.mod._

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global
  implicit val contextShift: ContextShift[IO]        = IO.contextShift(ec)

  initializeApp(
    FirebaseOptions()
      .setApiKey("AIzaSyCIbcwpfFLk-25OoXfxXT8QF2qZFnQaP1M")
      .setAuthDomain("wust-3.firebaseapp.com")
      .setProjectId("wust-3")
      .setStorageBucket("wust-3.appspot.com")
      .setMessagingSenderId("622146703474")
      .setAppId("1:622146703474:web:cc97a3a2de7231c11532eb"),
  )

  // https://firebase.google.com/docs/emulator-suite/connect_rtdb#instrument_your_app_to_talk_to_the_emulators
  val db = getFirestore()
  if (window.location.hostname == "localhost") {
    connectFirestoreEmulator(db, "localhost", 8080)
  }

  override def getTopic(topicId: TopicId): Observable[Option[Event.Topic]] =
    docObservable(doc(db, "topics", topicId).withConverter(circeConverter[Event.Topic]))

  def getTopicsByField(_type: String, field: String, value: TopicId): Observable[js.Array[Event.Topic]] =
    queryObservable(
      query(
        collection(db, "topics"),
        where("_type", WhereFilterOp.EqualssignEqualssign, _type),
        where(field, WhereFilterOp.EqualssignEqualssign, value),
      ).withConverter(circeConverter[Event.Topic]),
    ).map(_.map(_.data().get))

  override def getBindingsBySubject(subjectId: TopicId): Observable[js.Array[Event.Topic]] =
    getTopicsByField("Binding", "subject", subjectId)

  override def getBindingsByPredicate(subjectId: TopicId): Observable[js.Array[Event.Topic]] =
    getTopicsByField("Binding", "predicate", subjectId)

  override def getBindingsByObject(subjectId: TopicId): Observable[js.Array[Event.Topic]] =
    getTopicsByField("Binding", "obj", subjectId)

  override def writeTopic(topic: Event.Topic): IO[Unit] =
    IO.fromFuture(IO(setDoc[Event.Topic](doc(db, "topics", topic.id).withConverter(circeConverter[Event.Topic]), topic).toFuture))

  override def newId(): String = util.Random.alphanumeric.take(10).mkString
}
