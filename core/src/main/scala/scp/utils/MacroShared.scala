package scp.utils

import scala.reflect.macros.whitebox.Context

trait MacroShared {
  type Ctx <: Context
  val Ctx: Ctx
  import Ctx.universe._
  
  
  
  def typeToTree(tpe: Type): Tree = {
    val r = tpe match {
      case TypeRef(pre, sym, Nil) =>
        TypeTree(tpe)
      case TypeRef(pre, sym, args) =>
        //AppliedTypeTree(Ident(sym.name),
        //  args map { x => typeToTree(x) })
        TypeTree(tpe)
      case AnnotatedType(annotations, underlying) =>
        typeToTree(underlying)
      case _ => TypeTree(tpe)
    }
    //println(s"typeToTree($tpe) = ${showCode(r)}")
    r
  }
  
  
  object ScalaRepeated {
    
    private val q"def f(x:$stupidFuck[Int])" = q"def f(x:Int*)"
    
    def apply(tp: Tree): Tree = tq"$stupidFuck[$tp]"
    
    def unapply(tp: Tree): Option[Tree] = q"def f(x:$tp)" match {
      case q"def f(x:$t*)" => Some(t)
      case _ => None
    }
    
  }
  
  
  implicit class TreeOps(private val self: Tree) {
    def transform(pf: PartialFunction[Tree, Tree]) =  {
      new Transformer {
        override def transform(x: Tree) =
          if (pf isDefinedAt x) pf(x)
          else super.transform(x)
      } transform self
    }
    def transformRec(rec_pf: (Tree => Tree) => PartialFunction[Tree, Tree]) = transformer(rec_pf)(self)
  }
  
  def transformer(rec_pf: (Tree => Tree) => PartialFunction[Tree, Tree]) = {
    new Transformer {
      val pf: PartialFunction[Tree, Tree] = rec_pf(transform)
      override def transform(x: Tree) =
        if (pf isDefinedAt x) pf(x)
        else super.transform(x)
    } transform _
  }
  
  
  
  // Debugging
  
  def showAttrs(s: Symbol) =
    s"""$s { typeSignature: ${s.typeSignature}, isTerm: ${s.isTerm}, isClass: ${s.isClass}, isPackageClass: ${s.isPackageClass}, isPackage: ${s.isPackage}, isStatic: ${s.isStatic}, isModule: ${s.isModule}, isModuleClass: ${s.isModuleClass} }""" +
    {val c = s.companion; if (c == NoSymbol) "" else
    s"""\ncompanion $c { typeSignature: ${c.typeSignature}, isTerm: ${s.isTerm}, isClass: ${c.isClass}, isPackageClass: ${c.isPackageClass}, isPackage: ${c.isPackage}, isStatic: ${c.isStatic}, isModule: ${c.isModule}, isModuleClass: ${c.isModuleClass} }"""}
       //|$s:""".stripMargin
  
  
  def assume(cond: Boolean, msg: String = null) =
    if (!cond) Ctx.warning(Ctx.enclosingPosition, "An assumption was violated. Please report this to the quasiquote maintainers."+(
        if (msg != null) s"[$msg]" else ""
      ))
  
}
