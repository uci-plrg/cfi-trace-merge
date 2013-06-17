package analysis.graph.representation;

public interface NodeList {
	int size();

	boolean isSingleton();

	Node get(int index);

	NodeList copy(ExecutionGraph containingGraph);
}
