package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;

public class ExecutionReport {

	static String getModuleName(ClusterNode<?> node) {
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				return "Module entry from ";
			case CLUSTER_EXIT:
				return "Module exit to ";
			case SINGLETON:
				return "JIT singleton ";
			case TRAMPOLINE:
				return "Dynamic standalone ";
			default:
				return node.getModule().unit.filename;
		}

	}

	static long getId(ClusterNode<?> node) {
		switch (node.getType()) {
			case CLUSTER_ENTRY:
			case CLUSTER_EXIT:
				// ideally show hash source: { <module>!export, <module>!callback, <module>!main }
				return node.getHash();
			default:
				return node.getRelativeTag();
		}
	}

	static boolean isReportedEdgeType(EdgeType type) {
		switch (type) {
			case DIRECT:
			case INDIRECT:
			case UNEXPECTED_RETURN:
			case GENCODE_PERM:
			case GENCODE_WRITE:
				return true;
			case CALL_CONTINUATION:
			case EXCEPTION_CONTINUATION:
				return false;
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	private List<ReportEntry> entries = new ArrayList<ReportEntry>();

	public void sort() {

	}

	public void print(File outputFile) throws FileNotFoundException {
		PrintStream out = new PrintStream(outputFile);
		for (ReportEntry entry : entries) {
			entry.print(out);
			out.println();
		}
	}

	void addEntry(ClusterNode<?> node) {
		entries.add(new NewNodeReport(node));
	}

	void addEntry(Edge<ClusterNode<?>> edge) {
		entries.add(new NewEdgeReport(edge));
	}

	void addEntry(ClusterUIB uib) {
		// entries.add(new what??
	}

	void addEntry(ClusterSSC ssc) {
		entries.add(new SuspiciousSyscallReport(ssc));
	}

	void addEntry(ClusterSGE sge) {
		entries.add(new SuspiciousGencodeReport(sge));
	}

	void addEntry(AnonymousModule module, AnonymousSubgraph box) {
		entries.add(new NewWhiteBoxReport(module, box));
	}
}
