package edu.uci.eecs.crowdsafe.merge.exception;

public class MergedFailedException extends RuntimeException {

	public MergedFailedException(String format, Object... args) {
		super(String.format(format, args));
	}
}
