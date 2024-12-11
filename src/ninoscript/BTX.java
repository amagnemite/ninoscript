package ninoscript;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BTX {
	public BTX(byte[] data) {
		int index = 8;
		IntByteArrayInputStream stream = new IntByteArrayInputStream(data);
		
		stream.skip(8);
		int fileSize = stream.readU32();
	}
	
	public void convertPNGToBTX(File png) {
		//BufferedImage
	}
	
}
