package colibri

import org.scalablytyped.runtime.StringDictionary
import typings.firebaseFirestore.mod.{collection => _, doc => _, _}

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
      case Some(data) => setDoc(document, data)
      case None       => deleteDoc(document.asInstanceOf[DocumentReference[Any]])
    }

  def docSubject[T](document: DocumentReference[T]): Subject[Option[T]] = Subject.from(
    docObserver(document),
    docObservable(document),
  )

  def queryObservable[T](query: Query_[T]): Observable[js.Array[QueryDocumentSnapshot[T]]] =
    Observable.create { observer =>
      val unsubscribe = onSnapshot(query, (querySnapshot: QuerySnapshot[T]) => observer.onNext(querySnapshot.docs))
      Cancelable(unsubscribe)
    }

}
