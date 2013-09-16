package edu.uci.eecs.crowdsafe.merge.graph.main;

import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;

public class InfinitelyMergeTwoExecutionGraphs {

	public static void main(String[] args) {

		MergeTwoExecutionGraphs main = new MergeTwoExecutionGraphs();
		main.run(new ArgumentStack(args), Integer.MAX_VALUE);
	}
}
