package edu.uci.eecs.crowdsafe.analysis.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LittleEndianInputStream {
	private static final int BUFFER_SIZE = 1 << 11;

	private final InputStream input;

	private int end = -1;
	private int lastLong = -1;
	private int longIndex = -1;
	long data[] = new long[BUFFER_SIZE];
	long buffer[] = new long[8];

	public LittleEndianInputStream(InputStream input) {
		this.input = input;
	}

	public boolean ready() throws IOException {
		return (longIndex < lastLong) || (input.available() > 0);
	}

	public long readLong() throws IOException {
		if ((end < 0) || (longIndex == BUFFER_SIZE)) {
			end = Math.min(input.available(), BUFFER_SIZE);
			int index;
			for (int i = 0; i < end; i++) {
				index = (i & 7) ^ 7;
				buffer[index] = (long) (input.read() & 0xff);
				if (index == 0) {
					data[i >> 3] = (buffer[0] << 0x38) | (buffer[1] << 0x30)
							| (buffer[2] << 0x28) | (buffer[3] << 0x20)
							| (buffer[4] << 0x18) | (buffer[5] << 0x10)
							| (buffer[6] << 0x8) | buffer[7];
				}
			}
			longIndex = 0;
			lastLong = (end >> 3);
		}

		return data[longIndex++];
	}

	public void close() throws IOException {
		input.close();
	}

	// unit test
	public static void main(String[] args) {
		try {
			byte[] buffer = new byte[] { 0x10, 0x32, 0x54, 0x76, (byte) 0x98,
					(byte) 0xba, (byte) 0xdc, (byte) 0xfe, 0x10, 0x32, 0x54,
					0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe,
					0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba,
					(byte) 0xdc, (byte) 0xfe, 0x10, 0x32, 0x54, 0x76,
					(byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe, 0x10,
					0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc,
					(byte) 0xfe, (byte) 0xef, (byte) 0xcd, (byte) 0xab,
					(byte) 0x89, 0x67, 0x45, 0x23, 0x01, (byte) 0xef,
					(byte) 0xcd, (byte) 0xab, (byte) 0x89, 0x67, 0x45, 0x23,
					0x01, (byte) 0xef, (byte) 0xcd, (byte) 0xab, (byte) 0x89,
					0x67, 0x45, 0x23, 0x01 };

			ByteArrayInputStream input = new ByteArrayInputStream(buffer);
			LittleEndianInputStream test = new LittleEndianInputStream(input);

			while (test.ready()) {
				long reversed = test.readLong();
				System.out.println(String.format(
						"LittleEndianInputStream read value 0x%x", reversed));
			}

			input.reset();
			DataInputStream dataInput = new DataInputStream(input);
			while (dataInput.available() > 0) {
				long forward = dataInput.readLong();
				System.out.println(String.format(
						"DataInputStream read value 0x%x", forward));
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
