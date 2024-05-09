package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ScriptReader {
	private static final int PADDING1 = 4;
	private static final int SCRIPTNAMEINDEX = 8;
	private static final byte m = 0x6D;
	private static final byte p = 0x70;
	//private static final byte DASH = 0x2D;
	//private static final byte SPACE = 0x20;
	
	private String scriptName;
	private List<BlockData> blockList = new ArrayList<BlockData>();
	
	public ScriptReader(File file) {
		byte[] fullFileBytes;
		try {
			FileInputStream input = new FileInputStream(file);
			fullFileBytes = new byte[input.available()];
			input.read(fullFileBytes);
			input.close();
		} 
		catch (FileNotFoundException e) {
			return;
		}
		catch (IOException e) {
			return;
		}
		
		//magic word
		if(fullFileBytes[0] != 0x0A && fullFileBytes[1] != 0x08 && fullFileBytes[2] != 0x1F) {
			return;
		}
		
		int scriptNameLength = fullFileBytes[SCRIPTNAMEINDEX] & 0xFF;
		byte[] nameBytes = new byte[scriptNameLength];
		for(int i = 1; i <= scriptNameLength; i++) {
			nameBytes[i - 1] = fullFileBytes[SCRIPTNAMEINDEX + i];
		}
		scriptName = new String(nameBytes);
		//System.out.println(scriptName);
		
		for(int i = 0; i < fullFileBytes.length; i++) {
			byte b = fullFileBytes[i];
			
			switch(b) {
				case 0x00:
					continue;
				case 0x05:
					//it seems that the max block length should be 255 bytes?
					//longest so far is 210
					//so 05 01-FF 00 01 01-FF 00
					
					if(fullFileBytes[i + 1] == 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 2] != 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 3] != 0x01) {
						continue;
					}
					
					if(fullFileBytes[i + 4] == 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 5] != 0x00) {
						continue;
					}
					
					//there's blocks with a leading 04 that otherwise follow the header style but aren't headers
					if(fullFileBytes[i - 1] == 0x04) {
						continue;
					}
					
					BlockData data = parseBlock(fullFileBytes, i);
					if(data != null) {
						blockList.add(data);
						i += data.getFullBlockLength();
					}
					break;
				default:
					break;
			}
		}
		//System.out.println(blockList.size());
	}
	
	private BlockData parseBlock(byte[] fullFileBytes, int start) {
		int shift = start; //starts at the 05 loc
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerLength = 0;
		int speakerStart = 0;

		int fullBlockLength = fullFileBytes[shift + 1] & 0xFF;
		
		shift += 4; //skip 01	
		int textLength = fullFileBytes[shift] & 0xFF;
		
		shift += 2;
		int textStart = shift;
		
		textBytes = new byte[textLength];
		for(int i = 0; i < textLength; i++) {
			textBytes[i] = fullFileBytes[shift + i];
		}
		
		shift += textLength + PADDING1;
		//starting at this point, blocks are variable
		
		while(shift < start + fullBlockLength) {
			//need to do conversion here since by default comparisons are done with signed vals
			int currentByte = Byte.toUnsignedInt(fullFileBytes[shift]);
			int nextByte = Byte.toUnsignedInt(fullFileBytes[shift + 1]);
			int nextByte2 = Byte.toUnsignedInt(fullFileBytes[shift + 2]);
			int nextByte3 = Byte.toUnsignedInt(fullFileBytes[shift + 3]);
			
			if(currentByte < 0x0F) { //for now, hardcode possible interesting things
				if(shift + 1 >= start + fullBlockLength) { //end of block, ignore
					break;
				}
				else if(currentByte == 0x00) {
					if(nextByte == 0x00 && nextByte2 == 0x00) {
						//sometimes speakers have 3 leading 00s, so this is a dumb way to avoid it
						shift += 3;
						continue;
					}
					else {
						speakerBytes = parseSpeaker(nextByte, nextByte2, nextByte3, shift, fullFileBytes);
						
						if(speakerBytes != null) {
							speakerLength = nextByte2;
							speakerStart = shift + 3;
							shift += 3 + speakerLength;
						}
						else {
							shift++;
						}
					}
				}
				else if(nextByte == m) {
					//likely model file
					shift += 1 + (fullFileBytes[shift] & 0xFF);
				}
				else if(nextByte == p) {
					//likely joint anim file
					shift += 1 + (fullFileBytes[shift] & 0xFF);
				}
				else { //all we know is sub 0x0F val
					speakerBytes = parseSpeaker(nextByte, nextByte2, nextByte3, shift, fullFileBytes);
										
					if(speakerBytes != null) {
						speakerLength = nextByte2;
						speakerStart = shift + 3;
						shift += 3 + speakerLength;
					}
					else {
						shift++;
					}
				}
			}
			else {
				shift++;
			}
		}
	
		return new BlockData(fullBlockLength, textLength, textStart, speakerStart, speakerLength, textBytes, speakerBytes);
	}
	
	private byte[] parseSpeaker(int nextByte, int nextByte2, int nextByte3, int shift, byte[] fullFileBytes) {
		if(nextByte < 0x05 && nextByte2 < 0x0F) {
			if((nextByte3 >= 0x41 && nextByte3 <= 0x5A) || (nextByte3 >= 0x61 && nextByte3 <= 0x7A)) {
				//' is also used
				//nextByte3 == DASH || nextByte3 == SPACE || 
				//00-03 00-04 00-0E [a letter]
				//likely a speaker header
				
				int speakerLength = nextByte2;
				int speakerStart = shift + 3;
				byte[] speakerBytes = new byte[speakerLength];
				for(int i = 0; i < speakerLength; i++) {
					speakerBytes[i] = fullFileBytes[speakerStart + i];
				}
				return speakerBytes;
			}
			else {
				return null;
			}
		}
		else {
			return null;
		}
	}
	
	public String toString() {
		return scriptName;
	}
	
	public List<BlockData> getBlockList() {
		return blockList;
	}
	
	public static class BlockData {
		private int fullBlockLength;
		private int textLength;
		private int textStart;
		private int speakerStart;
		private int speakerLength;
		
		private byte[] textBytes;
		private byte[] speakerBytes;
		
		public BlockData(int fullBlockLength, int textLength, int textStart, int speakerStart, int speakerLength,
				byte[] textBytes, byte[] speakerBytes) {
			this.fullBlockLength = fullBlockLength;
			this.textLength = textLength;
			this.textStart = textStart;
			this.speakerLength = speakerLength;
			this.speakerStart = speakerStart;
			this.textBytes = textBytes;
			this.speakerBytes = speakerBytes;
		}

		public int getFullBlockLength() {
			return fullBlockLength;
		}

		public void setFullBlockLength(int fullBlockLength) {
			this.fullBlockLength = fullBlockLength;
		}

		public int getTextLength() {
			return textLength;
		}

		public void setTextLength(int textLength) {
			this.textLength = textLength;
		}

		public int getTextStart() {
			return textStart;
		}

		public void setTextStart(int textStart) {
			this.textStart = textStart;
		}

		public int getSpeakerStart() {
			return speakerStart;
		}

		public void setSpeakerStart(int speakerStart) {
			this.speakerStart = speakerStart;
		}

		public int getSpeakerLength() {
			return speakerLength;
		}

		public void setSpeakerLength(int speakerLength) {
			this.speakerLength = speakerLength;
		}

		public byte[] getTextBytes() {
			return textBytes;
		}

		public void setTextBytes(byte[] textBytes) {
			this.textBytes = textBytes;
		}

		public byte[] getSpeakerBytes() {
			return speakerBytes;
		}

		public void setSpeakerBytes(byte[] speakerBytes) {
			this.speakerBytes = speakerBytes;
		}
		
	}
}
