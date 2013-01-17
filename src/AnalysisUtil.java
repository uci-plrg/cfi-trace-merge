import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

public class AnalysisUtil {
	public static final ByteOrder byteOrder = ByteOrder.nativeOrder();

	public static HashSet<Long> minus(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		for (Long elem : s2)
			if (res.contains(elem))
				res.remove(elem);
		return res;
	}

	public static HashSet<Long> intersection(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		res.retainAll(s2);
		return res;
	}

	public static Long toLittleEndian(Long l) {
		if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
			ByteBuffer bbuf = ByteBuffer.allocate(8);
			bbuf.order(ByteOrder.BIG_ENDIAN);
			bbuf.putLong(l);
			bbuf.order(ByteOrder.LITTLE_ENDIAN);
			return bbuf.getLong(0);
		}
		return l;
	}

	

	public static String getProgName(String dirName) {
		int endIndex = dirName.indexOf('-');
		if (endIndex > dirName.indexOf('_') && dirName.indexOf('_') != -1) {
			endIndex = dirName.indexOf('_');
		}
		return dirName.substring(0, endIndex);
	}

	public static HashSet<Long> initSetFromFile(String fileName) {
		DataInputStream in = null;
		try {
			in = new DataInputStream(new FileInputStream(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		HashSet<Long> set = new HashSet<Long>();

		try {
			// int bytesLeft = in.available() / 8;
			Long hashCode;
			while (true) {
				hashCode = in.readLong();
				set.add(toLittleEndian(hashCode));
			}
		} catch (EOFException e) {
			// end of line
		} catch (IOException e) {
			e.printStackTrace();
		}
		// in.flush();
		// in.close();
		return set;
	}

	public static void writeSetToFile(String outputFileName,
			HashSet<Long> hashset) {
		try {
			File f = new File(outputFileName);
			if (!f.exists()) {
				f.createNewFile();
			}

			FileOutputStream outputStream = new FileOutputStream(f, false);
			DataOutputStream dataOutput = new DataOutputStream(outputStream);

			System.out.println("Start outputing hash set to " + outputFileName
					+ " file.");

			for (Long l : hashset) {
				dataOutput.writeLong(toLittleEndian(l));
			}

			System.out.println("Finish outputing hash set to " + outputFileName
					+ " file.");
			outputStream.close();
			dataOutput.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
