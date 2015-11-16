package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;

public class NewWhiteBoxReport implements ReportEntry {

	private final AnonymousModule module;
	private final AnonymousSubgraph box;

	NewWhiteBoxReport(AnonymousModule module, AnonymousSubgraph box) {
		this.module = module;
		this.box = box;
	}

	@Override
	public void print(PrintStream out) {
		// could add edge counts via analyze(false), then query the edge counter
		out.format("Dynamic standalone of size %d nodes owned by %s", box.getExecutableNodeCount(),
				module.owningCluster.name);
	}
}
