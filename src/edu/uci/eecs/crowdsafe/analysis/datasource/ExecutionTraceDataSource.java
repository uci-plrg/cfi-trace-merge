package edu.uci.eecs.crowdsafe.analysis.datasource;

import java.io.IOException;
import java.io.InputStream;

public interface ExecutionTraceDataSource {
	int getProcessId();

	String getProcessName();

	InputStream getDataInputStream(ExecutionTraceStreamType streamType)
			throws IOException;
}
