package utcompling.mlnsemantics.inference

import org.apache.commons.logging.LogFactory
import utcompling.scalalogic.discourse._
import utcompling.scalalogic.discourse.impl._
import utcompling.scalalogic.discourse.candc.boxer._
import utcompling.scalalogic.discourse.candc.boxer.expression._
import utcompling.scalalogic.discourse.candc.boxer.expression.interpreter.impl._
import utcompling.scalalogic.drt.expression._
import utcompling.scalalogic.top.expression._
import utcompling.scalalogic.discourse.candc.call._
import utcompling.scalalogic.discourse.candc.call.impl._
import utcompling.scalalogic.discourse.candc.parse.output.impl._
import opennlp.scalabha.util.FileUtils
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.CollectionUtil._
import utcompling.scalalogic.discourse.candc.boxer.expression.interpreter.BoxerExpressionInterpreter
import utcompling.scalalogic.discourse.candc.boxer.expression.parse.BoxerExpressionParser
import dhg.depparse._
import expression.BoxerExpression
import utcompling.mlnsemantics.util.DepParseUtil

/**
 * Discourse Interpreter that simply interprets pre-parsed strings
 */
class DependencyParsedBoxerDiscourseInterpreter[T](
    depParser: DepParser,
    wordSequence:Boolean=false)
  extends DiscourseInterpreter[T] {

  private val LOG = LogFactory.getLog(classOf[DependencyParsedBoxerDiscourseInterpreter[T]])
  /**
   * Hook to which all interpret calls delegate.
   */
  override def batchInterpretMultisentence(inputs: List[List[String]], discourseIds: Option[List[String]] = None, question: Boolean = false, verbose: Boolean = false): List[Option[T]] = {
    val newDiscourseIds = discourseIds.getOrElse((0 until inputs.length).map(_.toString).toList)
    
    require(inputs.length == newDiscourseIds.length)
    
    //val graphs = inputs.map(t => depParser.apply(t.head).head)
    //val box = graphs.map(g =>  Option(predicatesToBoxerExpression(g.logic, "t").asInstanceOf[T]))

    //return box;
    
    (inputs zipSafe newDiscourseIds)
      .map {
        case (sentence, discourseId) =>
          //val graph = depParser.apply(sentence.head).head
          //println(graph.graphviz)
          //val boxerExp = predicatesToBoxerExpression(graph.logic, discourseId)
          val boxerExp = DepParseUtil.process(sentence.head, discourseId, wordSequence);
          val listOfExpW = List((boxerExp, 1.0))
          val exp:T = BoxerPrs(listOfExpW).asInstanceOf[T]
          Option(  exp )
        case _ => None
      }
      .toList
  }
  
  //protected def predicatesToBoxerExpression(preds: List[Predicate]): T;
  
  def predicatesToBoxerExpression(preds: List[Predicate], discId: String) = {
    var refs: Set[String] = Set();
    var conds: Set[BoxerExpression] = Set();
    preds.foreach(p=>{
      p.varName2 match {
        case Some(varName2) => conds += BoxerRel(discId, List(), BoxerVariable(p.varName), BoxerVariable(varName2), p.name, 0); 
        case _ => {
         var pos = p.tag.substring(0,1).toLowerCase;
         if(pos == "j") pos = "a"
         conds += BoxerPred(discId, List(BoxerIndex(p.idx)), BoxerVariable(p.varName), p.name, pos, 0)
         refs += p.varName
        }
      } 
    })
    //refs: List[(List[BoxerIndex], BoxerVariable)]
    //conds: List[BoxerExpression])

    BoxerDrs(refs.map(ref => (List(), BoxerVariable(ref))).toList, conds.toList);
  }

}


