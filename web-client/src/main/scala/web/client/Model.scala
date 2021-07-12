package wust.client
import wust.client.Event.Head
import cats.effect.IO
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
  def getTopic(topicId: Event.TopicId): Observable[Event.Topic]
  def getBindingsBySubject(subjectId: Event.TopicId): Observable[Seq[Event.TopicId]]
  def getBindingsByObject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]]
  def postEvent(event: Event.Event): IO[Unit]
  def newId(): String
  def now(): Long
}

object LocalStorageDatabase extends Api {
  val topicSubscriptions          = mutable.MultiDict.empty[Event.TopicId, Observer[Event.Topic]]
  val bindingSubjectSubscriptions = mutable.MultiDict.empty[Event.TopicId, Observer[Seq[Event.TopicId]]]

  val topics              = new ReactiveDocumentLocalStorage[Event.VersionId, Event.Topic](namespace = "topics")
  val currentTopicVersion = new ReactiveDocumentLocalStorage[Event.TopicId, Event.VersionId](
    namespace = "currentTopicVersion",
    onUpdate = { case (topicId, versionId) =>
      topicSubscriptions.get(topicId).foreach(obs => topics.get(versionId).foreach(obs.onNext))
    },
  )
  val bindingsBySubject   =
    new ReactiveDocumentLocalStorage[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingBySubject")
  val bindingsByObject    =
    new ReactiveDocumentLocalStorage[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingByObject")

  def topicById(topicId: Event.TopicId): Option[Event.Topic] = {
    for {
      versionId <- currentTopicVersion.get(topicId)
      topic     <- topics.get(versionId)
    } yield topic
  }

  override def getTopic(topicId: Event.TopicId): Observable[Event.Topic] = {
    println(s"getTopic: ${topicId} -> ${topicById(topicId)}")
    Observable.create { obs =>
      // initial value
      topicById(topicId).fold {
        obs.onError(new Exception(s"Topic ${topicId} not found"))
      } {
        obs.onNext
      }
      // subscribe to future values
      topicSubscriptions += (topicId -> obs)
      Cancelable(() => topicSubscriptions -= (topicId -> obs))
    }
  }

  override def getBindingsBySubject(subjectId: Event.TopicId): Observable[Seq[Event.TopicId]] = {
    println(s"getBindingsBySubject: ${subjectId} -> ${bindingsBySubject.get(subjectId)}")
    Observable.create { obs =>
      // potential initial values (might be empty)
      bindingsBySubject.get(subjectId).foreach(obs.onNext)
      // subscribe to future values
      bindingSubjectSubscriptions += (subjectId -> obs)
      Cancelable(() => bindingSubjectSubscriptions -= (subjectId -> obs))
    }
  }

  override def getBindingsByObject(objectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
    println(s"getBindingsByObject: ${objectId} -> ${bindingsByObject.get(objectId)}")
    bindingsByObject(objectId)
  }

  def postEvent(event: Event.Event): IO[Unit] = IO {
    println(event)
    event match {
      case topic: Event.Literal     => topics(topic.version) = topic
      case topic: Event.Binding     =>
        topics(topic.version) = topic
        bindingsBySubject.updateWith(topic.subject) { seqOpt =>
          Some(seqOpt.fold(Seq(topic.id))(_ :+ topic.id))
        }
        bindingsByObject.updateWith(topic.obj) { seqOpt =>
          Some(seqOpt.fold(Seq(topic.id))(_ :+ topic.id))
        }
        ()
      case Head(topicId, versionId) => currentTopicVersion(topicId) = versionId
    }
  }
  def newId()                                 = util.Random.alphanumeric.take(10).mkString
  def now()                                   = System.currentTimeMillis()
}

abstract class ReactiveDocumentStorage[K <: String, V: Encoder: Decoder]() {
  final val subscriptions = mutable.MultiDict.empty[K, Observer[Option[V]]]
  storageChangeEvents.foreach { case (key, value) =>
    subscriptions.get(key).foreach(_.onNext(value))
  }

  final def getLive(key: K): Observable[Option[V]] = {
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

  final def set(elem: (K, Option[V])): IO[Unit] = {
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

  def storageRead(key: K): IO[Option[V]]
  def storageWrite(elem: (K, Option[V])): IO[Unit]
  def storageChangeEvents: Observable[(K, Option[V])] = Observable.empty
}

class ReactiveDocumentLocalStorage[K <: String, V: Encoder: Decoder](collection: String)
    extends ReactiveDocumentStorage[K, V] {
  def nkey(key: String): String                      = s"${collection}_${key}"
  def storageRead(key: K): cats.effect.IO[Option[V]] = IO { LocalStorage(nkey(key)).flatMap(decode[V](_).toOption) }
  def storageWrite(elem: (K, Option[V])): cats.effect.IO[Unit] = {
    elem match {
      case (key, Some(value)) => IO { LocalStorage.update(key = nkey(key), value.asJson.noSpaces) }
      case (key, None)        => IO { LocalStorage.remove(key = nkey(key)) }
    }
  }
}
