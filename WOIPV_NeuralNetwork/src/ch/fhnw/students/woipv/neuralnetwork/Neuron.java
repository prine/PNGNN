package ch.fhnw.students.woipv.neuralnetwork;

public class Neuron {
		
	/**
	 * Neuron Type
	 * @author prine
	 *
	 */
	public enum NeuronType {
		INPUT,
		HIDDEN,
		OUTPUT
	}
	
	/**
	 * Constructor
	 * 
	 * @param type
	 * @param id
	 * @param act
	 * @param bias
	 */
	public Neuron(NeuronType type, int id, double act, double bias) {
		this.type = type;
		this.id = id;
		this.act = act;
		this.bias = bias;
	}
	
	public NeuronType type;
	public int id;
	public double act;
	public double bias;
	public double result;
	
	
	@Override
	public String toString() {
		return "[" + type + "] (" + id + ") => act: " + act + ", bias: " + bias;
	}
}
