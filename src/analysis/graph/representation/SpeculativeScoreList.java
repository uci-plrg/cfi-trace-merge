package analysis.graph.representation;

import java.util.ArrayList;

public class SpeculativeScoreList {
	private ArrayList<SpeculativeScoreRecord> records;

	private int lowScoreTailCnt = 0;
	private int lowScoreDivergenceCnt = 0;
	private int oneMatchFalseCnt = 0;
	private int oneMatchTrueCnt = 0;
	private int manyMatchesAmbifuityCnt = 0;
	private int manyMatchesCorrectCnt = 0;

	public SpeculativeScoreList() {
		records = new ArrayList<SpeculativeScoreRecord>();
	}

	public void add(SpeculativeScoreRecord record) {
		records.add(record);
	}
	
	public void showResult() {
		System.out.println("The statistical results for speculative scoring is:");
		System.out.println("lowScoreTailCnt: " + lowScoreTailCnt);
		System.out.println("lowScoreDivergenceCnt: " + lowScoreDivergenceCnt);
		System.out.println("oneMatchFalseCnt: " + oneMatchFalseCnt);
		System.out.println("oneMatchTrueCnt: " + oneMatchTrueCnt);
		System.out.println("manyMatchesAmbifuityCnt: " + manyMatchesAmbifuityCnt);
		System.out.println("manyMatchesCorrectCnt: " + manyMatchesCorrectCnt);
	}

	public void count() {
		for (int i = 0; i < records.size(); i++) {
			SpeculativeScoreRecord record = records.get(i);
			switch (record.speculativeScoreType) {
			case LowScoreTail:
				lowScoreTailCnt++;
				break;
			case LowScoreDivergence:
				lowScoreDivergenceCnt++;
				break;
			case OneMatchFalse:
				oneMatchFalseCnt++;
				break;
			case OneMatchTrue:
				oneMatchTrueCnt++;
				break;
			case ManyMatchesAmbiguity:
				manyMatchesAmbifuityCnt++;
				break;
			case ManyMatchesCorrect:
				manyMatchesCorrectCnt++;
				break;
			default:
				break;
			}
		}
	}
}
