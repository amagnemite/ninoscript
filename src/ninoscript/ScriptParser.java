package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import ninoscript.ConvoData.*;

public class ScriptParser {
	public enum ConvoMagic {
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
		ConvoMagic(byte value, byte[] format, int fullLengthOffset, int textLengthOffset) {
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
	private static final int INTERNALSCRIPTNAMEINDEX = 8;
	private static final int SECTIONS = 8;
	private static final int VISUALBLOCKS = 8; //blocks 0-7 of section 5 seem to be visual related
	private static final byte m = 0x6D;
	private static final byte p = 0x70;
	
	private boolean isSingleConvo = false;
	private String fileName;
	private byte[] fullFileBytes;
	private String scriptName = "";
	private List<ConversationData> convoList = new ArrayList<ConversationData>();
	private List<ConvoData> blockList = new ArrayList<ConvoData>();
	private List<Integer> usedConvoIDs = new ArrayList<Integer>();
	private List<Integer> unusedConvoIds = new ArrayList<Integer>();
	
	//only keep track of the first block that stores the string, since we can just cross reference in the stringlistmap
	private Map<String, ConvoData> existingStringMap = new HashMap<String, ConvoData>();
	private Map<String, List<ConvoData>> stringListMap = new HashMap<String, List<ConvoData>>();
	
	public ScriptParser(File file) {
		int scriptNameLength = 0;
		byte[] nameBytes = null;
		int previousBlockCount = 0;
		int lastBlockOfConv = 0;
		boolean firstConvo = true;
		ConvoData data;
		ByteBuffer sectionFive = null;
		ByteBuffer sectionSeven;
		
		final int BLOCKZEROLENGTH = 12;
		final int BLOCKONEPRECONVOLENGTH = 2;
		final int BLOCKONESTRINGCOUNT = 2;
		final int BLOCKONEPOSTCONVOLENGTH = 7;
		
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
			scriptNameLength = fullFileBytes[INTERNALSCRIPTNAMEINDEX] & 0xFF;
			nameBytes = new byte[scriptNameLength];
			
			for(int i = 1; i <= scriptNameLength; i++) {
				nameBytes[i - 1] = fullFileBytes[INTERNALSCRIPTNAMEINDEX + i];
			}
			try {
				scriptName = new String(nameBytes, "Shift-JIS");
			}
			catch (UnsupportedEncodingException e) {
			}
		}
		
		int k = 4;
		for(int j = 0; j < SECTIONS; j++) { //4 after initial magic
			if(j != 7) {
				int length = (fullFileBytes[k] & 0xFF) | (fullFileBytes[k + 1] & 0xFF) << 8 | (fullFileBytes[k + 2] & 0xFF) << 16 |
						(fullFileBytes[k + 3] & 0xFF) << 24;
				
				if(j != 5) {
					k = 4 + length;
				}
				else {
					sectionFive = ByteBuffer.allocate(length);
					sectionFive.order(ByteOrder.LITTLE_ENDIAN);
					sectionFive.put(fullFileBytes, k + 4, k + 4 + length);
				}
			}
			else { //section 7/convoblock doesn't have an overall length
				sectionSeven = ByteBuffer.allocate(fullFileBytes.length - 1 - k);
				sectionSeven.order(ByteOrder.LITTLE_ENDIAN);
				sectionSeven.put(fullFileBytes, k, fullFileBytes.length - 1);
			}
		}
		//TODO: check to make sure all scripts can have a 5/7
		
		int flag;
		for(int i = 0; i < VISUALBLOCKS; i++) {
			int countByte = sectionFive.get() & 0xFF;
			
			switch(i) {
				case 0:
					for(int j = 0; j < countByte; j++) {
						getSubFuncOneLength(sectionFive);
						sectionFive.position(sectionFive.position() + BLOCKZEROLENGTH);
					}
					break;
				case 1:
					for(int j = 0; j < countByte; j++) {
						//gets read again in func but need to ref here, so don't increment pos	
						int firstByte = sectionFive.get(sectionFive.position()) & 0xFF;
						getSubFuncOneLength(sectionFive);
						sectionFive.position(sectionFive.position() + BLOCKONEPRECONVOLENGTH);		
						getMultipleArrayLength(sectionFive, BLOCKONESTRINGCOUNT);			
						getConvoIDs(sectionFive);
						sectionFive.position(sectionFive.position() + BLOCKONEPOSTCONVOLENGTH);
						
						flag = sectionFive.getInt();
						if((flag & 0x400) != 0) {
							int loopCount = sectionFive.get() & 0xFF;
							getMultipleArrayLength(sectionFive, loopCount);
							if((flag & 0x800) != 0) {
								System.out.println(fileName + " has 0x800 flag");
								sectionFive.position(sectionFive.position() + 1);
							}
						}
						if((flag & 0x4000) != 0) {
							sectionFive.position(sectionFive.position() + 6);
						}
						if((flag & 0x8) != 0) {
							sectionFive.position(sectionFive.position() + 8);
						}
						if((flag & 0x8000) != 0) {
							sectionFive.position(sectionFive.position() + 4);
						}
						if((flag & 0x20) != 0) {
							int length = sectionFive.get() & 0xFF;
							sectionFive.position(sectionFive.position() + length);
						}
						if((flag & 0x100) != 0) {
							sectionFive.position(sectionFive.position() + 1);
						}
						if((flag & 0x200) != 0) {
							sectionFive.position(sectionFive.position() + 4);
						}
						if((flag & 0x10000) != 0) {
							sectionFive.position(sectionFive.position() + 1);
						}
						if(firstByte == 1) {
							sectionFive.position(sectionFive.position() + 28);
						}
					}
					break;
				case 2:	
					for(int j = 0; j < countByte; j++) {
						parseSectionTwo(sectionFive);
					}
					break;
				case 3:
					getSubFuncOneLength(sectionFive);
					getArrayLength(sectionFive);
					sectionFive.position(sectionFive.position() + 1);
					flag = sectionFive.get() & 0xFF;
					if((flag & 0x8) != 0) {
						sectionFive.position(sectionFive.position() + 1);
					}
					getSubFuncTwoLength(sectionFive);
					break;
				case 4:
					getSubFuncOneLength(sectionFive);
					getConvoIDs(sectionFive);
					sectionFive.position(sectionFive.position() + 2);
					getSubFuncTwoLength(sectionFive);
					break;
				case 5:
					System.out.println(fileName + " has section 6 of block 5");
					break;
					//getSubFuncOneLength(sectionFive);
				case 6:
					System.out.println(fileName + " has section 7 of block 5");
					break;
			}
		}
		
		int convoID = 
		
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
	
	private void getArrayLength(ByteBuffer buffer) {
		int length = buffer.get() & 0xFF;
		buffer.position(buffer.position() + length);
	}
	
	private void getMultipleArrayLength(ByteBuffer buffer, int loopCount) {
		for(int l = 0; l < loopCount; l++) {
			getArrayLength(buffer);
		}
	}
	
	private void getSubFuncOneLength(ByteBuffer buffer) {
		final int UNKNOWNFLAG1 = 0x3C;
		final int UNKNOWNFLAG2 = 0x3;
		
		int flagVal = buffer.get() & 0xFF;
		int loopCount = buffer.get() & 0xFF;
		
		if((flagVal & UNKNOWNFLAG1) == 0) {
			if((flagVal & UNKNOWNFLAG2) != 0) {
				buffer.position(buffer.position() + loopCount * 8); //read 8 bytes per loop
			}
		}
		else {
			for(int j = 0; j < loopCount; j++) {
				getArrayLength(buffer); //read u8 length + array[length] per loop
			}
		}
	}
	
	private void getSubFuncTwoLength(ByteBuffer buffer) {
		final int CASETWOBYTES = 24;
		final int CASEONEBYTES = 16;
		final int CASEFOURBYTES = 28;
		
		buffer.position(buffer.position() + 1);
		int switchVal = buffer.get() & 0xFF;
		switch(switchVal) {
			case 0:
			case 2:
				buffer.position(buffer.position() + CASETWOBYTES);
				break;
			case 1:
				buffer.position(buffer.position() + CASEONEBYTES);
				break;
			case 3:
				System.out.println(fileName + " case 3 in subfunc 2");
				break;
			case 4:
				buffer.position(buffer.position() + CASEFOURBYTES);
				break;
		}
	}
	
	private void getSubFuncThreeLength(ByteBuffer buffer) {
		final int UNKNOWNFLAG1 = 0x81;
		final int UNKNOWNFLAG2 = 0x2;
		final int UNKNOWNFLAG3 = 0x4;
		final int UNKNOWNFLAG4 = 0x40;
		
		buffer.position(buffer.position() + 4);
		int flag = buffer.get() & 0xFF;
		if((flag & UNKNOWNFLAG1) != 0 && (flag & UNKNOWNFLAG2) != 0) {
			getArrayLength(buffer);
			if((flag & UNKNOWNFLAG3) != 0) {
				buffer.position(buffer.position() + 1);
			}
		}
		if((flag & UNKNOWNFLAG4) != 0) {
			buffer.position(buffer.position() + 1);
		}
	}
	
	private void getConvoIDs(ByteBuffer buffer) {
		final int EXTRADATABYTES = 12;
		int id = buffer.getInt();
		
		
		if(id != 0xFFFFFFFF) {
			usedConvoIDs.add(id);
		}
		
		int loopCount = buffer.get() & 0xFF;
		for(int j = 0; j < loopCount; j++) {
			buffer.position(buffer.position() + EXTRADATABYTES);
			id = buffer.getInt();
			System.out.println("extra id: " + id);
			usedConvoIDs.add(id);
		}
	}
	
	private void parseSectionTwo(ByteBuffer buffer) {
		final int COMMONREAD = 2;
		final int ONEPRESTRING = 1;
		final int ONEPOSTSTRING = 8;
		final int THREEPOSTFUNC = 6;
		final int FOURPOSTFUNC = 4;
		final int SIXPRECONVO = 20;
		final int DPREFLAG = 5;
		final int FORTYPOSTCONVO = 7;
		
		final int FOURFLAGONE = 0x8;
		final int FOURFLAGTWO = 0x40;
		final int SIXFLAGONE = 0x20;
		final int SIXFLAGTWO = 0x40;
		final int AFLAGONE = 0x1;
		final int AFLAGTWO = 0x2;
		final int DFLAG = 0x8;
		final int FORTYFLAGONE = 0x8000;
		final int FORTYFLAGTWO = 0x20;
		final int FORTYFLAGTHREE = 0x100;
		final int FORTYFLAGFOUR = 0x1;
		final int FORTYFLAGFIVE = 0x2;
		
		int firstByte = buffer.get(buffer.position()) & 0xFF;
		int flag;
		getSubFuncOneLength(buffer);
		buffer.position(buffer.position() + COMMONREAD);
		
		switch(firstByte) {
			case 1:
			case 2:
				buffer.position(buffer.position() + ONEPRESTRING);
				getArrayLength(buffer);
				buffer.position(buffer.position() + ONEPOSTSTRING);
				break;
			case 3:
				buffer.position(buffer.position() + 1);
				getSubFuncThreeLength(buffer);
				buffer.position(buffer.position() + THREEPOSTFUNC);
				getConvoIDs(buffer);
				break;
			case 4:
				buffer.position(buffer.position() + 1);
				getSubFuncThreeLength(buffer);
				buffer.position(buffer.position() + FOURPOSTFUNC);
				flag = buffer.get() & 0xFF;
				if((flag & FOURFLAGONE) != 0) {
					buffer.position(buffer.position() + 1);
				}
				if((flag & FOURFLAGTWO) != 0) {
					buffer.position(buffer.position() + 1);
				}
				getConvoIDs(buffer);
				break;
			case 5:
				System.out.println(fileName + " has section 2 subsection 5");
				break;
			case 6:
				getArrayLength(buffer);
				buffer.position(buffer.position() + SIXPRECONVO);
				getArrayLength(buffer);
				buffer.position(buffer.position() + 1);
				getConvoIDs(buffer);
				flag = buffer.get() & 0xFF; 
				if((flag & SIXFLAGONE) != 0) {
					buffer.position(buffer.position() + 2);
				}
				if((flag & SIXFLAGTWO) != 0) {
					buffer.position(buffer.position() + 2);
				}
				break;
			case 7:
				System.out.println(fileName + " has section 2 subsection 7");
				break;
			case 8:
				System.out.println(fileName + " has section 2 subsection 8");
				break;
			case 9:
				System.out.println(fileName + " has section 2 subsection 9");
				break;
			case 0xA:
				flag = buffer.get() & 0xFF;
				if((flag & AFLAGONE) != 0) {
					buffer.position(buffer.position() + 2);
				}
				if((flag & AFLAGTWO) != 0) {
					buffer.position(buffer.position() + 2);
				}
				break;
			case 0xB:
				System.out.println(fileName + " has section 2 subsection b");
				break;
			case 0xC:
				System.out.println(fileName + " has section 2 subsection c");
				break;
			case 0xD:
				buffer.position(buffer.position() + DPREFLAG);
				flag = buffer.get() & 0xFF;
				if((flag & DFLAG) != 0) {
					getMultipleArrayLength(buffer, buffer.get() & 0xFF);
				}
				break;
			case 0xE:
				System.out.println(fileName + " has section 2 subsection e");
				break;
			case 0xF:
				System.out.println(fileName + " has section 2 subsection f");
				break;
			case 0x10:
				System.out.println(fileName + " has section 2 subsection 0x10");
				break;
			case 0x11:
				System.out.println(fileName + " has section 2 subsection 0x11");
				break;
			case 0x12:
				System.out.println(fileName + " has section 2 subsection 0x12");
				break;
			case 0x13:
				System.out.println(fileName + " has section 2 subsection 0x13");
				break;
			case 0x41:
				System.out.println(fileName + " has section 2 subsection 0x41");
			case 0x40:
				getArrayLength(buffer);
				getArrayLength(buffer);
				getConvoIDs(buffer);
				flag = buffer.get() & 0xFF;
				if((flag & FORTYFLAGONE) != 0) {
					buffer.position(buffer.position() + 4);
				}
				if((flag & FORTYFLAGTWO) != 0) {
					getArrayLength(buffer);
				}
				if((flag & FORTYFLAGTHREE) != 0) {
					buffer.position(buffer.position() + 1);
				}
				flag = buffer.get() & 0xFF;
				if((flag & FORTYFLAGFOUR) != 0) {
					buffer.position(buffer.position() + 4);
				}
				if((flag & FORTYFLAGFIVE) != 0) {
					buffer.position(buffer.position() + 4);
				}
				break;
			default:
				System.out.println(fileName + " has unknown section 2 type " + firstByte);
		}
	}
	
	private boolean isCharacter(int val) {
		if((val >= 0x81 && val <= 0x84) || (val >= 0x21 && val <= 0x7E)) {
			return true;
		}
		return false;
	}
	
	private void checkIfStringExists(ConvoData data) {
		String string = data.getTextString();
		
		if(!existingStringMap.containsKey(string)) {
			existingStringMap.put(string, data);
		}
		else {
			ConvoData firstBlock = existingStringMap.get(string);
			List<ConvoData> sublist;
			if(!stringListMap.containsKey(string)) {
				sublist = new ArrayList<ConvoData>();
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

	private ConvoData parseBlock(byte[] fullFileBytes, int start) {
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
	
		return new ExtraInfoConvoData(ConvoMagic.DIALOGUE, start, fullBlockLength, textLength, textStart, speakerStart, speakerLength, textBytes, speakerBytes,
				speakerDataBytes);
	}
	
	private ConvoData parseNonDialogue(byte[] fullFileBytes, int start) {
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
		
		return new ConvoData(ConvoMagic.NONDIALOGUE, start, fullBlockLength, textLength, textStart, textBytes);
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
	
	private ConvoData parseTextEntry(byte[] fullFileBytes, int start) {
		int shift = start + 1;
		byte[] textBytes = null;
		byte[] answerBytes;
		int fullBlockLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
		int answerLength = -1;
		int answerStart = -1;
		int textLength = 0;
		int textStart = 0;
		ConvoMagic magic = null;
		
		//TODO: may want some special handling for the japanese ver, which seems to use 0A for a formatting thing
		
		shift += 3; //skip [length1] [length2] 07
		int id1 = fullFileBytes[shift];
		int id2 = fullFileBytes[shift + 1];
		
		if(id1 == 0x03 && id2 == 0x01) {
			magic = ConvoMagic.TEXTENTRYLONG;
			shift += 7;
			//skip 03 01 01 00 00 00 02
		}
		else if(id1 == 0x02 && id2 == 0x01) {
			magic = ConvoMagic.TEXTENTRYNODESCRIPT;
			shift += 2;
		}
		else {
			magic = ConvoMagic.TEXTENTRY;
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
		
		if(magic != ConvoMagic.TEXTENTRYNODESCRIPT) {
			textLength = (fullFileBytes[shift] & 0xFF) | (fullFileBytes[shift + 1] & 0xFF) << 8;
			shift += 2;
			textStart = shift;
			
			textBytes = new byte[textLength];
			for(int i = 0; i < textLength; i++) {
				textBytes[i] = fullFileBytes[shift + i];
			}
		}
		
		return new ExtraInfoConvoData(magic, start, fullBlockLength, textLength, textStart, answerStart, answerLength, textBytes, answerBytes, null);
	}
	
	public String toString() {
		return scriptName + " (" + fileName + ")";
	}
	
	public List<ConvoData> getBlockList() {
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
