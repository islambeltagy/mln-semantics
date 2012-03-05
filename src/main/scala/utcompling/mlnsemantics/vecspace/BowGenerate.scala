package utcompling.mlnsemantics.vecspace

import utcompling.mlnsemantics.util.ScoobiUtil._
import scala.collection.JavaConversions._
import utcompling.scalalogic.util.FileUtils._
import utcompling.scalalogic.util.CollectionUtils._
import utcompling.scalalogic.discourse.candc.call.impl.CandcImpl
import utcompling.scalalogic.discourse.DiscourseInterpreter
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{ Map => MMap }
import com.nicta.scoobi.Scoobi._
import com.nicta.scoobi.DList
import com.nicta.scoobi.DList._
import com.nicta.scoobi.io.text.TextInput._
import com.nicta.scoobi.io.text.TextOutput._
import java.io.File

/**
 *
 */
object BowGenerate {

  val MIN_COUNT = 10 //50
  val NUM_FEATURES = 20 //00
  val WINDOW_SIZE = scala.Int.MaxValue
  val punctuation = Set(".", ",", "``", "''", "'", "`", "--", ":", ";", "-RRB-", "-LRB-", "?", "!", "-RCB-", "-LCB-", "...")

  def main(allArgs: Array[String]) = withHadoopArgs(allArgs) { args =>
    val List(inputFile, outputFile) = args.toList

    val allWordsSorted = getSortedCounts(inputFile)
    println("ALL WORDS:")
    allWordsSorted.foreach(println)
    val features = allWordsSorted.take(NUM_FEATURES).map(_._1).toSet

    val vectors = getBowVectors(inputFile, features, allWordsSorted.toMap)
    val vectorStrings = vectors.map { case (word, vector) => "%s\t%s".format(word, vector.map { case (feature, count) => "%s\t%s".format(feature, count) }.mkString("\t")) }
    persist(toTextFile(vectorStrings, outputFile))
  }

  def getSortedCounts(inputFile: String) = {
    val DUMMY = ""

    // Get the count of each word in the corpus
    val countsWithDummy: DList[(String, (Int, Int))] =
      fromTextFile(inputFile)
        .flatMap(_ // for each sentence
          .trim // remove trailing space
          .split(" ").toList // split into individual tokens
          .counts // map words to the number of times they appear in this sentence
          .map {
            // map word to its count in the sentence AND a count of 1 document 
            // that they word has appeared in. 
            case (word, count) => (word, (count, 1))
          } + (DUMMY -> (0, 1))) // add a dummy word to count the total number of sentences
        .groupByKey
        .combine { case ((tf1: Int, df1: Int), (tf2: Int, df2: Int)) => (tf1 + tf2, df1 + df2) }

    // Extract the sentence-counting dummy
    val (dummyCount, counts) = countsWithDummy.partition { case (word, _) => word == DUMMY }

    // Get scalar number of sentences
    val List((_, (_, numSentences))) = dummyCount.toIterable.toList
    println("numSentences = " + numSentences)

    // Keep only the non-punctuation words occurring more than MIN_COUNT times
    val filteredCounts = counts.filter { case (w, (tf, df)) => tf >= MIN_COUNT && !punctuation(w) }

    // Compute TF-IDF value for each word (negated so that sorting works out)
    // (Note: We use 1 instead of the actual number of documents b/c all we care about is the ordering.) 
    val tfidfs = filteredCounts.map { case (word, (tf, df)) => (word, -tf * math.log(numSentences.toDouble / df)) }

    // Sort by frequency
    val sortedTfidfs: DList[(String, Double)] = tfidfs.map(_.swap).groupByKey.flatMap { case (tfidf, words) => words.map(_ -> -tfidf) }

    sortedTfidfs.toIterable
  }

  def getBowVectors(inputFile: String, features: Set[String], tfidfs: Map[String, Double]) = {
    fromTextFile(inputFile)
      .map(_ // for each sentence
        .trim // remove trailing space
        .split(" ").toList) // split into individual tokens
      .flatMap { tokens => // take the list of tokens
        tokens.zipWithIndex.collect {
          case (token, i) if tfidfs.contains(token) => // for each token that meets the cutoff
            val before = tokens.slice(i - WINDOW_SIZE, i) // get the tokens before it
            val after = tokens.slice(i + 1, i + 1 + WINDOW_SIZE) // and the tokens after it
            val featuresInWindow = (before ++ after).filter(features) // keep only the features in the window
            (token, featuresInWindow)
        }
      }
      .groupByKey.map {
        case (word, contexts) =>
          (word, contexts.flatten.counts.map { // convert contexts to feature counts
            case (feature, count) =>
              (feature, count * tfidfs(feature)) // scale feature counts by the TF-IDF of the feature
          })
      }
  }
}