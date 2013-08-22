package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDirectory;

public class ProcessGraphDataLoader {

	public static ProcessExecutionGraph loadProcessGraph(File dir)
			throws IOException {
		ProcessTraceDataSource dataSource = new ProcessTraceDirectory(dir);
		ProcessGraphLoadSession session = new ProcessGraphLoadSession(
				dataSource);
		return session.loadGraph();
	}
}
