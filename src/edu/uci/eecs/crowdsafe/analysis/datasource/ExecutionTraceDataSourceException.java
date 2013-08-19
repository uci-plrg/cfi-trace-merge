package edu.uci.eecs.crowdsafe.analysis.datasource;

public class ExecutionTraceDataSourceException extends RuntimeException {
	public ExecutionTraceDataSourceException(String message) {
		super(message);
	}

	public ExecutionTraceDataSourceException(String message, Exception source) {
		super(message, source);
	}
}
