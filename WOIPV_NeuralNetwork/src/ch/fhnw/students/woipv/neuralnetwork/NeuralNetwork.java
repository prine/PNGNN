package ch.fhnw.students.woipv.neuralnetwork;

import java.util.ArrayList;

import ch.fhnw.students.woipv.neuralnetwork.Neuron.NeuronType;

/**
 * NeuralNetwork implementation
 * At the moment its only possible to use:
 * - one hidden layer
 * - one input layer
 * - one output layer
 * 
 * One implementation of the activation function canbe added
 * 
 * @author Robin Oster
 *
 */
public class NeuralNetwork {
	
	/**
	 * Contains all the neurons
	 */
	private ArrayList<Layer> layers;
	
	/**
	 * Contains the corresponding weights between two neurons
	 */
	private double[][] neuronWeights;
	
	/**
	 * Amount of neurons used in the neural network
	 */
	private int numberOfNeurons;
	
	/**
	 * Activation Function
	 */
	private ActivationFunction activationFunction;
	
	/**
	 * NeuralNetwork constructor
	 * 
	 * @param numberOfNeurons
	 */
	public NeuralNetwork(int numberOfNeurons) {
		// because it is starting with index 1 and not 0
		this.numberOfNeurons = numberOfNeurons + 1;
		neuronWeights = new double[this.numberOfNeurons][this.numberOfNeurons];
		layers = new ArrayList<Layer>();
		
		// set the current activation function to the sigmoid function
		activationFunction = new ActivationSigmoid();
	}
	
	/**
	 * Get Layers
	 * 
	 * @return ArrayList<Layer> listOfLayers
	 */
	public ArrayList<Layer> getLayers() {
		return this.layers;
	}
	
	/**
	 * Get Layer by a type
	 * 
	 * @param NeuronType type
	 * @return Layer layer
	 */
	public Layer getLayerByType(NeuronType type) {
		for(Layer layer : layers) {
			if(layer.getType().equals(type)) {
				return layer;
			}
		}
		
		return null;
	}
	
	
	/**
	 * Set neuron weigths
	 * 
	 * @param neuronWeights
	 */
	public void setNeuronWeights(double[][] neuronWeights) {
		this.neuronWeights = neuronWeights;
	}
	
	
	/**
	 * Print all the existing layers
	 */
	public void printLayers() {
		for(Layer layer : layers) {
			
			System.out.println("Layer: " + layer.getType());
			
			for(Neuron neuron : layer.getNeurons()) {
				System.out.println(neuron);
			}
		}
	}
	
	
	/**
	 * Calculate the final output of the neural network
	 * 
	 * @param inputValues
	 * @return
	 */
	public Layer calculate(double[] inputValues) {
		
		Layer layerInput = getLayerByType(NeuronType.INPUT);
		Layer layerHidden = getLayerByType(NeuronType.HIDDEN);
		Layer layerOutput = getLayerByType(NeuronType.OUTPUT);
		
		double sum = 0;
		int i = 0;
		
		// CALCULATE VALUES FOR THE HIDDEN LAYER
		for(Neuron destNeuron : layerHidden.getNeurons()) {
			
			sum = 0;
			i = 0;
			
			for(Neuron srcNeuron : layerInput.getNeurons()) {
				sum += neuronWeights[destNeuron.id][srcNeuron.id]*inputValues[i];
				
				i++;
			}
			
			// write the result into the result var in the destination neuron
			destNeuron.result = activationFunction.calculate(sum + destNeuron.bias);
		}
		
		
		// CALCULATE VALUES FOR THE OUTPUT LAYER
		for(Neuron destNeuron : layerOutput.getNeurons()) {
			
			sum = 0;
			
			for(Neuron srcNeuron : layerHidden.getNeurons()) {
				sum += neuronWeights[destNeuron.id][srcNeuron.id]*srcNeuron.result;
			}
			
			// write the result into the result var in the destination neuron
			destNeuron.result = activationFunction.calculate(sum + destNeuron.bias);
			// destNeuron.result = 0.2;
			
		}
		
		return layerOutput;
	}
	

	/**
	 * Print the weights
	 */
	public void printWeights() {

		Layer layerInput = getLayerByType(NeuronType.INPUT);
		Layer layerHidden = getLayerByType(NeuronType.HIDDEN);
		Layer layerOutput = getLayerByType(NeuronType.OUTPUT);
		
		
		System.out.println("INPUT -> HIDDEN");
		System.out.println("------------------------");
		for(Neuron destNeuron : layerHidden.getNeurons()) {
			
			System.out.println(destNeuron + " has the following incoming neurons:");
			
			for(Neuron srcNeuron : layerInput.getNeurons()) {
				System.out.println(srcNeuron.id + " weight: " + neuronWeights[destNeuron.id][srcNeuron.id]);
			}
		}
	}
	
	
	/**
	 * Get neuron weights
	 * 
	 * @return double[][] neuronWeights
	 */
	public double[][] getNeuronWeights() {
		return this.neuronWeights;
	}
}
