package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

interface ReportEntry {

	void setEventFrequencies(ProgramEventFrequencies.ProgramPropertyReader programFrequencies,
			ModuleEventFrequencies.ModulePropertyReader moduleFrequencies);

	int getRiskIndex();

	void print(PrintStream out);
}
