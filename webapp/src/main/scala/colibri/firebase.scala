package colibri

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import org.scalablytyped.runtime.StringDictionary
import org.scalajs.dom.console
import typings.firebaseFirestore.mod.{collection as _, doc as _, *}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

package object firebase {
  // https://firebase.google.com/docs/firestore/query-data/listen

  def docObservable[T](document: DocumentReference[T]): Observable[Option[T]] =
    Observable.create { observer =>
      val ununsafeSubscribe =
        onSnapshot(document, (snapshot: DocumentSnapshot[T]) => observer.unsafeOnNext(snapshot.data().toOption))
      Cancelable(ununsafeSubscribe)
    }

  def docObserver[T](document: DocumentReference[T]): Observer[Option[T]] =
    Observer.create {
      case Some(data) =>
        console.log("writing ", data)
//        // somehow firebase doesn't write js class instances directly,
//        // so we'll convert it to a plain js object first
//        // TODO: https://stackoverflow.com/a/66774294
//        val plainObject = js.JSON.parse(js.JSON.stringify(data)).asInstanceOf[T]
        setDoc(document, data)
      case None => deleteDoc(document.asInstanceOf[DocumentReference[Any]])
    }

  def docSubject[T](document: DocumentReference[T]): Subject[Option[T]] = Subject.from(
    docObserver(document),
    docObservable(document),
  )

  def queryObservable[T](query: Query_[T]): Observable[js.Array[QueryDocumentSnapshot[T]]] =
    Observable.create { observer =>
      val ununsafeSubscribe =
        onSnapshot(query, (querySnapshot: QuerySnapshot[T]) => observer.unsafeOnNext(querySnapshot.docs))
      Cancelable(ununsafeSubscribe)
    }

  def getDocsIO[T](query: Query_[T]): IO[js.Array[QueryDocumentSnapshot[T]]] =
    IO.fromFuture(IO(getDocs(query).toFuture)).map(_.docs)

  def circeConverter[T: Encoder: Decoder]: FirestoreDataConverter[T] = js.Dynamic
    .literal(
      toFirestore = { (modelObject: WithFieldValue[T]) =>
        js.JSON.parse(modelObject.asInstanceOf[T].asJson.noSpaces).asInstanceOf[DocumentData]
      },
      fromFirestore = { (snapshot: QueryDocumentSnapshot[DocumentData]) =>
        snapshot.data().flatMap { data =>
          val decoded = decode[T](js.JSON.stringify(data))
          decoded.left.foreach(errors => console.warn("Could not decode: ", errors, "\n", data))
          decoded.toOption.getOrElse(js.undefined)
        }
      },
    )
    .asInstanceOf[FirestoreDataConverter[T]]
}
