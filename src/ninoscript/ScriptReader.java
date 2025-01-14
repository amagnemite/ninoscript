package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ninoscript.BlockData.*;

public class ScriptReader {
	public enum Magic {
		DIALOGUE (Integer.valueOf(0x05).byteValue(), new byte[] {0x05, 0x00, 0x00, 0x01, 0x00, 0X00}, 1, 4),
		NONDIALOGUE (Integer.valueOf(0x26).byteValue(), new byte[] {0x26, 0x00, 0x00, 0x01, 0x00}, 1, 4),
		TEXTENTRY (Integer.valueOf(0x31).byteValue(), new byte[] {0x31, 0x00, 0x00, 0x07, 0x02, 0x02, 0x00, 0x00}, 1, 6),
		TEXTENTRYNODESCRIPT (Integer.valueOf(0x31).byteValue(), new byte[] {0x31, 0x00, 0x00, 0x07, 0x02, 0x01, 0x00, 0x00}, 1, 6),
		TEXTENTRYLONG (Integer.valueOf(0x31).byteValue(), 
				new byte[] {0x31, 0x00, 0x00, 0x07, 0x03, 0x01, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00}, 1, 11);
		
		//31 [fulllength1] [fulllength2] 07 02 02 [answerlength] 00
		//31 [fulllength1] [fulllength2] 07 02 01 [answerlength] 00
		//31 [fulllength1] [fulllength2] 07 03 01 01 00 00 00 02 [answerlength] 00
		//0x11 and 0x29 might also be text
		//0x11 is dialogue options
		
		private byte value;
		private byte[] format;
		private int fullLengthOffset;
		private int textLengthOffset;
		Magic(byte value, byte[] format, int fullLengthOffset, int textLengthOffset) {
			this.value = value;
			this.format = format;
			this.fullLengthOffset = fullLengthOffset;
			this.textLengthOffset = textLengthOffset;
		}
		
		public byte getValue() {
			return value;
		}
		
		public byte[] getFormat() {
			return format;
		}
		
		public int getFullLengthOffset() {
			return fullLengthOffset;
		}
		
		public int getTextLengthOffset() {
			return textLengthOffset;
		}
	}
	
	private static final int PADDING1 = 4;
	private static final int SCRIPTNAMEINDEX = 8;
	private static final byte m = 0x6D;
	private static final byte p = 0x70;
	
	private boolean isSingleConvo = false;
	private String fileName;
	private byte[] fullFileBytes;
	private String scriptName = "";
	private List<ConversationData> convoList = new ArrayList<ConversationData>();
	private List<BlockData> blockList = new ArrayList<BlockData>();
	
	//only keep track of the first block that stores the string, since we can just cross reference in the stringlistmap
	private Map<String, BlockData> existingStringMap = new HashMap<String, BlockData>();
	private Map<String, List<BlockData>> stringListMap = new HashMap<String, List<BlockData>>();
	
	public ScriptReader(File file) {
		int scriptNameLength = 0;
		byte[] nameBytes = null;
		int previousBlockCount = 0;
		int lastBlockOfConv = 0;
		boolean firstConvo = true;
		BlockData data;
		
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
		if(fullFileBytes[0] == 0x0A && fullFileBytes[1] == 0x08 && fullFileBytes[2] == 0x1F) {
			
		}
		else if(fullFileBytes[0] == 0x0A && fullFileBytes[1] == 0x09 && fullFileBytes[2] == 0x19) {
			isSingleConvo = true;
		}
		else {
			return;
		}
		
		fileName = file.getName();
		
		if(!isSingleConvo) {
			scriptNameLength = fullFileBytes[SCRIPTNAMEINDEX] & 0xFF;
			nameBytes = new byte[scriptNameLength];
			
			for(int i = 1; i <= scriptNameLength; i++) {
				nameBytes[i - 1] = fullFileBytes[SCRIPTNAMEINDEX + i];
			}
			try {
				scriptName = new String(nameBytes, "Shift-JIS");
			}
			catch (UnsupportedEncodingException e) {
			}
		}
		
		for(int i = 0; i < fullFileBytes.length; i++) {
			byte b = fullFileBytes[i];
			
			switch(b) {
				case 0x00:
					continue;
				case 0x0A:
					//these come after conversation lengths
					if(fullFileBytes[i + 1] == 0x09 && fullFileBytes[i + 2] == 0x19 && fullFileBytes[i + 3] == 0x00) {
						int conversationLength = 0;
						
						if(!isSingleConvo) {
							conversationLength = (fullFileBytes[i - 4] & 0xFF) | (fullFileBytes[i - 3] & 0xFF) << 8 |
								(fullFileBytes[i - 2] & 0xFF) << 16 | (fullFileBytes[i - 1] & 0xFF) << 24;
						}
						
						if(!firstConvo) {
							//not the first convo block
							if(previousBlockCount == lastBlockOfConv) {
								convoList.remove(convoList.size() - 1); //if no blocks, remove the last convo added
							}
							else {
								convoList.get(convoList.size() - 1).setLastBlock(lastBlockOfConv - 1);
								previousBlockCount = lastBlockOfConv;
							}
						}
						else {
							firstConvo = false;
						}
						
						if(!isSingleConvo) {
							convoList.add(new ConversationData(i - 4, conversationLength, 0));
						}
						else {
							convoList.add(new ConversationData(0, conversationLength, 0));
						}
					}
					break;
				case 0x05:
					//it seems that the max block length should be 255 bytes?
					//longest so far is 210
					//so 05 01-FF 00-FF 01 01-FF 00-FF
					
					if(fullFileBytes[i + 1] == 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 2] > 0x01) {
						continue;
					}
					
					if(fullFileBytes[i + 3] != 0x01) {
						continue;
					}
					
					if(fullFileBytes[i + 4] == 0x00) {
						continue;
					}
					
					if(fullFileBytes[i + 5] > 0x01) {
						continue;
					}
					
					//there's blocks with a leading 04 that otherwise follow the header style but aren't headers
					if(fullFileBytes[i - 1] == 0x04) {
						continue;
					}
					
					if(isCharacter(Byte.toUnsignedInt(fullFileBytes[i + 6]))) {
						data = parseBlock(fullFileBytes, i);
						if(data != null) {
							blockList.add(data);
							checkIfStringExists(data);
							i += data.getFullBlockLength();
							lastBlockOfConv++;
						}
					}
					break;
				case 0x26:
					//non conversation text
					//format of 26 01-FF 00-FF 01 1-FF
					//if(fullFileBytes[i - 1] != 0x00) {
					//	continue;
					//}
					
					if(fullFileBytes[i + 1] == 0x00) {
						continue;
					}
					
					
					if(fullFileBytes[i + 2] > 0x01) {
						continue;
					}
					
					
					if(fullFileBytes[i + 3] != 0x01) {
						continue;
					}
					
					if(fullFileBytes[i + 4] == 0x00) {
						continue;
					}
					
					if(isCharacter(Byte.toUnsignedInt(fullFileBytes[i + 5]))) {
						data = parseNonDialogue(fullFileBytes, i);
						if(data != null) {
							blockList.add(data);
							checkIfStringExists(data);
							i += data.getFullBlockLength();
							lastBlockOfConv++;
						}
					}
					break;
				case 0x31:
					//text entry puzzle
					//there seems to be three variants of this
					//31 [2B length] 07 02 02 [answer length] 00
					//31 [2B length] 07 02 01 [answer length] 00
					//31 [2B length] 07 03 01 01 00 00 00 02 [answer length] 00
					
					if(fullFileBytes.length < i + 6) {
						continue;
					}
					
					int nextByte1 = fullFileBytes[i + 4];
					int nextByte2 = fullFileBytes[i + 5];
					int nextByte3 = fullFileBytes[i + 6];
					
					if(fullFileBytes[i + 3] != 0x07) {
						continue;
					}
					
					if(nextByte1 != 0x02 && nextByte1 != 0x03) {
						continue;
					}
					
					if(nextByte2 != 0x01 && nextByte1 != 0x02) {
						continue;
					}
					
					if(nextByte3 > 0x0F) {
						continue;
					}
					
					data = parseTextEntry(fullFileBytes, i);
					if(data != null) {
						blockList.add(data);
						i += data.getFullBlockLength();
						lastBlockOfConv++;
					}
					break;
				default:
					break;
			}
		}
		if(previousBlockCount != lastBlockOfConv) {
			convoList.get(convoList.size() - 1).setLastBlock(lastBlockOfConv - 1);
		}
	}
	
	private boolean isCharacter(int val) {
		if((val >= 0x81 && val <= 0x84) || (val >= 0x21 && val <= 0x7E)) {
			return true;
		}
		return false;
	}
	
	private void checkIfStringExists(BlockData data) {
		String string = data.getTextString();
		
		if(!existingStringMap.containsKey(string)) {
			existingStringMap.put(string, data);
		}
		else {
			BlockData firstBlock = existingStringMap.get(string);
			List<BlockData> sublist;
			if(!stringListMap.containsKey(string)) {
				sublist = new ArrayList<BlockData>();
				stringListMap.put(string, sublist);
				
				sublist.add(firstBlock);	
				firstBlock.setSharedStringList(sublist);	
			}
			else {
				sublist = stringListMap.get(string);
			}
			sublist.add(data);
			data.setSharedStringList(sublist);
		}
	}

	private BlockData parseBlock(byte[] fullFileBytes, int start) {
		//TODO: may want to do something about 0x1c, which the jpn ver seems to use and sometimes appears in scripts
		int shift = start + 1; //now at full length byte
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerLength = -1;
		int speakerStart = -1;
		boolean hasSpeaker = false;
		
		int fullBlockLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
		
		shift += 3; //skip [length1] [length2] 01
		int textLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
		
		shift += 2;
		int textStart = shift;
		
		if(textLength > fullBlockLength) {
			return null;
		}
		
		textBytes = new byte[textLength];
		for(int i = 0; i < textLength; i++) {
			textBytes[i] = fullFileBytes[shift + i];
		}
		//shift += textLength;
		//byte[] fourBytes = new byte[] {fullFileBytes[shift], fullFileBytes[shift+1], fullFileBytes[shift+2], fullFileBytes[shift+3]};
		byte[] speakerDataBytes = null;
		
		//shift += PADDING1;
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
						if(!hasSpeaker) {
							speakerBytes = parseSpeaker(nextByte, nextByte2, nextByte3, shift, fullFileBytes);
							
							if(speakerBytes != null) {
								speakerDataBytes = new byte[] {fullFileBytes[shift], fullFileBytes[shift+1],fullFileBytes[shift+2]};
								speakerLength = nextByte2;
								speakerStart = shift + 3;
								shift += 3 + speakerLength;
								hasSpeaker = true;
							}
							else {
								shift++;
							}
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
				else { //all we know is sub 0x0F 
					if(!hasSpeaker) {
						speakerBytes = parseSpeaker(nextByte, nextByte2, nextByte3, shift, fullFileBytes);
						
						if(speakerBytes != null) {
							speakerDataBytes = new byte[] {fullFileBytes[shift], fullFileBytes[shift+1],fullFileBytes[shift+2]};
							speakerLength = nextByte2;
							speakerStart = shift + 3;
							shift += 3 + speakerLength;
							hasSpeaker = true;
						}
						else {
							shift++;
						}
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
	
		return new ExtraInfoBlockData(Magic.DIALOGUE, start, fullBlockLength, textLength, textStart, speakerStart, speakerLength, textBytes, speakerBytes,
				speakerDataBytes);
	}
	
	private BlockData parseNonDialogue(byte[] fullFileBytes, int start) {
		int shift = start + 1; //starts at full length
		byte[] textBytes;
		int fullBlockLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
		
		shift += 3; //skip [length1] [length2] 01
		int textLength = fullFileBytes[shift] & 0xFF;
		
		if(textLength > fullBlockLength) {
			return null;
		}
		
		shift++;
		int textStart = shift;
		
		textBytes = new byte[textLength];
		for(int i = 0; i < textLength; i++) {
			textBytes[i] = fullFileBytes[shift + i];
		}
		
		return new BlockData(Magic.NONDIALOGUE, start, fullBlockLength, textLength, textStart, textBytes);
	}
	
	private byte[] parseSpeaker(int nextByte, int nextByte2, int nextByte3, int shift, byte[] fullFileBytes) {
		if(nextByte < 0x05 && nextByte2 < 0x1F) {
			if(isCharacter(nextByte3)) {
				//00-03 00-04 00-1f [a letter]
				//likely a speaker header
				
				int speakerLength = nextByte2;
				int speakerStart = shift + 3;
				byte[] speakerBytes = new byte[speakerLength];
				for(int i = 0; i < speakerLength; i++) {
					speakerBytes[i] = fullFileBytes[speakerStart + i];
				}
				return speakerBytes;
			}
		}
		return null;
	}
	
	private BlockData parseTextEntry(byte[] fullFileBytes, int start) {
		int shift = start + 1;
		byte[] textBytes = null;
		byte[] answerBytes;
		int fullBlockLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
		int answerLength = -1;
		int answerStart = -1;
		int textLength = 0;
		int textStart = 0;
		Magic magic = null;
		
		//TODO: may want some special handling for the japanese ver, which seems to use 0A for a formatting thing
		
		shift += 3; //skip [length1] [length2] 07
		int id1 = fullFileBytes[shift];
		int id2 = fullFileBytes[shift + 1];
		
		if(id1 == 0x03 && id2 == 0x01) {
			magic = Magic.TEXTENTRYLONG;
			shift += 7;
			//skip 03 01 01 00 00 00 02
		}
		else if(id1 == 0x02 && id2 == 0x01) {
			magic = Magic.TEXTENTRYNODESCRIPT;
			shift += 2;
		}
		else {
			magic = Magic.TEXTENTRY;
			shift += 2;
		}
		answerLength = fullFileBytes[shift] & 0xFF;
		shift += 2;
		answerStart = shift;
		
		if(answerLength > fullBlockLength) {
			return null;
		}
		
		answerBytes = new byte[answerLength];
		for(int i = 0; i < answerLength; i++) {
			answerBytes[i] = fullFileBytes[shift + i];
		}
		shift += answerLength;
		
		if(magic != Magic.TEXTENTRYNODESCRIPT) {
			textLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
			shift += 2;
			textStart = shift;
			
			textBytes = new byte[textLength];
			for(int i = 0; i < textLength; i++) {
				textBytes[i] = fullFileBytes[shift + i];
			}
		}
		
		return new ExtraInfoBlockData(magic, start, fullBlockLength, textLength, textStart, answerStart, answerLength, textBytes, answerBytes, null);
	}
	
	public String toString() {
		return scriptName + " (" + fileName + ")";
	}
	
	public List<BlockData> getBlockList() {
		return blockList;
	}
	
	public byte[] getFullFileBytes() {
		return fullFileBytes;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public List<ConversationData> getConvoList() {
		return convoList;
	}
	
	public static class ConversationData {
		//this is the index of the length, 4b before 0A 09 19 00
		private int start;
		private int length;
		private int lastBlock;
		
		public ConversationData(int start, int length, int lastBlock) {
			this.start = start;
			this.length = length;
			this.lastBlock = lastBlock;
		}

		public int getStart() {
			return start;
		}

		public void setStart(int start) {
			this.start = start;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}
		
		public int getLastBlock() {
			return lastBlock;
		}

		public void setLastBlock(int lastBlock) {
			this.lastBlock = lastBlock;
		}
	}
}
