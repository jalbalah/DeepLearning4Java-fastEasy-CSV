package org.deeplearning4j.examples.dataExamples;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
/**
 * @author Adam Gibson, 
   modified by Jason Albalah
 */
public class CSVExample {

    private static Logger log = LoggerFactory.getLogger(CSVExample.class);

    public static void main(String[] args) throws  Exception {

		System.out.println();

		// try other loading than RecordReader... (from word2vecrawtextexample.java)
		String filePath = "C:/Users/Jason /lbalah/Desktop/Java/dl4j-0.4-examples/dl4j-examples/src/main/resources";//new ClassPathResource("raw_sentences.txt").getFile().getAbsolutePath();

        //First: get the dataset using the record reader. CSVRecordReader handles loading/parsing
        //String filename = "SandPFormatAllRand.csv";
		if(args.length < 6) {
			System.out.println("Incorrect command line arguments: filename numClassesInLastCol numCols numRowsOrBatchSize learningRate iterations");
			System.out.println("Eg.: XOR.csv 2 6 250 .1 100000");
		}
		String filename = args[0];
		boolean iris = false; // test dataset
		if(filename == "iris.csv") iris = true;
		int numLinesToSkip = 0;
        String delimiter = ",";
        RecordReader recordReader = new CSVRecordReader(numLinesToSkip,delimiter);
		recordReader.initialize(new FileSplit(new File(System.getProperty("user.dir")+"/dl4j-examples/src/main/resources/"+filename)));
		//recordReader.initialize(new FileSplit(new ClassPathResource("iris.txt").getFile())); // FileNotFound exception

        //Second: the RecordReaderDataSetIterator handles conversion to DataSet objects, ready for use in neural network
		int labelIndex = Integer.parseInt(args[2]) - 1;
		int numClasses = Integer.parseInt(args[1]);
		int batchSize = Integer.parseInt(args[3]);
		if(iris){
			labelIndex = 4;     //5 values in each row of the iris.txt CSV: 4 input features followed by an integer label (class) index. Labels are the 5th value (index 4) in each row
			numClasses = 3;     //3 classes (types of iris flowers) in the iris data set. Classes have integer values 0, 1 or 2
			batchSize = 150;    //Iris data set: 150 examples total. We are loading all of them into one DataSet (not recommended for large data sets)
		}
        DataSetIterator iterator = new RecordReaderDataSetIterator(recordReader,batchSize,labelIndex,numClasses);	
		int numInputs = labelIndex;
		int outputNum = numClasses;
		if(iris){ 
			numInputs = 4;
			outputNum = 3;
		}
        int iterations = Integer.parseInt(args[5]);
		float LR = Float.parseFloat(args[4]);
        long seed = 6;	
		
		DataSet allData = iterator.next();
        allData.shuffle();
        SplitTestAndTrain testAndTrain = allData.splitTestAndTrain(0.65);  //Use 65% of data for training

        DataSet trainingData = testAndTrain.getTrain();
        DataSet testData = testAndTrain.getTest();

        //We need to normalize our data. We'll use NormalizeStandardize (which gives us mean 0, unit variance):
        DataNormalization normalizer = new NormalizerStandardize();
        normalizer.fit(trainingData);           //Collect the statistics (mean/stdev) from the training data. This does not modify the input data
        normalizer.transform(trainingData);     //Apply normalization to the training data
        normalizer.transform(testData);         //Apply normalization to the test data. This is using statistics calculated from the *training* set

        log.info("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(seed)
            .iterations(iterations)
            .activation("tanh")
            .weightInit(WeightInit.XAVIER)
            .learningRate(LR)
            .regularization(true).l2(1e-4)
            .list()
            .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(3)
                .build())
            .layer(1, new DenseLayer.Builder().nIn(3).nOut(3)
                .build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .activation("softmax")
                .nIn(3).nOut(outputNum).build())
            .backprop(true).pretrain(false)
            .build();

        //run the model
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        model.setListeners(new ScoreIterationListener(100));

        model.fit(trainingData);

        //evaluate the model on the test set
        Evaluation eval = new Evaluation(3);
		//Evaluation eval = new Evaluation(163);
        INDArray output = model.output(testData.getFeatureMatrix());
        eval.eval(testData.getLabels(), output);
        log.info(eval.stats());
    }

}