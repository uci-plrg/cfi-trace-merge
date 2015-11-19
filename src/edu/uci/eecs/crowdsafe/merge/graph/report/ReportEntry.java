package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

interface ReportEntry {
	
	void setEventFrequencies(ModuleEventFrequencies frequencies);

	void setEventFrequencies(ProgramEventFrequencies frequencies);
	
	void evaluateRisk();
	
	int getRiskIndex();

	void print(PrintStream out);
}
