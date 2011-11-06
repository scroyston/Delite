package ppl.dsl.deliszt.analysis

import java.io.{PrintWriter}

import util.control.Breaks._

import scala.collection.immutable.{Set => ISet}
import scala.collection.mutable.{Set => MSet, Map => MMap, HashMap, ArrayBuilder}

import scala.virtualization.lms.common._
import scala.virtualization.lms.internal._

import ppl.delite.framework.{Config, DeliteApplication}
import ppl.delite.framework.analysis.TraversalAnalysis
import ppl.delite.framework.datastruct.scala.{Seq => _, _}

import ppl.dsl.deliszt.{DeLisztExp}
import ppl.dsl.deliszt.datastruct.scala._
import ppl.dsl.deliszt.{MeshObj, Cell}

trait DeLisztCodeGenAnalysis extends TraversalAnalysis {
  val IR: DeliteApplication with DeLisztExp
  import IR._
  
  object MultipleMeshSet {
    def apply(in: MultipleMeshObj, f: Int => MeshSet) : MeshSet = {
      val builder = new MultipleMeshSetBuilder()
      
      for(mo <- in.objs) {
        builder += f(mo)
      }
      
      builder.result
    }
  
    def apply(e: Exp[_], f: Int => MeshSet) : MeshSet = {
      apply(moValue(e), f)
    }
  }
  
  object MultipleMeshObj {
    def apply(mo: Int) = new MultipleMeshObjImpl(ISet(mo))
    def apply(ms: MeshSet) = new MultipleMeshObjImpl(ms.toSet)
    
    def apply(e: Any) = {
      e match {
        case mo: Int => OneObj(mo)
        case _ => e
      }
    }

    def multi(in: MultipleMeshObj, f: Int => MultipleMeshObj) : MultipleMeshObj = {
      var out: MultipleMeshObj = NoObjs()
      
      for(mo <- in.objs) {
        out = out ++ f(mo)
      }
      
      out
    }
    
    def multi(e: Exp[_], f: Int => MultipleMeshObj) : MultipleMeshObj = {
      multi(moValue(e), f)
    }
    
    def multi(e: Int, f: Int => MultipleMeshObj) : MultipleMeshObj = {
      multi(OneObj(e), f)
    }
    
    def apply(in: MultipleMeshObj, f: Int => Int) : MultipleMeshObj = {
      var out: MultipleMeshObj = NoObjs()
      
      for(mo <- in.objs) {
        out = out + f(mo)
      }
      
      out
    }
    
    def apply(e: Exp[_], f: Int => Int) : MultipleMeshObj = {
      apply(moValue(e), f)
    }
    
    def apply(e: Int, f: Int => Int) : MultipleMeshObj = {
      apply(OneObj(e), f)
    }
  }
  
  import Stencil.StencilMap

  val forMap = new HashMap[Int,StencilMap]()
  val msMap = MMap[Int,MeshSet]()
  val schedules = new HashMap[Int,ArrayBuilder[TP[Any]]]() { override def default(key: Int) = { val buf = ArrayBuilder.make[TP[Any]](); this(key) = buf; buf } }
  
  // Store the current top level for loop
  var topFor : Option[Int] = None
  var currentFor : Option[Int] = None
  
  def addSchedule(sym: Sym[Any], rhs: Def[Any]) {
    currentFor match {
      case Some(i) => { schedules(i) += TP(sym, rhs) }
      case _ => { throw new RuntimeException("No current for set") }
    }
  }
  
  // And the current mesh object in the top level for loop
  var topMo : Option[Int] = None
  
  val className = "StencilCollector"
  val on = true
  
  var collectingSchedule = false
  var trivial = false
  var indexSym : Sym[Any] = null
  var moSyms : ISet[Int] = null
  
  override def initializeGenerator(buildDir:String, args: Array[String], _analysisResults: MMap[String,Any]): Unit = {
    super.initializeGenerator(buildDir, args, _analysisResults)
    
    analysisResults("StencilCollectorStencils") = forMap
    analysisResults("StencilCollectorMeshsets") = msMap
  
    MeshLoader.init(if(args.length > 0) args(0) else "liszt.cfg")
  }
  
  // Mark accesses
  def markRead[T](f: Exp[_], i: Exp[MeshObj]) {
    val sym = f.asInstanceOf[Sym[_]]
    val mos = value[MultipleMeshObj](i)
    
    topFor match {
	    case Some(x) => {
	      for(mo <- mos.objs) {
  	      forMap(x)(topMo.get).read += FieldAccess(sym.id, mo)
  	    }
	    }
	    case None => System.out.println("No top level for")
    }
  }
  
  def markWrite[T](f: Exp[_], i: Exp[MeshObj], moType: Manifest[_]) {
    val sym = f.asInstanceOf[Sym[_]]
    val mos = value[MultipleMeshObj](i)
    
    topFor match {
	    case Some(x) => {
	      for(mo <- mos.objs) {
          mo match {
            case 0 if moType <:< manifest[Cell] => // Ignore
            case _ => forMap(x)(topMo.get).write += FieldAccess(sym.id, mo)
          }
	      }
	    }
	    case None => System.out.println("No top level for")
    }    
  }
  
  def matchFor(i: Int) = {
    topFor match {
      case Some(x: Int) => i == x
      case _ => false
    }
  }
  
  def setFor(i: Int, ms: MeshSet) {
    topFor = Some(i)
    forMap(i) = new HashMap[Int,ReadWriteSet]() { override def default(key: Int) = { val rwset = new ReadWriteSet(); this(key) = rwset; rwset }  }
    
    msMap(i) = ms
  }
  
  val values = MMap[Int,Any]()
  
  def store(sym: Sym[_], x: Any) {
    values(sym.id) = x
  }
    
  def combine(a: Any, b: Any) = {
    (a, b) match {
      case (x: MultipleMeshObj, y: MultipleMeshObj) => x ++ y
      case (x: MultipleMeshObj, _) => x
      case (_, y: MultipleMeshObj) => y
      
      // Just pick one, doesn't really matter
      case _ => a
    }
  }
  
  def combine(a: Exp[Any], b: Exp[Any]) : Any = {
    val resultA = value(a)
    val resultB = value(b)
    
    // Combine the two! Only care really if it is a mesh element though
    combine(resultA,resultB)
  }
  
  def rawValue(x: Exp[Any]) : Option[Any] = x match {
    case Const(null) => None
    case Const(s: String) => Some(s)
    case null => None
    case Const(f: Float) => Some(f)
    case Const(z) => Some(z)
    case Sym(n) => values.get(n)
    // Doesn't exist anymore?
    // case External(s, args) => null
    case _ => { throw new RuntimeException("Could not get value"); None }
  }
  
  def value[T:Manifest](x: Exp[Any]) : T = {
    (rawValue(x) match {
    case Some(o) => o
    case None => null
    case _ => null
  }).asInstanceOf[T]}
  
  def value[T:Manifest](x: Def[Any]) : T = (maybeValue(x) match {
    case Some(o) => o
    case None => null
    case _ => null
  }).asInstanceOf[T]
  
  def moValue(x: Exp[Any]) = value[MultipleMeshObj](x)
  
  def maybeValue(x: Def[Any])(implicit stream: PrintWriter) : Option[Any] = {
    val o = x match {
      // or could also lookup the exp
      case DeLisztBoundarySetCells(name) => Mesh.boundarySetCells(value[String](name))
      case DeLisztBoundarySetEdges(name) => Mesh.boundarySetEdges(value[String](name))
      case DeLisztBoundarySetFaces(name) => Mesh.boundarySetFaces(value[String](name))
      case DeLisztBoundarySetVertices(name) => Mesh.boundarySetVertices(value[String](name))

      case DeLisztMesh() => OneObj(0)

      case DeLisztVerticesCell(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesCell(mo))
      case DeLisztVerticesEdge(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesEdge(mo))
      case DeLisztVerticesFace(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesFace(mo))
      case DeLisztVerticesVertex(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesVertex(mo))
      case DeLisztVerticesMesh(e) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesMesh)

      case DeLisztVertex(e, i, m) => MultipleMeshObj(e, (mo:Int) => Mesh.mesh.vertex(mo, value(i)))

      case DeLisztFaceVerticesCCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesCCW(mo))
      case DeLisztFaceVerticesCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.verticesCW(mo))

      case DeLisztCellsCell(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsCell(mo))
      case DeLisztCellsEdge(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsEdge(mo))
      case DeLisztCellsFace(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsFace(mo))
      case DeLisztCellsVertex(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsVertex(mo))
      case DeLisztCellsMesh(e) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsMesh)

      case DeLisztEdgeCellsCCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsCCW(mo))
      case DeLisztEdgeCellsCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.cellsCW(mo))

      case DeLisztEdgesCell(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesCell(mo))
      case DeLisztEdgesFace(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesFace(mo))
      case DeLisztEdgesVertex(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesVertex(mo))
      case DeLisztEdgesMesh(e) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesMesh)

      case DeLisztFacesCell(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesCell(mo))
      case DeLisztFacesEdge(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesEdge(mo))
      case DeLisztFacesVertex(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesVertex(mo))
      case DeLisztFacesMesh(e) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesMesh)

      case DeLisztEdgeFacesCCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesCCW(mo))
      case DeLisztEdgeFacesCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.facesCW(mo))
  
      case DeLisztFaceEdgesCCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesCCW(mo))
      case DeLisztFaceEdgesCW(e, m) => MultipleMeshSet(e, (mo:Int) => Mesh.mesh.edgesCW(mo))

      case DeLisztEdgeHead(e, m) => MultipleMeshObj(e, (mo:Int) => Mesh.mesh.head(mo))
      case DeLisztEdgeTail(e, m) => MultipleMeshObj(e, (mo:Int) => Mesh.mesh.tail(mo))

      case DeLisztFaceInside(e, m) => MultipleMeshObj(e, (mo:Int) => Mesh.mesh.inside(mo))
      case DeLisztFaceOutside(e, m) => MultipleMeshObj(e, (mo:Int) => Mesh.mesh.outside(mo))
  
      case DeLisztFace(e, i, m) => MultipleMeshObj(e, (mo: Int) => Mesh.mesh.face(mo, value(i)))

      case DeLisztFlipEdge(e) => MultipleMeshObj(e, (mo:Int) => Mesh.flip(mo))
      case DeLisztFlipFace(e) => MultipleMeshObj(e, (mo:Int) => Mesh.flip(mo))

      case DeLisztTowardsEdgeVertex(e, v, m) => MultipleMeshObj.multi(e, (e1: Int) => MultipleMeshObj(v, (e2: Int) => Mesh.mesh.towardsEdgeVertex(e1, e2)))
      case DeLisztTowardsFaceCell(e, c, m) => MultipleMeshObj.multi(e, (e1: Int) => MultipleMeshObj(c, (e2: Int) => Mesh.mesh.towardsFaceCell(e1, e2)))
      
      case DeliteCollectionApply(e, i) => {
        val obj = (rawValue(e), rawValue(i)) match {
          case (Some(c), Some(idx)) => (c.asInstanceOf[DeliteCollection[Any]]).dcApply(idx.asInstanceOf[Int])
          case _ => None
        }
        
        MultipleMeshObj(obj)
      }
      
      // While loops. Execute once. Store results if results are a mesh element
      case While(c,b) => {
        blockValue(b)
      }
      
      case DeliteWhile(c,b) => {
        blockValue(b)
      }
      
      // Execute both branches. Store results if results are a mesh element
      case IfThenElse(c,a,b) => {        
        val resultA = blockValue[Any](a)
        val resultB = blockValue[Any](b)
        
        // Combine the two! Only care really if it is a mesh element though
        combine(resultA,resultB)
      }
      
      case DeliteIfThenElse(c,a,b,f) => {        
        val resultA = blockValue[Any](a)
        val resultB = blockValue[Any](b)
                
        // Combine the two! Only care really if it is a mesh element though
        combine(resultA,resultB)
      }

      case _ => None
    }
    
    o match {
      case None => None
      case _ => Some(o)
    }
  }
  
  def blockValue[T:Manifest](b: Exp[Any]) = value[T](getBlockResult(b))
  
  def runSchedule(tps: Seq[TP[Any]]) {
    for(TP(sym, rhs) <- tps) {
      rhs match {
        // Foreach, only apply to top level foreach though...
        case f@MeshSetForeach(m, b) => {
          // Get value for the mesh set
          val ms = value[MeshSet](m)
          
          // Definitely not a top level foreach
          // Run foreach over mesh set
          var i = 0
          
          for(mo <- ms) {
            // Store loop index in loop index symbol
            store(f.i, i)
            store(f.v, i)
            
            // Re "emit" block
            runSchedule(schedules(sym.id).result)
            
            i += 1
          }
        }
        
        // Mark 
        case FieldApply(f,i) => {
          // Mark a read on the field for the current element... for i
          // BUT WE DON'T EVEN USE READS SO SCREW IT
          // markRead(f, i)
        }
        
        case w@FieldUpdate(f,i,v) => {
          // Mark a write on the field for the current element... for i
          markWrite(f, i, w.moM)
        }
          
        case w@FieldPlusUpdate(f,i,v) => {
          // Mark a write on the field for the current element... for i
          markWrite(f, i, w.moM)
        }
        
        case w@FieldTimesUpdate(f,i,v) => {
          // Mark a write on the field for the current element... for i
          markWrite(f, i, w.moM)
        }
          
        case w@FieldMinusUpdate(f,i,v) => {
          // Mark a write on the field for the current element... for i
          markWrite(f, i, w.moM)
        }
        
        case w@FieldDivideUpdate(f,i,v) => {
          // Mark a write on the field for the current element... for i
          markWrite(f, i, w.moM)
        }
        
        case _ => None
      }
    
      maybeValue(rhs) match {
        case Some(o) => {
          store(sym, o)
        }
        case None => {}
      }
    }
  }
  
  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = {
    // System.out.println("EMITTING NODE")
    // System.out.println(rhs)
    
    if(Config.collectStencil) {
      if(collectingSchedule) {
        var matched = true
        
        rhs match {
          case DeliteCollectionApply(e, i) => {
            // Found the sym for the top level foreach index meshobj          
            i match {
              case Sym(i) if(indexSym.id == i) => { moSyms += sym.id }
              case _ =>
            }
          }
          
          case DeLisztFlipEdge(e) => {
            e match {
              case Sym(i) if(moSyms.contains(i)) => { moSyms += sym.id }
              case _ =>
            }
          }
          
          case DeLisztFlipFace(e) => {
            e match {
              case Sym(i) if(moSyms.contains(i)) => { moSyms += sym.id }
              case _ =>
            }
          }
          
          // Control constructs
          
          case f@MeshSetForeach(m, b) => {
            // Set
            currentFor = Some(sym.id)

            f.body match {
              case DeliteForeachElem(func, sync) => emitBlock(func)
            }
            
            // Restore
            currentFor = Some(sym.id)
          }
          
          // While loops. Execute once. Store results if results are a mesh element
          case DeliteWhile(c,b) => {
            emitBlock(c)
            
            emitBlock(b)
          }
          
          // While loops. Execute once. Store results if results are a mesh element
          case While(c,b) => {
            emitBlock(c)
            
            emitBlock(b)
          }
          
          // Execute both branches. Store results if results are a mesh element
          case IfThenElse(c,a,b) => {
            emitBlock(c)
            
            emitBlock(a)
            emitBlock(b)
          }
          
                
          // Execute both branches. Store results if results are a mesh element
          case DeliteIfThenElse(c,a,b,h) => {
            emitBlock(c)
            
            emitBlock(a)
            emitBlock(b)
          }
          
          // Field accesses, check for triviality
          case FieldApply(f,i) =>
          
          case FieldUpdate(f,i,v) => {
            i match {
              case Sym(i) if !moSyms.contains(i) => { trivial = false }
              case _ =>
            }
          }
            
          case w@FieldPlusUpdate(f,i,v) => {
            i match {
              case Sym(i) if !moSyms.contains(i) => { trivial = false }
              case _ =>
            }
          }
          
          case w@FieldTimesUpdate(f,i,v) => {
            i match {
              case Sym(i) if !moSyms.contains(i) => { trivial = false }
              case _ =>
            }
          }
            
          case w@FieldMinusUpdate(f,i,v) => {
            i match {
              case Sym(i) if !moSyms.contains(i) => { trivial = false }
              case _ =>
            }
          }
          
          case w@FieldDivideUpdate(f,i,v) => {
            i match {
              case Sym(i) if !moSyms.contains(i) => { trivial = false }
              case _ =>
            }
          }
          
          // Rest should just be added to schedule
          case DeLisztBoundarySetCells(name) =>
          case DeLisztBoundarySetEdges(name) =>
          case DeLisztBoundarySetFaces(name) =>
          case DeLisztBoundarySetVertices(name) =>
          case DeLisztMesh() =>
          case DeLisztVerticesCell(e, m) =>
          case DeLisztVerticesEdge(e, m) =>
          case DeLisztVerticesFace(e, m) =>
          case DeLisztVerticesVertex(e, m) =>
          case DeLisztVerticesMesh(e) =>
          case DeLisztVertex(e, i, m) =>
          case DeLisztFaceVerticesCCW(e, m) =>
          case DeLisztFaceVerticesCW(e, m) =>
          case DeLisztCellsCell(e, m) =>
          case DeLisztCellsEdge(e, m) =>
          case DeLisztCellsFace(e, m) =>
          case DeLisztCellsVertex(e, m) =>
          case DeLisztCellsMesh(e) =>
          case DeLisztEdgeCellsCCW(e, m) =>
          case DeLisztEdgeCellsCW(e, m) =>
          case DeLisztEdgesCell(e, m) =>
          case DeLisztEdgesFace(e, m) =>
          case DeLisztEdgesVertex(e, m) =>
          case DeLisztEdgesMesh(e) =>
          case DeLisztFacesCell(e, m) =>
          case DeLisztFacesEdge(e, m) =>
          case DeLisztFacesVertex(e, m) =>
          case DeLisztFacesMesh(e) =>
          case DeLisztEdgeFacesCCW(e, m) =>
          case DeLisztEdgeFacesCW(e, m) =>
          case DeLisztFaceEdgesCCW(e, m) =>
          case DeLisztFaceEdgesCW(e, m) =>
          case DeLisztEdgeHead(e, m) =>
          case DeLisztEdgeTail(e, m) =>
          case DeLisztFaceInside(e, m) =>
          case DeLisztFaceOutside(e, m) =>
          case DeLisztFace(e, i, m) =>
          case DeLisztTowardsEdgeVertex(e, v, m) =>
          case DeLisztTowardsFaceCell(e, c, m) =>
          
          case _ => { matched = false }
        }
        
        // Add to schedule for this for loop
        if(matched) {
          addSchedule(sym, rhs)
        }
      }
      else {
        rhs match {
          // Foreach, only apply to top level foreach though...
          case f@MeshSetForeach(m, b) => {
            // Get value for the mesh set
            val ms = value[MeshSet](m)
        
            // Mark current top foreach....
            setFor(sym.id, ms)
            System.out.println("Found a top level foreach sym " + sym.id)
            
            // Do trivial coloring detection
            collectingSchedule = true
            trivial = true
            indexSym = f.v
            moSyms = ISet()
            currentFor = Some(sym.id)
            
            f.body match {
              case DeliteForeachElem(func, sync) => emitBlock(func)
            }
            
            collectingSchedule = false
            
            if(matchFor(sym.id) && trivial) {
              System.out.println("Detected trivial loop")
              forMap.remove(sym.id)
            }
            else {
              // Get value for the mesh set
              val ms = value[MeshSet](m)
              
              // Definitely not a top level foreach
              // Run foreach over mesh set
              var i = 0
              
              for(mo <- ms) {
                topMo = Some(mo)
              
                // Store loop index in loop index symbol
                store(f.i, i)
                store(f.v, i)
                
                // Re "emit" block
                runSchedule(schedules(sym.id).result)
                
                i += 1
              }
            }
            
            // Clear out the current for loop
            topFor = None
          }
          
          // While loops. Execute once. Store results if results are a mesh element
          case DeliteWhile(c,b) => {
            emitBlock(c)
            
            emitBlock(b)
          }
          
          // While loops. Execute once. Store results if results are a mesh element
          case While(c,b) => {
            emitBlock(c)
            
            emitBlock(b)
          }
          
          // Execute both branches. Store results if results are a mesh element
          case IfThenElse(c,a,b) => {
            emitBlock(c)
            
            emitBlock(a)
            emitBlock(b)
          }
          
                
          // Execute both branches. Store results if results are a mesh element
          case DeliteIfThenElse(c,a,b,h) => {
            emitBlock(c)
            
            emitBlock(a)
            emitBlock(b)
          }
            
          // There shouldn't be any field accesses if we aren't collecting the schedule          
          case _ =>
        }
      
        maybeValue(rhs) match {
          case Some(o) => {
            store(sym, o)
          }
          case None => {}
        }
      }
    }
  }
}
