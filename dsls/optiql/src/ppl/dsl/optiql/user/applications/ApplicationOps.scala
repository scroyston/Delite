/***********************************************************
** AUTOGENERATED USING bin/lift_user_class.py
************************************************************/

package ppl.dsl.optiql.user.applications

import ppl.dsl.optiql.datastruct.scala.liftables._
import java.io.PrintWriter
import ppl.delite.framework.{DSLType}
import ppl.delite.framework.datastructures._
import scala.virtualization.lms.common.ScalaGenFat
import scala.virtualization.lms.util.OverloadHack
import scala.virtualization.lms.common.{EffectExp, BaseFatExp, Variables}

//OptiQL Specific Header
import ppl.dsl.optiql.datastruct.scala.util.Date


trait CustomerOps extends DSLType with Variables with OverloadHack {

  object Customer {
    def apply(c_custkey: Rep[Int], c_name: Rep[String], c_address: Rep[String], c_nationkey: Rep[Int], c_phone: Rep[String], c_acctbal: Rep[Double], c_mktsegment: Rep[String], c_comment: Rep[String]) = customer_obj_new(c_custkey, c_name, c_address, c_nationkey, c_phone, c_acctbal, c_mktsegment, c_comment)
  }

  implicit def repCustomerToCustomerOps(x: Rep[Customer]) = new customerOpsCls(x)
  implicit def customerToCustomerOps(x: Var[Customer]) = new customerOpsCls(readVar(x))

  class customerOpsCls(__x: Rep[Customer]) {
    def c_custkey = customer_c_custkey(__x)
    def c_name = customer_c_name(__x)
    def c_address = customer_c_address(__x)
    def c_nationkey = customer_c_nationkey(__x)
    def c_phone = customer_c_phone(__x)
    def c_acctbal = customer_c_acctbal(__x)
    def c_mktsegment = customer_c_mktsegment(__x)
    def c_comment = customer_c_comment(__x)
  }

  //object defs
  def customer_obj_new(c_custkey: Rep[Int], c_name: Rep[String], c_address: Rep[String], c_nationkey: Rep[Int], c_phone: Rep[String], c_acctbal: Rep[Double], c_mktsegment: Rep[String], c_comment: Rep[String]): Rep[Customer]

  //class defs
  def customer_c_custkey(__x: Rep[Customer]): Rep[Int]
  def customer_c_name(__x: Rep[Customer]): Rep[String]
  def customer_c_address(__x: Rep[Customer]): Rep[String]
  def customer_c_nationkey(__x: Rep[Customer]): Rep[Int]
  def customer_c_phone(__x: Rep[Customer]): Rep[String]
  def customer_c_acctbal(__x: Rep[Customer]): Rep[Double]
  def customer_c_mktsegment(__x: Rep[Customer]): Rep[String]
  def customer_c_comment(__x: Rep[Customer]): Rep[String]
}

trait CustomerOpsExp extends CustomerOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class CustomerObjectNew(c_custkey: Exp[Int], c_name: Exp[String], c_address: Exp[String], c_nationkey: Exp[Int], c_phone: Exp[String], c_acctbal: Exp[Double], c_mktsegment: Exp[String], c_comment: Exp[String]) extends Def[Customer]
  def customer_obj_new(c_custkey: Exp[Int], c_name: Exp[String], c_address: Exp[String], c_nationkey: Exp[Int], c_phone: Exp[String], c_acctbal: Exp[Double], c_mktsegment: Exp[String], c_comment: Exp[String]) = reflectEffect(CustomerObjectNew(c_custkey, c_name, c_address, c_nationkey, c_phone, c_acctbal, c_mktsegment, c_comment))
  def customer_c_custkey(__x: Rep[Customer]) = FieldRead[Int](__x, "c_custkey", "Int")
  def customer_c_name(__x: Rep[Customer]) = FieldRead[String](__x, "c_name", "String")
  def customer_c_address(__x: Rep[Customer]) = FieldRead[String](__x, "c_address", "String")
  def customer_c_nationkey(__x: Rep[Customer]) = FieldRead[Int](__x, "c_nationkey", "Int")
  def customer_c_phone(__x: Rep[Customer]) = FieldRead[String](__x, "c_phone", "String")
  def customer_c_acctbal(__x: Rep[Customer]) = FieldRead[Double](__x, "c_acctbal", "Double")
  def customer_c_mktsegment(__x: Rep[Customer]) = FieldRead[String](__x, "c_mktsegment", "String")
  def customer_c_comment(__x: Rep[Customer]) = FieldRead[String](__x, "c_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenCustomerOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case CustomerObjectNew(c_custkey, c_name, c_address, c_nationkey, c_phone, c_acctbal, c_mktsegment, c_comment) => emitValDef(sym, "new " + remap(manifest[Customer]) + "(" + quote(c_custkey)  + "," + quote(c_name)  + "," + quote(c_address)  + "," + quote(c_nationkey)  + "," + quote(c_phone)  + "," + quote(c_acctbal)  + "," + quote(c_mktsegment)  + "," + quote(c_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait LineItemOps extends DSLType with Variables with OverloadHack {

  object LineItem {
    def apply(l_orderkey: Rep[Int], l_partkey: Rep[Int], l_suppkey: Rep[Int], l_linenumber: Rep[Int], l_quantity: Rep[Double], l_extendedprice: Rep[Double], l_discount: Rep[Double], l_tax: Rep[Double], l_returnflag: Rep[Char], l_linestatus: Rep[Char], l_shipdate: Rep[Date], l_commitdate: Rep[Date], l_receiptdate: Rep[Date], l_shipinstruct: Rep[String], l_shipmode: Rep[String], l_comment: Rep[String]) = lineitem_obj_new(l_orderkey, l_partkey, l_suppkey, l_linenumber, l_quantity, l_extendedprice, l_discount, l_tax, l_returnflag, l_linestatus, l_shipdate, l_commitdate, l_receiptdate, l_shipinstruct, l_shipmode, l_comment)
  }

  implicit def repLineItemToLineItemOps(x: Rep[LineItem]) = new lineitemOpsCls(x)
  implicit def lineitemToLineItemOps(x: Var[LineItem]) = new lineitemOpsCls(readVar(x))

  class lineitemOpsCls(__x: Rep[LineItem]) {
    def l_orderkey = lineitem_l_orderkey(__x)
    def l_partkey = lineitem_l_partkey(__x)
    def l_suppkey = lineitem_l_suppkey(__x)
    def l_linenumber = lineitem_l_linenumber(__x)
    def l_quantity = lineitem_l_quantity(__x)
    def l_extendedprice = lineitem_l_extendedprice(__x)
    def l_discount = lineitem_l_discount(__x)
    def l_tax = lineitem_l_tax(__x)
    def l_returnflag = lineitem_l_returnflag(__x)
    def l_linestatus = lineitem_l_linestatus(__x)
    def l_shipdate = lineitem_l_shipdate(__x)
    def l_commitdate = lineitem_l_commitdate(__x)
    def l_receiptdate = lineitem_l_receiptdate(__x)
    def l_shipinstruct = lineitem_l_shipinstruct(__x)
    def l_shipmode = lineitem_l_shipmode(__x)
    def l_comment = lineitem_l_comment(__x)
  }

  //object defs
  def lineitem_obj_new(l_orderkey: Rep[Int], l_partkey: Rep[Int], l_suppkey: Rep[Int], l_linenumber: Rep[Int], l_quantity: Rep[Double], l_extendedprice: Rep[Double], l_discount: Rep[Double], l_tax: Rep[Double], l_returnflag: Rep[Char], l_linestatus: Rep[Char], l_shipdate: Rep[Date], l_commitdate: Rep[Date], l_receiptdate: Rep[Date], l_shipinstruct: Rep[String], l_shipmode: Rep[String], l_comment: Rep[String]): Rep[LineItem]

  //class defs
  def lineitem_l_orderkey(__x: Rep[LineItem]): Rep[Int]
  def lineitem_l_partkey(__x: Rep[LineItem]): Rep[Int]
  def lineitem_l_suppkey(__x: Rep[LineItem]): Rep[Int]
  def lineitem_l_linenumber(__x: Rep[LineItem]): Rep[Int]
  def lineitem_l_quantity(__x: Rep[LineItem]): Rep[Double]
  def lineitem_l_extendedprice(__x: Rep[LineItem]): Rep[Double]
  def lineitem_l_discount(__x: Rep[LineItem]): Rep[Double]
  def lineitem_l_tax(__x: Rep[LineItem]): Rep[Double]
  def lineitem_l_returnflag(__x: Rep[LineItem]): Rep[Char]
  def lineitem_l_linestatus(__x: Rep[LineItem]): Rep[Char]
  def lineitem_l_shipdate(__x: Rep[LineItem]): Rep[Date]
  def lineitem_l_commitdate(__x: Rep[LineItem]): Rep[Date]
  def lineitem_l_receiptdate(__x: Rep[LineItem]): Rep[Date]
  def lineitem_l_shipinstruct(__x: Rep[LineItem]): Rep[String]
  def lineitem_l_shipmode(__x: Rep[LineItem]): Rep[String]
  def lineitem_l_comment(__x: Rep[LineItem]): Rep[String]
}

trait LineItemOpsExp extends LineItemOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class LineItemObjectNew(l_orderkey: Exp[Int], l_partkey: Exp[Int], l_suppkey: Exp[Int], l_linenumber: Exp[Int], l_quantity: Exp[Double], l_extendedprice: Exp[Double], l_discount: Exp[Double], l_tax: Exp[Double], l_returnflag: Exp[Char], l_linestatus: Exp[Char], l_shipdate: Exp[Date], l_commitdate: Exp[Date], l_receiptdate: Exp[Date], l_shipinstruct: Exp[String], l_shipmode: Exp[String], l_comment: Exp[String]) extends Def[LineItem]
  def lineitem_obj_new(l_orderkey: Exp[Int], l_partkey: Exp[Int], l_suppkey: Exp[Int], l_linenumber: Exp[Int], l_quantity: Exp[Double], l_extendedprice: Exp[Double], l_discount: Exp[Double], l_tax: Exp[Double], l_returnflag: Exp[Char], l_linestatus: Exp[Char], l_shipdate: Exp[Date], l_commitdate: Exp[Date], l_receiptdate: Exp[Date], l_shipinstruct: Exp[String], l_shipmode: Exp[String], l_comment: Exp[String]) = reflectEffect(LineItemObjectNew(l_orderkey, l_partkey, l_suppkey, l_linenumber, l_quantity, l_extendedprice, l_discount, l_tax, l_returnflag, l_linestatus, l_shipdate, l_commitdate, l_receiptdate, l_shipinstruct, l_shipmode, l_comment))
  def lineitem_l_orderkey(__x: Rep[LineItem]) = FieldRead[Int](__x, "l_orderkey", "Int")
  def lineitem_l_partkey(__x: Rep[LineItem]) = FieldRead[Int](__x, "l_partkey", "Int")
  def lineitem_l_suppkey(__x: Rep[LineItem]) = FieldRead[Int](__x, "l_suppkey", "Int")
  def lineitem_l_linenumber(__x: Rep[LineItem]) = FieldRead[Int](__x, "l_linenumber", "Int")
  def lineitem_l_quantity(__x: Rep[LineItem]) = FieldRead[Double](__x, "l_quantity", "Double")
  def lineitem_l_extendedprice(__x: Rep[LineItem]) = FieldRead[Double](__x, "l_extendedprice", "Double")
  def lineitem_l_discount(__x: Rep[LineItem]) = FieldRead[Double](__x, "l_discount", "Double")
  def lineitem_l_tax(__x: Rep[LineItem]) = FieldRead[Double](__x, "l_tax", "Double")
  def lineitem_l_returnflag(__x: Rep[LineItem]) = FieldRead[Char](__x, "l_returnflag", "Char")
  def lineitem_l_linestatus(__x: Rep[LineItem]) = FieldRead[Char](__x, "l_linestatus", "Char")
  def lineitem_l_shipdate(__x: Rep[LineItem]) = FieldRead[Date](__x, "l_shipdate", "Date")
  def lineitem_l_commitdate(__x: Rep[LineItem]) = FieldRead[Date](__x, "l_commitdate", "Date")
  def lineitem_l_receiptdate(__x: Rep[LineItem]) = FieldRead[Date](__x, "l_receiptdate", "Date")
  def lineitem_l_shipinstruct(__x: Rep[LineItem]) = FieldRead[String](__x, "l_shipinstruct", "String")
  def lineitem_l_shipmode(__x: Rep[LineItem]) = FieldRead[String](__x, "l_shipmode", "String")
  def lineitem_l_comment(__x: Rep[LineItem]) = FieldRead[String](__x, "l_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenLineItemOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case LineItemObjectNew(l_orderkey, l_partkey, l_suppkey, l_linenumber, l_quantity, l_extendedprice, l_discount, l_tax, l_returnflag, l_linestatus, l_shipdate, l_commitdate, l_receiptdate, l_shipinstruct, l_shipmode, l_comment) => emitValDef(sym, "new " + remap(manifest[LineItem]) + "(" + quote(l_orderkey)  + "," + quote(l_partkey)  + "," + quote(l_suppkey)  + "," + quote(l_linenumber)  + "," + quote(l_quantity)  + "," + quote(l_extendedprice)  + "," + quote(l_discount)  + "," + quote(l_tax)  + "," + quote(l_returnflag)  + "," + quote(l_linestatus)  + "," + quote(l_shipdate)  + "," + quote(l_commitdate)  + "," + quote(l_receiptdate)  + "," + quote(l_shipinstruct)  + "," + quote(l_shipmode)  + "," + quote(l_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait NationOps extends DSLType with Variables with OverloadHack {

  object Nation {
    def apply(n_nationkey: Rep[Int], n_name: Rep[String], n_regionkey: Rep[Int], n_comment: Rep[String]) = nation_obj_new(n_nationkey, n_name, n_regionkey, n_comment)
  }

  implicit def repNationToNationOps(x: Rep[Nation]) = new nationOpsCls(x)
  implicit def nationToNationOps(x: Var[Nation]) = new nationOpsCls(readVar(x))

  class nationOpsCls(__x: Rep[Nation]) {
    def n_nationkey = nation_n_nationkey(__x)
    def n_name = nation_n_name(__x)
    def n_regionkey = nation_n_regionkey(__x)
    def n_comment = nation_n_comment(__x)
  }

  //object defs
  def nation_obj_new(n_nationkey: Rep[Int], n_name: Rep[String], n_regionkey: Rep[Int], n_comment: Rep[String]): Rep[Nation]

  //class defs
  def nation_n_nationkey(__x: Rep[Nation]): Rep[Int]
  def nation_n_name(__x: Rep[Nation]): Rep[String]
  def nation_n_regionkey(__x: Rep[Nation]): Rep[Int]
  def nation_n_comment(__x: Rep[Nation]): Rep[String]
}

trait NationOpsExp extends NationOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class NationObjectNew(n_nationkey: Exp[Int], n_name: Exp[String], n_regionkey: Exp[Int], n_comment: Exp[String]) extends Def[Nation]
  def nation_obj_new(n_nationkey: Exp[Int], n_name: Exp[String], n_regionkey: Exp[Int], n_comment: Exp[String]) = reflectEffect(NationObjectNew(n_nationkey, n_name, n_regionkey, n_comment))
  def nation_n_nationkey(__x: Rep[Nation]) = FieldRead[Int](__x, "n_nationkey", "Int")
  def nation_n_name(__x: Rep[Nation]) = FieldRead[String](__x, "n_name", "String")
  def nation_n_regionkey(__x: Rep[Nation]) = FieldRead[Int](__x, "n_regionkey", "Int")
  def nation_n_comment(__x: Rep[Nation]) = FieldRead[String](__x, "n_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenNationOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case NationObjectNew(n_nationkey, n_name, n_regionkey, n_comment) => emitValDef(sym, "new " + remap(manifest[Nation]) + "(" + quote(n_nationkey)  + "," + quote(n_name)  + "," + quote(n_regionkey)  + "," + quote(n_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait OrderOps extends DSLType with Variables with OverloadHack {

  object Order {
    def apply(o_orderkey: Rep[Int], o_custkey: Rep[Int], o_orderstatus: Rep[Char], o_totalprice: Rep[Double], o_orderdate: Rep[Date], o_orderpriority: Rep[String], o_clerk: Rep[String], o_shippriority: Rep[Int], o_comment: Rep[String]) = order_obj_new(o_orderkey, o_custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment)
  }

  implicit def repOrderToOrderOps(x: Rep[Order]) = new orderOpsCls(x)
  implicit def orderToOrderOps(x: Var[Order]) = new orderOpsCls(readVar(x))

  class orderOpsCls(__x: Rep[Order]) {
    def o_orderkey = order_o_orderkey(__x)
    def o_custkey = order_o_custkey(__x)
    def o_orderstatus = order_o_orderstatus(__x)
    def o_totalprice = order_o_totalprice(__x)
    def o_orderdate = order_o_orderdate(__x)
    def o_orderpriority = order_o_orderpriority(__x)
    def o_clerk = order_o_clerk(__x)
    def o_shippriority = order_o_shippriority(__x)
    def o_comment = order_o_comment(__x)
  }

  //object defs
  def order_obj_new(o_orderkey: Rep[Int], o_custkey: Rep[Int], o_orderstatus: Rep[Char], o_totalprice: Rep[Double], o_orderdate: Rep[Date], o_orderpriority: Rep[String], o_clerk: Rep[String], o_shippriority: Rep[Int], o_comment: Rep[String]): Rep[Order]

  //class defs
  def order_o_orderkey(__x: Rep[Order]): Rep[Int]
  def order_o_custkey(__x: Rep[Order]): Rep[Int]
  def order_o_orderstatus(__x: Rep[Order]): Rep[Char]
  def order_o_totalprice(__x: Rep[Order]): Rep[Double]
  def order_o_orderdate(__x: Rep[Order]): Rep[Date]
  def order_o_orderpriority(__x: Rep[Order]): Rep[String]
  def order_o_clerk(__x: Rep[Order]): Rep[String]
  def order_o_shippriority(__x: Rep[Order]): Rep[Int]
  def order_o_comment(__x: Rep[Order]): Rep[String]
}

trait OrderOpsExp extends OrderOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class OrderObjectNew(o_orderkey: Exp[Int], o_custkey: Exp[Int], o_orderstatus: Exp[Char], o_totalprice: Exp[Double], o_orderdate: Exp[Date], o_orderpriority: Exp[String], o_clerk: Exp[String], o_shippriority: Exp[Int], o_comment: Exp[String]) extends Def[Order]
  def order_obj_new(o_orderkey: Exp[Int], o_custkey: Exp[Int], o_orderstatus: Exp[Char], o_totalprice: Exp[Double], o_orderdate: Exp[Date], o_orderpriority: Exp[String], o_clerk: Exp[String], o_shippriority: Exp[Int], o_comment: Exp[String]) = reflectEffect(OrderObjectNew(o_orderkey, o_custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment))
  def order_o_orderkey(__x: Rep[Order]) = FieldRead[Int](__x, "o_orderkey", "Int")
  def order_o_custkey(__x: Rep[Order]) = FieldRead[Int](__x, "o_custkey", "Int")
  def order_o_orderstatus(__x: Rep[Order]) = FieldRead[Char](__x, "o_orderstatus", "Char")
  def order_o_totalprice(__x: Rep[Order]) = FieldRead[Double](__x, "o_totalprice", "Double")
  def order_o_orderdate(__x: Rep[Order]) = FieldRead[Date](__x, "o_orderdate", "Date")
  def order_o_orderpriority(__x: Rep[Order]) = FieldRead[String](__x, "o_orderpriority", "String")
  def order_o_clerk(__x: Rep[Order]) = FieldRead[String](__x, "o_clerk", "String")
  def order_o_shippriority(__x: Rep[Order]) = FieldRead[Int](__x, "o_shippriority", "Int")
  def order_o_comment(__x: Rep[Order]) = FieldRead[String](__x, "o_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenOrderOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case OrderObjectNew(o_orderkey, o_custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment) => emitValDef(sym, "new " + remap(manifest[Order]) + "(" + quote(o_orderkey)  + "," + quote(o_custkey)  + "," + quote(o_orderstatus)  + "," + quote(o_totalprice)  + "," + quote(o_orderdate)  + "," + quote(o_orderpriority)  + "," + quote(o_clerk)  + "," + quote(o_shippriority)  + "," + quote(o_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait PartOps extends DSLType with Variables with OverloadHack {

  object Part {
    def apply(p_partkey: Rep[Int], p_name: Rep[String], p_mfgr: Rep[String], p_brand: Rep[String], p_type: Rep[String], p_size: Rep[Int], p_container: Rep[String], p_retailprice: Rep[Double], p_comment: Rep[String]) = part_obj_new(p_partkey, p_name, p_mfgr, p_brand, p_type, p_size, p_container, p_retailprice, p_comment)
  }

  implicit def repPartToPartOps(x: Rep[Part]) = new partOpsCls(x)
  implicit def partToPartOps(x: Var[Part]) = new partOpsCls(readVar(x))

  class partOpsCls(__x: Rep[Part]) {
    def p_partkey = part_p_partkey(__x)
    def p_name = part_p_name(__x)
    def p_mfgr = part_p_mfgr(__x)
    def p_brand = part_p_brand(__x)
    def p_type = part_p_type(__x)
    def p_size = part_p_size(__x)
    def p_container = part_p_container(__x)
    def p_retailprice = part_p_retailprice(__x)
    def p_comment = part_p_comment(__x)
  }

  //object defs
  def part_obj_new(p_partkey: Rep[Int], p_name: Rep[String], p_mfgr: Rep[String], p_brand: Rep[String], p_type: Rep[String], p_size: Rep[Int], p_container: Rep[String], p_retailprice: Rep[Double], p_comment: Rep[String]): Rep[Part]

  //class defs
  def part_p_partkey(__x: Rep[Part]): Rep[Int]
  def part_p_name(__x: Rep[Part]): Rep[String]
  def part_p_mfgr(__x: Rep[Part]): Rep[String]
  def part_p_brand(__x: Rep[Part]): Rep[String]
  def part_p_type(__x: Rep[Part]): Rep[String]
  def part_p_size(__x: Rep[Part]): Rep[Int]
  def part_p_container(__x: Rep[Part]): Rep[String]
  def part_p_retailprice(__x: Rep[Part]): Rep[Double]
  def part_p_comment(__x: Rep[Part]): Rep[String]
}

trait PartOpsExp extends PartOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class PartObjectNew(p_partkey: Exp[Int], p_name: Exp[String], p_mfgr: Exp[String], p_brand: Exp[String], p_type: Exp[String], p_size: Exp[Int], p_container: Exp[String], p_retailprice: Exp[Double], p_comment: Exp[String]) extends Def[Part]
  def part_obj_new(p_partkey: Exp[Int], p_name: Exp[String], p_mfgr: Exp[String], p_brand: Exp[String], p_type: Exp[String], p_size: Exp[Int], p_container: Exp[String], p_retailprice: Exp[Double], p_comment: Exp[String]) = reflectEffect(PartObjectNew(p_partkey, p_name, p_mfgr, p_brand, p_type, p_size, p_container, p_retailprice, p_comment))
  def part_p_partkey(__x: Rep[Part]) = FieldRead[Int](__x, "p_partkey", "Int")
  def part_p_name(__x: Rep[Part]) = FieldRead[String](__x, "p_name", "String")
  def part_p_mfgr(__x: Rep[Part]) = FieldRead[String](__x, "p_mfgr", "String")
  def part_p_brand(__x: Rep[Part]) = FieldRead[String](__x, "p_brand", "String")
  def part_p_type(__x: Rep[Part]) = FieldRead[String](__x, "p_type", "String")
  def part_p_size(__x: Rep[Part]) = FieldRead[Int](__x, "p_size", "Int")
  def part_p_container(__x: Rep[Part]) = FieldRead[String](__x, "p_container", "String")
  def part_p_retailprice(__x: Rep[Part]) = FieldRead[Double](__x, "p_retailprice", "Double")
  def part_p_comment(__x: Rep[Part]) = FieldRead[String](__x, "p_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenPartOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case PartObjectNew(p_partkey, p_name, p_mfgr, p_brand, p_type, p_size, p_container, p_retailprice, p_comment) => emitValDef(sym, "new " + remap(manifest[Part]) + "(" + quote(p_partkey)  + "," + quote(p_name)  + "," + quote(p_mfgr)  + "," + quote(p_brand)  + "," + quote(p_type)  + "," + quote(p_size)  + "," + quote(p_container)  + "," + quote(p_retailprice)  + "," + quote(p_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait PartSupplierOps extends DSLType with Variables with OverloadHack {

  object PartSupplier {
    def apply(ps_partkey: Rep[Int], ps_suppkey: Rep[Int], ps_availqty: Rep[Int], ps_supplycost: Rep[Double], ps_comment: Rep[String]) = partsupplier_obj_new(ps_partkey, ps_suppkey, ps_availqty, ps_supplycost, ps_comment)
  }

  implicit def repPartSupplierToPartSupplierOps(x: Rep[PartSupplier]) = new partsupplierOpsCls(x)
  implicit def partsupplierToPartSupplierOps(x: Var[PartSupplier]) = new partsupplierOpsCls(readVar(x))

  class partsupplierOpsCls(__x: Rep[PartSupplier]) {
    def ps_partkey = partsupplier_ps_partkey(__x)
    def ps_suppkey = partsupplier_ps_suppkey(__x)
    def ps_availqty = partsupplier_ps_availqty(__x)
    def ps_supplycost = partsupplier_ps_supplycost(__x)
    def ps_comment = partsupplier_ps_comment(__x)
  }

  //object defs
  def partsupplier_obj_new(ps_partkey: Rep[Int], ps_suppkey: Rep[Int], ps_availqty: Rep[Int], ps_supplycost: Rep[Double], ps_comment: Rep[String]): Rep[PartSupplier]

  //class defs
  def partsupplier_ps_partkey(__x: Rep[PartSupplier]): Rep[Int]
  def partsupplier_ps_suppkey(__x: Rep[PartSupplier]): Rep[Int]
  def partsupplier_ps_availqty(__x: Rep[PartSupplier]): Rep[Int]
  def partsupplier_ps_supplycost(__x: Rep[PartSupplier]): Rep[Double]
  def partsupplier_ps_comment(__x: Rep[PartSupplier]): Rep[String]
}

trait PartSupplierOpsExp extends PartSupplierOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class PartSupplierObjectNew(ps_partkey: Exp[Int], ps_suppkey: Exp[Int], ps_availqty: Exp[Int], ps_supplycost: Exp[Double], ps_comment: Exp[String]) extends Def[PartSupplier]
  def partsupplier_obj_new(ps_partkey: Exp[Int], ps_suppkey: Exp[Int], ps_availqty: Exp[Int], ps_supplycost: Exp[Double], ps_comment: Exp[String]) = reflectEffect(PartSupplierObjectNew(ps_partkey, ps_suppkey, ps_availqty, ps_supplycost, ps_comment))
  def partsupplier_ps_partkey(__x: Rep[PartSupplier]) = FieldRead[Int](__x, "ps_partkey", "Int")
  def partsupplier_ps_suppkey(__x: Rep[PartSupplier]) = FieldRead[Int](__x, "ps_suppkey", "Int")
  def partsupplier_ps_availqty(__x: Rep[PartSupplier]) = FieldRead[Int](__x, "ps_availqty", "Int")
  def partsupplier_ps_supplycost(__x: Rep[PartSupplier]) = FieldRead[Double](__x, "ps_supplycost", "Double")
  def partsupplier_ps_comment(__x: Rep[PartSupplier]) = FieldRead[String](__x, "ps_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenPartSupplierOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case PartSupplierObjectNew(ps_partkey, ps_suppkey, ps_availqty, ps_supplycost, ps_comment) => emitValDef(sym, "new " + remap(manifest[PartSupplier]) + "(" + quote(ps_partkey)  + "," + quote(ps_suppkey)  + "," + quote(ps_availqty)  + "," + quote(ps_supplycost)  + "," + quote(ps_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait RegionOps extends DSLType with Variables with OverloadHack {

  object Region {
    def apply(r_regionkey: Rep[Int], r_name: Rep[String], r_comment: Rep[String]) = region_obj_new(r_regionkey, r_name, r_comment)
  }

  implicit def repRegionToRegionOps(x: Rep[Region]) = new regionOpsCls(x)
  implicit def regionToRegionOps(x: Var[Region]) = new regionOpsCls(readVar(x))

  class regionOpsCls(__x: Rep[Region]) {
    def r_regionkey = region_r_regionkey(__x)
    def r_name = region_r_name(__x)
    def r_comment = region_r_comment(__x)
  }

  //object defs
  def region_obj_new(r_regionkey: Rep[Int], r_name: Rep[String], r_comment: Rep[String]): Rep[Region]

  //class defs
  def region_r_regionkey(__x: Rep[Region]): Rep[Int]
  def region_r_name(__x: Rep[Region]): Rep[String]
  def region_r_comment(__x: Rep[Region]): Rep[String]
}

trait RegionOpsExp extends RegionOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class RegionObjectNew(r_regionkey: Exp[Int], r_name: Exp[String], r_comment: Exp[String]) extends Def[Region]
  def region_obj_new(r_regionkey: Exp[Int], r_name: Exp[String], r_comment: Exp[String]) = reflectEffect(RegionObjectNew(r_regionkey, r_name, r_comment))
  def region_r_regionkey(__x: Rep[Region]) = FieldRead[Int](__x, "r_regionkey", "Int")
  def region_r_name(__x: Rep[Region]) = FieldRead[String](__x, "r_name", "String")
  def region_r_comment(__x: Rep[Region]) = FieldRead[String](__x, "r_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenRegionOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case RegionObjectNew(r_regionkey, r_name, r_comment) => emitValDef(sym, "new " + remap(manifest[Region]) + "(" + quote(r_regionkey)  + "," + quote(r_name)  + "," + quote(r_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}

trait SupplierOps extends DSLType with Variables with OverloadHack {

  object Supplier {
    def apply(s_suppkey: Rep[Int], s_name: Rep[String], s_address: Rep[String], s_nationkey: Rep[Int], s_phone: Rep[String], s_acctbal: Rep[Double], s_comment: Rep[String]) = supplier_obj_new(s_suppkey, s_name, s_address, s_nationkey, s_phone, s_acctbal, s_comment)
  }

  implicit def repSupplierToSupplierOps(x: Rep[Supplier]) = new supplierOpsCls(x)
  implicit def supplierToSupplierOps(x: Var[Supplier]) = new supplierOpsCls(readVar(x))

  class supplierOpsCls(__x: Rep[Supplier]) {
    def s_suppkey = supplier_s_suppkey(__x)
    def s_name = supplier_s_name(__x)
    def s_address = supplier_s_address(__x)
    def s_nationkey = supplier_s_nationkey(__x)
    def s_phone = supplier_s_phone(__x)
    def s_acctbal = supplier_s_acctbal(__x)
    def s_comment = supplier_s_comment(__x)
  }

  //object defs
  def supplier_obj_new(s_suppkey: Rep[Int], s_name: Rep[String], s_address: Rep[String], s_nationkey: Rep[Int], s_phone: Rep[String], s_acctbal: Rep[Double], s_comment: Rep[String]): Rep[Supplier]

  //class defs
  def supplier_s_suppkey(__x: Rep[Supplier]): Rep[Int]
  def supplier_s_name(__x: Rep[Supplier]): Rep[String]
  def supplier_s_address(__x: Rep[Supplier]): Rep[String]
  def supplier_s_nationkey(__x: Rep[Supplier]): Rep[Int]
  def supplier_s_phone(__x: Rep[Supplier]): Rep[String]
  def supplier_s_acctbal(__x: Rep[Supplier]): Rep[Double]
  def supplier_s_comment(__x: Rep[Supplier]): Rep[String]
}

trait SupplierOpsExp extends SupplierOps with FieldAccessOpsExp with EffectExp with BaseFatExp {
  case class SupplierObjectNew(s_suppkey: Exp[Int], s_name: Exp[String], s_address: Exp[String], s_nationkey: Exp[Int], s_phone: Exp[String], s_acctbal: Exp[Double], s_comment: Exp[String]) extends Def[Supplier]
  def supplier_obj_new(s_suppkey: Exp[Int], s_name: Exp[String], s_address: Exp[String], s_nationkey: Exp[Int], s_phone: Exp[String], s_acctbal: Exp[Double], s_comment: Exp[String]) = reflectEffect(SupplierObjectNew(s_suppkey, s_name, s_address, s_nationkey, s_phone, s_acctbal, s_comment))
  def supplier_s_suppkey(__x: Rep[Supplier]) = FieldRead[Int](__x, "s_suppkey", "Int")
  def supplier_s_name(__x: Rep[Supplier]) = FieldRead[String](__x, "s_name", "String")
  def supplier_s_address(__x: Rep[Supplier]) = FieldRead[String](__x, "s_address", "String")
  def supplier_s_nationkey(__x: Rep[Supplier]) = FieldRead[Int](__x, "s_nationkey", "Int")
  def supplier_s_phone(__x: Rep[Supplier]) = FieldRead[String](__x, "s_phone", "String")
  def supplier_s_acctbal(__x: Rep[Supplier]) = FieldRead[Double](__x, "s_acctbal", "Double")
  def supplier_s_comment(__x: Rep[Supplier]) = FieldRead[String](__x, "s_comment", "String")

  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case _ => super.mirror(e,f)
  }
}

trait ScalaGenSupplierOps extends ScalaGenFat {
  val IR: ApplicationOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  // these are the ops that call through to the underlying real data structure
    case SupplierObjectNew(s_suppkey, s_name, s_address, s_nationkey, s_phone, s_acctbal, s_comment) => emitValDef(sym, "new " + remap(manifest[Supplier]) + "(" + quote(s_suppkey)  + "," + quote(s_name)  + "," + quote(s_address)  + "," + quote(s_nationkey)  + "," + quote(s_phone)  + "," + quote(s_acctbal)  + "," + quote(s_comment)  + ")")
    case _ => super.emitNode(sym, rhs)
  }
}
trait ApplicationOps extends CustomerOps with LineItemOps with NationOps with OrderOps with PartOps with PartSupplierOps with RegionOps with SupplierOps
trait ApplicationOpsExp extends FieldAccessOpsExp with CustomerOpsExp with LineItemOpsExp with NationOpsExp with OrderOpsExp with PartOpsExp with PartSupplierOpsExp with RegionOpsExp with SupplierOpsExp
trait ScalaGenApplicationOps extends ScalaGenFieldAccessOps with ScalaGenCustomerOps with ScalaGenLineItemOps with ScalaGenNationOps with ScalaGenOrderOps with ScalaGenPartOps with ScalaGenPartSupplierOps with ScalaGenRegionOps with ScalaGenSupplierOps 
