// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.kore

import collection.JavaConverters._
import collection.LinearSeq
import KORE._
import scala.collection.AbstractIterator
import scala.collection.IterableLike
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

/* Interfaces */

sealed trait KORE // marker for KORE final classes added as a result of a discussion with Brandon about sealing

trait HasAttributes {
  def att: Attributes
}

trait K extends HasAttributes with Matcher with Rewriting {
  protected type This <: K

  def copy(att: Attributes): This
}

trait KItem extends K {
  //  def ~>(seq: KSequence): KSequence = this +: seq 
}

trait KLabel extends KLabelToString {
  val name: String
} // needs to be a KLabel to be able to have KVariables in its place

/* Data Structures */

case class KString(s: String) // just a wrapper to mark it

case class KApply(val klabel: KLabel, val klist: KList, val att: Attributes = Attributes())
  extends KAbstractCollection with KORE with KApplyMatcher with KApplyToString with Equals {
  type This = KApply

  val delegate: Iterable[K] = klist.delegate

  def newBuilder: Builder[K, KApply] = klist.newBuilder mapResult { new KApply(klabel, _, att) }

  override def canEqual(that: Any) = that match {
    case t: KApply => t.klabel == klabel
    case _ => false
  }

  def copy(att: Attributes): KApply = KApply(klabel, klist, att)
}

trait KToken extends KItem with KORE with KTokenMatcher with KTokenToString {
  val sort: Sort
  val s: KString
}

case class KUninterpretedToken(sort: Sort, s: KString, override val att: Attributes = Attributes())
  extends KToken with KTokenToString {
  type This = KToken
  def copy(att: Attributes): KToken = new KUninterpretedToken(sort, s, att)
}

case class ConcreteKLabel(name: String) extends KLabel {
  def apply(ks: K*) = new KApply(this, KList(ks))
}

case class KSequence(val klist: KList, val att: Attributes = Attributes())
  extends KAbstractCollection with KSequenceMatcher with KSequenceToString {
  type This = KSequence
  def delegate = klist.delegate

  def newBuilder: Builder[K, KSequence] = klist.newBuilder mapResult { new KSequence(_, att) }
  def copy(att: Attributes): KSequence = new KSequence(klist, att)

  override def canEqual(that: Any) = that.isInstanceOf[KSequence]
}

case class KVariable(name: String, att: Attributes = Attributes())
  extends KItem with KORE with KLabel with KVariableMatcher with KVariableToString {
  type This = KVariable
  def copy(att: Attributes): KVariable = new KVariable(name, att)
}

case class KRewrite(left: K, right: K, att: Attributes = Attributes())
  extends K with KORE with KRewriteMatcher with KRewriteToString {
  type This = KRewrite
  def copy(att: Attributes): KRewrite = new KRewrite(left, right, att)
  val klist = KList(left, right)
}

/*  Constructors */

object KToken {
  def apply(sort: Sort, s: KString, att: Attributes = Attributes()) =
    KUninterpretedToken(sort, s, att)

  def unapply(t: KToken) = Some((t.sort, t.s, t.att))
}

object KVariable {
  val it = this
}

object KSequence extends CanBuildKCollection {
  type This = KSequence

  def newBuilder = KList.newBuilder mapResult { new KSequence(_, Attributes()) }

  def fromJava(l: Array[K]) = new KSequence(KList(l: _*), Attributes())
}

object KRewrite {
  def apply(klist: KList, att: Attributes): KRewrite = klist match {
    case KList(left, right) => new KRewrite(left, right, att)
  }
  def apply(list: K*): KRewrite = apply(KList(list: _*), Attributes())
}

object EmptyK {
  def apply() = KSequence(KList(), Attributes())
}

object KLabel extends ConcreteKLabel("KLabel") {
  def apply(name: String) = ConcreteKLabel(name)
  def unapply(klabel: ConcreteKLabel): Option[String] = Some(klabel.name)
}

/* Constructors for matching KORE */

object KLabelWithQuotes {
  def apply(s: String) = {
    KLabel(s.stripPrefix("`").stripSuffix("`"))
  }
}

case class Sort(name: String) extends SortToString

object KORE {
  implicit def StringToKString(s: String) = KString(s)

  implicit def seqOfKsToKList(ks: Seq[K]) = KList(ks: _*)

  implicit def SymbolToKLabel(s: Symbol) = KLabel(s.name)
}
