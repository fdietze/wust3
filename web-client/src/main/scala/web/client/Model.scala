package wust.client
import wust.client.Event.Head
import cats.effect.IO
import cats.data.OptionT
import cats.implicits._
import collection.mutable
import org.scalajs.dom.ext.LocalStorage
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import colibri.Observable
import colibri.Subject
import colibri.Observer
import colibri.Cancelable

object Event {
  type TopicId   = String
  type VersionId = String
  type LiteralId = TopicId

  sealed trait Event
  sealed trait Topic extends Event {
    def id: TopicId
    def version: VersionId
    def parent: Option[VersionId]
    def timestamp: Long
  }

  case class Literal(
      id: TopicId,
      version: VersionId,
      parent: Option[VersionId],
      timestamp: Long,
      //
      value: String,
      // schema: Set[LiteralId]
  ) extends Topic

  case class Binding(
      id: TopicId,
      version: VersionId,
      parent: Option[VersionId],
      timestamp: Long,
      //
      subject: TopicId,
      predicate: LiteralId,
      obj: TopicId,
      // cardinality: Option[Int]
  ) extends Topic

  case class Head(topicId: TopicId, versionId: VersionId) extends Event

  // case class Version(
  //     value: VersionId
  // ) extends Topic

  // case class Tombstone(
  //     id: TopicId,
  //     version: VersionId,
  //     parent: Option[VersionId],
  //     timestamp: Long,
  // ) extends Topic

//     case class Proposal(
//         user: UserId,
//         versions: Seq[VersionId]
//     ) extends Topic
}

trait Api {
  def getTopic(topicId: Event.TopicId): Observable[Option[Event.Topic]]
  def getBindingsBySubject(subjectId: Event.TopicId): Observable[Seq[Event.TopicId]]
  def getBindingsByObject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]]
  def postEvent(event: Event.Event): IO[Unit]
  def newId(): String
  def now(): Long
}

object LocalStorageDatabase extends Api {
  val topics =
    new ReactiveDocumentLocalStorage[Event.Topic](
      collection = "topic",
      primaryKey = topic => topic.version,
      indices = Seq(
        { case binding: Event.Binding => binding.subject },
        { case binding: Event.Binding => binding.obj },
      ),
    )
  val heads  = new ReactiveDocumentLocalStorage[Event.Head](
    collection = "head",
    primaryKey = head => head.topicId,
  )
  def topicById(topicId: Event.TopicId): Observable[Option[Event.Topic]] = {
    heads
      .getLive(topicId)
      .switchMap(head => head.fold(Observable(None))(head => topics.getLive(head.versionId)))
  }

  override def getTopic(topicId: Event.TopicId): Observable[Option[Event.Topic]] = {
    // println(s"getTopic: ${topicId} -> ${topicById.storageRead(topicId)}")
    topicById(topicId)
  }

  override def getBindingsBySubject(subjectId: Event.TopicId): Observable[Seq[Event.TopicId]] = {

    // topics.getWithIndex("subject", key)
    // println(s"getBindingsBySubject: ${subjectId} -> ${bindingsBySubject.get(subjectId)}")
    // bindingsBySubject.getLive(subjectId)
    ???
  }

  override def getBindingsByObject(objectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
    // println(s"getBindingsByObject: ${objectId} -> ${bindingsByObject.get(objectId)}")
    // bindingsByObject(objectId)
    ???
  }

  def postEvent(event: Event.Event): IO[Unit] = {
    println(event)
    event match {
      case topic: Event.Literal => topics.write(topic)
      case topic: Event.Binding => topics.write(topic)
      case topic: Head          => heads.write(topic)
    }
  }
  def newId()                                                                       = util.Random.alphanumeric.take(10).mkString
  def now()                                                                         = System.currentTimeMillis()
}

abstract class ReactiveDocumentStorage[V: Encoder: Decoder](
    primaryKey: V => String,
    indices: Seq[(String, PartialFunction[V, String])],
) {
  final val subscriptions = mutable.MultiDict.empty[String, Observer[Option[V]]]
  storageChangeEvents.foreach { case (key, value) =>
    subscriptions.get(key).foreach(_.onNext(value))
  }

  final def getLive(key: String): Observable[Option[V]] = {
    Observable.concatAsync(
      // initial value
      storageRead(key),
      // subscribe to future values
      Observable.create[Option[V]] { obs =>
        val subscription = (key -> obs)
        subscriptions += subscription
        Cancelable(() => subscriptions -= subscription)
      },
    )
  }

  final def write(value: V): IO[Unit]     = set(primaryKey(value) -> Some(value))
  final def delete(key: String): IO[Unit] = set(key -> None)
  final def set(elem: (String, Option[V])): IO[Unit] = {
    val (key, value) = elem
    for {
      oldValue <- storageRead(key)
      _        <- IO {
                    // optimistic update
                    subscriptions.get(key).foreach(_.onNext(value))
                  }
      _        <- storageWrite(elem).onError { case error =>
                    IO {
                      subscriptions.get(key).foreach(_.onNext(oldValue))
                    },
                  }
    } yield ()
  }

  def storageReadRaw(key: String): IO[Option[String]]
  def storageWriteRaw(elem: (String, Option[String])): IO[Unit]

  final def storageRead(key: String): IO[Option[V]] = {
    (for {
      raw     <- OptionT(storageReadRaw(key))
      decoded <- OptionT(decode[V](raw).toOption.pure[IO])
    } yield decoded).value
  }
  final def storageWrite(elem: (String, Option[V])): IO[Unit] = {
    val (key, valueOpt) = elem
    storageWriteRaw(key -> valueOpt.map(_.asJson.noSpaces))
  }
  def storageChangeEvents: Observable[(String, Option[V])] = Observable.empty
}

class ReactiveDocumentLocalStorage[V: Encoder: Decoder](
    collection: String,
    primaryKey: V => String,
    indices: Seq[(String, PartialFunction[V, String])] = Seq.empty,
) extends ReactiveDocumentStorage[V](primaryKey, indices) {
  def namespacedKey(key: String): String                          = s"${collection}_${key}"
  def storageReadRaw(key: String): cats.effect.IO[Option[String]] = IO {
    LocalStorage(namespacedKey(key))
  }
  def storageWriteRaw(elem: (String, Option[String])): cats.effect.IO[Unit] = {
    elem match {
      case (key, Some(value)) => IO { LocalStorage.update(key = namespacedKey(key), value) }
      case (key, None)        => IO { LocalStorage.remove(key = namespacedKey(key)) }
    }
  }
}
