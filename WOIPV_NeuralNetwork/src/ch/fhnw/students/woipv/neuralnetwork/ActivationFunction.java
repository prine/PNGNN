package ch.fhnw.students.woipv.neuralnetwork;

public interface ActivationFunction {

	/**
	 * Calculates the output with a defined function and an input param
	 * 
	 * @param double in
	 * 
	 * @return double out
	 * 
	 */
	double calculate(double in);
}
