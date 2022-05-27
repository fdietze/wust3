package webapp.atoms

import cats.effect.{ContextShift, IO}
import colibri.firebase.*
import colibri.{Observable, Subject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.scalajs.dom.window
import typings.firebaseFirestore.mod.where

import cps.*                 // async, await
import cps.monads.{given, *} // support for built-in monads (i.e. Future)

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
    def getReferences(atomId: AtomId): Future[Seq[Reference]]
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
    shape: Vector[AtomId],
  )

  object Atom {
//    val discriminator                        = "_type"
//    implicit val genDevConfig: Configuration =
//      Configuration.default.withDiscriminator(discriminator)
    def literal(id: AtomId, value: String) = Atom(id, Some(value), targets = Map.empty, shape = Vector.empty)

    implicit val decoder: Decoder[Atom] = deriveDecoder
    implicit val encoder: Encoder[Atom] = deriveEncoder
  }

  case class Reference(atomId: AtomId, key: String)
  object Reference {
    implicit val encoder: Encoder[Reference] = deriveEncoder
    implicit val decoder: Decoder[Reference] = deriveDecoder
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

  val referenceCollectionName                                   = "references"
  val referenceConverter: FirestoreDataConverter[api.Reference] = circeConverter[api.Reference]

  def atomDoc(atomId: api.AtomId): DocumentReference[api.Atom] =
    doc(db, atomCollection, atomId.value).withConverter(atomConverter)

  def referenceColl(atomId: api.AtomId): CollectionReference[api.Reference] =
    collection(db, atomCollection, atomId.value, referenceCollectionName)
      .withConverter(referenceConverter)
      .asInstanceOf[CollectionReference[api.Reference]]

  override def getAtom(atomId: api.AtomId): Observable[Option[api.Atom]] =
    docObservable(atomDoc(atomId))

  override def setAtom(atom: api.Atom): Future[Unit] = async[Future] {
    // write atom itself
    await(setDoc(atomDoc(atom.id), atom).toFuture)

    // clear backreferences
    val referenceDocs = await(getReferenceDocs(atom.id))
    await(Future.sequence(referenceDocs.map(doc => deleteDoc(doc.ref.asInstanceOf[DocumentReference[Any]]).toFuture)))

    // write target backreferences
    await(Future.sequence(atom.targets.map { case (key, targetId) =>
      addDoc(referenceColl(targetId), api.Reference(atom.id, key)).toFuture
        .map(_ => null) // Future[Unit] collapses to Unit
    }))
    ()
  }

  override def findAtom(queryString: String): Future[Seq[api.Atom]] =
    async[Future] {
      val snapshots = await(
        getDocs(
          query(
            collection(db, atomCollection),
            // primitive prefix search
            where("value", WhereFilterOp.GreaterthansignEqualssign, queryString),
            where("value", WhereFilterOp.LessthansignEqualssign, s"${queryString}z"),
          )
            .withConverter(atomConverter),
        ),
      ).docs
      val atoms = snapshots.flatMap(_.data().toOption).toSeq
      atoms
    }

  def getReferenceDocs(atomId: api.AtomId): Future[Vector[QueryDocumentSnapshot[api.Reference]]] = async[Future] {
    val querySnapshot = await(
      getDocs(
        query(
          collectionGroup(db, referenceCollectionName),
          where("atomId", WhereFilterOp.EqualssignEqualssign, atomId.value),
        ).withConverter(referenceConverter),
      ).toFuture,
    )
    querySnapshot.docs.toVector
  }

  override def getReferences(atomId: api.AtomId): Future[Seq[api.Reference]] = async[Future] {
    await(getReferenceDocs(atomId)).flatMap { doc =>
      doc
        .data()
        .toOption
        .map(backref => api.Reference(atomId = api.AtomId(doc.ref.parent.parent.id), key = backref.key))
    }
  }

  override def newId(): api.AtomId = api.AtomId(util.Random.alphanumeric.take(10).mkString)
}
