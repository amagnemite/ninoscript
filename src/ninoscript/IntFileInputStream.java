package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class IntFileInputStream extends FileInputStream {
	public IntFileInputStream(File file) throws FileNotFoundException {
		super(file);
	}
	
	public int readU8() throws IOException {
		return read() & 0xFF;
	}
	
	public int readU16() throws IOException {
		byte[] bytes = new byte[2];
		int readBytes = read(bytes);
		return readBytes == -1 ? readBytes : (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
	}
	
	public int readU32() throws IOException {
		byte[] bytes = new byte[4];
		int readBytes = read(bytes);
		return readBytes == -1 ? readBytes : (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
	}
}
