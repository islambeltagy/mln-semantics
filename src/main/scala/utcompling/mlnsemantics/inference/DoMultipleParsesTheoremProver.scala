package utcompling.mlnsemantics.inference

import edu.mit.jwi.item.POS
import scala.collection.JavaConversions._
import scala.collection.mutable.SetBuilder
import utcompling.mlnsemantics.inference.support.HardWeightedExpression
import utcompling.mlnsemantics.inference.support.WeightedExpression
import utcompling.mlnsemantics.vecspace.BowVector
import utcompling.scalalogic.discourse.candc.boxer.expression._
import utcompling.mlnsemantics.inference.support.SoftWeightedExpression
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.CollectionUtil._
import org.apache.commons.logging.LogFactory
import support.HardWeightedExpression
import utcompling.mlnsemantics.run.Sts
import opennlp.scalabha.util.FileUtils
import scala.math

class DoMultipleParsesTheoremProver(
  delegate: ProbabilisticTheoremProver[BoxerExpression])
  extends ProbabilisticTheoremProver[BoxerExpression] {  
  
  private val LOG = LogFactory.getLog(classOf[DoMultipleParsesTheoremProver])

  override def prove(
    constants: Map[String, Set[String]],
    declarations: Map[BoxerExpression, Seq[String]],
    evidence: List[BoxerExpression],
    assumptions: List[WeightedExpression[BoxerExpression]],
    goal: BoxerExpression): Seq[Double] = {

    var score: List[Double] = List();/* = Sts.opts.task match {
      case "sts" => 0;
      case "rte" => -10;
    }*/
    //var scoreDenum: Double = 0;
    var index = 0;

    def misParseResult : List[Double] = 
    {
		return { 
			if(Sts.opts.task == "sts")
				List(-2.0, -2.0)
			else  if (Sts.opts.task == "rte" /*&& Sts.opts.withNegT*/ && Sts.opts.softLogicTool != "psl")
				List(-2.0, -2.0, -2.0, -2.0)
			else List(-2.0)
		}
    }

    goal match
    {
      case BoxerPrs(goalParses) => 
      {
        assumptions.head.expression match
        {
	    	case BoxerPrs(assumptionParses) =>
	    	{
	    		for(i <- 0 to Sts.opts.kbest-1)
	    		{
	    			for(j <- 0 to Sts.opts.kbest-1)
		    		{
						var result: List[Double] = List();
	    				if (goalParses.length <= i || assumptionParses.length <= /*i*/ j)
							result = misParseResult
	    				else
	    				{
							val assumption = assumptionParses.apply( /*i*/ j)._1;
							val goal = goalParses.apply(i)._1
							//println(assumption);
							//println(goal)
							result  = 
							{
								if(assumption == BoxerDrs() || goal == BoxerDrs())
									misParseResult
								else 
									delegate.prove(Map(), Map(), List(), List(HardWeightedExpression(assumption)), goal).toList
							}
							if(Sts.opts.task == "sts")
								assert(result.length == 2);
							else  if (Sts.opts.task == "rte" && /*Sts.opts.withNegT &&*/ Sts.opts.softLogicTool != "psl")
								assert(result.length == 4, "length should be 4 but got " + result.length + " with values: " + result); //check GivenNotTextProbabilisticTheoremProver for details
							else assert(result.length == 1);
							/*
							val oneScore = result match { case Seq(s) => s; case Seq() => -1 /* unknown error*/};
							if(Sts.opts.task == "sts")
							{
								if (oneScore <= 0 )	
								{
									score = score ++ List(oneScore)
									score = score ++ List(oneScore)
								}
								else
								{
									println(oneScore);
									score = score ++ List((oneScore.toInt/10)/10000.0)
									score = score ++ List(oneScore - ((oneScore.toInt/10)*10) )
								}
							}
							else
								score = score ++ List(oneScore)
							*/	    					
	    				}
						if (score.length == 0)
							score = score ++ result;
						else
							score = score.indices.map (idx => scala.math.max(score(idx), result(idx))).toList
		    		} 
	    		}
			}//end case BoxerPrs(assumptionParses)
	    	case _ =>  throw new RuntimeException ("Premise and Hypothesis both should start with BoxerPrs")
	    }//end assumptions.head.expression match
      }//end of case BoxerPrs(goalParses)
      case _ => return delegate.prove(constants, declarations, evidence, assumptions, goal)
     } //eng goal match 
     return score
  }  
}
