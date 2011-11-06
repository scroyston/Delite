package ppl.delite.framework.ops

import java.io.{FileWriter, File, PrintWriter}

import scala.reflect.SourceContext
import scala.virtualization.lms.common._
import scala.virtualization.lms.internal.{GenericCodegen, GenericFatCodegen, GenerationFailedException}
import ppl.delite.framework.datastructures.DeliteArrayOpsExp
import ppl.delite.framework.Config
import ppl.delite.framework.extern.lib._
import ppl.delite.framework.collections.{EmitterFactory, ScalaEmitter}
import ppl.delite.framework.datastruct.scala.Bucket

//trait DeliteOpsExp extends BaseFatExp with EffectExp with VariablesExp with LoopsFatExp {
trait DeliteOpsExp extends BaseFatExp with EffectExp with VariablesExp with LoopsFatExp with IfThenElseFatExp
    with VariantsOpsExp with DeliteCollectionOpsExp with DeliteArrayOpsExp
    with OrderingOpsExp with CastingOpsExp with ImplicitOpsExp with WhileExp with StaticDataExp {
  
  

/*
  //may try this some time to wrap functions that are passed as case class args...
  case class FF[A,B](val x: Rep[A], val y: Rep[B])(val f: Rep[A]=>Rep[B])  
  type ===>[A,B] = FF[A,B]
  implicit def deliteFunc[A:Manifest,B:Manifest](f: Rep[A]=>Rep[B]): A ===> B = { val x = fresh[A]; val y = reifyEffects(f(x)); FF(x, y)(f) }
*/
  
  /**
   * The base type of the DeliteOp hierarchy.
   */
  /*sealed*/ trait DeliteOp[A] extends Def[A] {
    type OpType <: DeliteOp[A]
    def original: Option[(Transformer,Def[_])] = None // we should have type OpType, really but then it needs to be specified in mirror (why?)
    def copyOrElse[B](f: OpType => B)(e: => B): B = original.map(p=>f(p._2.asInstanceOf[OpType])).getOrElse(e)
    def copyTransformedOrElse[B](f: OpType => Exp[B])(e: => Exp[B]): Exp[B] = original.map(p=>p._1(f(p._2.asInstanceOf[OpType]))).getOrElse(e)

/*
    consider z1 = VectorPlus(a,b), which could be something like z1 = Block(z2); z2 = loop(a.size) { i => a(i) + b(i) }
    we might want to mirror z1 because we have changed z2.
    but since a and b are the same, if we use case class equality: 
    1) we end up with the same object z1
    2) we created a third object that creates a new loop but is immediately discarded
*/
    // GROSS HACK ALERT
    // This is a hack to (among other things) enable objects with non-structural equality 
    // (e.g. functions) as case class parameters. Otherwise cse won't work and we mirror either not 
    // enough or the world...
    // However, we don't want to infringe on cse in the normal IR construction case. That is, 2 calls
    // to X.t (creating to MatrixTrans(x) instances) should result in the second MatrixTrans(x) node 
    // being cse'd, even though it has a new impl symbol. Thus we change the meaning of equality 
    // based on whether we're mirroring or not.
    override def equals(x: Any): Boolean = (this,x) match {
      case (a: Product,b: Product) => 
        if (a.productPrefix == b.productPrefix) {
          val r1 = a.productIterator.toList == b.productIterator.toList
          val r2 = syms(a) == syms(b)
          lazy val inMirror = Thread.currentThread.getStackTrace.exists(_.getMethodName == "mirror")
          //if (r1 != r2)
            //printdbg("?== "+this+","+x + " is "+r1+"/"+r2+" syms "+syms(a)+"/"+syms(b))
          r1 && (!inMirror || r2)
        } else false
      case _ => super.equals(x)
    }
  }

  //sealed trait DeliteFatOp extends FatDef

  /**
   * A sequential task - will execute block in a single thread and respect any free variable dependencies inside it.
   *
   * @param  block   the task to execute; must be reified if it contains effectful operations!
   */
  class DeliteOpSingleTask[A](block0: => Exp[A], val requireInputs: Boolean = false) extends DeliteOp[A] {
    type OpType <: DeliteOpSingleTask[A]
    final lazy val block: Exp[A] = copyTransformedOrElse(_.block)(block0)
  }

  /**
   * A method call to an external library.
   */
  abstract class DeliteOpExternal[A:Manifest] extends DeliteOp[A] {
    type OpType <: DeliteOpExternal[A]
    def alloc: Exp[A]
    val funcName: String
    final lazy val allocVal: Exp[A] = copyTransformedOrElse(_.allocVal)(reifyEffects(alloc))
  }

  /**
   * The base class for most data parallel Delite ops.
   */
  abstract class DeliteOpLoop[A] extends AbstractLoop[A] with DeliteOp[A] {
    type OpType <: DeliteOpLoop[A]
    def copyBodyOrElse(e: => Def[A]): Def[A] = original.map(p=>mirrorLoopBody(p._2.asInstanceOf[OpType].body,p._1)).getOrElse(e)
    final lazy val v: Sym[Int] = copyTransformedOrElse(_.v)(fresh[Int]).asInstanceOf[Sym[Int]]
  }

  //case class DeliteOpFatLoop(val size: Exp[Int], val v: Sym[Int], val body: List[Def[Any]]) extends AbstractFatLoop with DeliteFatOp
  
  
  // for use in loops:

  case class DeliteForeachElem[A](
    func: Exp[A],
    sync: Exp[List[Any]]
    // TODO: this is sort of broken right now, re-enable when we figure out how to make this work without emitting dependencies twice
    //cond: List[Exp[Boolean]] = Nil
  ) extends Def[Unit]
  
  //case class DeliteCollectElem[A, CA <: DeliteCollection[A]]( 
  // functions on CA currently happen at generation-time, and are not type-checked
  case class DeliteCollectElem[A, CA]( 
    aV: Sym[DeliteArray[A]],
    alloc: Exp[CA],
    allocDataStructure: Exp[CA],
    func: Exp[A],
    cond: List[Exp[Boolean]] = Nil,
    emitterFactory: Option[EmitterFactory] = None
    // TODO: note that the alloc block right now directly references the size
    // which is not part of DeliteCollectElem instance. we might want to fix that 
  ) extends Def[CA] {
    def needsCombine = emitterFactory.map(_.needsCombine).getOrElse(cond.nonEmpty)
    def needsPostProcess = emitterFactory.map(_.needsPostProcess).getOrElse(cond.nonEmpty)
    def needsPostProcess2 = emitterFactory.map(_.needsPostProcess2).getOrElse(false)
    def needsSpecialCollect = emitterFactory.nonEmpty || cond.nonEmpty
    def needsSpecialExtra = emitterFactory.nonEmpty || cond.nonEmpty
    def needsSpecialInit = emitterFactory.nonEmpty || cond.nonEmpty
    def emitterScala = emitterFactory.map(_.scala)
  }

  def elemType(elem: DeliteCollectElem[_,_]) = elem.aV.Type.typeArguments(0)
  def elemType(elem: DeliteHashElem[_,_,_,_]) = manifest[Any] //KJB: hack to bypass struct logic for HashElem

  case class DeliteHashElem[K, V1, V2, CR](
    alloc: Exp[CR],
    func: Exp[(K, V1)],
    //inner: Def[V2], TODO
    convertToV2: Exp[V2], // TODO
    cond: List[Exp[Boolean]] = Nil,
    emitterFactory: Option[EmitterFactory] = None
    // TODO: note that the alloc block right now directly references the size
    // which is not part of DeliteCollectElem instance. we might want to fix that
  ) extends Def[CR] {
    def needsCombine = emitterFactory.map(_.needsCombine).getOrElse(cond.nonEmpty)
    def needsPostProcess = emitterFactory.map(_.needsPostProcess).getOrElse(cond.nonEmpty)
    def needsPostProcess2 = emitterFactory.map(_.needsPostProcess2).getOrElse(false)
    def needsSpecialCollect = emitterFactory.nonEmpty || cond.nonEmpty
    def needsSpecialExtra = emitterFactory.nonEmpty || cond.nonEmpty
    def needsSpecialInit = emitterFactory.nonEmpty || cond.nonEmpty
    def emitterScala = emitterFactory.map(_.scala)
  }

  
  case class DeliteReduceElem[A](
    func: Exp[A],
    cond: List[Exp[Boolean]] = Nil,
    zero: Exp[A],
    rV: (Sym[A], Sym[A]),
    rFunc: Exp[A],
    stripFirst: Boolean
  ) extends Def[A]

  case class DeliteReduceTupleElem[A,B](
    func: (Exp[A],Exp[B]),
    cond: List[Exp[Boolean]] = Nil,
    zero: (Exp[A],Exp[B]),
    rVPar: ((Sym[A], Sym[B]),(Sym[A], Sym[B])),
    rVSeq: ((Sym[A], Sym[B]),(Sym[A], Sym[B])),
    rFuncPar: (Exp[A],Exp[B]),
    rFuncSeq: (Exp[A],Exp[B]),
    stripFirst: Boolean
  ) extends Def[A]



  def loopBodyNeedsStripFirst[A](e: Def[A]) = e match {
    case e:DeliteReduceElem[_] => e.stripFirst
    case e:DeliteReduceTupleElem[_,_] => e.stripFirst
    case _ => false
  }
  
  def loopBodyNeedsCombine[A](e: Def[A]) = e match {
    case e:DeliteReduceElem[_] => true
    case e:DeliteReduceTupleElem[_,_] => true
    case e:DeliteCollectElem[_,_] => e.needsCombine
    case e:DeliteHashElem[_,_,_,_] => e.needsCombine
    case _ => false
  }  

  def loopBodyNeedsPostProcess[A](e: Def[A]) = e match {
    case e:DeliteCollectElem[_,_] => e.needsPostProcess
    case e:DeliteHashElem[_,_,_,_] => e.needsPostProcess
    case _ => false
  }  

  def loopBodyNeedsPostProcess2[A](e: Def[A]) = e match {
    case e:DeliteCollectElem[_,_] => e.needsPostProcess2
    case e:DeliteHashElem[_,_,_,_] => e.needsPostProcess2
    case _ => false
  }
  
  /**
   * A Conditional task - will emit a Conditional DEG node as well as kernels for the then and else clauses
   *
   * @param  cond    the condition of the Conditional
   * @param  thenp   the Then block to execute if condition is true
   * @param  elsep   the Else block to execute if condition is false
   */
  trait DeliteOpCondition[A] extends DeliteOp[A] {
    type OpType <: DeliteOpCondition[A]
    val cond: Exp[Boolean]
    val thenp: Exp[A]
    val elsep: Exp[A]
  }

  /**
   * An while loop - will emit an while loop DEG node as well as a kernel for the body
   *
   * @param  cond  condition expression, will be emitted as a kernel
   * @param  body   the body of the loop
   */
  trait DeliteOpWhileLoop extends DeliteOp[Unit] {
    type OpType <: DeliteOpWhileLoop
    val cond: Exp[Boolean]
    val body: Exp[Unit]
  }

  /**
   *  Delite parallel ops - represents common parallel execution patterns, most of which
   *  are represented by an underlying 'loop' abstraction. All Delite parallel ops must
   *  not mutate their inputs or any global state, and produce a single output, with the
   *  exception of DeliteOpForeach. DeliteOpForeach can only mutate shared state protected 
   *  by the 'sync' method; all other side-effects and writes must be disjoint (i.e., not
   *  have inter-iteration dependencies). In all cases, there is no ordering guarantee.
   *  
   *  Note that size is supplied explicitly to allow domain-specific pattern rewrites.
   *  
   *  One design question moving forward: fusing can handle much of the composition here
   *  automatically (e.g. MapReduce / ZipWithReduce), but we currently don't have a way
   *  to represent this fused thing as a single parallel op (we have to unroll it).
   *  It would be nice to be general, so that we could have e.g. a ZipZipReduce op that is
   *  automatically fused and still a single IR node. OpComposite?
   *  
   *  NOTE ABOUT ZERO: 
   *    the supplied zero parameter is required to have value equality *in the generated code*
   *    it will not be used unless the collection is empty or in a conditional reduce where the
   *    first (or more) conditions fail. In both cases, we never try to actually reduce a zero
   *    element - we only return it or use it as an initialization check.
   *  
   *    if stripFirst is set to false, i.e. for a mutable reduction, then the zero value is used
   *    to allocate the accumulator, and it IS used in the initial reduction.
   */
  
  abstract class DeliteOpGroupBy[A: Manifest,
                                 K: Manifest,
                                 V: Manifest,
                                 CA <: DeliteCollection[A]: Manifest,
                                 CV <: DeliteCollection[V]: Manifest,
                                 CR: Manifest]
  extends DeliteOpLoop[CR] {
    type OpType <: DeliteOpGroupBy[A, K, V, CA, CV, CR]
    
    // supplied by subclass
    val in: Exp[DeliteCollection[A]]
    //val size: Exp[Int] -- could be dc_size(in), but we want type-specific pattern matching to work
    def func: Exp[A] => Exp[(K, V)]
    def alloc: Exp[CR]
    def emitterFactory: Option[EmitterFactory]
    
    def convertToCV: Exp[Bucket[V]] => Exp[CV]
    
    final lazy val vC: Sym[Bucket[V]] = copyTransformedOrElse(_.vC)(fresh[Bucket[V]]).asInstanceOf[Sym[Bucket[V]]]
    
    // loop
    lazy val body: Def[CR] = copyBodyOrElse(DeliteHashElem[K,V,CV,CR](
      alloc = reifyEffects(this.alloc),
      func = reifyEffects(this.func(dc_apply(in, v))),
      convertToV2 = reifyEffects(this.convertToCV(vC)),
      emitterFactory = emitterFactory
    ))
  }
  

  abstract class DeliteOpMapLike[A:Manifest, CA:Manifest] extends DeliteOpLoop[CA] {
    type OpType <: DeliteOpMapLike[A,CA]

     def alloc: Exp[CA]
     def allocWithArray: Exp[DeliteArray[A]] => Exp[CA] = { data => val res = alloc; dc_unsafeSetData(res.asInstanceOf[Exp[DeliteCollection[A]]], data); res }
     def emitterFactory: Option[EmitterFactory] = None
     
     //final lazy val allocVal: Exp[CA] = copyTransformedOrElse(_.allocVal)(reifyEffects(alloc))
     final lazy val aV: Sym[DeliteArray[A]] = copyTransformedOrElse(_.aV)(fresh[DeliteArray[A]]).asInstanceOf[Sym[DeliteArray[A]]]
   }

  /**
   * Parallel map from DeliteCollection[A] => DeliteCollection[B]. Input functions can depend on free
   * variables, but they cannot depend on other elements of the input or output collection (disjoint access).
   *
   * @param  in    the input collection
   * @param  size  the size of the input collection
   * @param  func  the mapping function Exp[A] => Exp[B]
   * @param  alloc function returning the output collection. if it is the same as the input collection,
   *               the operation is mutable; (=> DeliteCollection[B]).
   */ 
  abstract class DeliteOpMap[A:Manifest,
                             B:Manifest, CB:Manifest]
    extends DeliteOpMapLike[B,CB] {
    type OpType <: DeliteOpMap[A,B,CB]

    // supplied by subclass
    val in: Exp[DeliteCollection[A]]
    //val size: Exp[Int] // could be dc_size(in), but we want type-specific pattern matching to work
    def func: Exp[A] => Exp[B]
    def alloc: Exp[CB]

    // loop
    lazy val body: Def[CB] = copyBodyOrElse(DeliteCollectElem[B, CB](
      aV = this.aV,
      alloc = reifyEffects(this.allocWithArray(aV)),
      allocDataStructure = reifyEffects(this.alloc),
      func = reifyEffects(this.func(dc_apply(in, v))),
      emitterFactory = emitterFactory
    ))
  }
  

  /**
   *  Currently conditionally appends values to buffers, which are concatenated in the combine stage.
   *  Note that it is also implicitly a Map-Filter (it accepts a mapping function). Should this be renamed? 
   */
  abstract class DeliteOpFilter[A:Manifest,
                                B:Manifest, CB:Manifest]
    extends DeliteOpMapLike[B,CB] {
    type OpType <: DeliteOpFilter[A,B,CB]

    // supplied by subclass
    val in: Exp[DeliteCollection[A]]
    //val size: Exp[Int] // could be dc_size(in), but we want type-specific pattern matching to work
    def func: Exp[A] => Exp[B]
    def alloc: Exp[CB]
    def cond: Exp[A] => Exp[Boolean] // does this need to be more general (i.e. a List?)

    // loop
    lazy val body: Def[CB] = copyBodyOrElse(DeliteCollectElem[B, CB](
      aV = this.aV,
      alloc = reifyEffects(this.allocWithArray(aV)),
      allocDataStructure = reifyEffects(this.alloc),
      func = reifyEffects(this.func(dc_apply(in,v))),
      cond = reifyEffects(this.cond(dc_apply(in,v)))::Nil,
      emitterFactory = emitterFactory
    ))
  }  
  
  
  /**
   * Parallel 2 element zipWith from (DeliteCollection[A],DeliteCollection[B]) => DeliteCollection[R].
   * Input functions can depend on free variables, but they cannot depend on other elements of the input or
   * output collection (disjoint access). 
   *
   * @param  inA   the first input collection
   * @param  inB   the second input collection
   * @param  size  the size of the collections (should be the same) 
   * @param  func  the zipWith function; ([Exp[A],Exp[B]) => Exp[R]
   * @param  alloc function returning the output collection. if it is the same as the input collection,
   *               the operation is mutable; (=> DeliteCollection[B]).
   */
  abstract class DeliteOpZipWith[A:Manifest,
                                 B:Manifest,
                                 R:Manifest, CR:Manifest]
    extends DeliteOpMapLike[R,CR] {
    type OpType <: DeliteOpZipWith[A,B,R,CR]
      
    // supplied by subclass   
    val inA: Exp[DeliteCollection[A]]
    val inB: Exp[DeliteCollection[B]]
    def func: (Exp[A], Exp[B]) => Exp[R]
    def alloc: Exp[CR]
    
    // loop
    lazy val body: Def[CR] = copyBodyOrElse(DeliteCollectElem[R, CR](
      aV = this.aV,
      alloc = reifyEffects(this.allocWithArray(aV)),
      allocDataStructure = reifyEffects(this.alloc),
      func = reifyEffects(this.func(dc_apply(inA,v), dc_apply(inB,v))),
      emitterFactory = None
    ))
  }
    
  abstract class DeliteOpReduceLike[A:Manifest] extends DeliteOpLoop[A] {
    type OpType <: DeliteOpReduceLike[A]
    final lazy protected val rV: (Sym[A],Sym[A]) = copyOrElse(_.rV)((reflectMutableSym(fresh[A]), fresh[A])) // TODO: transform vars??
    val mutable: Boolean = false
    // TODO: should reflectMutableSym only be called if we're actually mutating the accumulator?
  }
  
  /**
   * Parallel reduction of a DeliteCollection[A]. Reducing function must be associative.
   *
   * @param  in    the input collection
   * @param  size  the size of the input collection 
   * @param  zero  the "empty" value - must have value equality 
   * @param  func  the reduction function; ([Exp[A],Exp[A]) => Exp[A]. Must be associative.
   */
  abstract class DeliteOpReduce[A:Manifest] extends DeliteOpReduceLike[A] {
    type OpType <: DeliteOpReduce[A]
      
    // supplied by subclass   
    val in: Exp[DeliteCollection[A]]
    val zero: Exp[A] 
    def func: (Exp[A], Exp[A]) => Exp[A]
    
    // loop    
    lazy val body: Def[A] = copyBodyOrElse(DeliteReduceElem[A](
      func = reifyEffects(dc_apply(in,v)),
      zero = this.zero,
      rV = this.rV,
      rFunc = reifyEffects(this.func(rV._1, rV._2)),
      stripFirst = !isPrimitiveType(manifest[A]) && !this.mutable
    ))
  }
  
  
  /**
   * Parallel map-reduction from a DeliteCollection[A] => R. The map-reduce is composed, so no temporary collection
   * is instantiated to hold the result of the map.
   *
   * @param  in      the input collection
   * @param  size    the size of the input collection
   * @param  zero    the "empty" value - must have value equality  
   * @param  map     the mapping function; Exp[A] => Exp[R]
   * @param  reduce  the reduction function; ([Exp[R],Exp[R]) => Exp[R]. Must be associative.
   */
  abstract class DeliteOpMapReduce[A:Manifest,R:Manifest]
    extends DeliteOpReduceLike[R] {
    type OpType <: DeliteOpMapReduce[A,R]
    
    // supplied by subclass   
    val in: Exp[DeliteCollection[A]]
    val zero: Exp[R] 
    def map: Exp[A] => Exp[R]
    def reduce: (Exp[R], Exp[R]) => Exp[R]
    
    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceElem[R](
      func = reifyEffects(map(dc_apply(in,v))),
      zero = this.zero,
      rV = this.rV,
      rFunc = reifyEffects(reduce(rV._1, rV._2)),
      stripFirst = !isPrimitiveType(manifest[R]) && !this.mutable
    ))
  }
  
  // should this be folded into DeliteOpMapReduce (or into DeliteOpFilter)?
  abstract class DeliteOpFilterReduce[A:Manifest,R:Manifest]
    extends DeliteOpReduceLike[R] {
    type OpType <: DeliteOpFilterReduce[A,R]
    
    // supplied by subclass   
    val in: Exp[DeliteCollection[A]]
    val zero: Exp[R] 
    def func: Exp[A] => Exp[R]        
    def reduce: (Exp[R], Exp[R]) => Exp[R]
    def cond: Exp[A] => Exp[Boolean] // does this need to be more general (i.e. a List?)
    
    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceElem[R](
      func = reifyEffects(this.func(dc_apply(in,v))),
      cond = reifyEffects(this.cond(dc_apply(in,v)))::Nil,
      zero = this.zero,
      rV = this.rV,
      rFunc = reifyEffects(reduce(rV._1, rV._2)),
      stripFirst = !isPrimitiveType(manifest[R]) && !this.mutable
    ))
  }  

  // for sumIf ...
/*
  abstract class DeliteOpFilterReduceFold[A:Manifest,R:Manifest]
    extends DeliteOpLoop[R] {
    type OpType <: DeliteOpFilterReduceFold[A,R]

    // supplied by subclass
    val in: Exp[DeliteCollection[A]]
    val zero: Exp[R]
    def func: Exp[A] => Exp[R]
    def reduce: (Exp[R], Exp[R]) => Exp[R]
    def cond: Exp[A] => Exp[Boolean] // does this need to be more general (i.e. a List?)

    def accum: Exp[A] => Exp[R] //= (elem) => zero // given an element, return appropriate accum (i.e. vector of size)
    def step: (Exp[R], Exp[A]) => Exp[Unit] = (acc,elem) => ifThenElse(dc_apply(in,v))reduce(acc, func(elem))

      val z = dc_apply(in,v)
      if (cond(z)) {
        if (fresh)
          reduce(accum(z), func(z))
        else
          reduce(acc, func(z))
      }



    def step: (Exp[R], Exp[A]) => Exp[Unit] = (acc,elem) => ifThenElse(dc_apply(in,v))reduce(acc, func(elem))

    final lazy protected val aV: Sym[A] = copyOrElse(_.aV)(fresh[A]) // TODO: transform vars??
    final lazy protected val fV: (Sym[A],Sym[A]) = copyOrElse(_.fV)((reflectMutableSym(fresh[A]), fresh[A])) // TODO: transform vars??
    final lazy protected val rV: (Sym[A],Sym[A]) = copyOrElse(_.rV)((reflectMutableSym(fresh[A]), fresh[A])) // TODO: transform vars??

    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceStepElem[R](
      func = reifyEffects(this.func(dc_apply(in,v))),
      cond = reifyEffects(this.cond(dc_apply(in,v)))::Nil,
      zero = this.zero,
      aV =
      sFunc = reifyEffects(fold(fV._1, fV._2)),
      rV = this.rV,
      rFunc = reifyEffects(reduce(rV._1, rV._2)),
      stripFirst = false
    ))
  }
*/
  // reduce tuple in parallel, return first component
  abstract class DeliteOpFilterReduceFold[R:Manifest]
    extends DeliteOpLoop[R] {
    type OpType <: DeliteOpFilterReduceFold[R]

    // supplied by subclass
    val in: Exp[DeliteCollection[Int]]
    val zero: (Exp[R], Exp[Int])
    def func: Exp[Int] => (Exp[R],Exp[Int])
    def reducePar: ((Exp[R],Exp[Int]), (Exp[R],Exp[Int])) => (Exp[R],Exp[Int])
    def reduceSeq: ((Exp[R],Exp[Int]), (Exp[R],Exp[Int])) => (Exp[R],Exp[Int]) // = reduce

    val mutable: Boolean = false
    final lazy protected val rVPar: ((Sym[R],Sym[Int]),(Sym[R],Sym[Int])) = copyOrElse(_.rVPar)(((reflectMutableSym(fresh[R]),reflectMutableSym(fresh[Int])), (fresh[R],fresh[Int])))
    final lazy protected val rVSeq: ((Sym[R],Sym[Int]),(Sym[R],Sym[Int])) = copyOrElse(_.rVSeq)(((reflectMutableSym(fresh[R]),reflectMutableSym(fresh[Int])), (fresh[R],fresh[Int])))
    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceTupleElem[R,Int](
      func = /*reifyEffects*/func(dc_apply(in,v)), //FIXME: tupled reify
      zero = this.zero,
      rVPar = this.rVPar,
      rVSeq = this.rVSeq,
      rFuncPar = /*reifyEffects*/(reducePar(rVPar._1, rVPar._2)),  //FIXME: tupled reify
      rFuncSeq = /*reifyEffects*/(reduceSeq(rVSeq._1, rVSeq._2)),  //FIXME: tupled reify
      stripFirst = false //(!isPrimitiveType(manifest[R]) || !isPrimitiveType(manifest[R])) && !this.mutable
    ))
  }


  /**
   * Parallel zipWith-reduction from a (DeliteCollection[A],DeliteCollection[A]) => R. The map-reduce is composed,
   * so no temporary collection is instantiated to hold the result of the map.
   *
   * @param  inA     the first input collection
   * @param  inB     the second input collection
   * @param  size    the size of the input collections (should be the same)
   * @param  zero    the "empty" value - must have value equality  
   * @param  zip     the zipWith function; reified version of (Exp[A],Exp[B]) => Exp[R]
   * @param  reduce  the reduction function; reified version of ([Exp[R],Exp[R]) => Exp[R]. Must be associative.
   */
  abstract class DeliteOpZipWithReduce[A:Manifest,B:Manifest,R:Manifest]
    extends DeliteOpReduceLike[R] {
    type OpType <: DeliteOpZipWithReduce[A,B,R]
      
    // supplied by subclass   
    val inA: Exp[DeliteCollection[A]]
    val inB: Exp[DeliteCollection[B]]
    val zero: Exp[R]
    def zip: (Exp[A], Exp[B]) => Exp[R]
    def reduce: (Exp[R], Exp[R]) => Exp[R]
    
    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceElem[R](
      func = reifyEffects(zip(dc_apply(inA,v), dc_apply(inB,v))),
      zero = this.zero,
      rV = this.rV,
      rFunc = reifyEffects(reduce(rV._1, rV._2)),
      stripFirst = !isPrimitiveType(manifest[R]) && !this.mutable
    ))
  }
  
  // reduce tuple in parallel, return first component
  abstract class DeliteOpZipWithReduceTuple[A:Manifest,B:Manifest,R:Manifest,Q:Manifest]
    extends DeliteOpLoop[R] {
    type OpType <: DeliteOpZipWithReduceTuple[A,B,R,Q]

    // supplied by subclass
    val inA: Exp[DeliteCollection[A]]
    val inB: Exp[DeliteCollection[B]]
    val zero: (Exp[R], Exp[Q])
    def zip: (Exp[A], Exp[B]) => (Exp[R],Exp[Q])
    def reduce: ((Exp[R],Exp[Q]), (Exp[R],Exp[Q])) => (Exp[R],Exp[Q])

    val mutable: Boolean = false
    final lazy protected val rV: ((Sym[R],Sym[Q]),(Sym[R],Sym[Q])) = copyOrElse(_.rV)(((reflectMutableSym(fresh[R]),reflectMutableSym(fresh[Q])), (fresh[R],fresh[Q]))) // TODO: transform vars??
    // loop
    lazy val body: Def[R] = copyBodyOrElse(DeliteReduceTupleElem[R,Q](
      func = /*reifyEffects*/(zip(dc_apply(inA,v), dc_apply(inB,v))), //FIXME: tupled reify
      zero = this.zero,
      rVPar = this.rV,
      rVSeq = this.rV,
      rFuncPar = /*reifyEffects*/(reduce(rV._1, rV._2)),  //FIXME: tupled reify
      rFuncSeq = /*reifyEffects*/(reduce(rV._1, rV._2)),  //FIXME: tupled reify
      stripFirst = (!isPrimitiveType(manifest[R]) || !isPrimitiveType(manifest[R])) && !this.mutable
    ))
  }  

  /**
   * Parallel foreach from DeliteCollection[A] => Unit. Input functions must specify any free variables that it
   * requires are protected (e.g. locked before chunk execution) using the sync list.
   *
   * @param  in     the input collection
   * @param  size   the size of the input collection  
   * @param  func   the foreach function Exp[A] => Exp[Unit]
   * @param  sync   a function from an index to a list of objects that should be locked, in a total ordering,
   *                prior to chunk execution, and unlocked after; (Exp[Int] => Exp[List[Any]])
   */  
  abstract class DeliteOpForeach[A:Manifest] extends DeliteOpLoop[Unit] { //DeliteOp[Unit] {    
    type OpType <: DeliteOpForeach[A]
    val in: Exp[DeliteCollection[A]]
    val size: Exp[Int]
    def func: Exp[A] => Exp[Unit]
    def sync: Exp[Int] => Exp[List[Any]] // TODO: need to extend runtime to do something with sync in multiloop
    
    final lazy val i: Sym[Int] = copyOrElse(_.i)(fresh[Int])
    lazy val body: Def[Unit] = copyBodyOrElse(DeliteForeachElem(
      func = reifyEffects(this.func(dc_apply(in,v))),
      sync = reifyEffects(this.sync(i))
    ))
  }

  abstract class DeliteOpIndexedLoop extends DeliteOpLoop[Unit] {
    type OpType <: DeliteOpIndexedLoop
    val size: Exp[Int]
    def func: Exp[Int] => Exp[Unit]
    
    lazy val body: Def[Unit] = copyBodyOrElse(DeliteForeachElem(
      func = reifyEffects(this.func(v)),
      sync = unit(List())
    ))
  }
    
  /**
   * Deprecated Delite ops  
   */
  
  /*
   * We temporarily need these two versions of 'foreach' until we get 'sync' working with the new version.
   */

  @deprecated("DeliteOpForeach2 should only be used if sync is required. It will be removed as soon as sync works with DeliteOpForeach", "") // TODO: swap names with DeliteOpForeach
  abstract class DeliteOpForeach2[A,C[X] <: DeliteCollection[X]]() extends DeliteOp[Unit] with DeliteOpMapLikeWhileLoopVariant {
    val in: Exp[C[A]]
    val v: Sym[A]
    val func: Exp[Unit]
    val i: Sym[Int]
    val sync: Exp[List[Any]]

    lazy val alloc = Const()
    lazy val variant = {
      implicit val mA: Manifest[A] = v.Type.asInstanceOf[Manifest[A]]
      reifyEffects {
        var index = var_new(unit(0))
        var vs = var_new(unit(null).asInstanceOfL[A])
        while (index < in.size) {
          vs = in(index)
          rebind(v.asInstanceOf[Sym[A]], ReadVar(vs))
          //reflectEffect(findDefinition(func.asInstanceOf[Sym[Unit]]).get.rhs)
          var x = var_new(func)
          index += 1
        }
        alloc
      }
    }
  }

  // TODO: should we make all DeliteOps be boundable? This is probably not the right way to do this anyways.
  @deprecated("DeliteOpForeachBounded should only be used if sync is required. It will be removed as soon as sync works with DeliteOpForeach", "")
  abstract class DeliteOpForeachBounded[B,A <: B,C[X <: B] <: DeliteCollection[X]] extends DeliteOp[Unit] {
    val in: Exp[C[A]]
    val v: Sym[A]
    val func: Exp[Unit]
    val i: Sym[Int]
    val sync: Exp[List[Any]]
  }


  

  ///////////////////////////
  // effects + other helpers

  // used by delite code generators to handle nested delite ops TR: shouldn't this be part of the codegen hierarchy?
  var deliteKernel: Boolean = false
  var deliteResult: Option[List[Exp[Any]]] = None
  var deliteInputs: List[Sym[Any]] = Nil

  var simpleCodegen: Boolean = false// try to generate more readable code

  // TODO: move to lms? TR: will that actually work? it looks pretty unsafe to rebind syms
  def rebind(sym: Sym[Any], rhs: Def[Any]) = createDefinition(sym, rhs).rhs

  def summarizeBody[A](d: Def[A]) = d match {
    case e: DeliteForeachElem[_] => summarizeEffects(e.func).star
    case e: DeliteCollectElem[_,_] => summarizeEffects(e.func).star
    //case e: DeliteReduceElem[_] => (summarizeEffects(e.func) andThen summarizeEffects(e.rFunc)).star
    case e: DeliteReduceElem[_] => 
      // explicitly remove writes to the accumulator -- can we generalize this somehow?
      def clean(xs: List[Sym[Any]]) = xs.filterNot(_ == e.rV._1)
      val ef = summarizeEffects(e.func)
      val er = summarizeEffects(e.rFunc)
      val er2 = er.copy(mayRead = clean(er.mayRead), mstRead = clean(er.mstRead), 
                        mayWrite = clean(er.mayWrite), mstWrite = clean(er.mstWrite))
      (ef andThen er2).star // not 100% correct
    case e: DeliteReduceTupleElem[_,_] =>
      // explicitly remove writes to the accumulator -- can we generalize this somehow?
      def cleanP(xs: List[Sym[Any]]) = xs.filterNot(x => x == e.rVPar._1._1 || x == e.rVPar._1._2)
      def cleanS(xs: List[Sym[Any]]) = xs.filterNot(x => x == e.rVSeq._1._1 || x == e.rVSeq._1._2)
      val ef = summarizeEffects(e.func._1) andAlso summarizeEffects(e.func._2)
      val erp = summarizeEffects(e.rFuncPar._1) andAlso summarizeEffects(e.rFuncPar._2)
      val ers = summarizeEffects(e.rFuncSeq._1) andAlso summarizeEffects(e.rFuncSeq._2)
      val erp2 = erp.copy(mayRead = cleanP(erp.mayRead), mstRead = cleanP(erp.mstRead),
                        mayWrite = cleanP(erp.mayWrite), mstWrite = cleanP(erp.mstWrite))
      val ers2 = ers.copy(mayRead = cleanS(ers.mayRead), mstRead = cleanS(ers.mstRead),
                        mayWrite = cleanS(ers.mayWrite), mstWrite = cleanS(ers.mstWrite))
      (ef andAlso erp2 andAlso ers2).star
  }
  
  // TODO: just to make refactoring easier in case we want to change to reflectSomething
  // def reflectPure[A:Manifest](x: Def[A]): Exp[A] = toAtom(x)

  // alternative: leave reflectPure as above and override toAtom...

  def reflectPure[A:Manifest](d: Def[A]): Exp[A] = d match {  // TODO: should take a SourceContext, too
    case x: DeliteOpLoop[_] =>
      val mutableInputs = readMutableData(d) //TODO: necessary or not??
      //val mutableInputs = Nil // readMutableData(d) TODO: necessary or not??
      val re = Read(mutableInputs)
      val be = summarizeBody(x.body)
      reflectEffect(d, re andAlso be)
    case x: DeliteOpSingleTask[_] =>      
      val mutableInputs = readMutableData(d) //TODO: necessary or not??
      //val mutableInputs = Nil // readMutableData(d) TODO: necessary or not??
      val re = Read(mutableInputs)
      val be = summarizeEffects(x.block)
      reflectEffect(d, re andAlso be)
    case _ => 
      toAtom(d)
  }

  // TBD: move logic from reflectPure (above) into reflectEffect?

  // HACK lazy val bites again: must make sure that block is evaluated!
  override def reflectEffect[A:Manifest](d: Def[A], u: Summary)(implicit ctx: SourceContext): Exp[A] = d match {
    case x: DeliteOpSingleTask[_] =>
      x.block
      super.reflectEffect(d,u)
    case x: DeliteOpLoop[_] =>
      x.body
      super.reflectEffect(d,u)
    case _ =>
      super.reflectEffect(d,u)
  }

  // what about this: enable?
  // override def reflectMutable[A:Manifest](d: Def[A]): Exp[A] = d match {
  //   case x: DeliteOpLoop[_] => 
  //     val mutableInputs = readMutableData(d)    
  //     val allocAndRead = Alloc() andAlso Read(mutableInputs)
  //     val be = summarizeBody(x.body)
  //     val z = reflectEffect(d, allocAndRead andAlso be)
  //     
  //     val mutableAliases = mutableTransitiveAliases(d)
  //     checkIllegalSharing(z, mutableAliases)      
  //     z
  //   case _ => 
  //     super.reflectMutable(d)
  // }
      

  //////////////
  // mirroring

  override def mirrorFatDef[A:Manifest](d: Def[A], f: Transformer): Def[A] = mirrorLoopBody(d,f) // TODO: cleanup

  def mirrorLoopBody[A](d: Def[A], f: Transformer): Def[A] = {
    d match {
      case e: DeliteCollectElem[a,ca] => 
        DeliteCollectElem[a,ca]( // need to be a case class for equality (do we rely on equality?)
          aV = f(e.aV).asInstanceOf[Sym[DeliteArray[a]]],
          alloc = f(e.alloc),
          allocDataStructure = f(e.allocDataStructure),
          func = f(e.func),
          cond = f(e.cond),
          emitterFactory = e.emitterFactory
        ).asInstanceOf[Def[A]]
      case e: DeliteForeachElem[a] => 
        DeliteForeachElem[a](
          func = f(e.func),
          sync = f(e.sync)
//          cond = f(e.cond)
        ).asInstanceOf[Def[A]] // reasonable?
      case e: DeliteReduceElem[a] => 
        DeliteReduceElem[a](
          func = f(e.func),
          cond = f(e.cond),
          zero = f(e.zero),
          rV = (f(e.rV._1).asInstanceOf[Sym[a]], f(e.rV._2).asInstanceOf[Sym[a]]), // need to transform bound vars ??
          rFunc = f(e.rFunc),
          stripFirst = e.stripFirst
        ).asInstanceOf[Def[A]]
      case e: DeliteReduceTupleElem[a,b] =>
        DeliteReduceTupleElem[a,b](
          func = (f(e.func._1),f(e.func._2)),
          cond = f(e.cond),
          zero = (f(e.zero._1),f(e.zero._2)),
          rVPar = ((f(e.rVPar._1._1).asInstanceOf[Sym[a]], f(e.rVPar._1._2).asInstanceOf[Sym[b]]),(f(e.rVPar._2._1).asInstanceOf[Sym[a]], f(e.rVPar._2._2).asInstanceOf[Sym[b]])), // need to transform bound vars ??
          rVSeq = ((f(e.rVSeq._1._1).asInstanceOf[Sym[a]], f(e.rVSeq._1._2).asInstanceOf[Sym[b]]),(f(e.rVSeq._2._1).asInstanceOf[Sym[a]], f(e.rVSeq._2._2).asInstanceOf[Sym[b]])), // need to transform bound vars ??
          rFuncPar = (f(e.rFuncPar._1),f(e.rFuncPar._2)),
          rFuncSeq = (f(e.rFuncSeq._1),f(e.rFuncSeq._2)),
          stripFirst = e.stripFirst
        ).asInstanceOf[Def[A]]
    }
  }
  

  //////////////
  // dependencies

  override def syms(e: Any): List[Sym[Any]] = e match { //TR TODO: question -- is alloc a dependency (should be part of result) or a definition (should not)???
                                                        // aks: answer -- we changed it to be internal to the op to make things easier for CUDA. not sure if that still needs
                                                        // to be the case. similar question arises for sync
    case s: DeliteOpSingleTask[_] if s.requireInputs => syms(s.block) ++ super.syms(e) // super call: add case class syms (iff flag is set)
    case s: DeliteOpSingleTask[_] => syms(s.block)
    case e: DeliteOpExternal[_] => syms(e.allocVal) ++ super.syms(e)
    case op: DeliteCollectElem[_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.alloc) ++ syms(op.allocDataStructure)
    case op: DeliteHashElem[_,_,_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.alloc)
//    case op: DeliteForeachElem[_] => syms(op.func) ++ syms(op.cond) ++ syms(op.sync)
    case op: DeliteForeachElem[_] => syms(op.func) ++ syms(op.sync)
    case op: DeliteReduceElem[_] => syms(op.func) ++ syms(op.cond) ++ syms(op.zero) ++ syms(op.rFunc)
    case op: DeliteReduceTupleElem[_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.zero) ++ syms(op.rFuncSeq) ++ syms(op.rFuncPar) // should be ok for tuples...
    case foreach: DeliteOpForeach2[_,_] => /*if (shallow) syms(foreach.in) else*/ syms(foreach.in) ++ syms(foreach.func) ++ syms(foreach.sync)
    case foreach: DeliteOpForeachBounded[_,_,_] => /*if (shallow) syms(foreach.in) else*/ syms(foreach.in) ++ syms(foreach.func) ++ syms(foreach.sync)
    case _ => super.syms(e)
  }
    
  override def readSyms(e: Any): List[Sym[Any]] = e match { //TR FIXME: check this is actually correct
    case s: DeliteOpSingleTask[_] if s.requireInputs => syms(s.block) ++ super.syms(e) // super call: add case class syms (iff flag is set)
    case s: DeliteOpSingleTask[_] => syms(s.block)
    case e: DeliteOpExternal[_] => syms(e.allocVal) ++ super.syms(e)
    case op: DeliteCollectElem[_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.alloc) ++ syms(op.allocDataStructure)
    case op: DeliteHashElem[_,_,_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.alloc)
//    case op: DeliteForeachElem[_] => syms(op.func) ++ syms(op.cond) ++ syms(op.sync)
    case op: DeliteForeachElem[_] => syms(op.func) ++ syms(op.sync)
    case op: DeliteReduceElem[_] => syms(op.func) ++ syms(op.cond) ++ syms(op.zero) ++ syms(op.rFunc)
    case op: DeliteReduceTupleElem[_,_] => syms(op.func) ++ syms(op.cond) ++ syms(op.zero) ++ syms(op.rFuncSeq) ++ syms(op.rFuncPar)
    case foreach: DeliteOpForeach2[_,_] => syms(foreach.in)
    case foreach: DeliteOpForeachBounded[_,_,_] => syms(foreach.in) 
    case _ => super.readSyms(e)
  }
  
  override def boundSyms(e: Any): List[Sym[Any]] = e match {
    case s: DeliteOpSingleTask[_] => effectSyms(s.block)
    case e: DeliteOpExternal[_] => effectSyms(e.allocVal) /*++ super.effectSyms(e) */
    case op: DeliteHashElem[_,_,_,_] => effectSyms(op.func) ++ effectSyms(op.cond) ++ effectSyms(op.alloc)
    case op: DeliteCollectElem[_,_] => effectSyms(op.func) ++ effectSyms(op.cond) ++ syms(op.aV) ++ effectSyms(op.alloc) ++ effectSyms(op.allocDataStructure)
//    case op: DeliteForeachElem[_] => effectSyms(op.func) ++ effectSyms(op.cond) ++ effectSyms(op.sync)
    case op: DeliteForeachElem[_] => effectSyms(op.func) ++ effectSyms(op.sync)
    case op: DeliteReduceElem[_] => List(op.rV._1, op.rV._2) ++ effectSyms(op.func) ++ effectSyms(op.cond) ++ effectSyms(op.zero) ++ effectSyms(op.rFunc)
    case op: DeliteReduceTupleElem[_,_] => syms(op.rVPar) ++ syms(op.rVSeq) ++ effectSyms(op.func._1) ++ effectSyms(op.cond) ++ effectSyms(op.zero) ++ effectSyms(op.rFuncPar) ++ effectSyms(op.rFuncSeq)
    case foreach: DeliteOpForeach2[_,_] => foreach.v::foreach.i::effectSyms(foreach.func):::effectSyms(foreach.sync)
    case foreach: DeliteOpForeachBounded[_,_,_] => foreach.v::foreach.i::effectSyms(foreach.func):::effectSyms(foreach.sync)
    case _ => super.boundSyms(e)
  }

  
  override def symsFreq(e: Any): List[(Sym[Any], Double)] = e match {
    case s: DeliteOpSingleTask[_] if s.requireInputs => freqNormal(s.block) ++ super.symsFreq(e) // super call: add case class syms (iff flag is set)
    case s: DeliteOpSingleTask[_] => freqNormal(s.block)
    case e: DeliteOpExternal[_] => freqNormal(e.allocVal) ++ super.symsFreq(e)
    case op: DeliteCollectElem[_,_] => freqNormal(op.alloc) ++ freqHot(op.cond) ++ freqHot(op.func) ++ freqNormal(op.allocDataStructure)
    case op: DeliteHashElem[_,_,_,_] => freqNormal(op.alloc) ++ freqHot(op.cond) ++ freqHot(op.func)
//    case op: DeliteForeachElem[_] => freqNormal(op.sync) ++ freqHot(op.cond) ++ freqHot(op.func)
    case op: DeliteForeachElem[_] => freqNormal(op.sync) ++ freqHot(op.func)
    case op: DeliteReduceElem[_] => freqHot(op.cond) ++ freqHot(op.func) ++ freqNormal(op.zero) ++ freqHot(op.rFunc)
    case op: DeliteReduceTupleElem[_,_] => freqHot(op.cond) ++ freqHot(op.func) ++ freqNormal(op.zero) ++ freqHot(op.rFuncSeq) ++ freqHot(op.rFuncPar)
    case foreach: DeliteOpForeach2[_,_] => freqNormal(foreach.in) ++ freqHot(foreach.func) ++ freqHot(foreach.sync)
    case foreach: DeliteOpForeachBounded[_,_,_] => freqNormal(foreach.in) ++ freqHot(foreach.func) ++ freqHot(foreach.sync)
    case _ => super.symsFreq(e)
  }
  
	/////////////////////
  // aliases and sharing

  override def aliasSyms(e: Any): List[Sym[Any]] = e match {
    case s: DeliteOpSingleTask[_] => syms(s.block)
    case e: DeliteOpExternal[_] => Nil
    case op: DeliteCollectElem[_,_] => Nil // in particular not op.alloc !
    case op: DeliteHashElem[_,_,_,_] => Nil // in particular not op.alloc !
    case op: DeliteForeachElem[_] => Nil
    case op: DeliteReduceElem[_] => Nil
    case op: DeliteReduceTupleElem[_,_] => Nil
    case foreach: DeliteOpForeach2[_,_] => Nil
    case foreach: DeliteOpForeachBounded[_,_,_] => Nil
    case _ => super.aliasSyms(e)
  }

  override def containSyms(e: Any): List[Sym[Any]] = e match {
    case s: DeliteOpSingleTask[_] => Nil
    case e: DeliteOpExternal[_] => Nil
    case op: DeliteCollectElem[_,_] => syms(op.func)
    case op: DeliteHashElem[_,_,_,_] => syms(op.func)
    case op: DeliteForeachElem[_] => Nil
    case op: DeliteReduceElem[_] => Nil
    case op: DeliteReduceTupleElem[_,_] => Nil
    case foreach: DeliteOpForeach2[_,_] => Nil
    case foreach: DeliteOpForeachBounded[_,_,_] => Nil
    case _ => super.containSyms(e)
  }

  override def extractSyms(e: Any): List[Sym[Any]] = e match {
    case s: DeliteOpSingleTask[_] => Nil
    case e: DeliteOpExternal[_] => Nil
    case op: DeliteCollectElem[_,_] => Nil
    case op: DeliteHashElem[_,_,_,_] => Nil
    case op: DeliteForeachElem[_] => Nil
    case op: DeliteReduceElem[_] => Nil
    case op: DeliteReduceTupleElem[_,_] => Nil
    case foreach: DeliteOpForeach2[_,_] => Nil
    case foreach: DeliteOpForeachBounded[_,_,_] => Nil
    case _ => super.extractSyms(e)
  }

  override def copySyms(e: Any): List[Sym[Any]] = e match {
    case s: DeliteOpSingleTask[_] => Nil
    case e: DeliteOpExternal[_] => syms(e.allocVal)
    case op: DeliteCollectElem[_,_] => syms(op.alloc) ++ syms(op.allocDataStructure)
    case op: DeliteHashElem[_,_,_,_] => syms(op.alloc)
    case op: DeliteForeachElem[_] => Nil
    case op: DeliteReduceElem[_] => Nil
    case op: DeliteReduceTupleElem[_,_] => Nil
    case foreach: DeliteOpForeach2[_,_] => Nil
    case foreach: DeliteOpForeachBounded[_,_,_] => Nil
    case _ => super.copySyms(e)
  }
}



trait BaseGenDeliteOps extends BaseGenLoopsFat with LoopFusionOpt with BaseGenStaticData {
  val IR: DeliteOpsExp
  import IR._

  //abstract override def emitValDef(sym: Sym[Any], rhs: String)(implicit stream: PrintWriter): Unit = 
  //  if (!simpleCodegen) super.emitValDef(sym,rhs) else stream.print(quote(sym)+";")
  //def emitVarDef(sym: Sym[Any], rhs: String)(implicit stream: PrintWriter): Unit
  //def quote(x: Exp[Any]) : String = 

  // TODO: what about deliteResult and deliteInput??

  // CAVEAT: DeliteCodegen does not inherit from this trait, so this is called
  // only within kernels

  override def focusBlock[A](result: Exp[Any])(body: => A): A = {
    var saveKernel = deliteKernel
    deliteKernel = false
    val ret = super.focusBlock(result)(body)
    deliteKernel = saveKernel
    ret
  }

/*
  // overridden only to attach DeliteFatOp trait to result ...
  override def fatten(e: TP[Any]): TTP = e.rhs match {
    case op: DeliteOpLoop[_] => 
      TTP(List(e.sym), DeliteFatLoop(op.size, op.v, List(op.body)))
    case _ => super.fatten(e)
  }
*/

/*
  // TODO: can implement generically? or need override for VectorSize and all others?
  override def unapplySimpleDomain(e: Def[Int]): Option[Exp[Any]] = e match {
    case ArrayLength(a) => Some(a)
    case _ => super.unapplySimpleDomain(e)
  }
*/
  override def unapplySimpleCollect(e: Def[Any]) = e match {
    case e: DeliteCollectElem[_,_] if e.cond.isEmpty => Some(e.func)
    case _ => super.unapplySimpleCollect(e)
  }

  override def unapplySimpleCollectIf(e: Def[Any]) = e match {
    case e: DeliteCollectElem[_,_] => Some((e.func, e.cond))
//    case e: DeliteReduceElem[_] => Some((e.func, e.cond)) // TODO: aks -- testing fusing conditionals for reduce elems
    case _ => super.unapplySimpleCollectIf(e)
  }

  override def applyAddCondition(e: Def[Any], c: List[Exp[Boolean]]) = e match {
    case e: DeliteCollectElem[_,_] => e.copy(cond = e.cond ++ c)
    case e: DeliteReduceElem[_] => e.copy(cond = e.cond ++ c)
    case _ => super.applyAddCondition(e,c)
  }

  override def shouldApplyFusion(currentScope: List[TTP])(result: List[Exp[Any]]) = Config.opfusionEnabled

}



trait ScalaGenStaticDataDelite extends ScalaGenStaticData {
  val IR: StaticDataExp
  import IR._
  
  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case StaticData(x) => 
      // TODO: should call super if we're generating a single big scala file
      emitValDef(sym, "ppl.delite.runtime.graph.ops.Arguments.staticData["+remap(sym.Type)+"](\""+quote(sym)+"\") // static data: " + x)
    case _ => super.emitNode(sym, rhs)
  }
}


trait ScalaGenDeliteOps extends ScalaGenLoopsFat with ScalaGenStaticDataDelite with BaseGenDeliteOps { // not sure where to mix in ScalaGenStaticData
  import IR._

  def quotearg(x: Sym[Any]) = quote(x) + ": " + quotetp(x)
  def quotetp(x: Sym[Any]) = remap(x.Type)
/*
  def quoteZero(x: Sym[Any]) = x.Type.toString match { 
    case "Int" | "Long" | "Float" | "Double" => "0" 
    case "Boolean" => "false"
    case _ => "null" 
  }
*/

  //TODO: modularize code generators even more

  /**
   * MultiLoop components
   */
  def emitCollectElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteCollectElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
    if (elem.needsSpecialCollect) {    
      if (elem.cond.nonEmpty)
        stream.print("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") ")
      emitter.emitAddToBuffer(elemType(elem), prefixSym, quote(sym), quote(getBlockResult(elem.func)))
    } else {
      def arrayUpdate(field: String) {
        val dot = if (field == "") "" else "."
        stream.println(prefixSym + quote(sym) + "_data" + field + "(" + quote(op.v) + ") = " + quote(getBlockResult(elem.func)) + dot + field)
      }
      structType(elemType(elem)) match {
        case Some(elems) => elems.foreach(f => arrayUpdate(f._1))
        case None => arrayUpdate("")
      }
    }
  }

  
  /**
   * MultiLoop components
   */
  def emitHashElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteHashElem[_,_,_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
    if (elem.needsSpecialCollect) {
      if (elem.cond.nonEmpty) stream.print("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") ")
      if (deliteKernel)
        emitter.emitAddToBuffer(elemType(elem), prefixSym, quote(sym), quote(getBlockResult(elem.func)))
      else
        emitter.emitAddToDataStructure(prefixSym, quote(sym), quote(getBlockResult(elem.func)))
    } else
      stream.println(prefixSym + quote(sym) + ".dcUpdate(" + quote(op.v) + ", " + quote(getBlockResult(elem.func)) + ")")
  }
  
  def emitForeachElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteForeachElem[_])(implicit stream: PrintWriter) {
    // if (elem.cond.nonEmpty)
    //   stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
    stream.println(quote(getBlockResult(elem.func)))
    // if (elem.cond.nonEmpty) {
    //   stream.println("}")                         
    // }
  }
  
  // -- begin emit reduce
  
  def emitFirstReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
      if (elem.cond.nonEmpty) {
        // if we have conditionals, we have to delay the the initialization of the accumulator to the
        // first element where the condition is true
        stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
        stream.println(quote(getBlockResult(elem.func)))
        stream.println("} else {")
        stream.println(prefixSym + quote(sym) + "_zero")
        stream.println("}")
      }
      else {
        stream.println(quote(getBlockResult(elem.func)))        
      }
  }

  def emitReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty) {
      stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {"/*}*/)
      if (elem.stripFirst)
        emitInitializeOrReduction(op, sym, elem, prefixSym)
      else
        emitReduction(op, sym, elem, prefixSym)
      stream.println("}")
    }
    else {
      emitReduction(op, sym, elem, prefixSym)
    }
  }

  def emitReduceTupleElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      sys.error("tuple reduce with external conditions not implemented!")
    }
    else {
      emitReductionTuple(op, sym, elem, prefixSym)
    }
  }

  def emitInitializeOrReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("// TODO: we could optimize this check away with more convoluted runtime support if necessary")          
    stream.println("if (" + prefixSym + quote(sym) + " == " + prefixSym + quote(sym) + "_zero" + ") " + prefixSym + quote(sym) + " = {")
    
    // initialize
    stream.println(quote(getBlockResult(elem.func)))
    stream.println("}")
    // or reduce
    stream.println("else {")
    emitReduction(op, sym, elem, prefixSym)
    stream.println("}")
  } 
        
  def emitReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("val " + quote(elem.rV._1) + " = " + prefixSym + quote(sym))
    stream.println("val " + quote(elem.rV._2) + " = " + quote(getBlockResult(elem.func)))
    emitBlock(elem.rFunc)
    stream.println(prefixSym + quote(sym) + " = " + quote(getBlockResult(elem.rFunc)))    
  }

  def emitReductionTuple(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String)(implicit stream: PrintWriter) {
    val rV = elem.rVSeq
    val rFunc = elem.rFuncSeq
    stream.println("val " + quote(rV._1._1) + " = " + prefixSym + quote(sym) + "  ")
    stream.println("val " + quote(rV._1._2) + " = " + prefixSym + quote(sym) + "_2")
    stream.println("val " + quote(rV._2._1) + " = " + quote(getBlockResult(elem.func._1)))
    stream.println("val " + quote(rV._2._2) + " = " + quote(getBlockResult(elem.func._2)))
    emitFatBlock(List(rFunc._1, rFunc._2))
    stream.println(prefixSym + quote(sym) + "   = " + quote(getBlockResult(rFunc._1)))
    stream.println(prefixSym + quote(sym) + "_2 = " + quote(getBlockResult(rFunc._2)))
  }

  // -- end emit reduce emit
  
  // TODO:

  def emitMultiLoopFuncs(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    val elemFuncs = op.body flatMap { // don't emit dependencies twice!
      case elem: DeliteHashElem[_, _, _, _] => elem.func :: elem.cond
      case elem: DeliteCollectElem[_,_] => elem.func :: elem.cond
//      case elem: DeliteForeachElem[_] => elem.cond // only emit func inside condition! TODO: how to avoid emitting deps twice? // elem.func :: elem.cond
      case elem: DeliteForeachElem[_] => List(elem.func) 
      case elem: DeliteReduceElem[_] => elem.func :: elem.cond
      case elem: DeliteReduceTupleElem[_,_] => elem.func._1 :: elem.func._2 :: elem.cond
    }
    emitFatBlock(elemFuncs)
  }

  def emitInlineAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    // initialization             
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_, _, _, _]) =>
        stream.println("val " + quote(sym) + " = {"/*}*/)
        emitBlock(elem.alloc) // FIXME: how do we know it is the right size? conditions could have been added retrospectively!!
        if (elem.cond.nonEmpty)
          stream.println("//TODO: buffer size might be wrong (loop has conditions)")
        stream.println(quote(getBlockResult(elem.alloc)))
        stream.println(/*{*/"}")              
      case (sym, elem: DeliteCollectElem[_,_]) =>
        if (elem.cond.nonEmpty) {
          structType(elemType(elem)) match {
            case Some(elems) => buf_block(elems)
            case None => buf_block(Map(""->getBlockResult(elem.func).Type.asInstanceOf[Manifest[Any]]))
          }
          def buf_block(elems: Map[String,Manifest[Any]]) {
            elems.foreach {e => stream.println("var " + quote(sym) + "_buf" + e._1 + ": Array[" + remap(e._2) + "] = new Array(128)") }
            stream.println("var " + quote(sym) + "_size = 0")
            stream.println("def " + quote(sym) + "_buf_append(" + elems.map(e => "x" + e._1 + ": " + remap(e._2)).mkString(",") + "): Unit = {"/*}*/)
            stream.println("if (" + quote(sym) + "_size >= " + quote(sym) + "_buf" + elems.head._1 + ".length) {"/*}*/)
            elems.foreach { e => stream.println("val old" + e._1 + " = " + quote(sym) + "_buf" + e._1)
              stream.println(quote(sym) + "_buf" + e._1 + " = new Array(2*old" + e._1 + ".length)")
              stream.println("System.arraycopy(old" + e._1 + ", 0, " + quote(sym) + "_buf" + e._1 + ", 0, old" + e._1 + ".length)")
            }
            stream.println(/*{*/"}")
            elems.foreach { e => stream.println(quote(sym) + "_buf" + e._1 + "(" + quote(sym) + "_size) = x" + e._1) }
            stream.println(quote(sym) + "_size += 1")
            stream.println(/*{*/"}")
          }
        } else {
          structType(elemType(elem)) match {
            case Some(elems) => data_block(elems)
            case None => data_block(Map(""->getBlockResult(elem.func).Type.asInstanceOf[Manifest[Any]]))
          }
          def data_block(elems: Map[String, Manifest[Any]]) = elems.foreach { e =>
            stream.println("val " + quote(sym) + "_data" + e._1 + ": Array[" + remap(e._2) + "] = new Array(" + quote(op.size) + ")")
          }
        }
/*
        stream.println("val " + quote(sym) + " = {"/*}*/)
        emitBlock(elem.alloc) // FIXME: how do we know it is the right size? conditions could have been added retrospectively!!
        if (elem.cond.nonEmpty)
          stream.println("//TODO: buffer size might be wrong (loop has conditions)")
        stream.println(quote(getBlockResult(elem.alloc)))
        stream.println(/*{*/"}")
*/
      case (sym, elem: DeliteForeachElem[_]) => 
        stream.println("var " + quotearg(sym) + " = ()") // must be type Unit
      case (sym, elem: DeliteReduceElem[_]) =>
        stream.println("val " + quote(sym) + "_zero = {"/*}*/)
        emitBlock(elem.zero)
        stream.println(quote(getBlockResult(elem.zero)))
        stream.println(/*{*/"}")
        stream.println("var " + quotearg(sym) + " = " + quote(sym) + "_zero")
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        stream.println("val " + quote(sym) + "_zero   = {"/*}*/) // better use emitFatBlock?
        emitBlock(elem.zero._1)
        stream.println(quote(getBlockResult(elem.zero._1)))
        stream.println(/*{*/"}")
        stream.println("val " + quote(sym) + "_zero_2 = {"/*}*/)
        emitBlock(elem.zero._2)
        stream.println(quote(getBlockResult(elem.zero._2)))
        stream.println(/*{*/"}")
        stream.println("var " + quote(sym) + "  " + " = " + quote(sym) + "_zero  ") // should have types...
        stream.println("var " + quote(sym) + "_2" + " = " + quote(sym) + "_zero_2")
    }
    stream.println("var " + quote(op.v) + " = 0")
    //if (true) { //op.body exists (loopBodyNeedsStripFirst _)) { preserve line count as indicator for succesful fusing
    if (op.body exists (loopBodyNeedsStripFirst _)) {
      stream.println("if (" + quote(op.size) + " > 0) { // prerun fat loop " + symList.map(quote).mkString(",")/*}*/)
      /* strip first iteration */     
      emitMultiLoopFuncs(op, symList)
      (symList zip op.body) foreach {
        case (sym, elem: DeliteCollectElem[_,_]) =>
          emitCollectElem(op, sym, elem)
        case (sym, elem: DeliteForeachElem[_]) => 
          stream.println(quote(sym) + " = {"/*}*/)
          emitForeachElem(op, sym, elem)
          stream.println(/*{*/"}")
        case (sym, elem: DeliteReduceElem[_]) =>
          if (elem.stripFirst) {
            stream.println(quote(sym) + " = {"/*}*/)
            emitFirstReduceElem(op, sym, elem)
            stream.println(/*{*/"}")
          }
          else {
            emitReduceElem(op, sym, elem)
          }
      }
      stream.println(/*{*/"}")
      stream.println(quote(op.v) + " = 1")
    }
    stream.println("while (" + quote(op.v) + " < " + quote(op.size) + ") {  // begin fat loop " + symList.map(quote).mkString(",")/*}*/)
    // body
    emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_, _, _, _]) =>
        emitHashElem(op, sym, elem)
      case (sym, elem: DeliteCollectElem[_,_]) =>
        emitCollectElem(op, sym, elem)
      case (sym, elem: DeliteForeachElem[_]) => 
        stream.println(quote(sym) + " = {"/*}*/)                             
        emitForeachElem(op, sym, elem)
        stream.println(/*{*/"}")
      case (sym, elem: DeliteReduceElem[_]) =>
        emitReduceElem(op, sym, elem)
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        emitReduceTupleElem(op, sym, elem)
    }
    stream.println(quote(op.v) + " += 1")
    stream.println(/*{*/"} // end fat loop " + symList.map(quote).mkString(","))
    // finalize
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        elem.emitterFactory.map(_.scala).get.emitDataDeclaration(elemType(elem), quote(sym), "", quote(sym) + "_data")
        stream.println("val " + quote(sym) + " = {")
        emitBlock(elem.alloc)
        elem.emitterFactory.get.scala.emitInitializeDataStructure(quote(sym), "", quote(getBlockResult(elem.alloc)), quote(sym) + "_data")
        stream.println("}")
      case (sym, elem: DeliteCollectElem[_,_]) =>
        structType(elemType(elem)) match {
          case Some(elems) => final_block(elems)
          case None => final_block(Map(""->getBlockResult(elem.func).Type.asInstanceOf[Manifest[Any]]))
        }
        def final_block(elems: Map[String, Manifest[Any]]) {
          if (elem.cond.nonEmpty) {
            elems.foreach { e =>
              stream.println("val " + quote(sym) + "_data" + e._1 + ": Array[" + remap(e._2) + "] = new Array(" + quote(sym) + "_size)")
              stream.println("System.arraycopy(" + quote(sym) + "_buf" + e._1 + ", 0, " + quote(sym) + "_data" + e._1 + ", 0, " + quote(sym) + "_size)")
              stream.println(quote(sym) + "_buf" + e._1 + " = null")
            }
          }
          if (elem.emitterFactory.isEmpty) {
            if (elems.head._1 == "")
              emitValDef(elem.aV, quote(sym) + "_data")
            else
              emitValDef(elem.aV, " = new " + remap(elem.aV.Type) + elems.map(e => quote(sym) + "_data" + e._1).mkString("(",",",")"))
          } else {
            elem.emitterFactory.map(_.scala).getOrElse(standardScalaEmitter).emitDataDeclaration(elemType(elem), quote(sym), "", quote(sym) + "_data")
          }
          stream.println("val " + quote(sym) + " = {"/*}*/)
          emitBlock(elem.alloc)
          stream.println(quote(getBlockResult(elem.alloc)))
          stream.println(/*{*/"}")
        }
      case (sym, elem: DeliteForeachElem[_]) => 
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
  }
  
  implicit def standardScalaEmitter = new ScalaEmitter {
    def emitBufferDefs(elemType: Manifest[_], kernelname: String, basename: String, elemtype: String)(implicit stream: PrintWriter) {
      structType(elemType) match {
          case Some(elems) => buf_block(elems.map(e => (e._1, remap(e._2))))
          case None => buf_block(Map("" -> elemtype))
        }
        def buf_block(elems: Map[String,String]) {
          elems.foreach {e => stream.println("var " + basename + "_buf" + e._1 + ": Array[" + e._2 + "] = _") }
          stream.println("var " + basename + "_size = 0")
          stream.println("var " + basename + "_offset = 0")
          stream.println("var " + basename + "_chunkIdx: Int = _")
          stream.println("var " + basename + "_numChunks: Int = _")
          stream.println("var " + basename + "_activations: Array[activation_" + kernelname + "] = _")
          stream.println("def " + basename + "_buf_init(chunkIdx: Int, numChunks: Int): Unit = {"/*}*/)
          stream.println(basename + "_chunkIdx = chunkIdx")
          stream.println(basename + "_numChunks = numChunks")
          elems.foreach { e => stream.println(basename + "_buf" + e._1 + " = new Array(128)") }
          stream.println(/*{*/"}")
          stream.println("def " + basename + "_buf_append(" + elems.map(e => "x" + e._1 + ": " + e._2).mkString(",") + "): Unit = {"/*}*/)
          stream.println("if (" + basename + "_size >= " + basename + "_buf" + elems.head._1 + ".length) {"/*}*/)
          elems.foreach { e => stream.println("val old" + e._1 + " = " + basename + "_buf" + e._1)
          stream.println(basename + "_buf" + e._1 + " = new Array(2*old" + e._1 + ".length)")
          stream.println("System.arraycopy(old" + e._1 + ", 0, " + basename + "_buf" + e._1 + ", 0, old" + e._1 + ".length)")
          }
          stream.println(/*{*/"}")
          elems.foreach { e => stream.println(basename + "_buf" + e._1 + "(" + basename + "_size) = x" + e._1) }
          stream.println(basename + "_size += 1")
          stream.println(/*{*/"}")
        }
        /*stream.println("def " + basename + "_buf_appendAll(xs: Array[" + elemtype + "], len: Int): Unit = {"/*}*/)
        stream.println("if (" + basename + "_size + len >= " + basename + "_buf.length) {"/*}*/)
        stream.println("val old = " + basename + "_buf")
        stream.println(basename + "_buf = new Array(2*(old.length+len))")
        stream.println("System.arraycopy(old, 0, " + basename + "_buf, 0, old.length)")
        stream.println(/*{*/"}")
        stream.println("System.arraycopy(xs, 0, " + basename + "_buf, " + basename + "_size, len)")
        stream.println(basename + "_size += len")
        stream.println(/*{*/"}") */
    }
    def emitInitSubActivation(basename: String, activname: String, chunkIdxVar: String, numChunksVar: String)(implicit stream: PrintWriter) {
      stream.println(activname + "." + basename + "_buf_init(" + chunkIdxVar + ", " + numChunksVar + ")")
    }
    def emitAddToBuffer(elemType: Manifest[_], prefixSym: String, basename: String, elemname: String)(implicit stream: PrintWriter) {
      structType(elemType) match {
        case Some(elems) => bufferAppend(elems.keys.toSeq)
        case None => bufferAppend(List(""))
      }
      def bufferAppend(fields: Seq[String]) {
        val dot = if (fields(0) == "") "" else "."
        stream.println(prefixSym + basename + "_buf_append(" + fields.map(f => elemname + dot + f).mkString(",") + ")")
      }
    }
    def emitAddToDataStructure(prefixSym: String, basename: String, elemname: String)(implicit stream: PrintWriter) {
      stream.println(prefixSym + basename + ".insert(" + prefixSym + basename + ".length, " + elemname + ")")
    }
    def emitPostCombine(basename: String, activname: String, lhsname: String)(implicit stream: PrintWriter) {
      stream.println(activname + "." + basename + "_offset = " + lhsname + "." + basename + "_offset + " + lhsname + "." + basename + "_size")
      
      // init first block
      stream.println("if (%s.%s_chunkIdx == 0) {".format(lhsname, basename))
      stream.println("%s.%s_activations = new Array(%s.%s_numChunks)".format(lhsname, basename, lhsname, basename))
      stream.println("%s.%s_activations(0) = %s".format(lhsname, basename, lhsname))
      stream.println("}")
      
      // copy and update the array
      stream.println("%s.%s_activations = %s.%s_activations".format(activname, basename, lhsname, basename))
      stream.println("%s.%s_activations(%s.%s_chunkIdx) = %s".format(activname, basename, activname, basename, activname))
    }
    def emitPostProcInit(elemType: Manifest[_], basename: String, activname: String)(implicit stream: PrintWriter) {
      //stream.println("if (" + activname + "." + basename + "_offset > 0) {"/*}*/) // set data array for result object
      stream.println("val len = " + activname + "." + basename + "_offset + " + activname + "." + basename + "_size")
      //stream.println("" + activname + "." + basename + ".unsafeSetData(new Array(len), len)")
      structType(elemType) match {
        case Some(elems) => postProc_block(elems.keys)
        case None => postProc_block(List(""))
      }
      def postProc_block(elems: Iterable[String]) = elems.foreach(e => stream.println(activname + "." + basename + "_data" + e + " = new Array(len)"))
      //stream.println(/*{*/"} else {"/*}*/)
      //stream.println("" + activname + "." + basename + ".unsafeSetData(" + activname + "." +basename + "_buf, " + activname + "." + basename + "_size)")
      //stream.println(activname + "." + basename + "_data = " + activname + "." + basename + "_buf")
      //stream.println(/*{*/"}")
    }
    def emitPostProcess(elemType: Manifest[_], basename: String, activname: String)(implicit stream: PrintWriter) {
      structType(elemType) match {
        case Some(elems) => postProcess_block(elems.keys)
        case None => { //TODO: use activations with struct types?
          stream.println("if (%s.%s_numChunks > 1) {".format(activname, basename))
          stream.println("%s.%s_data = %s.%s_activations(%s.%s_numChunks - 1).%s_data".format(activname, basename, activname, basename, activname, basename, basename))
          stream.println("}")
          postProcess_block(List(""))
        }
      }
      //stream.println("if (" + activname + "." + basename + "_data ne " + activname + "." + basename + "_buf)")
      def postProcess_block(elems: Iterable[String]) = elems.foreach { e =>
        stream.println("System.arraycopy(" + activname + "." + basename + "_buf" + e + ", 0, " + activname + "." + basename + "_data" + e + ", " + activname + "." + basename + "_offset, " + activname + "." + basename + "_size)")
        stream.println("" + activname + "." + basename + "_buf" + e + " = null")
      }
    }
    def emitPostCombine2(basename: String, activname: String, lhsname: String)(implicit stream: PrintWriter) {
    }
    def emitPostProcInit2(basename: String, activname: String)(implicit stream: PrintWriter) {
    }
    def emitPostProcess2(basename: String, activname: String)(implicit stream: PrintWriter) {
    }
    def emitDataDeclaration(elemType: Manifest[_], basename: String, prefix: String, dataname: String, dataType: String = "")(implicit stream: PrintWriter) {
      structType(elemType) match {
         case Some(elems) =>
           stream.println("val " + dataname + " = new " + dataType + elems.map(e => prefix + basename + "_data" + e._1).mkString("(",",",")"))
         case None =>
           stream.println("val " + dataname + " = " + prefix + basename + "_data")
       }
    }
    def emitInitializeDataStructure(basename: String, prefix: String, collectionname: String, dataname: String)(implicit stream: PrintWriter) {
      // emitted directly
    }
  }
  
  def emitAbstractFatLoopKernelExtra(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter): Unit = {
    val kernelName = symList.map(quote).mkString("")
    stream.println("final class activation_" + kernelName + " {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        stream.println("var " + quote(sym) + ": " + remap(sym.Type) + " = _")
        if (elem.needsSpecialExtra) {
          val emitter = elem.emitterScala.get
          emitter.emitBufferDefs(elemType(elem), kernelName, quote(sym), remap(getBlockResult(elem.func).Type))
        }
      case (sym, elem: DeliteCollectElem[_,_]) => 
        stream.println("var " + quote(sym) + ": " + remap(sym.Type) + " = _")
        if (elem.needsSpecialExtra) {
          val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
          emitter.emitBufferDefs(elemType(elem), kernelName, quote(sym), remap(getBlockResult(elem.func).Type))
        }
        structType(elemType(elem)) match {
          case Some(elems) => data_block(elems)
          case None => data_block(Map(""->getBlockResult(elem.func).Type.asInstanceOf[Manifest[Any]]))
        }
        def data_block(elems: Map[String,Manifest[Any]]) = elems.foreach { e =>
          stream.println("var " + quote(sym) + "_data" + e._1 + ": Array[" + remap(e._2) + "] = _")
        }
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("var " + quote(sym) + ": " + remap(sym.Type) + " = _")
      case (sym, elem: DeliteReduceElem[_]) =>
        stream.println("var " + quote(sym) + ": " + remap(sym.Type) + " = _")
        stream.println("var " + quote(sym) + "_zero: " + remap(sym.Type) + " = _")
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        stream.println("var " + quote(sym) + "  : " + remap(sym.Type) + " = _")
        stream.println("var " + quote(sym) + "_2: " + remap(elem.func._2.Type) + " = _")
        stream.println("var " + quote(sym) + "_zero  : " + remap(sym.Type) + " = _")
        stream.println("var " + quote(sym) + "_zero_2" + ": " + remap(elem.func._2.Type) + " = _")
    }
    stream.println(/*{*/"}")
  }

  def emitKernelAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    // kernel mode
    val kernelName = symList.map(quote).mkString("")
    val actType = "activation_"+kernelName
    //deliteKernel = false
    stream.println("val " + kernelName + " = new generated.scala.DeliteOpMultiLoop[" + actType + "] {"/*}*/)
    // TODO: if there are conditions, the output size is not known (but for now it is known to be smaller than the input size)
    // two options:
    // - combine (reduce step) using concat <-- simpler to implement but slow
    // - use a pre-combine scan step to find out where each processor should write the data
    //   and do the copying in another parallel map <-- faster but more work
    
    stream.println("def size = " + quote(op.size))
    stream.println("def alloc: " + actType + " = {"/*}*/)
    stream.println("val __act = new " + actType)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_, _, _, _]) =>
        emitBlock(elem.alloc) // FIXME: how do we know it is the right size? conditions could have been added retrospectively!!
        if (elem.cond.nonEmpty)
          stream.println("//TODO: buffer size might be wrong (loop has conditions)")
        stream.println("__act." + quote(sym) + " = " + quote(getBlockResult(elem.alloc))) //FIXME: do in post-process
      case (sym, elem: DeliteCollectElem[_,_]) =>
        //emitBlock(elem.alloc) // FIXME: how do we know it is the right size? conditions could have been added retrospectively!!
        //if (elem.cond.nonEmpty)
        //  stream.println("//TODO: buffer size might be wrong (loop has conditions)")
        //stream.println("__act." + quote(sym) + " = " + quote(getBlockResult(elem.alloc))) //FIXME: do in post-process
        if (elem.cond.nonEmpty)
          stream.println("// __act." + quote(sym) + "_data stays null for now")
        else {
          structType(elemType(elem)) match {
            case Some(elems) => {
              for ((name,tp) <- elems) {
                assert(structType(tp).isEmpty, "FIXME: nested structs in MultiLoop output array alloc")
                stream.println("__act." + quote(sym) + "_data" + name + " = new Array[" + remap(tp) + "](" + quote(op.size) + ")")
              }
            }
            case  None => stream.println("__act." + quote(sym) + "_data = new Array(" + quote(op.size) + ")")
          }
        }
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("__act." + quote(sym) + " = ()") // must be type Unit, initialized in init below
      case (sym, elem: DeliteReduceElem[_]) => 
        stream.println("__act." + quote(sym) + "_zero = {"/*}*/)
        emitBlock(elem.zero)
        stream.println(quote(getBlockResult(elem.zero)))
        stream.println(/*{*/"}")
        stream.println("__act." + quote(sym) + " = __act." + quote(sym) + "_zero")
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        stream.println("__act." + quote(sym) + "_zero   = {"/*}*/) // better use emitFatBlock?
        emitBlock(elem.zero._1)
        stream.println(quote(getBlockResult(elem.zero._1)))
        stream.println(/*{*/"}")
        stream.println("__act." + quote(sym) + "_zero_2 = {"/*}*/)
        emitBlock(elem.zero._2)
        stream.println(quote(getBlockResult(elem.zero._2)))
        stream.println(/*{*/"}")
        stream.println("__act." + quote(sym) + "  " + " = __act." + quote(sym) + "_zero  ")
        stream.println("__act." + quote(sym) + "_2" + " = __act." + quote(sym) + "_zero_2")
    }
    stream.println("__act")
    stream.println(/*{*/"}")
    // processRange
    stream.println("def processRange(__act: " + actType + ", start: Int, end: Int, chunkIdx: Int, numChunks: Int): " + actType + " = {"/*}*/)
    stream.println("var idx = start")
    stream.println("val __act2 = init(__act,idx,chunkIdx,numChunks)")
    //stream.println("idx += 1")
    stream.println("while (idx < end) {"/*}*/)
    stream.println("process(__act2, idx)")
    stream.println("idx += 1")
    stream.println("/*{*/}")
    stream.println("__act2")
    stream.println(/*{*/"}")

/*
    out.append("val acc = head.closure.init(out, idx)\n") // copy of out per chunk
                out.append("idx += 1\n")
    out.append("val hc = head.closure\n")
    out.append("while (idx < end) {\n")
    out.append("hc.process(acc, idx)\n")
    out.append("idx += 1\n")
    out.append("}\n")
*/
    //out.append("val acc = head.closure.processRange(out,idx,end)\n")


    stream.println("def init(__act: " + actType + ", " + quotearg(op.v) + ", chunkIdx: Int, numChunks: Int): " + actType + " = {"/*}*/)
    if (op.body exists (d => loopBodyNeedsCombine(d) || loopBodyNeedsPostProcess(d))) {
      //emitMultiLoopFuncs(op, symList)                               
      stream.println("val __act2 = new " + actType)
      (symList zip op.body) foreach {
        case (sym, elem: DeliteHashElem[_,_,_,_]) =>
          val emitter = elem.emitterScala.get
          if (elem.needsSpecialInit) {
            emitter.emitInitSubActivation(quote(sym), "__act2", "chunkIdx", "numChunks")
          }
          stream.println("__act2." + quote(sym) + " = " + "__act." + quote(sym))
          //emitCollectElem(op, sym, elem, "__act2.")
        case (sym, elem: DeliteCollectElem[_,_]) =>
          val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
          if (elem.needsSpecialInit) {
            emitter.emitInitSubActivation(quote(sym), "__act2", "chunkIdx", "numChunks")
          }
          stream.println("__act2." + quote(sym) + " = " + "__act." + quote(sym))
          //emitCollectElem(op, sym, elem, "__act2.")
          /* TODO ? 
          if (elem.cond.nonEmpty) {
            stream.println("__act2." + quote(sym) + "_buf_init")
          } else {
            stream.println("__act2." + quote(sym) + "_data = " + "__act." + quote(sym) + "_data")
          }
          emitCollectElem(op, sym, elem, "__act2.")
          */
        case (sym, elem: DeliteForeachElem[_]) => 
          stream.println("__act2." + quote(sym) + " = {"/*}*/)
          //emitForeachElem(op, sym, elem)
          stream.println(/*{*/"}")               
        case (sym, elem: DeliteReduceElem[_]) =>
          if (elem.stripFirst) {
            stream.println("__act2." + quote(sym) + "_zero = " + "__act." + quote(sym) + "_zero") // do we need zero here? yes, for comparing against...
            stream.println("__act2." + quote(sym) + " = {"/*}*/)
            //emitFirstReduceElem(op, sym, elem, "__act2.")
            stream.println(/*{*/"}")
          } else { 
            stream.println("__act2." + quote(sym) + "_zero = " + "__act." + quote(sym) + "_zero")
            if (isPrimitiveType(sym.Type)) {
              stream.println("__act2." + quote(sym) + " = " + "__act2." + quote(sym) + "_zero")
            } else {
              stream.println("__act2." + quote(sym) + " = " + "__act2." + quote(sym) + "_zero.cloneL") // separate zero buffer
            }
            //emitReduceElem(op, sym, elem, "__act2.")
          }
        case (sym, elem: DeliteReduceTupleElem[_,_]) =>
          // no strip first here ... stream.println("assert(false, \"TODO: tuple reduce\")")
          stream.println("__act2." + quote(sym) + "_zero   = " + "__act." + quote(sym) + "_zero  ")
          stream.println("__act2." + quote(sym) + "_zero_2 = " + "__act." + quote(sym) + "_zero_2")
          stream.println("__act2." + quote(sym) + "   = " + "__act2." + quote(sym) + "_zero  ")
          stream.println("__act2." + quote(sym) + "_2 = " + "__act2." + quote(sym) + "_zero_2")
          //emitReduceTupleElem(op, sym, elem, "__act2.")
      }
      stream.println("__act2")
    } else {
      //stream.println("process(__act, " + quote(op.v) + ")")
      stream.println("__act")
    }
    stream.println(/*{*/"}")
    stream.println("def process(__act: " + actType + ", " + quotearg(op.v) + "): Unit = {"/*}*/)
    emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        emitHashElem(op, sym, elem, "__act.")
      case (sym, elem: DeliteCollectElem[_,_]) =>
        emitCollectElem(op, sym, elem, "__act.")
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("val " + quote(sym) + " = {")
        emitForeachElem(op, sym, elem)
        stream.println("}")
      case (sym, elem: DeliteReduceElem[_]) =>
        emitReduceElem(op, sym, elem, "__act.")
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        emitReduceTupleElem(op, sym, elem, "__act.")
    }
    stream.println(/*{*/"}")
    stream.println("def combine(__act: " + actType + ", rhs: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
      case (sym, elem: DeliteCollectElem[_,_]) =>
      case (sym, elem: DeliteForeachElem[_]) => // nothing needed
      case (sym, elem: DeliteReduceElem[_]) =>
        // if either value is zero, return the other instead of combining
        stream.println("val " + quote(elem.rV._1) + " = " + "__act." + quote(sym))
        stream.println("val " + quote(elem.rV._2) + " = " + "rhs." + quote(sym))
        stream.println("if (" + quote(elem.rV._1) + " == " + "__act." + quote(sym) + "_zero) {"/*}*/) //TODO: what if zero is an accumulator (SumIf)?
        stream.println("__act." + quote(sym) + " = " + quote(elem.rV._2))
        stream.println(/*{*/"}")
        stream.println("else if (" + quote(elem.rV._2) + " != " + "__act." + quote(sym) + "_zero) {"/*}*/) //TODO: see above
        emitBlock(elem.rFunc)
        stream.println("__act." + quote(sym) + " = " + quote(getBlockResult(elem.rFunc)))
        stream.println(/*{*/"}")
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        // stream.println("assert(false, \"TODO: tuple reduce\")")
        val rV = elem.rVPar
        val rFunc = elem.rFuncPar
        stream.println("val " + quote(rV._1._1) + " = " + "__act." + quote(sym) + "  ")
        stream.println("val " + quote(rV._1._2) + " = " + "__act." + quote(sym) + "_2")
        stream.println("val " + quote(rV._2._1) + " = " + "rhs." + quote(sym) + "  ")
        stream.println("val " + quote(rV._2._2) + " = " + "rhs." + quote(sym) + "_2")
        emitFatBlock(List(rFunc._1, rFunc._2))
        stream.println("__act." + quote(sym) + "   = " + quote(getBlockResult(rFunc._1)))
        stream.println("__act." + quote(sym) + "_2 = " + quote(getBlockResult(rFunc._2)))
    }
    stream.println(/*{*/"}")
    // scan/postprocess follows
    stream.println("def postCombine(__act: " + actType + ", lhs: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess) {
          //calculate start offset from rhs.offset + rhs.size. if last chunk
          emitter.emitPostCombine(quote(sym), "__act", "lhs")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess) {
          //calculate start offset from rhs.offset + rhs.size. if last chunk
          emitter.emitPostCombine(quote(sym), "__act", "lhs")
        }
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    stream.println("def postProcInit(__act: " + actType + "): Unit = {"/*}*/) // only called for last chunk!!
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess) {
          emitter.emitPostProcInit(elemType(elem), quote(sym), "__act")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess) {
          emitter.emitPostProcInit(elemType(elem), quote(sym), "__act")
        }
        /* TODO ?
        if (elem.cond.nonEmpty) {
          // aks: this optimization can result in writing a too-large output array as the result of the delite op.
          // are we always guaranteed to have a logical size field in the output?
          //stream.println("if (__act." + quote(sym) + "_offset > 0) {"/*}*/) // set data array for result object
          stream.println("val len = __act." + quote(sym) + "_offset + __act." + quote(sym) + "_size")
          structType(elemType(elem)) match {
            case Some(elems) => {
              for ((name,tp) <- elems) {
                assert(structType(tp).isEmpty, "FIXME: nested structs in MultiLoop output array alloc")
                stream.println("__act." + quote(sym) + "_data" + name + " = new Array[" + remap(tp) + "](len)")
              }
            }
            case  None => stream.println("__act." + quote(sym) + "_data = new Array(len)")
          }
          //stream.println(/*{*/"} else {"/*}*/)
          //stream.println("__act." + quote(sym) + "_data = __act." +quote(sym) + "_buf")
          //stream.println(/*{*/"}")
          
        }
        */
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    stream.println("def postProcess(__act: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess) {
          emitter.emitPostProcess(elemType(elem), quote(sym), "__act")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess) {
          //calculate start offset from rhs.offset + rhs.size
          emitter.emitPostProcess(elemType(elem), quote(sym), "__act")
        }
      /* TODO ?
      case (sym, elem: DeliteCollectElem[_,_]) => //FIXME: get rid of .data and adapt to new alloc style
        if (elem.cond.nonEmpty) {
          //calculate start offset from rhs.offset + rhs.size
          //stream.println("if (__act." + quote(sym) + "_data ne __act." + quote(sym) + "_buf)")
          structType(elemType(elem)) match {
            case Some(elems) => copyBlock(elems.keys)
            case None => copyBlock(List(""))
          }
          def copyBlock(elems: Iterable[String]) = elems.foreach { e =>
            stream.println("System.arraycopy(__act." + quote(sym) + "_buf" + e + ", 0, __act." + quote(sym) + "_data" + e + ", __act." + quote(sym) + "_offset, __act." + quote(sym) + "_size)")
            stream.println("__act." + quote(sym) + "_buf" + e + " = null")
          }
        }
      */
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    
    // scan/postprocess 2 follows
    stream.println("def postCombine2(__act: " + actType + ", lhs: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess2) {
          //calculate start offset from rhs.offset + rhs.size. if last chunk
          emitter.emitPostCombine2(quote(sym), "__act", "lhs")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess2) {
          //calculate start offset from rhs.offset + rhs.size. if last chunk
          emitter.emitPostCombine2(quote(sym), "__act", "lhs")
        }
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    stream.println("def postProcInit2(__act: " + actType + "): Unit = {"/*}*/) // only called for last chunk!!
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess2) {
          emitter.emitPostProcInit2(quote(sym), "__act")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess2) {
          emitter.emitPostProcInit2(quote(sym), "__act")
        }
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    stream.println("def postProcess2(__act: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        val emitter = elem.emitterScala.get
        if (elem.needsPostProcess2) {
          //calculate start offset from rhs.offset + rhs.size
          emitter.emitPostProcess2(quote(sym), "__act")
        }
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        if (elem.needsPostProcess2) {
          //calculate start offset from rhs.offset + rhs.size
          emitter.emitPostProcess2(quote(sym), "__act")
        }
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    
    stream.println("def finalize(__act: " + actType + "): Unit = {"/*}*/)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteHashElem[_,_,_,_]) =>
        elem.emitterFactory.map(_.scala).get.emitDataDeclaration(elemType(elem), quote(sym), "__act.", quote(sym) + "_data")
        stream.println("val " + quote(sym) + " = {")
        emitBlock(elem.alloc)
        elem.emitterFactory.get.scala.emitInitializeDataStructure(quote(sym), "__act.", quote(getBlockResult(elem.alloc)), quote(sym) + "_data")
        stream.println("}")
      case (sym, elem: DeliteCollectElem[_,_]) =>
        val emitter = elem.emitterScala.getOrElse(standardScalaEmitter)
        elem.emitterFactory.map(_.scala).getOrElse(standardScalaEmitter).emitDataDeclaration(elemType(elem), quote(sym), "__act.", quote(elem.aV), remap(elem.aV.Type))
        stream.println("__act." + quote(sym) + " = {"/*}*/)
        if (elem.emitterScala.isEmpty) {
          emitBlock(elem.alloc)
          stream.println(quote(getBlockResult(elem.alloc)))
        } else {
          emitBlock(elem.allocDataStructure)
          emitter.emitInitializeDataStructure(quote(sym), "__act.", quote(getBlockResult(elem.allocDataStructure)), quote(elem.aV))
        }
        stream.println(/*{*/"}")
      case (sym, elem: DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
    }
    stream.println(/*{*/"}")
    
    stream.println(/*{*/"}")
    //deliteKernel = true
  }
  

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter) = rhs match {
    case op: AbstractFatLoop =>
      if (!deliteKernel) emitInlineAbstractFatLoop(op, symList)
      else emitKernelAbstractFatLoop(op, symList)
    case _ => super.emitFatNode(symList, rhs)
  }

  override def emitFatNodeKernelExtra(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter): Unit = rhs match {
    case op: AbstractFatLoop =>
      stream.println("//activation record for fat loop")
      emitAbstractFatLoopKernelExtra(op, symList)
    case ThinDef(op: AbstractLoop[_]) => 
      stream.println("//activation record for thin loop")
      emitAbstractFatLoopKernelExtra(SimpleFatLoop(op.size, op.v, List(op.body)), symList)
    case _ => 
      super.emitFatNodeKernelExtra(symList, rhs)
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case s:DeliteOpSingleTask[_] => {
      //printlog("EMIT single "+s)
      //val save = deliteKernel
      //deliteKernel = false
      val b = s.block
      if (!deliteKernel) {
        // straight-line
        stream.println("val " + quote(sym) + " = { " )
        emitBlock(b)
        stream.println(quote(getBlockResult(b)))
        stream.println("}")
      }
      else {
        // method wrapper
        stream.println("def " + quote(sym) + "_block = { ")
        emitBlock(b)
        stream.println(quote(getBlockResult(b)))
        stream.println("}")
        stream.println("val " + quote(sym) + " = " + quote(sym) + "_block")
      }
      //deliteKernel = save
    }
    case op: AbstractLoop[_] => 
      // TODO: we'd like to always have fat loops but currently they are not allowed to have effects
      stream.println("// a *thin* loop follows: " + quote(sym))
      emitFatNode(List(sym), SimpleFatLoop(op.size, op.v, List(op.body)))
/*    
      if (!deliteKernel) { // FIXME cond!
        op.body match {
          case elem: DeliteCollectElem[_,_] =>
            stream.println("val " + quote(sym) + " = {")
            emitBlock(elem.alloc)
            stream.println(quote(getBlockResult(elem.alloc)))
            stream.println("}")
          case elem: DeliteReduceElem[_] =>
            stream.println("var " + quotearg(sym) + " = " + quote(elem.zero))
        }
        stream.println("var " + quote(op.v) + " = 0")
        stream.println("while (" + quote(op.v) + " < " + quote(op.size) + ") {  // begin thin loop " + quote(sym))
        op.body match {
          case elem: DeliteCollectElem[_,_] =>
            emitBlock(elem.func)
            stream.println(quote(sym) + ".dcUpdate(" + quote(op.v) + ", " + quote(getBlockResult(elem.func)) + ")")
          case elem: DeliteReduceElem[_] =>
            emitBlock(elem.func)
            stream.println("val " + quote(elem.rV._1) + " = " + quote(sym))
            stream.println("val " + quote(elem.rV._2) + " = " + quote(getBlockResult(elem.func)))
            emitBlock(elem.rFunc)
            stream.println(quote(sym) + " = " + quote(getBlockResult(elem.rFunc)))
        }
        stream.println(quote(op.v) + " += 1")
        stream.println("} // end thin loop " + quote(sym))
      } else {
        stream.println("TODO: thin loop codegen")
      }
*/      
    case foreach:DeliteOpForeach2[_,_] => {
      if (deliteKernel == false){
        //stream.println("def " + quote(sym) + "_block = {")
        stream.println("val " + quote(sym) + " = {")
        stream.println("var forIdx = 0")
        stream.println("while (forIdx < " + quote(getBlockResult(foreach.in)) + ".size) { // begin foreach loop " + quote(sym))
        stream.println("val " + quote(foreach.v) + " = " + quote(getBlockResult(foreach.in)) + ".dcApply(forIdx)")
        emitBlock(foreach.func)
        stream.println(quote(getBlockResult(foreach.func)))
        stream.println("forIdx += 1")
        stream.println("} // end foreach loop " + quote(sym))
        stream.println("}")
        //stream.println("val " + quote(sym) + " = " + quote(sym) + "_block")
      }
      else {
        //deliteKernel = false
        stream.println("val " + quote(sym) + " = new generated.scala.DeliteOpForeach[" + remap(foreach.v.Type) + "] {")
        stream.println("def in = " + quote(getBlockResult(foreach.in)))
        stream.println("def sync(" + quote(foreach.i) + ": " + remap(foreach.i.Type) + ") = {")
        emitBlock(foreach.sync)
        stream.println(quote(getBlockResult(foreach.sync)))
        stream.println("}")
        stream.println("def foreach(" + quote(foreach.v) + ": " + remap(foreach.v.Type) + ") = {")
        emitBlock(foreach.func)
        stream.println(quote(getBlockResult(foreach.func)))
        stream.println("}}")
        //deliteKernel = true
      }
    }
    case foreach:DeliteOpForeachBounded[_,_,_] => {
      if (deliteKernel == false){
        stream.println("def " + quote(sym) + "_block = {")
        stream.println("var forIdx = 0")
        stream.println("while (forIdx < " + quote(getBlockResult(foreach.in.asInstanceOf[Exp[Any]])) + ".size) { // begin foreachBounded loop " + quote(sym))
        stream.println("val " + quote(foreach.v) + " = " + quote(getBlockResult(foreach.in.asInstanceOf[Exp[Any]])) + ".dcApply(forIdx)")
        emitBlock(foreach.func)
        stream.println(quote(getBlockResult(foreach.func)))
        stream.println("forIdx += 1")
        stream.println("} // end foreachBounded loop " + quote(sym))
        stream.println("}")
        stream.println("val " + quote(sym) + " = " + quote(sym) + "_block")
      }
      else {
        //deliteKernel = false
        stream.println("val " + quote(sym) + " = new generated.scala.DeliteOpForeach[" + remap(foreach.v.Type) + "] {")
        stream.println("def in = " + quote(getBlockResult(foreach.in.asInstanceOf[Exp[Any]])))
        stream.println("def sync(" + quote(foreach.i) + ": " + remap(foreach.i.Type) + ") = {")
        emitBlock(foreach.sync)
        stream.println(quote(getBlockResult(foreach.sync)))
        stream.println("}")
        stream.println("def foreach(" + quote(foreach.v) + ": " + remap(foreach.v.Type) + ") = {")
        emitBlock(foreach.func)
        stream.println(quote(getBlockResult(foreach.func)))
        stream.println("}}")
        //deliteKernel = true
      }
    }
    case _ => super.emitNode(sym,rhs)
  }
  
}

trait CudaGenDeliteOps extends CudaGenLoopsFat with BaseGenDeliteOps {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter) = rhs match {
    case op: AbstractFatLoop =>
      if (forceParallel) emitKernelAbstractFatLoop2(op, symList)
      else if (deliteKernel) emitKernelAbstractFatLoop(op, symList)
      else emitInlineAbstractFatLoop(op, symList)
    case _ => super.emitFatNode(symList, rhs)
  }

  def emitMultiLoopFuncs(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    val elemFuncs = op.body flatMap { // don't emit dependencies twice!
      case elem: DeliteCollectElem[_,_] => elem.func :: elem.cond
      case elem: DeliteForeachElem[_] => List(elem.func) 
      case elem: DeliteReduceElem[_] => elem.func :: elem.cond
      case elem: DeliteReduceTupleElem[_,_] => elem.func._1 :: elem.func._2 :: elem.cond
      case _ => throw new GenerationFailedException("CudaGen: ReduceTupleElem is not supported yet.")
    }
    //println("starting to call emitFatBlock in emitMultiLoopFuncs")
    emitFatBlock(elemFuncs)
    //println("finished calling emitFatBlock in emitMultiLoopsFuncs")
  }

  def emitCollectElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteCollectElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty) {
      throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet.")
      /*
      if (deliteKernel)
        stream.println(prefixSym + quote(sym) + "_buf_append(" + quote(getBlockResult(elem.func)) + ")")
      else
        stream.println(prefixSym + quote(sym) + ".insert(" + prefixSym + quote(sym) + ".length, " + quote(getBlockResult(elem.func)) + ")")
      */
    } 
    else {
      stream.println(prefixSym + quote(sym) + ".dcUpdate(" + quote(op.v) + ", " + quote(getBlockResult(elem.func)) + "); // emitCollectElem")
    }
  }
  
  def emitForeachElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteForeachElem[_])(implicit stream: PrintWriter) {
    stream.println(quote(getBlockResult(elem.func)))
  }
  
  def emitFirstReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
      if (elem.cond.nonEmpty) {
        stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
        stream.println(quote(getBlockResult(elem.func)))
        stream.println("} else {")
        stream.println(prefixSym + quote(sym) + "_zero")
        stream.println("}")
      }
      else {
        stream.println(quote(getBlockResult(elem.func)))        
      }
  }

  def emitReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
      emitReduction(op, sym, elem, prefixSym)
      stream.println("}")
    }
    else {
      //println("before")
      emitReduction(op, sym, elem, prefixSym)
      //println("after red")
    }
  }
 def emitReduceTupleElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      sys.error("tuple reduce with external conditions not implemented!")
    }
    else {
      emitReductionTuple(op, sym, elem, prefixSym)
    }
  }
/*
  def emitInitializeOrReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("// TODO: we could optimize this check away with more convoluted runtime support if necessary")          
    stream.println("if (" + prefixSym + quote(sym) + " == " + prefixSym + quote(sym) + "_zero" + ") " + prefixSym + quote(sym) + " = {")
    
    // initialize
    stream.println(quote(getBlockResult(elem.func)))
    stream.println("}")
    // or reduce
    stream.println("else {")
    emitReduction(op, sym, elem, prefixSym)
    stream.println("}")
  } 
  */

  def emitReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("%s %s = %s;".format(remap(elem.rV._1.Type),quote(elem.rV._1),prefixSym+quote(sym)))
    stream.println("%s %s = %s;".format(remap(elem.rV._2.Type),quote(elem.rV._2),quote(getBlockResult(elem.func))))
    emitBlock(elem.rFunc)
    stream.println(prefixSym + quote(sym) + " = " + quote(getBlockResult(elem.rFunc)) + ";")
  }

  def emitReductionTuple(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String)(implicit stream: PrintWriter) {
    val rV = elem.rVSeq
    val rFunc = elem.rFuncSeq

    stream.println("%s %s = %s;".format(remap(rV._1._1.Type),quote(rV._1._1),prefixSym+quote(sym)))
    stream.println("%s %s = %s_2;".format(remap(rV._1._2.Type),quote(rV._1._2),prefixSym+quote(sym)))
    stream.println("%s %s = %s;".format(remap(rV._2._1.Type),quote(rV._2._1),quote(getBlockResult(elem.func._1))))
    stream.println("%s %s = %s;".format(remap(rV._2._2.Type),quote(rV._2._2),quote(getBlockResult(elem.func._2))))
    emitFatBlock(List(rFunc._1, rFunc._2))
    stream.println(prefixSym + quote(sym) + "   = " + quote(getBlockResult(rFunc._1)) + ";")
    stream.println(prefixSym + quote(sym) + "_2 = " + quote(getBlockResult(rFunc._2)) + ";")
  }

  def emitInlineAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    forceParallel = false
    (symList zip op.body) foreach {
      //TODO: Check if primitive type operations
      case (sym, elem: DeliteCollectElem[_,_]) =>
        emitBlock(elem.alloc)
        stream.println("%s %s = %s;".format(remap(sym.Type),quote(sym),quote(getBlockResult(elem.alloc))))
        //throw new GenerationFailedException("CudaGen: Inlined DeliteCollectElem is not supported yet due to memory allocations.")
      case (sym, elem: DeliteForeachElem[_]) =>
        //throw new GenerationFailedException("CudaGen: Inlined DeliteForeachElem is not supported yet due to memory allocations.")
        //TODO: Why do we need this?
        //stream.println("var " + quotearg(sym) + " = ()") // must be type Unit
      case (sym, elem: DeliteReduceElem[_]) =>
        val (zeroFunc,freeVars) = emitDevFunc(elem.zero, List())
        if(freeVars.length == 0)
          stream.println("%s %s = %s();".format(remap(sym.Type),quote(sym),zeroFunc))
        else
          stream.println("%s %s = %s(%s);".format(remap(sym.Type),quote(sym),zeroFunc,freeVars))
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        val (zeroFunc_1,freeVars_1) = emitDevFunc(elem.zero._1, List())
        if(freeVars_1.length == 0)
          stream.println("%s %s = %s();".format(remap(elem.zero._1.Type),quote(sym),zeroFunc_1))
        else
          stream.println("%s %s = %s(%s);".format(remap(elem.zero._1.Type),quote(sym),zeroFunc_1,freeVars_1))
        val (zeroFunc_2,freeVars_2) = emitDevFunc(elem.zero._2, List())
        if(freeVars_2.length == 0)
          stream.println("%s %s_2 = %s();".format(remap(elem.zero._2.Type),quote(sym),zeroFunc_2))
        else
          stream.println("%s %s_2 = %s(%s);".format(remap(elem.zero._2.Type),quote(sym),zeroFunc_2,freeVars_2))
      case _ =>
        throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet." + symList.map(quote).mkString(""))
    }
    stream.println("int " + quote(op.v) + " = 0;")

        /*
    if (op.body exists (loopBodyNeedsStripFirst _)) {
      stream.println("if (" + quote(op.size) + " > 0) { // prerun fat loop " + symList.map(quote).mkString(","))
      emitMultiLoopFuncs(op, symList)
      (symList zip op.body) foreach {
        case (sym, elem: DeliteCollectElem[_,_]) =>
          emitCollectElem(op, sym, elem)
        case (sym, elem: DeliteForeachElem[_]) => 
          stream.println(quote(sym) + " = {")
          emitForeachElem(op, sym, elem)
          stream.println("}")
        case (sym, elem: DeliteReduceElem[_]) =>
          if (elem.stripFirst) {
            stream.println(quote(sym) + " = {")
            emitFirstReduceElem(op, sym, elem)
            stream.println("}")
          }
          else {
            emitReduceElem(op, sym, elem)
          }
      }
      stream.println("}")
      stream.println(quote(op.v) + " = 1;")
    }
  */

    stream.println("while (" + quote(op.v) + " < " + quote(op.size) + ") {  // begin fat loop " + symList.map(quote).mkString(","))
    // body
    emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteCollectElem[_,_]) =>
        emitCollectElem(op, sym, elem)
        stream.println("//start emitCollectElem")
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("//start emitForeachElem")
      case (sym, elem: DeliteReduceElem[_]) =>
        stream.println("//start emitReduceElem")
        emitReduceElem(op, sym, elem)
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        stream.println("//start emitReduceTupleElem")
        emitReduceTupleElem(op, sym, elem)
      case _ =>
        throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet.")
    }
    stream.println(quote(op.v) + " += 1;")
    stream.println(/*{*/"} // end fat loop " + symList.map(quote).mkString(","))
  }

  def emitKernelAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("CudaGen: Only 1 output is supported for FatLoop (No fusing yet).")

    //println("Entering emitKernelFatLoop")

    val serializeLoop = {
      (symList.length==1) && (op.body(0).isInstanceOf[DeliteReduceElem[_]]) //&& (!isPrimitiveType(op.body(0).asInstanceOf[DeliteReduceElem[_]].zero.Type))
    }

    if(serializeLoop) {
      //println("//serializing the loop " + quote(symList(0)))
      forceParallel = true
      stream.println("for (int %s = 0; %s < %s; %s++) {\n".format(quote(op.v),quote(op.v),quote(op.size),quote(op.v)))
      //println("1, before emitMultiLoopsFuncs:  size is " + symList.length)
      emitMultiLoopFuncs(op, symList)
      //println("1, after emitMultiLoopsFuncs")
    }
    else {
      currDim += 1
      val currDimStr = getCurrDimStr()
      setCurrDimLength(quote(op.size))
      stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))
      //optimizer.registerAlias(currDimStr,quote(op.v))
      stream.println(addTab()+"if( %s < %s ) {".format(quote(op.v), quote(op.size)))
      tabWidth += 1
      forceParallel = false
      emitMultiLoopFuncs(op, symList)
    }
      (symList zip op.body) foreach {
        case (sym, elem:DeliteCollectElem[_,_]) =>
          emitAllocFunc(sym,elem.alloc)
          stream.println(addTab()+"%s.dcUpdate(%s, %s);".format(quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
          //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
          //if(freeVars.length==0) {
          //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s));".format(quote(sym),currDimStr,loopFunc,currDimStr))
          //}
          //else {
          //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s,%s));".format(quote(sym),currDimStr,loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
          //}
        case (sym, elem:DeliteForeachElem[_]) =>
          //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
          //if(freeVars.length==0)
          //  stream.println(addTab()+"%s(%s);".format(loopFunc,currDimStr))
          //else
          //  stream.println(addTab()+"%s(%s,%s);".format(loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
        case (sym, elem: DeliteReduceElem[_]) =>
          if(isPrimitiveType(elem.zero.Type)) throw new GenerationFailedException("CudaGen: DeliteReduceElem is not supported yet.")
          val isAliased = checkAlias(elem.zero)
          assert(getBlockResult(elem.zero).isInstanceOf[Sym[Any]])
          if(!isAliased) emitAllocFunc(getBlockResult(elem.zero).asInstanceOf[Sym[Any]],elem.zero)
          else stream.println("%s %s_zero = %s;".format(remap(sym.Type),quote(sym),quote(getBlockResult(elem.zero))))
          emitCloneFunc(sym,getBlockResult(elem.zero).asInstanceOf[Sym[_]])
          emitReduceElem(op, sym, elem)
        case _ =>
          throw new GenerationFailedException("CudaGen: DeliteReduceTupleElem is not supported yet.")
    }
    tabWidth -= 1
    stream.println(addTab()+"}")
    currDim -= 1
  }

  def emitKernelAbstractFatLoop2(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("CudaGen: Only 1 output is supported for FatLoop (No fusing yet).")
    //println("Entering emitKernelAbstractFatLoop2 ")

    forceParallel = false

    val serializeLoop = {
      (symList.length==1) && (op.body(0).isInstanceOf[DeliteReduceElem[_]]) //&& (!isPrimitiveType(op.body(0).asInstanceOf[DeliteReduceElem[_]].zero.Type))
    }

    if(serializeLoop) {
      throw new GenerationFailedException("CudaGen: Nested serialize is not supported yet.")
    }

    currDim += 1
    val currDimStr = getCurrDimStr()
    setCurrDimLength(quote(op.size))
    stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))
    //optimizer.registerAlias(currDimStr,quote(op.v))
    stream.println(addTab()+"if( %s < %s ) {".format(quote(op.v), quote(op.size)))
    tabWidth += 1
    emitMultiLoopFuncs(op, symList)

    //println("entering foreach in 2")
    //println("How many? : " + symList.length)
    (symList zip op.body) foreach {
      case (sym, elem:DeliteCollectElem[_,_]) =>
        //println("in 2, collect" + quote(sym))
        emitAllocFunc(sym,elem.alloc)
        stream.println(addTab()+"%s.dcUpdate(%s, %s);".format(quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0) {
        //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s));".format(quote(sym),currDimStr,loopFunc,currDimStr))
        //}
        //else {
        //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s,%s));".format(quote(sym),currDimStr,loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
        //}
      case (sym, elem:DeliteForeachElem[_]) =>
        //println("int 2, foreach")
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0)
        //  stream.println(addTab()+"%s(%s);".format(loopFunc,currDimStr))
        //else
        //  stream.println(addTab()+"%s(%s,%s);".format(loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
      case (sym, elem: DeliteReduceElem[_]) =>
        //println("in 2, reduce")
        if(isPrimitiveType(elem.zero.Type)) throw new GenerationFailedException("CudaGen: DeliteReduceElem is not supported yet.")
        val isAliased = checkAlias(elem.zero)
        assert(getBlockResult(elem.zero).isInstanceOf[Sym[Any]])
        if(!isAliased) { println("calling emitAllocfunc with sym:" + quote(getBlockResult(elem.zero))); emitAllocFunc(getBlockResult(elem.zero).asInstanceOf[Sym[Any]],elem.zero)  }
        else stream.println("%s %s_zero = %s;".format(remap(sym.Type),quote(sym),quote(getBlockResult(elem.zero))))
        //println("calling clone with sym : " + quote(sym))
        emitCloneFunc(sym,getBlockResult(elem.zero).asInstanceOf[Sym[Any]])
        emitReduceElem(op, sym, elem)
      case _ =>
        throw new GenerationFailedException("CudaGen: DeliteReduceTupleElem is not supported yet.")
    }
    //println("finished foreach in 2")
    tabWidth -= 1
    stream.println(addTab()+"}")
    currDim -= 1
    forceParallel = true
  }

  def checkAlias(exp: Exp[Any]): Boolean = {
    false
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case s:DeliteOpSingleTask[_] => {
      val save = deliteKernel
      val b = s.block
      if (!save) {
        val (singleFunc, freeVars) = emitDevFunc(b,List())
        //val currDimStr = getCurrDimStr()
        stream.println(addTab()+"%s %s = %s(%s);".format(remap(sym.Type),quote(sym),singleFunc,freeVars.map(quote).mkString(",")))
      }
      else {
    	throw new GenerationFailedException("CudaGen: DeliteOpSingleTask is not GPUable." + quote(sym))
      }
    }
    
    case op: AbstractLoop[_] => 
      // TODO: we'd like to always have fat loops but currently they are not allowed to have effects
      stream.println("// a *thin* loop follows: " + quote(sym))
      stream.println("//here comes")
      emitFatNode(List(sym), SimpleFatLoop(op.size, op.v, List(op.body)))
      stream.println("//here comes done")

    case foreach:DeliteOpForeach2[_,_] => {
      if(!isPrimitiveType(foreach.v.Type)) throw new GenerationFailedException("CudaGen: Only primitive Types are allowed for input of foreach.")
      currDim += 1
      setCurrDimLength(quote(foreach.in)+"->size()")
      val currDimStr = getCurrDimStr()
      stream.println(addTab()+"if( %s < %s ) {".format(currDimStr,quote(foreach.in)+".size()"))
      tabWidth += 1
      val (foreachFunc,freeVars) = emitDevFunc(foreach.func, List(foreach.v))
      if(freeVars.length==0)
        stream.println(addTab()+"%s(%s.dcApply(%s));".format(foreachFunc,quote(foreach.in),currDimStr))
      else
        stream.println(addTab()+"%s(%s.dcApply(%s),%s);".format(foreachFunc,quote(foreach.in),currDimStr,freeVars.map(quote).mkString(",")))
      tabWidth -= 1
      stream.println(addTab()+"}")
      currDim -= 1
    }

    case _ => super.emitNode(sym,rhs)
  }

}

trait OpenCLGenDeliteOps extends OpenCLGenEffect with BaseGenDeliteOps {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter) = rhs match {
    case op: AbstractFatLoop =>
      if (forceParallel) emitKernelAbstractFatLoop2(op, symList)
      else if (deliteKernel) emitKernelAbstractFatLoop(op, symList)
      else emitInlineAbstractFatLoop(op, symList)
    case _ => super.emitFatNode(symList, rhs)
  }

  def emitMultiLoopFuncs(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    val elemFuncs = op.body flatMap { // don't emit dependencies twice!
      case elem: DeliteCollectElem[_,_] => elem.func :: elem.cond
      case elem: DeliteForeachElem[_] => List(elem.func)
      case elem: DeliteReduceElem[_] => elem.func :: elem.cond
      case elem: DeliteReduceTupleElem[_,_] => elem.func._1 :: elem.func._2 :: elem.cond
      case _ => throw new GenerationFailedException("CudaGen: ReduceTupleElem is not supported yet.")
    }
    //println("starting to call emitFatBlock in emitMultiLoopFuncs")
    emitFatBlock(elemFuncs)
    //println("finished calling emitFatBlock in emitMultiLoopsFuncs")
  }

  def emitCollectElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteCollectElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty) {
      throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet.")
      /*
      if (deliteKernel)
        stream.println(prefixSym + quote(sym) + "_buf_append(" + quote(getBlockResult(elem.func)) + ")")
      else
        stream.println(prefixSym + quote(sym) + ".insert(" + prefixSym + quote(sym) + ".length, " + quote(getBlockResult(elem.func)) + ")")
      */
    }
    else {
      stream.println(prefixSym + quote(sym) + ".dcUpdate(" + quote(op.v) + ", " + quote(getBlockResult(elem.func)) + "); // emitCollectElem")
    }
  }

  def emitForeachElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteForeachElem[_])(implicit stream: PrintWriter) {
    stream.println(quote(getBlockResult(elem.func)))
  }

  def emitFirstReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
      if (elem.cond.nonEmpty) {
        stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
        stream.println(quote(getBlockResult(elem.func)))
        stream.println("} else {")
        stream.println(prefixSym + quote(sym) + "_zero")
        stream.println("}")
      }
      else {
        stream.println(quote(getBlockResult(elem.func)))
      }
  }

 def emitReduceTupleElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      sys.error("tuple reduce with external conditions not implemented!")
    }
    else {
      emitReductionTuple(op, sym, elem, prefixSym)
    }
  }

  def emitReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
      emitReduction(op, sym, elem, prefixSym)
      stream.println("}")
    }
    else {
      //println("before")
      emitReduction(op, sym, elem, prefixSym)
      //println("after red")
    }
  }

/*
  def emitInitializeOrReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("// TODO: we could optimize this check away with more convoluted runtime support if necessary")
    stream.println("if (" + prefixSym + quote(sym) + " == " + prefixSym + quote(sym) + "_zero" + ") " + prefixSym + quote(sym) + " = {")

    // initialize
    stream.println(quote(getBlockResult(elem.func)))
    stream.println("}")
    // or reduce
    stream.println("else {")
    emitReduction(op, sym, elem, prefixSym)
    stream.println("}")
  }
  */

  def emitReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("%s %s = %s;".format(remap(elem.rV._1.Type),quote(elem.rV._1),prefixSym+quote(sym)))
    stream.println("%s %s = %s;".format(remap(elem.rV._2.Type),quote(elem.rV._2),quote(getBlockResult(elem.func))))
    emitBlock(elem.rFunc)
    stream.println(prefixSym + quote(sym) + " = " + quote(getBlockResult(elem.rFunc)) + ";")
  }

  def emitReductionTuple(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceTupleElem[_,_], prefixSym: String)(implicit stream: PrintWriter) {
    val rV = elem.rVSeq
    val rFunc = elem.rFuncSeq

    stream.println("%s %s = %s;".format(remap(rV._1._1.Type),quote(rV._1._1),prefixSym+quote(sym)))
    stream.println("%s %s = %s_2;".format(remap(rV._1._2.Type),quote(rV._1._2),prefixSym+quote(sym)))
    stream.println("%s %s = %s;".format(remap(rV._2._1.Type),quote(rV._2._1),quote(getBlockResult(elem.func._1))))
    stream.println("%s %s = %s;".format(remap(rV._2._2.Type),quote(rV._2._2),quote(getBlockResult(elem.func._2))))
    emitFatBlock(List(rFunc._1, rFunc._2))
    stream.println(prefixSym + quote(sym) + "   = " + quote(getBlockResult(rFunc._1)) + ";")
    stream.println(prefixSym + quote(sym) + "_2 = " + quote(getBlockResult(rFunc._2)) + ";")
  }

  def emitInlineAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    forceParallel = false
    (symList zip op.body) foreach {
      //TODO: Check if primitive type operations
      case (sym, elem: DeliteCollectElem[_,_]) =>
        throw new GenerationFailedException("CudaGen: Inlined DeliteCollectElem is not supported yet due to memory allocations.")
      case (sym, elem: DeliteForeachElem[_]) =>
        throw new GenerationFailedException("CudaGen: Inlined DeliteForeachElem is not supported yet due to memory allocations.")
        //TODO: Why do we need this?
        //stream.println("var " + quotearg(sym) + " = ()") // must be type Unit
      case (sym, elem: DeliteReduceElem[_]) =>
        val (zeroFunc,freeVars) = emitDevFunc(elem.zero, List())
        if(freeVars.length == 0)
          stream.println("%s %s = %s();".format(remap(sym.Type),quote(sym),zeroFunc))
        else
          stream.println("%s %s = %s(%s);".format(remap(sym.Type),quote(sym),zeroFunc,freeVars))
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        val (zeroFunc_1,freeVars_1) = emitDevFunc(elem.zero._1, List())
        if(freeVars_1.length == 0)
          stream.println("%s %s = %s();".format(remap(elem.zero._1.Type),quote(sym),zeroFunc_1))
        else
          stream.println("%s %s = %s(%s);".format(remap(elem.zero._1.Type),quote(sym),zeroFunc_1,freeVars_1))
        val (zeroFunc_2,freeVars_2) = emitDevFunc(elem.zero._2, List())
        if(freeVars_2.length == 0)
          stream.println("%s %s_2 = %s();".format(remap(elem.zero._2.Type),quote(sym),zeroFunc_2))
        else
          stream.println("%s %s_2 = %s(%s);".format(remap(elem.zero._2.Type),quote(sym),zeroFunc_2,freeVars_2))
      case _ =>
        throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet.")
    }
    stream.println("int " + quote(op.v) + " = 0;")

        /*
    if (op.body exists (loopBodyNeedsStripFirst _)) {
      stream.println("if (" + quote(op.size) + " > 0) { // prerun fat loop " + symList.map(quote).mkString(","))
      emitMultiLoopFuncs(op, symList)
      (symList zip op.body) foreach {
        case (sym, elem: DeliteCollectElem[_,_]) =>
          emitCollectElem(op, sym, elem)
        case (sym, elem: DeliteForeachElem[_]) =>
          stream.println(quote(sym) + " = {")
          emitForeachElem(op, sym, elem)
          stream.println("}")
        case (sym, elem: DeliteReduceElem[_]) =>
          if (elem.stripFirst) {
            stream.println(quote(sym) + " = {")
            emitFirstReduceElem(op, sym, elem)
            stream.println("}")
          }
          else {
            emitReduceElem(op, sym, elem)
          }
      }
      stream.println("}")
      stream.println(quote(op.v) + " = 1;")
    }
  */

    stream.println("while (" + quote(op.v) + " < " + quote(op.size) + ") {  // begin fat loop " + symList.map(quote).mkString(","))
    // body
    emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      case (sym, elem: DeliteCollectElem[_,_]) =>
        stream.println("//start emitCollectElem")
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("//start emitForeachElem")
      case (sym, elem: DeliteReduceElem[_]) =>
        stream.println("//start emitReduceElem")
        emitReduceElem(op, sym, elem)
      case (sym, elem: DeliteReduceTupleElem[_,_]) =>
        stream.println("//start emitReduceTupleElem")
        emitReduceTupleElem(op, sym, elem)
      case _ =>
        throw new GenerationFailedException("CudaGen: emitCollectElem with condition (filter) is not supported yet.")
    }
    stream.println(quote(op.v) + " += 1;")
    stream.println(/*{*/"} // end fat loop " + symList.map(quote).mkString(","))
  }

  def emitKernelAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("CudaGen: Only 1 output is supported for FatLoop (No fusing yet).")

    //println("Entering emitKernelFatLoop")

    val serializeLoop = {
      (symList.length==1) && (op.body(0).isInstanceOf[DeliteReduceElem[_]]) //&& (!isPrimitiveType(op.body(0).asInstanceOf[DeliteReduceElem[_]].zero.Type))
    }

    if(serializeLoop) {
      //println("//serializing the loop " + quote(symList(0)))
      forceParallel = true
      stream.println("for (int %s = 0; %s < %s; %s++) {\n".format(quote(op.v),quote(op.v),quote(op.size),quote(op.v)))
      //println("1, before emitMultiLoopsFuncs:  size is " + symList.length)
      emitMultiLoopFuncs(op, symList)
      //println("1, after emitMultiLoopsFuncs")
    }
    else {
      currDim += 1
      val currDimStr = getCurrDimStr()
      setCurrDimLength(quote(op.size))
      stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))
      //optimizer.registerAlias(currDimStr,quote(op.v))
      stream.println(addTab()+"if( %s < %s ) {".format(quote(op.v), quote(op.size)))
      tabWidth += 1
      forceParallel = false
      emitMultiLoopFuncs(op, symList)
    }
      (symList zip op.body) foreach {
        case (sym, elem:DeliteCollectElem[_,_]) =>
          emitAllocFunc(sym,elem.alloc)
          stream.println(addTab()+"%s_dcUpdate(%s, %s, %s);".format(remap(sym.Type),quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
          //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
          //if(freeVars.length==0) {
          //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s));".format(quote(sym),currDimStr,loopFunc,currDimStr))
          //}
          //else {
          //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s,%s));".format(quote(sym),currDimStr,loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
          //}
        case (sym, elem:DeliteForeachElem[_]) =>
          //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
          //if(freeVars.length==0)
          //  stream.println(addTab()+"%s(%s);".format(loopFunc,currDimStr))
          //else
          //  stream.println(addTab()+"%s(%s,%s);".format(loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
        case (sym, elem: DeliteReduceElem[_]) =>
          if(isPrimitiveType(elem.zero.Type)) throw new GenerationFailedException("CudaGen: DeliteReduceElem is not supported yet.")
          val isAliased = checkAlias(elem.zero)
          assert(getBlockResult(elem.zero).isInstanceOf[Sym[Any]])
          if(!isAliased) emitAllocFunc(getBlockResult(elem.zero).asInstanceOf[Sym[Any]],elem.zero)
          else stream.println("%s %s_zero = %s;".format(remap(sym.Type),quote(sym),quote(getBlockResult(elem.zero))))
          emitCloneFunc(sym,getBlockResult(elem.zero).asInstanceOf[Sym[_]])
          emitReduceElem(op, sym, elem)
        case _ =>
          throw new GenerationFailedException("CudaGen: DeliteReduceTupleElem is not supported yet.")
    }
    tabWidth -= 1
    stream.println(addTab()+"}")
    currDim -= 1
  }

  def emitKernelAbstractFatLoop2(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("CudaGen: Only 1 output is supported for FatLoop (No fusing yet).")
    //println("Entering emitKernelAbstractFatLoop2 ")

    forceParallel = false

    val serializeLoop = {
      (symList.length==1) && (op.body(0).isInstanceOf[DeliteReduceElem[_]]) //&& (!isPrimitiveType(op.body(0).asInstanceOf[DeliteReduceElem[_]].zero.Type))
    }

    if(serializeLoop) {
      throw new GenerationFailedException("CudaGen: Nested serialize is not supported yet.")
    }

    currDim += 1
    val currDimStr = getCurrDimStr()
    setCurrDimLength(quote(op.size))
    stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))
    //optimizer.registerAlias(currDimStr,quote(op.v))
    stream.println(addTab()+"if( %s < %s ) {".format(quote(op.v), quote(op.size)))
    tabWidth += 1
    emitMultiLoopFuncs(op, symList)

    //println("entering foreach in 2")
    //println("How many? : " + symList.length)
    (symList zip op.body) foreach {
      case (sym, elem:DeliteCollectElem[_,_]) =>
        //println("in 2, collect" + quote(sym))
        emitAllocFunc(sym,elem.alloc)
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s);".format(remap(sym.Type),quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0) {
        //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s));".format(quote(sym),currDimStr,loopFunc,currDimStr))
        //}
        //else {
        //  stream.println(addTab()+"%s.dcUpdate(%s, %s(%s,%s));".format(quote(sym),currDimStr,loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
        //}
      case (sym, elem:DeliteForeachElem[_]) =>
        //println("int 2, foreach")
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0)
        //  stream.println(addTab()+"%s(%s);".format(loopFunc,currDimStr))
        //else
        //  stream.println(addTab()+"%s(%s,%s);".format(loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
      case (sym, elem: DeliteReduceElem[_]) =>
        //println("in 2, reduce")
        if(isPrimitiveType(elem.zero.Type)) throw new GenerationFailedException("CudaGen: DeliteReduceElem is not supported yet.")
        val isAliased = checkAlias(elem.zero)
        assert(getBlockResult(elem.zero).isInstanceOf[Sym[Any]])
        if(!isAliased) emitAllocFunc(getBlockResult(elem.zero).asInstanceOf[Sym[Any]],elem.zero)
        else stream.println("%s %s_zero = %s;".format(remap(sym.Type),quote(sym),quote(getBlockResult(elem.zero))))
        //println("calling clone with sym : " + quote(sym))
        emitCloneFunc(sym,getBlockResult(elem.zero).asInstanceOf[Sym[Any]])
        emitReduceElem(op, sym, elem)

      case _ =>
        throw new GenerationFailedException("CudaGen: DeliteReduceTupleElem is not supported yet.")
    }
    //println("finished foreach in 2")
    tabWidth -= 1
    stream.println(addTab()+"}")
    currDim -= 1
    forceParallel = true
  }

  def checkAlias(exp: Exp[Any]): Boolean = {
    false
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case s:DeliteOpSingleTask[_] => {
      val save = deliteKernel
      val b = s.block
      if (!save) {
        val (singleFunc, freeVars) = emitDevFunc(b,List())
        //val currDimStr = getCurrDimStr()
        stream.println(addTab()+"%s %s = %s(%s);".format(remap(sym.Type),quote(sym),singleFunc,freeVars.map(quote).mkString(",")))
      }
      else {
    	throw new GenerationFailedException("CudaGen: DeliteOpSingleTask is not GPUable." + quote(sym))
      }
    }

    case op: AbstractLoop[_] =>
      // TODO: we'd like to always have fat loops but currently they are not allowed to have effects
      stream.println("// a *thin* loop follows: " + quote(sym))
      stream.println("//here comes")
      emitFatNode(List(sym), SimpleFatLoop(op.size, op.v, List(op.body)))
      stream.println("//here comes done")

    case foreach:DeliteOpForeach2[_,_] => {
      if(!isPrimitiveType(foreach.v.Type)) throw new GenerationFailedException("CudaGen: Only primitive Types are allowed for input of foreach.")
      currDim += 1
      setCurrDimLength(quote(foreach.in)+"->size()")
      val currDimStr = getCurrDimStr()
      stream.println(addTab()+"if( %s < %s ) {".format(currDimStr,quote(foreach.in)+".size()"))
      tabWidth += 1
      val (foreachFunc,freeVars) = emitDevFunc(foreach.func, List(foreach.v))
      if(freeVars.length==0)
        stream.println(addTab()+"%s(%s_dcApply(%s,%s));".format(foreachFunc,remap(foreach.in.Type),quote(foreach.in),currDimStr))
      else
        stream.println(addTab()+"%s(%s_dcApply(%s,%s),%s);".format(foreachFunc,remap(foreach.in.Type),quote(foreach.in),currDimStr,freeVars.map(quote).mkString(",")))
      tabWidth -= 1
      stream.println(addTab()+"}")
      currDim -= 1
    }

    case _ => super.emitNode(sym,rhs)
  }

  /*
  //var gpuVariant = true

  def isOuterSerial(op:AbstractFatLoop,symList:List[Sym[Any]]): Boolean = {
    var ret = false
    (symList zip op.body) foreach {
      //case (sym, DeliteCollectElem[_,_]) =>
      case (sym, elem:DeliteReduceElem[_]) =>
        if(!isPrimitiveType(elem.rV._1.Type)) ret = true
      case _ =>
    }
    ret
  }

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter) = rhs match {
    case op: AbstractFatLoop =>
      if (!deliteKernel) emitInlineAbstractFatLoop(op, symList)
      //else if(isOuterSerial(op,symList)) emitKernelAbstractFatLoop2(op,symList)
      else emitKernelAbstractFatLoop(op, symList)
    case _ => super.emitFatNode(symList, rhs)
  }

  def emitMultiLoopFuncs(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    val elemFuncs = op.body flatMap { // don't emit dependencies twice!
      case elem: DeliteCollectElem[_,_] => elem.func :: elem.cond
      case elem: DeliteForeachElem[_] => List(elem.func)
      case elem: DeliteReduceElem[_] => elem.func :: elem.cond
    }
    emitFatBlock(elemFuncs)
  }

  def emitCollectElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteCollectElem[_,_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty) {
      throw new GenerationFailedException("OpenCLGen: emitCollectElem with condition (filter) is not supported for OpenCL.")
      /*
      stream.print("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") ")
      if (deliteKernel)
        stream.println(prefixSym + quote(sym) + "_buf_append(" + quote(getBlockResult(elem.func)) + ")")
      else
        stream.println(prefixSym + quote(sym) + ".insert(" + prefixSym + quote(sym) + ".length, " + quote(getBlockResult(elem.func)) + ")")
      */
    }
    else {
      stream.println(prefixSym + quote(sym) + ".dcUpdate(" + quote(op.v) + ", " + quote(getBlockResult(elem.func)) + "); // emitCollectElem")
    }
  }

  def emitForeachElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteForeachElem[_])(implicit stream: PrintWriter) {
    stream.println(quote(getBlockResult(elem.func)))
  }

  // Reduction generation
  def emitFirstReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
      if (elem.cond.nonEmpty) {
        stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {")
        stream.println(quote(getBlockResult(elem.func)))
        stream.println("} else {")
        stream.println(prefixSym + quote(sym) + "_zero")
        stream.println("}")
      }
      else {
        stream.println(quote(getBlockResult(elem.func)))
      }
  }

  def emitReduceElem(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    if (elem.cond.nonEmpty){
      stream.println("if (" + elem.cond.map(c=>quote(getBlockResult(c))).mkString(" && ") + ") {"/*}*/)
      //if (elem.stripFirst)
      //  emitInitializeOrReduction(op, sym, elem, prefixSym)
      //else
        emitReduction(op, sym, elem, prefixSym)
      stream.println("}")
    }
    else {
      emitReduction(op, sym, elem, prefixSym)
    }
  }

/*
  def emitInitializeOrReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("// TODO: we could optimize this check away with more convoluted runtime support if necessary")
    stream.println("if (" + prefixSym + quote(sym) + " == " + prefixSym + quote(sym) + "_zero" + ") " + prefixSym + quote(sym) + " = {")

    // initialize
    stream.println(quote(getBlockResult(elem.func)))
    stream.println("}")
    // or reduce
    stream.println("else {")
    emitReduction(op, sym, elem, prefixSym)
    stream.println("}")
  }
  */
  //def emitReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    //val (reducFunc,freeVars) = emitDevFunc(elem.rFunc, List(elem.rV._1,elem.rV._2))
    //if(freeVars.length == 0)
    //  stream.println("%s %s = %s(%s,%s);".format(remap(sym.Type),quote(sym),reducFunc,elem.rV._1,elem.rV._2))
    //else
    //  stream.println("%s %s = %s(%s,%s,%s);".format(remap(sym.Type),quote(sym),reducFunc,elem.rV._1,elem.rV._2,freeVars))
  //}
  def emitReduction(op: AbstractFatLoop, sym: Sym[Any], elem: DeliteReduceElem[_], prefixSym: String = "")(implicit stream: PrintWriter) {
    stream.println("%s %s = %s;".format(remap(elem.rV._1.Type),quote(elem.rV._1),prefixSym+quote(sym)))
    stream.println("%s %s = %s;".format(remap(elem.rV._2.Type),quote(elem.rV._2),quote(getBlockResult(elem.func))))
    emitBlock(elem.rFunc)
    stream.println(prefixSym + quote(sym) + " = " + quote(getBlockResult(elem.rFunc)) + ";")
  }

  def emitInlineAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    // Check if all the operations are primitive type operations

    // initialization
    (symList zip op.body) foreach {
      case (sym, elem: DeliteCollectElem[_,_]) =>
        throw new GenerationFailedException("OpenCLGen: Nested DeliteOpMap is not supported")
      case (sym, elem: DeliteForeachElem[_]) =>
        throw new GenerationFailedException("OpenCLGen: Nested DeliteOpMap is not supported")
        //TODO: Why needed?
        //stream.println("var " + quotearg(sym) + " = ()") // must be type Unit
      case (sym, elem: DeliteReduceElem[_]) =>
        val (zeroFunc,freeVars) = emitDevFunc(elem.zero, List())
        if(freeVars.length == 0)
          stream.println("%s %s = %s();".format(remap(sym.Type),quote(sym),zeroFunc))
        else
          stream.println("%s %s = %s(%s);".format(remap(sym.Type),quote(sym),zeroFunc,freeVars))
    }
    stream.println("int " + quote(op.v) + " = 0;")
      /*
    if (op.body exists (loopBodyNeedsStripFirst _)) {
      stream.println("if (" + quote(op.size) + " > 0) { // prerun fat loop " + symList.map(quote).mkString(","))
      /* strip first iteration */
      emitMultiLoopFuncs(op, symList)
      (symList zip op.body) foreach {
        case (sym, elem: DeliteCollectElem[_,_]) =>
          emitCollectElem(op, sym, elem)
        case (sym, elem: DeliteForeachElem[_]) =>
          //stream.println(quote(sym) + " = {")
          //emitForeachElem(op, sym, elem)
          //stream.println("}")
        case (sym, elem: DeliteReduceElem[_]) =>
          if (elem.stripFirst) {
            stream.println(quote(sym) + " = {")
            emitFirstReduceElem(op, sym, elem)
            stream.println("}")
          }
          else {
            emitReduceElem(op, sym, elem)
          }
      }
      stream.println("}")
      stream.println(quote(op.v) + " = 1;")
    }
  */
    stream.println("while (" + quote(op.v) + " < " + quote(op.size) + ") {")

    stream.println("//generating the body of multiloop")
    emitMultiLoopFuncs(op, symList)

    (symList zip op.body) foreach {
      case (sym, elem: DeliteCollectElem[_,_]) =>
        stream.println("//start emitCollectElem")
      case (sym, elem: DeliteForeachElem[_]) =>
        stream.println("//start emitForeachElem")
        //stream.println(quote(sym) + " = {")
        //emitForeachElem(op, sym, elem)
        //stream.println("}")
      case (sym, elem: DeliteReduceElem[_]) =>
        stream.println("//start emitReduceElem")
        emitReduceElem(op, sym, elem)
    }
    stream.println(quote(op.v) + " += 1;")
    stream.println("}")
  }

  // If DeliteCollectElem has condition, it is not GPUable
  def checkGPUable(op: AbstractFatLoop, symList:List[Sym[Any]]):Boolean = {
    var isGPUable = true
    (symList zip op.body) foreach {
      case (sym, elem:DeliteCollectElem[_,_]) =>
        if(elem.cond.nonEmpty) isGPUable = false
      case (sym, elem:DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
    }
    isGPUable
  }

  def emitKernelAbstractFatLoop(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("OpenCLGen: Only 1 output is supported for FatLoop (No fusing yet).")
    if(!checkGPUable(op,symList)) throw new GenerationFailedException("OpenCLGen: checkGPUable failed!")

    currDim += 1
    val currDimStr = getCurrDimStr()
    setCurrDimLength(quote(op.size))
    stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))

    stream.println(addTab()+"if( %s < %s ) {".format(currDimStr, quote(op.size)))
    tabWidth += 1
    emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      case (sym, elem:DeliteCollectElem[_,_]) =>
        emitAllocFunc(sym,elem.alloc)
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s);".format(remap(sym.Type),quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0)
        //  stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s));".format(remap(sym.Type), quote(sym),currDimStr,loopFunc,currDimStr))
        //else
        //  stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s,%s));".format(remap(sym.Type), quote(sym),currDimStr,loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
      case (sym, elem:DeliteForeachElem[_]) =>
        //stream.println(addTab()+"%s_dcUpdate(%s, %s, %s);".format(remap(sym.Type)))
        //val (loopFunc,freeVars) = emitDevFunc(elem.func, List(op.v))
        //if(freeVars.length==0)
        //  stream.println(addTab()+"%s(%s);".format(loopFunc,currDimStr))
        //else
        //  stream.println(addTab()+"%s(%s,%s);".format(loopFunc,currDimStr,freeVars.map(quote).mkString(",")))
      case (sym, elem: DeliteReduceElem[_]) =>
        //if (isPrimitiveType(sym.Type)) { throw new GenerationFailedException("OpenCLGen: deliteKernel level DeliteReduceElem is not supported yet.") }
        //else {
        //  gpuVariant = true
        //  stream.println("for(int i=0; i<%s; i++) {".format(quote(op.v)))
        //  stream.println("}")
        //}
        //TODO: Need to allow MapReduce like kernels (serialize outer loop)
        throw new GenerationFailedException("OpenCLGen: deliteKernel level DeliteReduceElem is not supported yet.")
      case _ =>
        throw new GenerationFailedException("OpenCLGen: Unknown elem type.")
    }
    tabWidth -= 1
    stream.println(addTab()+"}")
    currDim -= 1
  }

  def emitKernelAbstractFatLoop2(op: AbstractFatLoop, symList: List[Sym[Any]])(implicit stream: PrintWriter) {
    //if(symList.length != 1) throw new GenerationFailedException("OpenCLGen: Only 1 output is supported for FatLoop (No fusing yet).")
    if(!checkGPUable(op,symList)) throw new GenerationFailedException("OpenCLGen: checkGPUable failed!")

    //currDim += 1
    //val currDimStr = getCurrDimStr()
    //setCurrDimLength(quote(op.size))
    //stream.println(addTab()+"int %s = %s;".format(quote(op.v),currDimStr))

    //stream.println(addTab()+"if( %s < %s ) {".format(currDimStr, quote(op.size)))
    tabWidth += 1
    //emitMultiLoopFuncs(op, symList)
    (symList zip op.body) foreach {
      //case (sym, elem:DeliteCollectElem[_,_]) =>
      //  emitAllocFunc(sym,elem.alloc)
      //  stream.println(addTab()+"%s_dcUpdate(%s, %s, %s);".format(remap(sym.Type),quote(sym),quote(op.v),quote(getBlockResult(elem.func))))
      //case (sym, elem:DeliteForeachElem[_]) =>
      case (sym, elem: DeliteReduceElem[_]) =>
        if (isPrimitiveType(sym.Type)) {
          throw new GenerationFailedException("OpenCLGen: deliteKernel level DeliteReduceElem is not supported yet.")
        }
        else {
          emitAllocFunc(sym,elem.zero)
          stream.println("for(int i=0; i<%s; i++) {".format(quote(op.v)))
          emitMultiLoopFuncs(op,symList)
          //emitFatBlock(List(elem.rFunc))
          emitReduction(op,sym,elem)
          stream.println("}")
        }
      case _ =>
        throw new GenerationFailedException("OpenCLGen: Unknown elem type.")
    }
    tabWidth -= 1
    //stream.println(addTab()+"}")
    //currDim -= 1
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case s:DeliteOpSingleTask[_] => {
      val save = deliteKernel
      deliteKernel = false
      val b = s.block
      if (!save) {
        val (singleFunc, freeVars) = emitDevFunc(b,List())
        val currDimStr = getCurrDimStr()
        stream.println(addTab()+"%s %s = %s(%s);".format(remap(sym.Type),quote(sym),singleFunc,freeVars.map(quote).mkString(",")))
      }
      else {
        //throw new GenerationFailedException("CudaGen: DeliteOpSingleTask is not GPUable." + quote(sym))
      }
      deliteKernel = save
    }

    case op: AbstractLoop[_] =>
      // TODO: we'd like to always have fat loops but currently they are not allowed to have effects
      stream.println("// a *thin* loop follows: " + quote(sym))
      emitFatNode(List(sym), SimpleFatLoop(op.size, op.v, List(op.body)))

    /*
    case s:DeliteOpSingleTask[_] => {
      val save = deliteKernel
      deliteKernel = false
      val b = s.block
      if (!save) {
        val (singleFunc, freeVars) = emitDevFunc(b,List())
        val currDimStr = getCurrDimStr()
        stream.println(addTab()+"%s %s = %s(%s);".format(remap(sym.Type),quote(sym),singleFunc,freeVars.map(quote).mkString(",")))
      }
      else {
    	  throw new GenerationFailedException("CudaGen: DeliteOpSingleTask is not GPUable." + quote(sym))
      }
      deliteKernel = save
    }

    case map:DeliteOpMap[_,_,_] => {
      deliteKernel = false
      if(!isPrimitiveType(map.func.Type)) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for map.")
      if(!isPrimitiveType(map.v.Type)) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for map.")
      currDim += 1
      val currDimStr = getCurrDimStr()
      setCurrDimLength(quote(map.in)+"->dcSize()")
      stream.println(addTab()+"if( %s < %s_dcSize(%s) ) {".format(currDimStr, remap(map.in.Type), quote(map.in)))
      tabWidth += 1
      val (mapFunc,freeVars) = emitDevFunc(map.func, List(map.v))
      if(freeVars.length==0)
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s_dcApply(%s, %s)));".format(remap(sym.Type),quote(sym),currDimStr,mapFunc,remap(map.in.Type),quote(map.in),currDimStr))
      else
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s_dcApply(%s, %s),%s));".format(remap(sym.Type), quote(sym),currDimStr,mapFunc,remap(map.in.Type),quote(map.in),currDimStr,freeVars.map(quote).mkString(",")))
      tabWidth -= 1
      stream.println(addTab()+"}")
      emitAllocFunc(sym,map.alloc)
	    if(map.in==map.alloc) throw new GenerationFailedException("OpenCLGen: Mutable input is not supported yet.")
      currDim -= 1
      deliteKernel = true
    }

    case zip: DeliteOpZipWith[_,_,_,_] => {
      deliteKernel = false
      if(!isPrimitiveType(zip.func.Type)) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for zipwith.")
      if(!isPrimitiveType(zip.v._1.Type )) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for zipwith.")
      if(!isPrimitiveType(zip.v._2.Type)) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for zipwith.")
      currDim += 1
      val currDimStr = getCurrDimStr()
      setCurrDimLength(quote(zip.inA)+"->dcSize()")
      stream.println(addTab()+"if( %s < %s_dcSize(%s) ) {".format(currDimStr,remap(zip.inA.Type),quote(zip.inA)))
      tabWidth += 1
      val (zipFunc,freeVars) = emitDevFunc(zip.func, List(zip.v._1, zip.v._2))
      if(freeVars.length==0)
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s_dcApply(%s,%s),%s_dcApply(%s,%s)));".format(remap(sym.Type), quote(sym),currDimStr, zipFunc, remap(zip.inA.Type), quote(zip.inA),currDimStr, remap(zip.inB.Type), quote(zip.inB),currDimStr))
      else
        stream.println(addTab()+"%s_dcUpdate(%s, %s, %s(%s_dcApply(%s, %s),%s_dcApply(%s,%s),%s));".format(remap(sym.Type), quote(sym),currDimStr, zipFunc, remap(zip.inA.Type), quote(zip.inA),currDimStr, remap(zip.inB.Type), quote(zip.inB),currDimStr,freeVars.map(quote).mkString(",")))
      tabWidth -= 1
      stream.println(addTab()+"}")
      emitAllocFunc(sym,zip.alloc)
	    if((zip.inA==zip.alloc) || (zip.inB==zip.alloc)) throw new GenerationFailedException("OpenCLGen: Mutable input is not supported yet.")
      currDim -= 1
      deliteKernel = false
    }

    case foreach:DeliteOpForeach[_,_] => {
      deliteKernel = false
      if(!isPrimitiveType(foreach.v.Type)) throw new GenerationFailedException("OpenCLGen: Only primitive Types are allowed for input of foreach.")
      currDim += 1
      setCurrDimLength(quote(foreach.in)+"->dcSize()")
      val currDimStr = getCurrDimStr()
      stream.println(addTab()+"if( %s < %s_dcSize(%s) ) {".format(currDimStr,remap(foreach.in.Type),quote(foreach.in)))
      tabWidth += 1
      val (foreachFunc,freeVars) = emitDevFunc(foreach.func, List(foreach.v))
      if(freeVars.length==0)
        stream.println(addTab()+"%s(%s_dcApply(%s,%s));".format(foreachFunc,remap(foreach.in.Type),quote(foreach.in),currDimStr))
      else
        stream.println(addTab()+"%s(%s_dcApply(%s,%s),%s);".format(foreachFunc,remap(foreach.in.Type),quote(foreach.in),currDimStr,freeVars.map(quote).mkString(",")))
      tabWidth -= 1
      stream.println(addTab()+"}")
      currDim -= 1
      deliteKernel = true
    }
  */

    case _ => super.emitNode(sym,rhs)
  }
  */

}

trait CGenDeliteOps extends CGenEffect with BaseGenDeliteOps {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef)(implicit stream: PrintWriter) = rhs match {
    case op: AbstractFatLoop =>
      //println("TODO: implement emitFatNode in CGenDeliteOps")
      throw new GenerationFailedException("TODO: implement emitFatNode in CGenDeliteOps")
    case _ => super.emitFatNode(symList, rhs)
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case s:DeliteOpSingleTask[_] =>
      emitBlock(s.block)
      emitValDef(sym,quote(getBlockResult(s.block)))

    //TODO: implement deliteops
    //case map:DeliteOpMap[_,_,_] =>
    //case zip: DeliteOpZipWith[_,_,_,_] =>
    //case mapR:DeliteOpMapReduce[_,_,_] =>
    case _ => super.emitNode(sym,rhs)
  }
}
