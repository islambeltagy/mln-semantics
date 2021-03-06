package utcompling.mlnsemantics.inference

import utcompling.scalalogic.discourse.candc.boxer.expression.BoxerPred
import utcompling.mlnsemantics.vecspace.BowVector
import opennlp.scalabha.util.CollectionUtils._
import utcompling.mlnsemantics.vecspace._
import org.apache.commons.logging.LogFactory
import utcompling.mlnsemantics.run.Sts

trait CompositeVectorMaker {
  def extractWordPosInd(pred: String): (String, String, Int) = {
    val items = pred.split("-").toArray
    val word = items.slice(0, items.length - 2) mkString "-"
    val pos = items(items.length - 2)
    val ind = items(items.length - 1)
    (word, pos, ind.toInt)
  }

  def extractWord(pred: String): String = {
    val (word, pos, ind) = extractWordPosInd(pred)
    Sts.opts.vectorspaceFormatWithPOS match {
      case true => word + "-" + pos
      case false => word
    }
  }

  def make(preds: Iterable[String], vectorspace: BowVectorSpace, sentence: String, lemmatizedSent: String): BowVector
}

case class SimpleCompositeVectorMaker() extends CompositeVectorMaker {
  override def make(preds: Iterable[String], vectorspace: BowVectorSpace, sentence: String, lemmatizedSent: String): BowVector = {
  	val words =  preds.map(extractWord).toSet //remove duplicate words
    val toCombine = words.flatMap(vectorspace.get)
    if (toCombine.isEmpty)
      new SparseBowVector(numDims = vectorspace.numDims)
    else
      toCombine.reduce(_ + _)
  }
}

case class NgramCompositeVectorMaker(n: Int, alpha: Double) extends CompositeVectorMaker {

  private def extractNgramVector(phrase: Array[String], vectorspace: BowVectorSpace): BowVector = {
    vectorspace.getOrZero(phrase mkString " ")
  }

  private def tokenize(phrase: String): Array[String] = {
    phrase.toLowerCase
          .replace(".", " .")
          .replace(",", " ,")
          .replace("n't", " n't")
          .split(" ")
  }

  private val LOG = LogFactory.getLog(classOf[NgramCompositeVectorMaker])
  override def make(preds: Iterable[String], vectorspace: BowVectorSpace, sentence: String, lemmatizedSent: String): BowVector = {
    assert(!utcompling.mlnsemantics.run.Sts.opts.vectorspaceFormatWithPOS, "NgramCompositeVectorMaker doesn't work with a pos-tagged space.")

    val sent_a = sentence.split(" ")
    val wordPosInds = preds.map(extractWordPosInd _).filter(_._3 > 0)
    val pred_words = wordPosInds.map(_._1)
    val positions = wordPosInds.map(_._3).toArray

    if (positions.length == 0) {
      vectorspace.zero
    } else {
      val subsentence = sent_a.slice(positions.min-1, positions.max)
      //LOG.info("We want a vector for '" + (preds mkString ",") + "' in '" + (subsentence mkString " ") + "'.")
      var finalV: BowVector = vectorspace.zero
      for (n_ <- (1 until (math.min(n, subsentence.length) + 1))) {
        val piece: BowVector = subsentence.sliding(n_).map(extractNgramVector(_, vectorspace)).reduce(_ + _)
        finalV = piece * alpha + finalV * (1 - alpha)
      }
      finalV
    }
  }
}

case class MultiplicationCompositeVectorMaker() extends CompositeVectorMaker {
  override def make(preds: Iterable[String], vectorspace: BowVectorSpace, sentence: String, lemmatizedSent: String): BowVector = {
    preds.map(extractWord).flatMap(vectorspace.get).reduce(_ :* _)
  }
}

