package utcompling.mlnsemantics.inference

import utcompling.mlnsemantics.vecspace._
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.CollectionUtil._
import math.min
import utcompling.mlnsemantics.run.Sts

trait RuleWeighter {
  def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace): Iterable[(String, Option[Double])]
}

case class UniformHardRuleWeighter() extends RuleWeighter {
  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    consequentAndContexts.map(_._1 -> None)
  }
}

case class ACtxWithCVecspaceRuleWeighter(
  compositeVectorMaker: CompositeVectorMaker)
  extends RuleWeighter {

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val pv = compositeVectorMaker.make(antecedentContext, vectorspace, Sts.text, Sts.textLemma)
    consequentAndContexts.map {
      case (consequent, consequentContext) =>
        consequent -> Some(vectorspace.get(consequent) match {
          case Some(cv) => Sts.opts.lexicalInferenceMethod.entails(pv, cv)
          case None => 0
        })
    }
  }
}

case class AwithCvecspaceRuleWeighter(
  compositeVectorMaker: CompositeVectorMaker)
  extends RuleWeighter {

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val pv = vectorspace.get(antecedent)
    consequentAndContexts.map {
      case (consequent, consequentContext) =>
        consequent -> Some(vectorspace.get(consequent) match {
          case Some(cv) => pv match {
            case Some(pv) => Sts.opts.lexicalInferenceMethod.entails(pv, cv)
            case None => 0  //it is 0 not Double.NegativeInfinity
          }
          case None => 0  //it is 0 not Double.NegativeInfinity
        })
    }
  }
}

case class AwithCvecspaceWithSpellingSimilarityRuleWeighter(
  compositeVectorMaker: CompositeVectorMaker)
  extends RuleWeighter {

  private def spellingSimilarity (s1: String, s2: String): Double = {
    if (s1.length() < 5)
      return 0;
    if (s2.length() < 5)
      return 0;
    var score = s1.toSet.intersect(s2.toSet).size * 1.0/ (s1.toSet ++ s2.toSet).size;
    score = min(0.99, score);
    if (score > 0.5)
      return score;
    else
      return 0;
  }
  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    /*
    println(Sts.text)
    println(Sts.textLemma)
    println(Sts.hypothesis)
    println(Sts.hypothesisLemma)
    */
    val pv = compositeVectorMaker.make(antecedent.split(" "), vectorspace, Sts.text, Sts.textLemma);
    consequentAndContexts.collect {
      case (consequent, consequentContext) =>
        val w = spellingSimilarity(antecedent, consequent);
        val cv = compositeVectorMaker.make(consequent.split(" "), vectorspace, Sts.hypothesis, Sts.hypothesisLemma);
        consequent -> Some(Sts.opts.lexicalInferenceMethod.entails(pv, cv))
    }
  }
}

case class CompositionalRuleWeighter(
  compositeVectorMaker: CompositeVectorMaker)
  extends RuleWeighter {

  val ruleWeighter = AwithCvecspaceWithSpellingSimilarityRuleWeighter(compositeVectorMaker);

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val antecedentWords =  antecedent.split(" ")
    consequentAndContexts.map {
      case (consequent, consequentContext) =>{
        val consequentWords = consequent.split(" ")
        var totalW = 0.0;

        antecedentWords.foreach(aw =>{
           consequentWords.foreach(cw =>{
           totalW = totalW + ruleWeighter.weightForRules(aw, antecedentContext, Map(cw->consequentContext), vectorspace).head._2.get;
           });
        });
        totalW = totalW/(antecedentWords.size * consequentWords.size) 
        consequent -> Some(totalW)
      }
    }
  }
}


case class AwithCtxCwithCtxVecspaceRuleWeighter(
  compositeVectorMaker: CompositeVectorMaker)
  extends RuleWeighter {

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val pv = compositeVectorMaker.make(antecedent +: antecedentContext.toSeq, vectorspace, Sts.text, Sts.textLemma)
    consequentAndContexts.map {
      case (consequent, consequentContext) =>
        val cv = compositeVectorMaker.make(consequent +: consequentContext.toSeq, vectorspace, Sts.hypothesis, Sts.hypothesisLemma)
        consequent -> Some(Sts.opts.lexicalInferenceMethod.entails(pv, cv))
    }
  }
}

class SameLemmaHardClauseRuleWeighter(delegate: RuleWeighter) extends RuleWeighter {
  def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace): Iterable[(String, Option[Double])] = {
    val (same, diff) = consequentAndContexts.partition(_._1 == antecedent)
    same.map(_._1).mapToVal(Some(Double.PositiveInfinity)) ++ delegate.weightForRules(antecedent, antecedentContext, diff, vectorspace)
  }
}

case class RankingRuleWeighter(
  delegate: RuleWeighter)
  extends RuleWeighter {

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val weighted = delegate.weightForRules(antecedent, antecedentContext, consequentAndContexts, vectorspace)
    val unoptionedWeighted = weighted.mapVals(_.getOrElse(Double.PositiveInfinity))
    val sortedGroupedWeighted = unoptionedWeighted.map(_.swap).groupByKey.toSeq.sortBy(-_._1).map(_._2)
    val ranked =
      sortedGroupedWeighted
        .foldLeft((Map[String, Int](), 1)) {
          case ((accum, rank), cs) =>
            (accum ++ cs.map(_ -> rank), rank + cs.size)
        }._1
    val inverseRanked = ranked.mapVals(1.0 / _)
    inverseRanked.normalizeValues.mapVals(Option(_))
  }
}

case class TopRuleWeighter(
  delegate: RuleWeighter)
  extends RuleWeighter {

  override def weightForRules(antecedent: String, antecedentContext: Iterable[String], consequentAndContexts: Map[String, Iterable[String]], vectorspace: BowVectorSpace) = {
    val weighted = delegate.weightForRules(antecedent, antecedentContext, consequentAndContexts, vectorspace)
    if (weighted.nonEmpty)
      Iterable(weighted.maxBy(_._2.getOrElse(Double.PositiveInfinity)))
    else
      Iterable.empty
  }
}
