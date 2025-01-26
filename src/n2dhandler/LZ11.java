package n2dhandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class LZ11 {
	private static final int BUFFERLENGTH = 0x1000;
	
	//adapted from https://github.com/FanTranslatorsInternational/Kuriimu2/blob/dev/src/Kompression/Implementations/Decoders/Nintendo/Lz11Decoder.cs
	//and https://github.com/pleonex/tinke/blob/master/Plugins/DSDecmp/DSDecmp/Formats/Nitro/LZ11.cs
	public static void decode(ByteBuffer input, ByteArrayOutputStream output) throws IOException {
		//FileInputStream input = new FileInputStream(file);
		byte[] header = new byte[4];
		
		input.get(header);
		if(header[0] != 0x11) { //not lz11 compressed
			//input.close();
			return;
		}
		int decompressedSize = (header[1] & 0xFF) | (header[2] & 0xFF) << 8 | (header[3] & 0xFF) << 16;
		
		byte[] buffer = new byte[BUFFERLENGTH];
		int bufferOffset = 0;
		byte flags = 0;
		int mask = 1;
		while(output.size() < decompressedSize) {
			if(mask == 1) {
				flags = input.get();
				if((flags & 0xFF) < 0) {
					return; //stream too short
				}
				mask = 0x80;
			}
			else {
				mask >>= 1;
			}
			
			if((flags & mask) > 0) {
				bufferOffset = handleCompressedBlock(input, output, buffer, bufferOffset);
			}
			else {
				bufferOffset = handleUncompressedBlock(input, output, buffer, bufferOffset);
			}
			if(bufferOffset == -1) {
				return;
			}
		}
	}
	
	private static int handleUncompressedBlock(ByteBuffer input, ByteArrayOutputStream output, byte[] buffer, int bufferOffset) throws IOException {
		byte next = input.get();
		if((next & 0xFF) < 0) {
			return -1; //stream too short
		}
		output.write(next);
		buffer[bufferOffset] = next;
		return (bufferOffset + 1) % BUFFERLENGTH;
	}
	
	private static int handleCompressedBlock(ByteBuffer input, ByteArrayOutputStream output, byte[] buffer, int bufferOffset) throws IOException {
		//if((input.getChannel().size() - input.getChannel().position()) < 2) { //compressed blocks start with 2 bytes
		if(input.capacity() - input.position() < 2) {
			return -1; //stream too short
		}
		
		int byte1 = input.get() & 0xFF;
		int byte2 = input.get() & 0xFF;
		int length, displacement;
		
		if(byte1 >> 4 == 0) {
			//if((input.getChannel().size() - input.getChannel().position()) < 1) {
			if(input.capacity() - input.position() < 1) {
				return -1; //stream too short
			}
			
			int byte3 = input.get() & 0xFF;
			length = (((byte1 & 0xF) << 4) | (byte2 >> 4)) + 0x11; //max 0xFF + 0x11 = 0x110
			displacement = (((byte2 & 0xF) << 8) | byte3) + 1; //max 0xFFF + 1 = 0x1000
		}
		else if(byte1 >> 4 == 1) {
			//if((input.getChannel().size() - input.getChannel().position()) < 2) {
			if(input.capacity() - input.position() < 2) {
				return -1; //stream too short
			}
			
			int byte3 = input.get() & 0xFF;
			int byte4 = input.get() & 0xFF;
			length = (((byte1 & 0xF) << 12) | (byte2 << 4) | (byte3 >> 4)) + 0x111; //max 0xFFFF + 0x111 = 0x10110
			displacement = (((byte3 & 0xF) << 8) | byte4) + 1; //max 0xFFF + 1 = 0x1000
		}
		else {
			length = (byte1 >> 4) + 1; //max 0xF + 1 = 0x10
			displacement = (((byte1 & 0xF) << 8) | byte2) + 1; //max 0xFFF + 1 = 0x1000
		}
		
		if(displacement > output.size()) {
			return -1 ; //displacement exception
		}
		
		int bufferIndex = bufferOffset + BUFFERLENGTH - displacement;
		for(int i = 0 ; i < length; i++) {
			byte next = buffer[bufferIndex % BUFFERLENGTH];
			bufferIndex++;
			output.write(next);
			buffer[bufferOffset] = next;
			bufferOffset = (bufferOffset + 1) % BUFFERLENGTH;
		}
		return bufferOffset;
	}
}
