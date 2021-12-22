package web.client
import cats.effect.{ContextShift, IO}
import colibri.firebase.{circeConverter, docObservable, docSubject, queryObservable}
import colibri.{Observable, Subject}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.scalajs.dom.window

import scala.scalajs.js

package object api {

  type AtomId = String
  trait Api {
    def getAtom(atomId: AtomId): Observable[Option[Atom]]
    def setAtom(atom: Atom): IO[Unit]
    def newId(): String
  }

  // Annotation is required when using semi-automatic derivation with configuration
  // https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala

  // http://www.hypergraphdb.org/docs/hypergraphdb.pdf

  type AtomID = String
//  @ConfiguredJsonCodec
  case class Atom(
    id: AtomID,
    _type: AtomID,
    value: Option[String],
    targets: Map[String, AtomID],
  )
  object Atom {
//    val discriminator                        = "_type"
//    implicit val genDevConfig: Configuration =
//      Configuration.default.withDiscriminator(discriminator)

    implicit val decoder: Decoder[Atom] = deriveDecoder
    implicit val encoder: Encoder[Atom] = deriveEncoder
  }
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

  val atomCollection                                  = "atoms"
  val atomConverter: FirestoreDataConverter[api.Atom] = circeConverter[api.Atom]

  def atomDoc(atomId: api.AtomId): DocumentReference[api.Atom] =
    doc(db, atomCollection, atomId).withConverter(atomConverter)

  override def getAtom(atomId: api.AtomId): Observable[Option[api.Atom]] =
    docObservable(doc(db, atomCollection, atomId).withConverter(atomConverter))

  override def setAtom(atom: api.Atom): IO[Unit] =
    IO.fromFuture(IO(setDoc[api.Atom](atomDoc(atom.id), atom).toFuture))

  override def newId(): String = util.Random.alphanumeric.take(10).mkString
}
