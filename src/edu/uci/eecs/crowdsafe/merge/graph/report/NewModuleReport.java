package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

class NewModuleReport implements ReportEntry {

	private final ApplicationModule cluster;

	NewModuleReport(ApplicationModule cluster) {
		this.cluster = cluster;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
	}

	@Override
	public int getRiskIndex() {
		return 1000;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Untrusted module %s", cluster.name);
	}
}
