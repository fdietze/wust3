package web.client
import cats.effect.{ContextShift, IO}
import colibri.firebase.{circeConverter, docObservable, docSubject, queryObservable}
import colibri.{Observable, Subject}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.scalajs.dom.window
import web.client.api.{Atom, Value}

import scala.scalajs.js

package object api {

  type AtomId = String
  trait Api {
    def getAtom(atomId: AtomId): Observable[Option[Atom]]
    def newId(): String
  }

  object PrimitiveStorage {}

  // Annotation is required when using semi-automatic derivation with configuration
  // https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala

  // http://www.hypergraphdb.org/docs/hypergraphdb.pdf

  type ID = String
  @ConfiguredJsonCodec
  sealed trait Value { def id: ID }
  object Value {
    case class Link(id: ID, ids: List[ID]) extends Value
    case class Data(id: ID, data: String)  extends Value

    val discriminator                        = "_type"
    implicit val genDevConfig: Configuration =
      Configuration.default.withDiscriminator(discriminator)

    implicit val decoder: Decoder[api.Value] = deriveDecoder
    implicit val encoder: Encoder[api.Value] = deriveEncoder
  }

  type AtomID = ID
  case class Atom(id: AtomID, _type: AtomID, value: Value, targets: List[AtomID])
}

object FirebaseApi extends api.Api {

  import typings.firebaseApp.mod.{initializeApp, FirebaseOptions}
  import typings.firebaseFirestore.mod._

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
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

  private val db = getFirestore()
  // https://firebase.google.com/docs/emulator-suite/connect_rtdb#instrument_your_app_to_talk_to_the_emulators
  if (window.location.hostname == "localhost") {
    connectFirestoreEmulator(db, "localhost", 8080)
  }

  val storeCollection                                   = "store"
  val valueConverter: FirestoreDataConverter[api.Value] = circeConverter[api.Value]

  private def getValue(valueId: api.ID): Observable[Option[api.Value]] = docObservable(
    doc(db, storeCollection, valueId).withConverter(valueConverter),
  )

  override def getAtom(atomId: api.AtomId): Observable[Option[api.Atom]] =
    getValue(atomId).switchMap {
      case Some(api.Value.Link(atomId, typeId :: valueId :: targetIds)) =>
        getValue(valueId).map(_.map(value => api.Atom(atomId, typeId, value, targetIds)))
      case _                                                            => Observable(None)
    }

//  def getTopicsByField(_type: String, field: String, value: TopicId): Observable[js.Array[Event.Topic]] =
//    queryObservable(
//      query(
//        collection(db, "topics"),
//        where("_type", WhereFilterOp.EqualssignEqualssign, _type),
//        where(field, WhereFilterOp.EqualssignEqualssign, value),
//      ).withConverter(circeConverter[Event.Topic]),
//    ).map(_.map(_.data().get))

//  override def writeTopic(topic: Event.Topic): IO[Unit] =
//    IO.fromFuture(IO(setDoc[Event.Topic](doc(db, "topics", topic.id).withConverter(circeConverter[Event.Topic]), topic).toFuture))

  override def newId(): String = util.Random.alphanumeric.take(10).mkString
}
