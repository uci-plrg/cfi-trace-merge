package analysis.graph.representation;

public class MutableInteger {
	private int innerVal;

	public MutableInteger(int innerVal) {
		this.innerVal = innerVal;
	}

	public void setVal(int val) {
		this.innerVal = val;
	}

	public int getVal() {
		return innerVal;
	}
}
