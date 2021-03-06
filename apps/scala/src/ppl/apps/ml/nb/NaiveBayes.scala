package ppl.apps.ml.nb

import ppl.dsl.optiml._
import ppl.dsl.optiml.datastruct.scala.{Vector,Matrix,TrainingSet,Labels}
import ppl.delite.framework.DeliteApplication

object NaiveBayesRunnerNCNR extends OptiMLApplicationRunnerBase with OptiMLNoCSE with OptiMLExp with NaiveBayes
object NaiveBayesRunnerNC extends OptiMLApplicationRunner with OptiMLNoCSE with NaiveBayes
object NaiveBayesRunnerNR extends OptiMLApplicationRunnerBase with OptiMLExp with NaiveBayes
object NaiveBayesRunner extends OptiMLApplicationRunner with NaiveBayes
trait NaiveBayes extends OptiMLApplication {

  def print_usage = {
    println("NaiveBayes <training file> <test file>")
    exit(-1)
  }

  def main() = {
    println("Naive Bayes Example Application")

    if (args.length < 2) print_usage

    val trainingFile = args(0)
    val testFile = args(1)

    // Train Model
    val trainingSet = MLInputReader.readTokenMatrix(trainingFile)
    //val start_train = System.currentTimeMillis()
    println("Training model on " + trainingSet.numSamples + " documents.")
    tic()
    val (phi_y1, phi_y0, phi_y) = train(trainingSet)
    toc(phi_y1,phi_y0)

    // test
    val testSet = MLInputReader.readTokenMatrix(testFile)
    println("phi_y1: "); phi_y1.pprint; println("phi_y0: "); phi_y0.pprint; println("phi_y: "+ phi_y)
    val incorrect_classifications = test(testSet, phi_y1, phi_y0, phi_y)
    println("Test error: " + incorrect_classifications.doubleValue() / testSet.numSamples.doubleValue())

    //PerformanceTimer.save("NaiveBayes")
  }

  def train(ts: Rep[TrainingSet[Double,Double]]) : (Rep[Vector[Double]], Rep[Vector[Double]], Rep[Double]) = {
    val numTrainDocs = ts.numSamples
    val numTokens = ts.numFeatures

//    println("training set: ")
//    ts.pprint
//    println("training set transposed: ")
//    ts.t.pprint
//    println("training set again: ")
//    ts.pprint
//    println("training set transposed again: ")
//    ts.t.pprint

    val words_per_email = (0::numTrainDocs){ i => ts(i).sum }

    val spamcount = ts.labels.sum

//    val phi_y1 = Vector.zeros(numTokens).mutable
//    val phi_y0 = Vector.zeros(numTokens).mutable

    val phi_y1 = (0::numTokens) { j =>
      val spamwordcount   = sumIf(0, numTrainDocs) { ts.labels(_) == 1 } { i => ts.t(j,i) }
      val spam_totalwords = sumIf(0, numTrainDocs) { ts.labels(_) == 1 } { i => words_per_email(i) }
      
      (spamwordcount + 1) / (spam_totalwords + numTokens)
    }
    
    val phi_y0 = (0::numTokens) { j => 
      val nonspamwordcount   = sumIf(0, numTrainDocs) { ts.labels(_) != 1 } { i => ts.t(j,i) }
      val nonspam_totalwords = sumIf(0, numTrainDocs) { ts.labels(_) != 1 } { i => words_per_email(i) }

      (nonspamwordcount + 1) / (nonspam_totalwords + numTokens)
    }
    
    /*
    for (j <- 0::numTokens) {
      var spamwordcount = 0.0
      var spam_totalwords = 0.0
      var nonspamwordcount = 0.0
      var nonspam_totalwords = 0.0
      var i = 0

      while (i < numTrainDocs) {
        if (ts.labels(i) == 1){
          spamwordcount += ts.t(j,i)
          spam_totalwords += words_per_email(i)
        }
        else {
          nonspamwordcount += ts.t(j,i)
          nonspam_totalwords += words_per_email(i)
        }
        i += 1
      }
      phi_y1(j) = (spamwordcount + 1) / (spam_totalwords + numTokens)
      phi_y0(j) = (nonspamwordcount + 1) / (nonspam_totalwords + numTokens)      
    }
    */

    val phi_y = spamcount / numTrainDocs

    (phi_y1, phi_y0, phi_y)
  }

  def test(ts: Rep[TrainingSet[Double,Double]], phi_y1: Rep[Vector[Double]], phi_y0: Rep[Vector[Double]], phi_y: Rep[Double]): Rep[Int] = {
    val numTestDocs = ts.numSamples
    val numTokens = ts.numFeatures

    println("Testing model on " + numTestDocs + " documents.")

    val output = (0::numTestDocs) { j =>
      // compute log(p(x|y=1)p(y=1)) and log(p(x|y=0)p(y=0))
      val p_norm = sumIf(0,numTokens) { i => ts(j,i) > 0 } { i => (Math.log(phi_y0(i)) + Math.log(1.-phi_y)) * ts(j,i) }
      val p_spam = sumIf(0,numTokens) { i => ts(j,i) > 0 } { i => (Math.log(phi_y1(i)) + Math.log(phi_y)) * ts(j,i) }

      if (p_spam > p_norm) 1.
      else 0.
    }

    // Compute error on test set
    // why does sum without Int infer a double when we return 1/0?
    val incorrectClassifications = sum(0, numTestDocs) { i =>
     if (ts.labels(i) != output(i)) 1
     else 0
    }
    incorrectClassifications
  }
}
