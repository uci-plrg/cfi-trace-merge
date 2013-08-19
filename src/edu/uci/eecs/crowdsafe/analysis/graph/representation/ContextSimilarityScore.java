package edu.uci.eecs.crowdsafe.analysis.graph.representation;

public class ContextSimilarityScore implements Comparable {
	public final int actualScore;
	public final int maxPotentialScore;

	public ContextSimilarityScore(int actualScore, int maxPotentialScore) {
		this.actualScore = actualScore;
		this.maxPotentialScore = maxPotentialScore;
	}

	public static final int times = 5;

	@Override
	public int compareTo(Object o) {
		ContextSimilarityScore another = (ContextSimilarityScore) o;
		if (actualScore == another.actualScore
				&& maxPotentialScore == another.maxPotentialScore) {
			return 0;
		}

		// FIXME: This is a way to compare the ContextSimilarityScore, it might
		// not be a good one, we need to do some experiment to find out the
		// better way.
		//
		if (actualScore >= another.actualScore * times) {
			return 1;
		} else if (actualScore <= another.actualScore * times) {
			return -1;
		}
		// if ((float) actualScore /
		return 0;
	}

}
