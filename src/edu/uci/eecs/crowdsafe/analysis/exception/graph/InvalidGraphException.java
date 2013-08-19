package edu.uci.eecs.crowdsafe.analysis.exception.graph;

public class InvalidGraphException extends RuntimeException {
	public InvalidGraphException(Exception source) {
		super(source);
	}

	public InvalidGraphException(String message) {
		super(message);
	}
}
