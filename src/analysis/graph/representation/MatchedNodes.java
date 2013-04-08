package analysis.graph.representation;

import java.util.HashMap;

public class MatchedNodes {
	private HashMap<Integer, Integer> matchedNodes12, matchedNodes21;
	
	public MatchedNodes() {
		matchedNodes12 = new HashMap<Integer, Integer>();
		matchedNodes21 = new HashMap<Integer, Integer>();
	}
	
	public boolean hasPair(int index1, int index2) {
		if (!matchedNodes12.containsKey(index1)) {
			return false;
		}
		if (matchedNodes12.get(index1) == index2) {
			return true;
		} else {
			return false;
		}
			
	}
	
	public boolean addPair(int index1, int index2) {
		if (matchedNodes12.containsKey(index1) || matchedNodes21.containsKey(index2))
			return false;
		matchedNodes12.put(index1, index2);
		matchedNodes21.put(index2, index1);
		return true;
	}
	
	public void removeByFirstIndex(int index1) {
		int index2 = matchedNodes12.get(index1);
		matchedNodes12.remove(index1);
		matchedNodes21.remove(index2);
	}
	
	public void removeBySecondIndex(int index2) {
		int index1 = matchedNodes21.get(index2);
		matchedNodes12.remove(index1);
		matchedNodes21.remove(index2);
	}
	
	public boolean containsKeyByFirstIndex(int index1) {
		return matchedNodes12.containsKey(index1);
	}
	
	public boolean containsKeyBySecondIndex(int index2) {
		return matchedNodes21.containsKey(index2);
	}
	
	public Integer getByFirstIndex(int index1) {
		return matchedNodes12.get(index1);
	}
	
	public Integer getBySecondIndex(int index2) {
		return matchedNodes21.get(index2);
	}
	
	public int size() {
		return matchedNodes12.size();
	}
}