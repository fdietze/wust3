package wust.client
import wust.client.Event.Head
import cats.effect.IO

import collection.mutable
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalajs.dom.window.localStorage

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

// object SnapshotModel {
//   import Event.TopicId
//   import Event.VersionId

//   sealed trait Topic

//   // case class Literal(id: TopicId, value: String) extends Topic
//   // case class Binding(
//   //     id: TopicId,
//   //     subject: TopicId,
//   //     predicate: TopicId,
//   //     obj: TopicId,
//   // )                                              extends Topic

//   case class Snapshot(
//       versionLookup: Map[VersionId, Event.Topic],
//       lookup: Map[TopicId, VersionId],
//       // subjectLookup: Map[TopicId, Binding],
//       // predicateLookup: Map[TopicId, Binding],
//       // objectLookup: Map[TopicId, Binding],
//   ) {
//     def addEvent(event: Event.Event): Snapshot = {
//       event match {
//         case topic: Event.Literal     =>
//           copy(versionLookup = versionLookup + (topic.version -> topic))
//         case topic: Event.Binding     =>
//           copy(versionLookup = versionLookup + (topic.version -> topic))
//         case Head(topicId, versionId) => copy(lookup = lookup + (topicId -> versionId))
//       }
//     }
//   }

// }

trait Api {
  def getTopic(topicId: Event.TopicId): IO[Event.Topic]
  def getBindingsBySubject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]]
  def getBindingsByObject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]]
  def postEvent(event: Event.Event): IO[Unit]
  def newId(): String
  def now(): Long
}

object LocalStorageDatabase extends Api {

  val topics                                         = new LocalStorageMap[Event.VersionId, Event.Topic](namespace = "topics")
  val currentTopicVersion                            = new LocalStorageMap[Event.TopicId, Event.VersionId](namespace = "currentTopicVersion")
  val bindingsBySubject                              = new LocalStorageMap[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingBySubject")
  val bindingsByObject                               = new LocalStorageMap[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingByObject")
  def topicById(topicId: Event.TopicId): Event.Topic = {
    val versionId = currentTopicVersion(topicId)
    topics(versionId)
  }

  override def getTopic(topicId: Event.TopicId): IO[Event.Topic] = IO {
    println(s"getTopic: ${topicId} -> ${topicById(topicId)}")
    topicById(topicId)
  }

  override def getBindingsBySubject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
    println(s"getBindingsBySubject: ${subjectId} -> ${bindingsBySubject(subjectId)}")
    bindingsBySubject(subjectId)
  }

  override def getBindingsByObject(objectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
    println(s"getBindingsByObject: ${objectId} -> ${bindingsByObject(objectId)}")
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

class LocalStorageMap[K <: String, V: Encoder: Decoder](namespace: String) extends mutable.Map[K, V] {
  def nkey(raw: String): String = s"${namespace}_${raw}"

  // Members declared in scala.collection.mutable.Growable
  def addOne(elem: (K, V)): this.type = {
    localStorage.setItem(key = nkey(elem._1), elem._2.asJson.noSpaces)
    this
  }

  // Members declared in scala.collection.IterableOnce
  def iterator: Iterator[(K, V)] = ???

  // Members declared in scala.collection.MapOps
  def get(key: K): Option[V] = Option(localStorage.getItem(nkey(key))).flatMap(decode[V](_).toOption)

  // Members declared in scala.collection.mutable.Shrinkable
  def subtractOne(elem: K): this.type = {
    localStorage.removeItem(nkey(elem))
    this
  }
}
