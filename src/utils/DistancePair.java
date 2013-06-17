package utils;

public class DistancePair implements Comparable<DistancePair> {
	public final String progName1, progName2;
	public final String path1, path2;
	public final float dist;

	public DistancePair(String progName1, String progName2, String path1,
			String path2, float dist) {
		this.progName1 = progName1;
		this.progName2 = progName2;
		this.path1 = path1;
		this.path2 = path2;
		this.dist = dist;
	}

	public int compareTo(DistancePair o) {
		DistancePair other = (DistancePair) o;
		if (dist > other.dist)
			return 1;
		else if (dist == other.dist)
			return 0;
		else
			return -1;
	}
}
