package web.client

import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalajs.dom.window.localStorage

import scala.collection.mutable

class LocalStorageMap {
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

}
