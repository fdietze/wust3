package webapp.atoms

import cats.effect.{ContextShift, IO}
import colibri.firebase.*
import colibri.{Observable, Subject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.scalajs.dom.window
import typings.firebaseFirestore.mod.where

import scala.concurrent.Future
import scala.scalajs.js

package object api {
  opaque type AtomId = String
  object AtomId {
    def apply(value: String): AtomId = value
    extension (atomId: AtomId) { def value: String = atomId }
  }

  trait Api {
    def getAtom(atomId: AtomId): Observable[Option[Atom]]
    def setAtom(atom: Atom): Future[Unit]
    def findAtom(query: String): Future[Seq[Atom]]
    def newId(): AtomId
  }

  // Annotation is required when using semi-automatic derivation with configuration
  // https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala

  // http://www.hypergraphdb.org/docs/hypergraphdb.pdf

//  @ConfiguredJsonCodec
  case class Atom(
    id: AtomId,
    // ab: Int | String,
//    _type: AtomID,
    value: Option[String],
    targets: Map[String, AtomId],
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
  import typings.firebaseFirestore.mod.*

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
    doc(db, atomCollection, atomId.value).withConverter(atomConverter)

  override def getAtom(atomId: api.AtomId): Observable[Option[api.Atom]] =
    docObservable(atomDoc(atomId))

  override def setAtom(atom: api.Atom): Future[Unit] =
    setDoc[api.Atom](atomDoc(atom.id), atom).toFuture

  override def findAtom(queryString: String): Future[Seq[api.Atom]] =
    for {
      snapshots <- getDocsIO(
                     query(
                       collection(db, atomCollection),
                       // primitive prefix search
                       where("value", WhereFilterOp.GreaterthansignEqualssign, queryString),
                       where("value", WhereFilterOp.LessthansignEqualssign, s"${queryString}z"),
                     )
                       .withConverter(atomConverter),
                   ).unsafeToFuture()
      atoms      = snapshots.flatMap(_.data().toOption).toSeq
    } yield atoms

  override def newId(): api.AtomId = api.AtomId(util.Random.alphanumeric.take(10).mkString)
}
