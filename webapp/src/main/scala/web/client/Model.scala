package web.client
import cats.effect.{ContextShift, IO}
import colibri.firebase.{circeConverter, docSubject}
import colibri.{Observable, Subject}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.scalajs.dom.window
import web.client.Event.TopicId

object Event {
  type TopicId   = String
  type VersionId = String
  type LiteralId = TopicId

// Annotation is required when using semi-automatic derivation with configuration
// https://github.com/circe/circe-generic-extras/blob/master/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala
  @ConfiguredJsonCodec
  sealed trait Topic {
    def id: TopicId
//    def version: VersionId
//    def parent: Option[VersionId]
//    def timestamp: Long
  }
  object Topic {
    implicit val genDevConfig: Configuration =
      Configuration.default.withDiscriminator("_type")

    implicit val decoder: Decoder[Topic] = deriveDecoder
    implicit val encoder: Encoder[Topic] = deriveEncoder
  }

  case class Literal(
    val id: TopicId,
//    version: VersionId,
//    parent: Option[VersionId],
//    timestamp: Long,
    //
    val value: String,
    // schema: Set[LiteralId]
  ) extends Topic

  case class Binding(
    val id: TopicId,
//    version: VersionId,
//    parent: Option[VersionId],
//    timestamp: Long,
    //
    val subject: TopicId,
    val predicate: LiteralId,
    val obj: TopicId,
    // cardinality: Option[Int]
  ) extends Topic
}

trait Api[F[_]] {
  def getTopic(topicId: Event.TopicId): F[Event.Topic]
  def getBindingsBySubject(subjectId: Event.TopicId): F[Seq[Event.TopicId]]
  def getBindingsByObject(subjectId: Event.TopicId): F[Seq[Event.TopicId]]
//  def postEvent(event: Event.Event): IO[Unit]
  def newId(): String
  def now(): Long
}

object FirebaseDatabase extends Api[Observable] {
  import typings.firebaseApp.mod.{initializeApp, FirebaseOptions}
  import typings.firebaseFirestore.mod._

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global
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

  // https://firebase.google.com/docs/emulator-suite/connect_rtdb#instrument_your_app_to_talk_to_the_emulators
  val db = getFirestore()
  if (window.location.hostname == "localhost") {
    connectFirestoreEmulator(db, "localhost", 8080)
  }

  val sub: Subject[Option[Event.Topic]] = docSubject(
    doc(db: typings.firebaseFirestore.mod.Firestore, "test", "aaxyz")
      .withConverter(circeConverter[Event.Topic]),
  )
  sub.foreach(println(_))
  sub.onNext(Some(Event.Binding("jo", "hi", "a", "x")))

  override def getTopic(topicId: TopicId): Observable[Event.Topic] = {
    val document = doc(db, "topics", "other", "something")
    println(document)
    ???
  }

  override def getBindingsBySubject(subjectId: TopicId): Observable[Seq[TopicId]] = ???

  override def getBindingsByObject(subjectId: TopicId): Observable[Seq[TopicId]] = ???

//  override def postEvent(event: Event.Event): IO[Unit] = ???

  override def newId(): String = ???

  override def now(): Long = ???
}

//object LocalStorageDatabase extends Api[IO] {
//
//  val topics                                         = new LocalStorageMap[Event.VersionId, Event.Topic](namespace = "topics")
//  val currentTopicVersion                            = new LocalStorageMap[Event.TopicId, Event.VersionId](namespace = "currentTopicVersion")
//  val bindingsBySubject                              = new LocalStorageMap[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingBySubject")
//  val bindingsByObject                               = new LocalStorageMap[Event.TopicId, Seq[Event.TopicId]](namespace = "bindingByObject")
//  def topicById(topicId: Event.TopicId): Event.Topic = {
//    val versionId = currentTopicVersion(topicId)
//    topics(versionId)
//  }
//
//  override def getTopic(topicId: Event.TopicId): IO[Event.Topic] = IO {
//    println(s"getTopic: ${topicId} -> ${topicById(topicId)}")
//    topicById(topicId)
//  }
//
//  override def getBindingsBySubject(subjectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
//    println(s"getBindingsBySubject: ${subjectId} -> ${bindingsBySubject(subjectId)}")
//    bindingsBySubject(subjectId)
//  }
//
//  override def getBindingsByObject(objectId: Event.TopicId): IO[Seq[Event.TopicId]] = IO {
//    println(s"getBindingsByObject: ${objectId} -> ${bindingsByObject(objectId)}")
//    bindingsByObject(objectId)
//  }
//
//  def postEvent(event: Event.Event): IO[Unit] = IO {
//    println(event)
//    event match {
//      case topic: Event.Literal     => topics(topic.version) = topic
//      case topic: Event.Binding     =>
//        topics(topic.version) = topic
//        bindingsBySubject.updateWith(topic.subject) { seqOpt =>
//          Some(seqOpt.fold(Seq(topic.id))(_ :+ topic.id))
//        }
//        bindingsByObject.updateWith(topic.obj) { seqOpt =>
//          Some(seqOpt.fold(Seq(topic.id))(_ :+ topic.id))
//        }
//        ()
//      case Head(topicId, versionId) => currentTopicVersion(topicId) = versionId
//    }
//  }
//  def newId()                                 = util.Random.alphanumeric.take(10).mkString
//  def now()                                   = System.currentTimeMillis()
//}
