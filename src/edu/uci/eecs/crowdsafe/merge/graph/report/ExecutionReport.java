package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class ExecutionReport {

	private List<ReportEntry> entries = new ArrayList<ReportEntry>();
	
	public void sort() {
		
	}
	
	public void print() {
		for (ReportEntry entry : entries) {
			entry.print();
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
}
