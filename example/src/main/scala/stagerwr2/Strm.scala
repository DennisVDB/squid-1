package stagerwr2

import squid.lib.transparencyPropagating
import squid.lib.transparent
import squid.quasi.overloadEncoding
import squid.utils._
import squid.quasi.{phase, embed, dbg_embed}

import Strm.{loopWhile,consumeWhile}

/**
  * Created by lptk on 23/06/17.
  */
@embed
//case class Strm[A](producer: () => Producer[A]) {
class Strm[A](val producer: () => Producer[A]) {
  
  
  @phase('Impl)
  @transparencyPropagating
  def map[B](f: A => B): Strm[B] = Strm(() => {
    val p = producer()
    k => p(f andThen k)
  })
  
  /*
  //@phase('Impl)
  @phase('FlatMapImpl)
  @transparencyPropagating
  def flatMap[B](f: A => Strm[B]): Strm[B] = Strm(() => {
    val p = producer()
    var curBs: Option[Producer[B]] = None
    k => {
      var consumed = false
      loopWhile {
        if (!curBs.isDefined) p(as => curBs = Some(f(as).producer()))
        curBs.fold(false) { bs =>
          bs { b =>
            k(b)
            consumed = true
          }
          if (!consumed) { curBs = None; true } else false
        }
      }
    }
  })
  */
  @phase('Impl)
  @transparencyPropagating
  def flatMap[B](f: A => Strm[B]): Strm[B] = Strm(() => {
    Strm.doFlatMap(producer(), f andThen (_.producer()))
  })
  
  @phase('Impl)
  @transparencyPropagating
  def filter(pred: A => Bool): Strm[A] = Strm(() => {
    val p = producer()
    k => {
      var continue = true
      while(continue) {
        continue = false
        p { a => if (pred(a)) k(a) else continue = true }
      }
    }
  })
  
  @phase('Impl)
  @transparencyPropagating
  def take(n: Int): Strm[A] = Strm(() => {
    val p = producer()
    var taken = 0
    k => if (taken < n) { taken += 1; p(k) }
  })
  
  @phase('Sugar)
  @transparent
  def foreach(f: A => Unit): Unit = {
    consumeWhile(this) { a => f(a); true }
    
    //fold(())(a => println(a))
    
    //val p = producer()
    //var mayHaveLeft = true
    //while (mayHaveLeft) {
    //  mayHaveLeft = false
    //  p { a =>
    //    f(a)
    //    mayHaveLeft = true
    //  }
    //}
  }
  @phase('Sugar)
  def fold[B](z: B)(f: (B,A) => B): B = {
    var curVal = z
    foreach { a => curVal = f(curVal, a) }
    curVal
  }
  
  
  @phase('Sugar)  
  def zip[B](that: Strm[B]): Strm[(A,B)] = zipWith(that)(_ -> _)
  
  @phase('Impl)
  @transparencyPropagating
  def zipWith[B,C](that: Strm[B])(combine: (A,B) => C): Strm[C] = Strm(() => {
    val p0 = producer()
    val p1 = that.producer()
    k => p0 { a => p1 { b => k(combine(a, b)) } }
  })
  
  // for the paper:
  @phase('Impl) def csme(f: A => Bool) = {
    val p = producer(); loopWhile {
      var cont = false; p { a => cont = f(a) }; cont }}
  
}

object Strm {
  
  @phase('Impl)
  @transparencyPropagating
  def apply[A](producer: () => Producer[A]) = new Strm(producer)
  
  //def pullable(p: Producer[A])
  @phase('Impl)
  @transparencyPropagating
  def pullable[A](s: Strm[A]): Strm[A] = s
  @phase('Sugar)
  def pullStrm[A](p: () => Producer[A]) = pullable(Strm(p))
  
  @phase('Sugar)
  def range(from: Int, to: Int): Strm[Int] = pullStrm(() => {
    var i = from
    k => {
      val iv = i
      if (iv <= to) { k(iv); i = iv + 1 }
    }
  })
  // for the paper:
  @phase('Sugar) def fromRange(from: Int, to: Int) = range(from,to)
  @phase('Sugar) def fromRangeImpl(from: Int, to: Int) = range(from,to)
  @phase('Sugar) def fromArray[A](xs: Array[A]): Strm[A] = pullable(fromArrayImpl(xs)) // tried without pullable; made sure still works [30/06/17]
  @transparencyPropagating @phase('Impl) def fromArrayImpl[A](xs: Array[A]): Strm[A] = range(0, xs.length-1).map(x => xs(x))
  
  @phase('Sugar)
  def fromIndexed[A](xs: IndexedSeq[A]): Strm[A] = range(0, xs.length-1).map(xs)
  
  @phase('Sugar)
  def unfold[A,B](init:B)(next:B => Option[(A,B)]): Strm[A] = pullStrm(() => {
    var st = init
    k => {
      next(st).foreach { nst_b =>
        st = nst_b._2
        k(nst_b._1)
      }
    }
  })
  
  
  @phase('Impl)
  def consumeWhile[A](s: Strm[A])(f: A => Bool) = {
    val p = s.producer()
    loopWhile {
      var continue = false
      p { a => continue = f(a) }
      continue
    }
  }
  @phase('Sugar)
  def consumeWhileNested[A,B](s: Strm[A])(nest: A => Strm[B])(f: B => Bool) = {
    consumeWhile(s) { a =>
      var continue = false 
      consumeWhile(nest(a)) { b => continue = f(b); continue }
      continue 
    }
  }
  @phase('Sugar)
  def consumeWhileZipped[A,B](s: Strm[A], p: Producer[B])(f: (A,B) => Bool) = {
    consumeWhile(s) { a =>
      var continue = false
      p { b => continue = f(a,b) }
      continue
    }
  }
  
  // version where consumeWhile
  /*
  @phase('Impl)
  def consumeWhile[A](s: Strm[A])(f: A => Bool): Bool = {
    val p = s.producer()
    var continue = false
    loopWhile {
      continue = false
      p { a => continue = f(a) }
      continue
    }
    !continue
  }
  @phase('Sugar)
  def consumeWhileNested[A,B](s: Strm[A])(nest: A => Strm[B])(f: B => Bool) = {
    consumeWhile(s) { a =>
      consumeWhile(nest(a))(f)
    }
  }
  @phase('Sugar)
  def consumeWhileZipped[A,B](s: Strm[A], p: Producer[B])(f: (A,B) => Bool) = {
    consumeWhile(s) { a =>
      var continue = false
      p { b => continue = f(a,b) }
      continue
    }
  }
  */
  
  
  
  
  @phase('FlatMap)
  def doFlatMap[A,B](p: Producer[A], f: A => Producer[B]): Producer[B] = {
    var curBs: Option[Producer[B]] = None
    k => {
      var consumed = false
      loopWhile {
        if (!curBs.isDefined) p(a => curBs = Some(f(a)))
        curBs.fold(false) { as =>
          as { a =>
            k(a)
            consumed = true
          }
          if (!consumed) { curBs = None; true } else false
        }
      }
    }
  }
  
  
  
  @phase('Impl)
  @transparencyPropagating
  def conditionally[A](cond: Bool)(lhs: Strm[A], rhs: Strm[A]): Strm[A] = Strm(() => {
    val p0 = lhs.producer()
    val p1 = rhs.producer()
    k => if (cond) p0(k) else p1(k)
  })
  // doesn't fuse because we don't do anything to inline things like `if(...) lambda else lambda`
  //def conditionally[A](cond: Bool)(lhs: Strm[A], rhs: Strm[A]): Strm[A] = Strm(() => {
  //  if (cond) {
  //    val p0 = lhs.producer()
  //    k => p0(k)
  //  } else {
  //    val p1 = rhs.producer()
  //    k => p1(k)
  //  }
  //})
  
  
  
  
  
  @transparent
  @inline
  //@phase('LL) // FIXME auto lowering of by-name params
  def loopWhile(cnd: => Bool) = {
    while(cnd)()
  }
  
}