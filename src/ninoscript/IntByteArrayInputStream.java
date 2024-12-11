package ninoscript;

import java.io.ByteArrayInputStream;

public class IntByteArrayInputStream extends ByteArrayInputStream {
	public IntByteArrayInputStream (byte[] buffer) {
		super(buffer);
	}
	
	public int readU8() {
		return read() & 0xFF;
	}
	
	public int readU16() {
		byte[] bytes = new byte[2];
		read(bytes, 0, 2);
		return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
	}
	
	public int readU32() {
		byte[] bytes = new byte[4];
		read(bytes, 0, 4);
		return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
	}
}
