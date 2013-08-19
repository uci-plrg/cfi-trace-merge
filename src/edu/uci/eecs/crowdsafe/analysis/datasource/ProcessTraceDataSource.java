package edu.uci.eecs.crowdsafe.analysis.datasource;

import java.io.IOException;
import java.io.InputStream;

public interface ProcessTraceDataSource {
	int getProcessId();

	String getProcessName();

	InputStream getDataInputStream(ProcessTraceStreamType streamType)
			throws IOException;
}
