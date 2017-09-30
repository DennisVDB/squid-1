package squid
package ir.fastanf

import utils._

/* Note: may not be a good idea: because of this and union-find symbols, symbols have to have a unique constant hash code,
 * and thus things like `x+y` and `a+b` will have the same hash code (where `x`, `y`, `a`, `b` are distinct symbols)...
 * OTOH, if hash codes were not constant it would make no sense to use HashMap/Set with trees as keys anyways...
 * so really, union-find symbols, while extermely helpful, force us to make `x+y` and `a+b` share the same hash code. */
trait CachedHashCode { 
  private var cachedHashCode = Int.MinValue
  override def hashCode = if (cachedHashCode == Int.MinValue) {
    val h = super.hashCode()
    cachedHashCode = h
    h
  } else cachedHashCode
}

abstract trait FlatOpt[+T] {
  def isEmpty: Boolean
  def get: T
}
abstract trait FlatSom[+T] { self: T =>
  def isEmpty: Boolean = false
  def get: T = this
  def som: FlatOpt[this.type] = this.asInstanceOf[FlatOpt[this.type]] // explain why safe?
}
case object Non extends FlatOpt[Nothing] {
  def isEmpty: Boolean = true
  def get: Nothing = lastWords(".get on emtpy option")
}
object Som {
  def apply[T](x: FlatSom[T]): FlatOpt[x.type] = x.som
  def unapply[T](x: FlatOpt[T]) = x
}

/** Trivial expression, that can be used as arguments */
sealed abstract class Def extends DefOption with DefOrTypeRep with FlatSom[Def] {
  def fold[R](df: Def => R, typeRep: TypeRep => R): R = df(this)
}

/** Expression that can be used as an argument or result; this includes let bindings. */
sealed abstract class Rep extends RepOption with ArgumentList with FlatSom[Rep] {
  def typ: TypeRep
  def * = SplicedArgument(this)
}

final case class Constant(value: Any) extends Rep with CachedHashCode {
  lazy val typ = value match { // TODO impl and rm lazy
    case _ => lastWords(s"Not a valid constant: $value")
  }
}

// TODO intern objects
final case class StaticModule(fullName: String) extends Rep with CachedHashCode {
  lazy val typ = ???
}

// TODO make sure this does not generate a field for `base`
//  if it does, perhaps use `private[this] implicit val`?
final case class Ascribe(self: Rep, typ: TypeRep)(implicit base: FastANF) extends Rep with CachedHashCode {
  import base.TypingRepOps
  require(!(self.typ =:= typ))
  assert(self.typ <:< typ) // TODO use 'soft assert' that logs warning if false but does not crash
}

trait MethodApp extends Def {
  def self: Rep
  def mtd: MethodSymbol
  def targs: List[TypeRep]
  def argss: ArgumentLists
  def typ: TypeRep
  override def toString = s"$self.${mtd.name}${argss.toArgssString}"
}
object MethodApp {
  // TODO: convert fun apps that apply on one-shot lambdas to let-bindings...
  def apply(self: Rep, mtd: MethodSymbol, targs: List[TypeRep], argss: ArgumentLists, typ: TypeRep)(implicit base: FastANF): MethodApp = {
    // TODO make sure we're not putting let bindings in argument position!
    mtd match {
      case base.FunApp => App(self, argss.asSingleArg)
      case _ => SimpleMethodApp(self: Rep, mtd: MethodSymbol, targs: List[TypeRep], argss)(typ)
    }
  }
}
final case class SimpleMethodApp protected(self: Rep, mtd: MethodSymbol, targs: List[TypeRep], argss: ArgumentLists)(val typ: TypeRep) 
  extends MethodApp with CachedHashCode

final case class App(fun: Rep, arg: Rep)(implicit base: FastANF) extends Def with MethodApp {
  val self = fun
  def mtd: MethodSymbol = base.`scala.Function1`.`method apply`.value
  def targs = Nil
  def argss = arg
  lazy val typ = fun.typ.asFunType.map(_._2).getOrElse(lastWords(s"Application on a non-function type `${fun.typ}`"))
}

/** To avoid useless wrappers/boxing in the most common cases, we have this scheme for argument lists:
  *   Scala example | syntax                      | representation
  *   ------------------------------------------------------------
  *   foo           | NoArgumentLists             | NoArgumentLists
  *   foo()         | NoArguments                 | NoArguments
  *   foo()()       | NoArguments ~~: NoArguments | ArgumentListCons(NoArguments, NoArguments)
  *   foo(a)        | a                           | a
  *   foo(a,b)      | a ~: b                      | ArgumentCons(a, b)
  *   foo(a)(b)     | a ~~: b                     | ArgumentListCons(a, b)
  *   foo(a,b)(c)   | a ~: b ~~: c                | ArgumentListCons(ArgumentCons(a, b), c)
  *   foo(a,b,c:_*) | a ~: b ~: c.*               | ArgumentCons(a, ArgumentCons(b, SplicedArgument(c)))
  *    etc.
  */
sealed trait ArgumentLists extends CachedHashCode {
  
  /** May fail; only call if certain that there is only one argument. */
  def asSingleArg: Rep = this.asInstanceOf[Rep]
  
  def ~~: (as: ArgumentList): ArgumentLists = ArgumentListCons(as, this)
  
  def toArgssString = this match {
    case NoArgumentLists => ""
    case NoArguments => s"()"
    case r: Rep => s"($r)"
    case _ => ??? // TODO
  }
}
final case object NoArgumentLists extends ArgumentLists {
  override def ~~: (as: ArgumentList): ArgumentLists = as
}
sealed trait ArgumentList extends ArgumentLists {
  def ~: (a: Rep): ArgumentList = ArgumentCons(a, this)
}
final case object NoArguments extends ArgumentList {
  override def ~: (a: Rep): ArgumentList = a
}
final case class SplicedArgument(arg: Rep) extends ArgumentList // Q: can make extend AnyVal? requires making all upper traits universal)
final case class ArgumentCons(head: Rep, tail: ArgumentLists) extends ArgumentList
final case class ArgumentListCons(head: ArgumentList, tail: ArgumentLists) extends ArgumentLists


trait Binding extends SymbolParent {
  val name: String
  def bound: Symbol
  def boundType: TypeRep
}
class LetBinding(val name: String, val bound: Symbol, val value: Def, private var _body: Rep) extends Rep with Binding {
  def body = _body
  def body_= (newBody: Rep) = _body = newBody
  def boundType = value.typ
  def typ = body.typ
  override def toString: String = s"val $bound = $value; $body"
}
class Lambda(val name: String, val bound: Symbol, val boundType: TypeRep,  val body: Rep)(implicit base: FastANF) extends Def with Binding {
  val typ: TypeRep =  base.funType(boundType, body.typ)
  override def toString: String = s"($bound: $boundType) => $body"
}

/** Currently used mainly for reification. */ // Note: could intern these objects
class UnboundSymbol(override val name: String, val boundType: TypeRep) extends Symbol with Binding {
  protected var _parent: SymbolParent = this
  override def typ = boundType
  val bound = this
  def defOrTyp: DefOrTypeRep = boundType
  override def toString: String = if (owner eq this) "?"+super.toString else super.toString
}

sealed trait SymbolParent { def typ: TypeRep }

abstract class Symbol extends Rep with SymbolParent {
  protected var _parent: SymbolParent
  def rebind(d: SymbolParent) = {
    // TODO make sure we don't do wrong rebindings? (to define)
    _parent = d
  }
  def representative: Symbol = owner.bound
  def owner: Binding = _parent match {
    case bnd: Binding =>
      assert(bnd.bound eq this)
      bnd
    case parent: Symbol =>
      val bnd = parent.owner
      _parent = bnd
      bnd
  }
  def typ = owner.boundType
  def dfn: DefOption = owner match {
    case bnd: LetBinding => bnd.value
    case _ => Non
  }
  def name = owner.name
  override def hashCode = 1337 // needs to have constant hashCode because it will be part of trees with cached hashCode
  override def equals(that: Any): Bool = that match {
    case s: Symbol => s.representative eq representative
    case _ => false
  }
  override def toString: String = s"${owner.name}#${System.identityHashCode(representative)}"
}

