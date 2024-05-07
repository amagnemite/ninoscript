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
	private static final byte A = 0x41;
	private static final byte Z = 0x5A;
	private static final byte m = 0x6D;
	private static final byte p = 0x70;
	
	private byte[] fullFileBytes;
	
	private String scriptName;
	private List<BlockData> blockList = new ArrayList<BlockData>();
	
	public ScriptReader(File file) {
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
		if(fullFileBytes[0] != 0x0A && fullFileBytes[1] != 0x08 && fullFileBytes[2] != 0x01F) {
			return;
		}
		
		int scriptNameLength = fullFileBytes[SCRIPTNAMEINDEX] & 0xFF;
		byte[] nameBytes = new byte[scriptNameLength];
		for(int i = 1; i <= scriptNameLength; i++) {
			nameBytes[i - 1] = fullFileBytes[SCRIPTNAMEINDEX + i];
		}
		scriptName = new String(nameBytes);
		System.out.println(scriptName);
		
		for(int i = 0; i < fullFileBytes.length; i++) {
			byte b = fullFileBytes[i];
			
			switch(b) {
				case 0x00:
					continue;
				case 0x05:
					if(fullFileBytes[i + 1] == 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 3] == 0x01) {
						blockList.add(parseBlock(i));
						i += (fullFileBytes[i] & 0xFF) | (fullFileBytes[i + 1] & 0xFF) << 8;
					}
					break;
		
			}
		}
	}
	
	private BlockData parseBlock(int start) {
		int shift = start;
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerLength = 0;
		int speakerStart = 0;
		
		shift++;
		int fullBlockLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift++] & 0xFF) << 8;
		
		shift++;
		int textLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift++] & 0xFF) << 8;
		
		int textStart = shift + 1; //double check this
		
		textBytes = new byte[textLength];
		for(int i = 0; i < textLength; i++) {
			textBytes[i] = fullFileBytes[shift + i];
		}
		
		shift += textLength + PADDING1;
		//starting at this point, blocks are variable
		
		while(shift < start + fullBlockLength) {
			if(fullFileBytes[shift] < 0x0F) { //for now, hardcode possible interesting things
				if(shift + 1 >= start + fullBlockLength) { //end of block, ignore
					break;
				}
				else if(fullFileBytes[shift] == 0x00) {
					//some speaker headers have leading 00s
					//also oliver seems to consistently have 00 xx 06, so possible other chars do too
					if(fullFileBytes[shift + 1] == 0x00) {
						continue;
					}
					else {
						if(fullFileBytes[shift + 2] < 0x0F) {
							speakerLength = fullFileBytes[shift + 2] & 0xFF;
							speakerStart = shift + 3;
							speakerBytes = new byte[speakerLength];
							for(int i = 0; i < speakerLength; i++) {
								speakerBytes[i] = fullFileBytes[speakerStart + i];
							}
							
							System.out.println(fullFileBytes[shift] + " " + fullFileBytes[shift + 1] + " " + fullFileBytes[shift + 2] +
									" " + new String(speakerBytes, StandardCharsets.US_ASCII));
							shift += 3 + speakerLength;
						}
					}
				}
				else if(fullFileBytes[shift + 1] == m) {
					//likely model file
					shift += fullFileBytes[shift] & 0xFF;
				}
				else if(fullFileBytes[shift + 1] == p) {
					//likely joint anim file
					shift += fullFileBytes[shift] & 0xFF;
				}
				else { //all we know is sub 0x0F val
					if(fullFileBytes[shift + 1] < 0x0F && fullFileBytes[shift + 2] < 0x0F) {
						//likely a speaker header
						speakerLength = fullFileBytes[shift + 2] & 0xFF;
						speakerStart = shift + 3;
						speakerBytes = new byte[speakerLength];
						for(int i = 0; i < speakerLength; i++) {
							speakerBytes[i] = fullFileBytes[speakerStart + i];
						}
						
						System.out.println(fullFileBytes[shift] + " " + fullFileBytes[shift + 1] + " " + fullFileBytes[shift + 2] +
								" " + new String(speakerBytes, StandardCharsets.US_ASCII));
						shift += 3 + speakerLength;
					}
				}
			}
		}
		
		return new BlockData(fullBlockLength, textLength, textStart, speakerStart, speakerLength, textBytes, speakerBytes);
	}
	
	public String toString() {
		return scriptName;
	}
	
	private static class BlockData {
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