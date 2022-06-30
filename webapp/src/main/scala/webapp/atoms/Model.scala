package webapp.atoms

import cats.effect.IO
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
import scala.scalajs.js.annotation._
import typings.node.global.console

package object api {
  opaque type AtomId = String
  object AtomId {
    def apply(value: String): AtomId = value
    extension (atomId: AtomId) { def value: String = atomId }
  }

  trait Api {
    def getAtom(atomId: AtomId): Observable[Option[Atom]]
    def setAtom(atom: Atom): Future[Unit]
    def findAtoms(query: String): Future[Vector[SearchResult]]
    def getUsages(targetAtomId: AtomId): Future[Vector[Reference]]
    def newId(): AtomId
  }

  // Annotation is required when using semi-automatic derivation with configuration
  // https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala

  // http://www.hypergraphdb.org/docs/hypergraphdb.pdf

//  @ConfiguredJsonCodec

  sealed trait Field

  object Field {
    case class AtomRef(atomId: AtomId) extends Field
    case class Value(value: String)    extends Field

//    val discriminator                        = "_type"
//    implicit val genDevConfig: Configuration =
//      Configuration.default.withDiscriminator(discriminator)
    implicit val decoder: Decoder[Field] = deriveDecoder
    implicit val encoder: Encoder[Field] = deriveEncoder
  }

  case class Atom(
    id: AtomId,
    // ab: Int | String,
//    _type: AtomID,
    targets: Map[String, Field],
    shape: Vector[AtomId],
    name: Option[String],
  ) {
    override def toString = {
      name.getOrElse {
        val t = targets.map {
          case (key, api.Field.Value(value))    => s"""$key: "$value""""
          case (key, api.Field.AtomRef(atomId)) => s"""$key: [${atomId.value.take(4).mkString}]"""
        }

        s"{${t.mkString(", ")}}:${id.take(4)} ${if (shape.nonEmpty) ":" else ""}${shape.map(_.take(4)).mkString(":")}"
      }
    }
  }

  object Atom {
    implicit val decoder: Decoder[Atom] = deriveDecoder
    implicit val encoder: Encoder[Atom] = deriveEncoder
  }

  case class SearchResult(atom: Atom, key: Option[String])
  object SearchResult {
    implicit val encoder: Encoder[SearchResult] = deriveEncoder
    implicit val decoder: Decoder[SearchResult] = deriveDecoder
  }

  case class Reference(atom: Atom, key: String)
  object Reference {
    implicit val encoder: Encoder[Reference] = deriveEncoder
    implicit val decoder: Decoder[Reference] = deriveDecoder
  }

}

object FirebaseApi extends api.Api {

  import typings.firebaseApp.mod.{initializeApp, FirebaseOptions}
  import typings.firebaseFirestore.mod.*

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

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

  val searchCollection                                          = "search"
  val searchConverter: FirestoreDataConverter[api.SearchResult] = circeConverter[api.SearchResult]

  val usageCollectionName                                       = "usages"
  val referenceConverter: FirestoreDataConverter[api.Reference] = circeConverter[api.Reference]

  def atomDoc(atomId: api.AtomId): DocumentReference[api.Atom] =
    doc(db, atomCollection, atomId.value).withConverter(atomConverter)

  def searchDoc(value: String, atomId: api.AtomId): DocumentReference[api.SearchResult] =
    doc(db, searchCollection, s"${value}_${atomId.value}").withConverter(searchConverter)

  def usageCollection(atomId: api.AtomId): CollectionReference[api.Reference] =
    collection(db, atomCollection, atomId.value, usageCollectionName)
      .withConverter(referenceConverter)
      .asInstanceOf[CollectionReference[api.Reference]]

  override def getAtom(atomId: api.AtomId): Observable[Option[api.Atom]] =
    docObservable(atomDoc(atomId))

  override def setAtom(atom: api.Atom): Future[Unit] = async[Future] {
    // write atom itself
    await(setDoc(atomDoc(atom.id), atom).toFuture)

    // clear usage index for this atom
    val usageDocs = await(getReverseUsageDocs(atom.id))
    await(Future.sequence(usageDocs.map(doc => deleteDoc(doc.ref.asInstanceOf[DocumentReference[Any]]).toFuture)))

    // clear search index for this atom
    val searchDocs = await(getSearchIndexDocs(atom.id))
    await(Future.sequence(searchDocs.map(doc => deleteDoc(doc.ref.asInstanceOf[DocumentReference[Any]]).toFuture)))

    // // write usage backreferences and search index
    Future.sequence(atom.targets.collect {
      case (key, api.Field.AtomRef(targetId)) =>
        addDoc(usageCollection(targetId), api.Reference(atom, key)).toFuture
      case (key, api.Field.Value(value)) =>
        setDoc(searchDoc(value, atom.id), api.SearchResult(atom, Some(key))).toFuture
    })
    ()
  }

  override def findAtoms(queryString: String): Future[Vector[api.SearchResult]] =
    async[Future] {
      val nameSearchAtoms: Vector[api.SearchResult] = await(
        getDocs(
          query(
            collection(db, atomCollection),
            // primitive prefix search
            where("name", WhereFilterOp.GreaterthansignEqualssign, queryString),
            where("name", WhereFilterOp.LessthansignEqualssign, s"${queryString}z"),
          ).withConverter(atomConverter),
        ).toFuture,
      ).docs.flatMap(_.data().toOption).map(atom => api.SearchResult(atom, None)).toVector

      val fieldSearchAtoms: Vector[api.SearchResult] = await(
        getDocs(
          query(
            collection(db, searchCollection),
            // primitive prefix search
            where(documentId(), WhereFilterOp.GreaterthansignEqualssign, queryString),
            where(documentId(), WhereFilterOp.LessthansignEqualssign, s"${queryString}z"),
          )
            .withConverter(searchConverter),
        ).toFuture,
      ).docs.flatMap(_.data().toOption).toVector

      nameSearchAtoms ++ fieldSearchAtoms
    }

  def getSearchIndexDocs(atomId: api.AtomId): Future[Vector[QueryDocumentSnapshot[api.SearchResult]]] = async[Future] {
    val querySnapshot = await(
      getDocs(
        query(
          collectionGroup(db, searchCollection),
          where("atom.id", WhereFilterOp.EqualssignEqualssign, atomId.value),
        ).withConverter(searchConverter),
      ).toFuture,
    )
    querySnapshot.docs.toVector
  }

  def getReverseUsageDocs(parentAtomId: api.AtomId): Future[Vector[QueryDocumentSnapshot[api.Reference]]] =
    async[Future] {
      val querySnapshot = await(
        getDocs(
          query(
            collectionGroup(db, usageCollectionName),
            where("atom.id", WhereFilterOp.EqualssignEqualssign, parentAtomId.value),
          ).withConverter(referenceConverter),
        ).toFuture,
      )
      querySnapshot.docs.toVector
    }

  def getUsageDocs(targetAtomId: api.AtomId): Future[Vector[QueryDocumentSnapshot[api.Reference]]] = async[Future] {
    val querySnapshot = await(getDocs(usageCollection(targetAtomId)).toFuture)
    querySnapshot.docs.toVector
  }

  override def getUsages(targetAtomId: api.AtomId): Future[Vector[api.Reference]] = async[Future] {
    await(getUsageDocs(targetAtomId)).flatMap { doc =>
      doc.data().toOption
    }
  }

  override def newId(): api.AtomId = api.AtomId(util.Random.alphanumeric.take(10).mkString)
}
