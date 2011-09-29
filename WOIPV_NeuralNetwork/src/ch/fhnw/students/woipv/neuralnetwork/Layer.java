package ch.fhnw.students.woipv.neuralnetwork;

import java.util.ArrayList;

import ch.fhnw.students.woipv.neuralnetwork.Neuron.NeuronType;

public class Layer {

	private ArrayList<Neuron> neurons;
	
	private NeuronType type;
	
	public Layer(NeuronType type) {
		neurons = new ArrayList<Neuron>();
		this.type = type;
	}
	
	public void addNeuron(Neuron neuron) {
		if(!neurons.contains(neuron)) {
			neurons.add(neuron);
		} else {
			System.out.println("Neuron is already in the neuron list");
		}
	}
	
	public ArrayList<Neuron> getNeurons() {
		return this.neurons;
	}
	
	public NeuronType getType() {
		return this.type;
	}
}
