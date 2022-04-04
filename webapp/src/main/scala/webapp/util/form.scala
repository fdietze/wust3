package webapp.util

import outwatch._
import outwatch.dsl._
import colibri._
import org.scalajs.dom.console
import org.scalajs.dom.HTMLInputElement

import scala.deriving.Mirror
import scala.deriving._
import scala.compiletime.{constValueTuple, erasedValue, summonInline}

package object form {
  // TODO: recursive case classes
  // TODO: List instead of only Seq

  trait Form[T] {
    def form(subject: Subject[T]): VNode
    def default: T
  }

  inline def summonAll[A <: Tuple]: List[Form[_]] =
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Form[t]] :: summonAll[ts]
    }

  def toTuple(xs: List[_], acc: Tuple): Tuple = xs match {
    case Nil      => acc
    case (h :: t) => h *: toTuple(t, acc)
  }

  object Form {

    inline given derived[A](using m: Mirror.Of[A]): Form[A] = {
      lazy val instances = summonAll[m.MirroredElemTypes]
      val labels         = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]

      // type ElemEditors = Tuple.Map[m.MirroredElemTypes, Editor]
      // val elemEditors = summonAll[ElemEditors].toList.asInstanceOf[List[Editor[Any]]]
      // val containers = labels.zip(elemEditors) map { (label, editor) => editor.container(label) }

      inline m match {
        case s: Mirror.SumOf[A]     => deriveSum(s, instances, labels)
        case p: Mirror.ProductOf[A] => deriveProduct(p, instances, labels)
      }
    }

    def deriveSum[A](s: Mirror.SumOf[A], instances: => List[Form[_]], labels: List[String]): Form[A] = {
      new Form[A] {
        def default: A =
          instances.head
            .asInstanceOf[Form[A]]
            .default

        def form(subject: Subject[A]) = {
          val labelToInstance: Map[String, Form[A]] =
            instances.zip(labels).map { case (instance, label) => label -> instance.asInstanceOf[Form[A]] }.toMap

          def labelForValue(value: A): String = {
            value.getClass.getSimpleName.split('$').head
          }

          div(
            select(
              cls := "select",
              instances.zip(labels).map { case (instance, label) =>
                option(
                  label,
                  selected <-- subject.map(x => labelForValue(x) == label),
                )
              },
              onChange.value.map(label => labelToInstance(label).default) --> subject,
            ),
            subject.map { value =>
              val label = labelForValue(value)
              labelToInstance(label).form(subject)
            },
          )
        }
      }
    }

    def deriveProduct[A](
      p: Mirror.ProductOf[A],
      instances: => List[Form[_]],
      labels: List[String],
    ): Form[A] =
      new Form[A] {
        def default: A =
          p.fromProduct(
            toTuple(instances.map(_.default), EmptyTuple),
          )

        def form(subject: Subject[A]) = {
          def listToTuple[T](l: List[T]): Tuple = l match {
            case x :: rest => x *: listToTuple(rest)
            case Nil       => EmptyTuple
          }

          val x: Subject[Seq[Any]] =
            subject
              .imapSubject[Seq[Any]](x => p.fromProduct(listToTuple(x.toList)))(
                _.asInstanceOf[Product].productIterator.toList,
              )

          val subjects = x.sequence

          div(
            cls := "pl-4",
            subjects.map { subjects =>
              instances.zip(subjects).zip(labels).map { case ((instance, sub), label) =>
                val f = (instance.form _).asInstanceOf[(Subject[Any] => VNode)]
                div(cls := "flex flex-col justify-start", div(label, cls := "w-24"), div(f(sub), cls := "mb-2"))
              }
            },
          )
        }
      }

  }

  given Form[String] with {
    def default                        = ""
    def form(subject: Subject[String]) =
      inputField(subject, inputTpe = "text", parse = str => str, toValue = value => value)
  }

  given Form[Int] with {
    def default                     = 0
    def form(subject: Subject[Int]) =
      inputField(
        subject,
        inputTpe = "number",
        parse = { case str if str.toIntOption.isDefined => str.toInt },
        toValue = value => value.toString,
      )
  }

  given Form[Boolean] with {
    def default                         = false
    def form(subject: Subject[Boolean]) =
      input(
        tpe := "checkbox",
        cls := "toggle",
        checked <-- subject,
        onClick.map { ev =>
          ev.target.asInstanceOf[HTMLInputElement].checked
        } --> subject,
      ),
  }

  given optionForm[T: Form]: Form[Option[T]] with {
    def default                           = None
    def form(subject: Subject[Option[T]]) = {
      val seqSubject: Subject[Seq[T]] = subject.imapSubject[Seq[T]](_.headOption)(_.toSeq)
      var innerBackup: T              = summon[Form[T]].default

      div(
        cls := "flex items-center",
        input(
          tpe := "checkbox",
          cls := "toggle",
          checked <-- subject.map(_.isDefined),
          onClick.map { ev =>
            ev.target.asInstanceOf[HTMLInputElement].checked
          }.map {
            case true  => Some(innerBackup)
            case false => None
          } --> subject,
        ),
        seqSubject.sequence.map(
          _.map(innerSub =>
            VDomModifier(summon[Form[T]].form(innerSub), managedFunction(() => innerSub.foreach(innerBackup = _))),
          ),
        ),
      )
    }
  }

  given seqForm[T: Form]: Form[Seq[T]] with {
    def default                        = Seq.empty
    def form(subject: Subject[Seq[T]]) = {
      div(
        subject.sequence.map(
          _.zipWithIndex.map { case (innerSub, i) =>
            div(
              summon[Form[T]].form(innerSub),
              button(
                "remove",
                cls := "btn",
                onClick.stopPropagation(subject).foreach { nowValue =>
                  subject.onNext(nowValue.patch(i, Nil, 1))
                },
              ),
            )
          },
        ),
        button(
          "add",
          cls := "btn",
          onClick(subject).foreach { nowValue =>
            subject.onNext(nowValue :+ summon[Form[T]].default)
          },
        ),
      )
    }
  }

  given mapForm[A: Form, B: Form]: Form[Map[A, B]] with {
    def default                           = Map.empty
    def form(subject: Subject[Map[A, B]]) = {
      val seqSubject = subject
        .imapSubject[Seq[(A, B)]](_.toMap)(_.toSeq)
      div(
        seqSubject.sequence
          .map(
            _.zipWithIndex.map { case (innerSub, i) =>
              val keySub: Subject[A]   = innerSub.lens[A](_._1)((kv, newK) => (newK, kv._2))
              val valueSub: Subject[B] = innerSub.lens[B](_._2)((kv, newV) => (kv._1, newV))
              div(
                cls := "flex",
                summon[Form[A]].form(keySub),
                "->",
                summon[Form[B]].form(valueSub),
                button(
                  "remove",
                  cls := "btn",
                  onClick.stopPropagation(seqSubject).foreach { nowValue =>
                    seqSubject.onNext(nowValue.patch(i, Nil, 1))
                  },
                ),
              )
            },
          ),
        button(
          "add",
          cls := "btn",
          onClick(seqSubject).foreach { nowValue =>
            seqSubject.onNext(nowValue :+ summon[Form[(A, B)]].default)
          },
        ),
      )
    }
  }

  def submitForm[T: Form](onSubmit: T => Unit)(using f: Form[T]) = {
    val subject = Subject.behavior(f.default)
    div(
      f.form(subject),
      button("submit", onClick(subject).foreach(onSubmit)),
    )
  }

  def inputField[T](
    subject: Subject[T],
    inputTpe: String,
    parse: PartialFunction[String, T],
    toValue: T => String,
  ) = input(
    tpe := inputTpe,
    value <-- subject.map(toValue),
    onInput.value.collect(parse) --> subject,
    cls := "input input-sm border-black",
  )
}
