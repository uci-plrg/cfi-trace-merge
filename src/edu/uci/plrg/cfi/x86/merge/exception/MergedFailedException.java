package edu.uci.plrg.cfi.x86.merge.exception;

public class MergedFailedException extends RuntimeException {

	public MergedFailedException(String format, Object... args) {
		super(String.format(format, args));
	}
}
