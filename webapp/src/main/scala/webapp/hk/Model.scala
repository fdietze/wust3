package webapp.hk

import colibri.firebase.{circeConverter, docObservable, docSubject, getDocsIO, queryObservable}
import colibri.{Observable, Subject}
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.parser.decode
import org.scalajs.dom.window
import org.scalajs.dom.console
import typings.firebaseFirestore.mod.where
import scala.concurrent.Future
import scala.util.Try
import cats.syntax.traverse._

import scala.concurrent.Future
import scala.scalajs.js
import typings.node.nodeStrings.der
import typings.node.nodeStrings.equal

package object api {
  type CId     = String
  type TopicId = String // | Cid

  trait Api {
    def getTopic(id: TopicId): Future[Option[Topic]]
    def addFrame(frame: Frame): Future[CId]
    def findNames(query: String): Future[Seq[Name]]
    def findFrames(query: String): Future[Seq[Frame]]
    def findTopics(query: String): Future[Seq[Topic]]
  }

  sealed trait Topic {
    def id: TopicId
  }

  sealed trait Immutable extends Topic {
    lazy val id: CId = calcId

    def calcId: CId =
      console.log((this: Topic).asJson.noSpaces)
      (this: Topic).asJson.noSpaces.hashCode.toString
  }

  case class Name(id: TopicId) extends Topic {
    override def toString = s"<$id>"
  }
  // type Name = TopicId

  sealed trait Literal extends Immutable

  object Literal {
    case class IntegerValue(
      value: Int,
    ) extends Literal {
      override def toString = s"\"$value\"^xsd:integer"
    }

    case class FloatValue(
      value: Float,
    ) extends Literal {
      override def toString = s"\"$value\"^xsd:float"
    }

    case class BooleanValue(
      value: Boolean,
    ) extends Literal {
      override def toString = s"\"$value\"^xsd:boolean"
    }

    case object NullValue extends Literal {
      override def toString = "\"\"^xsd:null"
    }

    case class StringLiteral(
      value: String,
      lang: String,
    ) extends Literal {
      override def toString = s"\"$value\"@$lang"
    }
  }

  sealed trait CastingValue {
    def isFormal: Boolean = this match {
      case CastingValue.F(_) => true
      case CastingValue.L(_) => true
      case _                 => false
    }

    override def toString = this match {
      case CastingValue.F(v) => s"<$v>"
      case CastingValue.T(v) => s"<?$v>"
      case CastingValue.L(v) => v.toString
      case CastingValue.N(v) => v.toString
    }

    def id: TopicId = this match {
      case CastingValue.F(v) => v
      case CastingValue.T(v) => v
      case CastingValue.L(v) => v.id
      case CastingValue.N(v) => v.id
    }
  }

  object CastingValue {
    case class L(value: Literal) extends CastingValue {
      override def toString = value.toString
    }

    case class N(value: Name) extends CastingValue {
      override def toString = value.toString
    }

    case class F(value: TopicId) extends CastingValue {
      // for formal frame references
      override def toString = s"<F#$value>"
    }

    case class T(value: TopicId) extends CastingValue {
      // mostly for frame references
      override def toString = s"<#$value>"
    }

    def fromTopic(t: Topic): Option[CastingValue] = t match {
      case n: Name                => Some(CastingValue.N(n))
      case f: Frame if f.isFormal => Some(CastingValue.F(f.id))
      case f: Frame               => Some(CastingValue.T(f.id))
      case l: Literal             => Some(CastingValue.L(l))
      case _                      => None
    }

  }

  sealed trait CastingKey {
    def isFormal: Boolean = this match {
      case CastingKey.F(_) => true
      case _               => false
    }

    override def toString = this match {
      case CastingKey.F(v) => s"<$v>"
      case CastingKey.T(v) => s"<?$v>"
      case CastingKey.N(v) => v.toString
    }

    def id: TopicId = this match {
      case CastingKey.F(v) => v
      case CastingKey.T(v) => v
      case CastingKey.N(v) => v.id
    }

    def encode: String = this match {
      case CastingKey.F(v) => s"F$v"
      case CastingKey.T(v) => s"T$v"
      case CastingKey.N(v) => s"N${v.id}"
    }
  }

  object CastingKey {
    case class N(value: Name) extends CastingKey {
      override def toString = value.toString
    }

    case class F(value: TopicId) extends CastingKey {
      // for formal frame references
      override def toString = s"<F#$value>"
    }

    case class T(value: TopicId) extends CastingKey {
      // mostly for frame references
      override def toString = s"<#$value>"
    }

    def decode(s: String): Option[CastingKey] = s.charAt(0) match {
      case 'F' => Some(CastingKey.F(s.substring(1)))
      case 'T' => Some(CastingKey.T(s.substring(1)))
      case 'N' => Some(CastingKey.N(Name(s.substring(1))))
      case _   => None
    }

    def equals(a: CastingKey, b: CastingKey): Boolean =
      a.encode == b.encode

    def fromTopic(t: Topic): Option[CastingKey] = t match {
      case n: Name                => Some(CastingKey.N(n))
      case f: Frame if f.isFormal => Some(CastingKey.F(f.id))
      case f: Frame               => Some(CastingKey.T(f.id))
      case _                      => None
    }
  }

  case class Frame(
    type_id: CastingKey,                   // a schema frame
    targets: Map[CastingKey, CastingValue],// key should be a Name
  ) extends Immutable {
    def names: Seq[Name] = (
      targets.keys.collect { case CastingKey.N(n) => n }
        ++ targets.values.collect { case CastingValue.N(n) => n }
        ++ Seq(type_id).collect { case CastingKey.N(n) => n }
    ).toSeq.distinct

    def subtopics: Seq[TopicId] = (
      targets.keys.collect {
        case CastingKey.F(n) => n
        case CastingKey.T(n) => n
      }
        ++ targets.values.collect {
          case CastingValue.F(n) => n
          case CastingValue.T(n) => n
        }
        ++ Seq(type_id).collect {
          case CastingKey.F(n) => n
          case CastingKey.T(n) => n
        }
    ).toSeq.distinct

    def isFormal: Boolean = type_id.isFormal && targets.forall(t => t._1.isFormal && t._2.isFormal)

    override def toString =
      s"[a $type_id ${if (targets.nonEmpty) ", " else ""}${targets.map { case (k, v) => s"$k $v" }.mkString(", ")}]"
  }

  object Topic {
    //    val discriminator                        = "_type"
    //    implicit val genDevConfig: Configuration =
    //      Configuration.default.withDiscriminator(discriminator)

    implicit val decoderCV: Decoder[CastingValue]  = deriveDecoder
    implicit val encoderCV: Encoder[CastingValue]  = deriveEncoder
    implicit val decoderCK: KeyDecoder[CastingKey] = new KeyDecoder[CastingKey] {
      override def apply(s: String): Option[CastingKey] = CastingKey.decode(s)
    }
    implicit val encoderCK: KeyEncoder[CastingKey] = new KeyEncoder[CastingKey] {
      override def apply(k: CastingKey): String = k.encode
    }

    implicit val decoder: Decoder[Topic] = deriveDecoder
    implicit val encoder: Encoder[Topic] = deriveEncoder
  }
}

object FirebaseApi extends api.Api {

  import typings.firebaseApp.mod.{initializeApp, FirebaseOptions}
  import typings.firebaseFirestore.mod._
  import org.scalajs.dom.console
  import scala.scalajs.js.JSConverters._
  import org.scalablytyped.runtime.StringDictionary

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def circeTopicConverter: FirestoreDataConverter[api.Topic] = js.Dynamic
    .literal(
      toFirestore = { (modelObject: WithFieldValue[api.Topic]) =>
        val topic = modelObject.asInstanceOf[api.Topic]
        val objR  = js.JSON.parse(topic.asJson.noSpaces)
        topic match {
          case frame: api.Frame =>
            objR.updateDynamic("names")(frame.names.map(_.id).toJSArray)
            objR.updateDynamic("topicIds")(frame.subtopics.toJSArray)
          case _                => ()
        }
        console.log("toFirestore3", objR)
        objR.asInstanceOf[DocumentData]
      },
      fromFirestore = { (snapshot: QueryDocumentSnapshot[DocumentData]) =>
        snapshot.data().flatMap { data =>
          data.remove("names")
          data.remove("topicIds")
          val decoded = decode[api.Topic](js.JSON.stringify(data))
          decoded.left.foreach(errors => console.warn("Could not decode: ", data, errors))
          decoded.toOption.getOrElse(js.undefined)
        }
      },
    )
    .asInstanceOf[FirestoreDataConverter[api.Topic]]

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
  // if (window.location.hostname == "localhost") {
  //   connectFirestoreEmulator(db, "localhost", 8080)
  // }

  val frameCollection                                   = "frames"
  val nameCollection                                    = "names"
  val topicConverter: FirestoreDataConverter[api.Topic] = circeTopicConverter

  def frameDoc(frameId: api.TopicId): DocumentReference[api.Topic] =
    doc(db, frameCollection, frameId).withConverter(topicConverter)

  def nameDoc(id: api.TopicId): DocumentReference[DocumentData] =
    doc(db, nameCollection, id)

  override def getTopic(id: api.TopicId): Future[Option[api.Topic]] = {
    val frameFuture = getDoc(frameDoc(id)).toFuture.map(_.data().toOption)
    val nameFuture  = getDoc(nameDoc(id)).toFuture.map(_.data().toOption.map(_ => api.Name(id)))
    val first       = Future.firstCompletedOf(List(frameFuture, nameFuture))
    first.flatMap {
      case Some(frame) => Future.successful(Some(frame))
      case None        =>
        for {
          frameData <- frameFuture
          nameData  <- nameFuture
        } yield nameData.orElse(frameData)
    }
  }

  override def addFrame(frame: api.Frame): Future[api.CId] = {
    frame.names.foreach(n =>
      setDoc(doc(db, nameCollection, n.id), js.Dynamic.literal().asInstanceOf[WithFieldValue[StringDictionary[Any]]]),
    )
    console.log("addFrame1", frame.id)
    var frameAsTopic = frame.asInstanceOf[api.Topic]
    try {
      var id = setDoc[api.Topic](frameDoc(frame.id), frameAsTopic).toFuture
      id.map(_ => frame.id)
    }
    catch {
      case e: Throwable =>
        console.error("addFrame2", e)
        throw e
    }
    // id.`then`[Unit](_ => console.log("addFrame2", frame.id), t => console.error("failed", t))
    // id.map(_ => console.log("addFrame succeeded"))
    // id.toFuture.map(_ => frame.id)
  }

  override def findFrames(queryString: String): Future[Seq[api.Frame]] = {
    val parts     = queryString.split("\\^").map(_.trim).filter(_.nonEmpty).toSeq
    println(parts)
    val partNames = parts.traverse(p => findNames(p))
    val results   = partNames.flatMap { s =>
      if (s.forall(_.nonEmpty))
        s.traverse(names =>
          getDocs(
            query(
              collection(db, frameCollection),
              where("names", WhereFilterOp.`array-contains-any`, names.map(_.id).toJSArray),
            ).withConverter(topicConverter),
          ).toFuture.map(snapshot =>
            snapshot.docs
              .map(_.data())
              .collect { case f: api.Frame => f }
              .toSeq,
          ),
        )
      else Future.successful(Seq.empty)
    }
    results.map(results =>
      if (results.isEmpty) Seq.empty
      else results.reduce(_ intersect _),
    )
  }

  override def findNames(queryString: String): Future[Seq[api.Name]] =
    getDocs(
      query(
        collection(db, nameCollection),
        // primitive prefix search
        where(
          "__name__",
          WhereFilterOp.GreaterthansignEqualssign,
          queryString,
        ),
        where(
          "__name__",
          WhereFilterOp.LessthansignEqualssign,
          s"${queryString}ÿ",
        ),
      ),
    ).toFuture.map(snapshot => snapshot.docs.map(doc => api.Name(doc.id)).toSeq)

  def findTopics(query: String): Future[Seq[api.Topic]] = {
    val lookForFrames = query.contains("^")
    if (lookForFrames) findFrames(query)
    else findNames(query)
  }
}
