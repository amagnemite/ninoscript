package ninoscript;

import java.io.ByteArrayInputStream;

public class IntByteArrayInputStream extends ByteArrayInputStream {
	public IntByteArrayInputStream(byte[] buffer) {
		super(buffer);
	}
	
	public IntByteArrayInputStream(byte[] buffer, int offset, int length) {
		super(buffer, offset, length);
	}
	
	public int readU8() {
		return read() & 0xFF;
	}
	
	//-1 if end of stream
	public int readU16() {
		byte[] bytes = new byte[2];
		int readBytes = read(bytes, 0, 2);
		return readBytes == -1 ? readBytes : (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
	}
	
	public int readU32() {
		byte[] bytes = new byte[4];
		int readBytes = read(bytes, 0, 4);
		return readBytes == -1 ? readBytes : (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
	}
}
