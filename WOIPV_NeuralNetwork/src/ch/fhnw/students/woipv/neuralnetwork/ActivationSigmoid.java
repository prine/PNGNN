package ch.fhnw.students.woipv.neuralnetwork;

public class ActivationSigmoid implements ActivationFunction {

	@Override
	public double calculate(double in) {
		return 1.0/(1.0 + Math.exp(-in));  
	}
}
