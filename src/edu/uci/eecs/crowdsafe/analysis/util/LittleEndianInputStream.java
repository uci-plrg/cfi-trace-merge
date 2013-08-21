package edu.uci.eecs.crowdsafe.analysis.util;

import java.io.IOException;
import java.io.InputStream;

public class LittleEndianInputStream {
	private static final int BUFFER_SIZE = 1024;

	private final InputStream input;

	private int end = -1;
	private int longIndex = -1;
	private final byte data[] = new byte[BUFFER_SIZE];

	public LittleEndianInputStream(InputStream input) {
		this.input = input;
	}

	public boolean ready() throws IOException {
		return input.available() > 0;
	}

	public long readLong() throws IOException {
		if ((end < 0) || ((longIndex * 8) == BUFFER_SIZE)) {
			end = Math.min(input.available(), BUFFER_SIZE);
			input.read(data, 0, end);
			longIndex = 0;
		}

		int index = longIndex * 8;
		longIndex++;
		return (long) (data[index + 7]) << 56
				| (long) (data[index + 6] & 0xff) << 48
				| (long) (data[index + 5] & 0xff) << 40
				| (long) (data[index + 4] & 0xff) << 32
				| (long) (data[index + 3] & 0xff) << 24
				| (long) (data[index + 2] & 0xff) << 16
				| (long) (data[index + 1] & 0xff) << 8
				| (long) (data[index] & 0xff);
	}

	public void close() throws IOException {
		input.close();
	}
}
