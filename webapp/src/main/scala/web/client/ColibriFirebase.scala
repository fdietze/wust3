package colibri

import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.scalablytyped.runtime.StringDictionary
import typings.firebaseFirestore.mod.{collection => _, doc => _, _}
import org.scalajs.dom.console
import scala.scalajs.js.JSConverters._
import web.client.Event

import scala.scalajs.js

package object firebase {
  // https://firebase.google.com/docs/firestore/query-data/listen

  def docObservable[T](document: DocumentReference[T]): Observable[Option[T]] =
    Observable.create { observer =>
      val unsubscribe = onSnapshot(document, (snapshot: DocumentSnapshot[T]) => observer.onNext(snapshot.data().toOption))
      Cancelable(unsubscribe)
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
      case None       => deleteDoc(document.asInstanceOf[DocumentReference[Any]])
    }

  def docSubject[T](document: DocumentReference[T]): Subject[Option[T]] = Subject.from(
    docObserver(document),
    docObservable(document),
  )

  def queryObservable[T <: js.Object](query: Query_[T]): Observable[js.Array[QueryDocumentSnapshot[T]]] =
    Observable.create { observer =>
      val unsubscribe = onSnapshot(query, (querySnapshot: QuerySnapshot[T]) => observer.onNext(querySnapshot.docs))
      Cancelable(unsubscribe)
    }

  def circeConverter[T: Encoder: Decoder]: FirestoreDataConverter[T] = js.Dynamic
    .literal(
      toFirestore = { (modelObject: WithFieldValue[T]) =>
        js.JSON.parse(modelObject.asInstanceOf[T].asJson.noSpaces).asInstanceOf[DocumentData]
      },
      fromFirestore = { (snapshot: QueryDocumentSnapshot[DocumentData]) =>
        snapshot.data().flatMap { data =>
          val decoded = decode[T](js.JSON.stringify(data))
          decoded.left.foreach(_ => console.warn("Could not decode: ", data))
          decoded.toOption.getOrElse(js.undefined)
        }
      },
    )
    .asInstanceOf[FirestoreDataConverter[T]]
}
