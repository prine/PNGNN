/**
 * 
 */
package ch.fhnw.students.woipv.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

import ch.fhnw.students.woipv.neuralnetwork.Layer;
import ch.fhnw.students.woipv.neuralnetwork.NeuralNetwork;
import ch.fhnw.students.woipv.neuralnetwork.Neuron;
import ch.fhnw.students.woipv.neuralnetwork.Neuron.NeuronType;

/**
 * JavaNNS Parser
 * 
 * @author Robin Oster (robin.oster@students.fhnw.ch)
 *
 */
public class JNNSParser {

	private static final String NETWORK_FILE_NAME = "compression.net";
	private Scanner scanner;
	private int currentLineNumber = 1; // starts with line 1 not 0...
	
	private HashMap<String, Integer> settings = new HashMap<String, Integer>();
	private String currentLine;
	
	private int numberOfUnits = Integer.MIN_VALUE;
	
	private int destNeuron = Integer.MIN_VALUE;
	
	private boolean firstTime = true;
	
	private NeuralNetwork neuralNetwork;
	
	public JNNSParser() {
		
		// START PARSING
		try {
			scanner = new Scanner(new File(NETWORK_FILE_NAME));
			
			while(scanner.hasNext()) {
				parseLine(scanner.nextLine());
			}
			
		} catch (FileNotFoundException e) {
			System.out.println("JNNSParser: Couldn't read the Network file (" + NETWORK_FILE_NAME + ")");
		}		
	}
	
	private void parseLine(String line) {
		
		currentLine = line;
		
		
		/*
		 * PARSE SETTINGS
		 */
		parseSettings();
				
		
		/*
		 * PARSE UNIT DEFINITION SECTION
		 */
		parseUnitDefinition();
		
		
		/*
		 * PARSE THE WEIGHTS
		 */
		parseWeights();
		
		
		currentLineNumber++;
	}

	private void parseSettings() {
		
		// PARSE THE SETTINGS
		if(currentLineNumber > 5 && currentLineNumber < 10) {
			
			String[] splitArr = currentLine.split(":");
			
			if(scanner.hasNext()) {
				String title = splitArr[0];
				String value = splitArr[1];
				
				settings.put(title.trim(), Integer.parseInt(value.trim()));
			}
		}
	}
	
	private void parseUnitDefinition() {
		
		int startOfUnitDefinition = 27;
	
		if(currentLineNumber > startOfUnitDefinition && currentLineNumber < (startOfUnitDefinition + settings.get("no. of units") + 1)) {
			
			
			if(firstTime) {
				// get the number of neurons and create the neuron weight matrix
				numberOfUnits = settings.get("no. of units");
				neuralNetwork = new NeuralNetwork(numberOfUnits);

				firstTime = false;
			}
			
			// First time initialize the neurons array with the correct size
			String[] splitArr = currentLine.split("\\|");
			
			NeuronType type;
			Neuron neuron;
			
			if(splitArr[5].trim().equals("i")) {
				// INPUT
				type = NeuronType.INPUT;
			} else if(splitArr[5].trim().equals("h")) {
				// HIDDEN
				type = NeuronType.HIDDEN;
			} else {
				// OUTPUT
				type = NeuronType.OUTPUT;
			}
			
			// Create neuron
			neuron = new Neuron(type, Integer.parseInt(splitArr[0].trim()), Double.parseDouble(splitArr[3].trim()), Double.parseDouble(splitArr[4].trim()));
					
			// add the neuron to the right layer
			if(neuralNetwork.getLayerByType(type) != null) {
				// add the neuron to the existing layer
				neuralNetwork.getLayerByType(type).addNeuron(neuron);
			} else {
				// Layer does not exist, create a new layer with the correct type
				Layer layer = new Layer(type);
				layer.addNeuron(neuron);
				neuralNetwork.getLayers().add(layer);
			}
		}
	}
	
	
	private void parseWeights() {
		
		if(numberOfUnits != Integer.MIN_VALUE) {
			
			int startOfConnectionDefinition = 34;

			if(currentLineNumber > (numberOfUnits + startOfConnectionDefinition)) {
				String[] splitArr = currentLine.split("\\|");
				
				if(splitArr.length == 1) {
				
					String[] splitArrLine = splitArr[0].split(",");
					
					for(int i = 0; i < splitArrLine.length; i++) {
						
						String[] splitArrValues = splitArrLine[i].split(":");
						
						if(splitArrValues.length == 2) {
							
							int srcNeuron = Integer.parseInt(splitArrValues[0].trim());
							double weight = Double.parseDouble(splitArrValues[1].trim());
		
							// set the weight
							double neuronWeights[][] = neuralNetwork.getNeuronWeights();
							neuronWeights[destNeuron][srcNeuron] = weight;
							neuralNetwork.setNeuronWeights(neuronWeights);
						}
						
					}
					
				} else if(splitArr.length == 3) {
					if(Character.isDigit(splitArr[0].trim().charAt(0))) {
					
						// SET src neuron
						destNeuron = Integer.parseInt(splitArr[0].trim());
						
						// Parse the rest (is now in the position 2)
						String[] splitArrLine = splitArr[2].split(",");
						
						for(int i = 0; i < splitArrLine.length; i++) {
							
							String[] splitArrValues = splitArrLine[i].split(":");
							
							if(splitArrValues.length == 2) {
								
								int srcNeuron = Integer.parseInt(splitArrValues[0].trim());
								double weight = Double.parseDouble(splitArrValues[1].trim());
								
								// set the weight
								double neuronWeights[][] = neuralNetwork.getNeuronWeights();
								neuronWeights[destNeuron][srcNeuron] = weight;
								neuralNetwork.setNeuronWeights(neuronWeights);
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Return Neural Network
	 * 
	 * @return NeuralNetwork neuralNetwork
	 */
	public NeuralNetwork getNeuralNetwork() {
		return this.neuralNetwork;
	}
	
	
	/**
	 * Main
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		JNNSParser parser = new JNNSParser();
	}
}
