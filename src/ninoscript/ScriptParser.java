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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import ninoscript.ConvoSubBlockData.*;

public class ScriptParser {
	public enum ConvoMagic {
		DIALOGUE (Integer.valueOf(0x05).byteValue()),
		NONDIALOGUE (Integer.valueOf(0x26).byteValue()),
		TEXTENTRY (Integer.valueOf(0x31).byteValue());
			
		//0x29 might also be text
		private byte value;
		
		ConvoMagic(byte value) {
			this.value = value;
		}
		
		public byte getValue() {
			return value;
		}
	}
	
	private static final int INTERNALSCRIPTNAMEOFFSET = 8;
	private static final int SECTIONS = 8;
	private static final int VISUALBLOCKS = 8; //blocks 0-7 of section 5 seem to be visual related
	private static final Set<Integer> TWOBYTETEXTTYPES = new TreeSet<Integer>(Arrays.asList(0x5, 0x11, 0x26, 0x29, 0x31));

	private boolean isSingleConvo = false;
	private String fileName;
	private byte[] fullFileBytes;
	private String scriptName = "";
	private List<Integer> usedConvoIDs = new ArrayList<Integer>();
	private Map<Integer, Conversation> convoMap = new TreeMap<Integer, Conversation>();
	
	//only keep track of the first block that contains a reused string, since we can just cross reference in the stringlistmap
	private Map<String, ConvoSubBlockData> stringFirstBlockOccurranceMap = new HashMap<String, ConvoSubBlockData>();
	
	private Map<String, List<ConvoSubBlockData>> stringListMap = new HashMap<String, List<ConvoSubBlockData>>();	
	
	//TODO: make a separate constructor for single convo files
	
	public ScriptParser(File file) {
		int scriptNameLength = 0;
		byte[] nameBytes = null;
		ConvoSubBlockData data;
		ByteBuffer sectionFive = null;
		ByteBuffer sectionSeven = null;
		
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
			scriptNameLength = fullFileBytes[INTERNALSCRIPTNAMEOFFSET] & 0xFF;
			nameBytes = new byte[scriptNameLength];
			
			for(int i = 1; i <= scriptNameLength; i++) {
				nameBytes[i - 1] = fullFileBytes[INTERNALSCRIPTNAMEOFFSET + i];
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
			else { //section 7/convo section doesn't have an overall length
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
					System.out.println(fileName + " has subsection 6 of section 5");
					break;
					//getSubFuncOneLength(sectionFive);
				case 6:
					System.out.println(fileName + " has subsection 7 of section 5");
					break;
			}
		}
		
		int definitionIndex = 0; //convos as they're listed in the script, not their id
		long bytes = (long) sectionSeven.getInt();
		int convoID = bytes != 0xFFFFFFFF ? (int) bytes : -1;	
		
		while(convoID != -1) {
			List<ConvoSubBlockData> blockList = new ArrayList<ConvoSubBlockData>();
			int startPos = sectionSeven.position();
			int convoLength = sectionSeven.getInt();
			int finalPos = sectionSeven.position() + convoLength;
			sectionSeven.position(sectionSeven.position() + 4); //magic 0A 09 19 00
			
			
			while(sectionSeven.position() < finalPos) {
				int subBlockType = sectionSeven.get() & 0xFF;
				
				if(!TWOBYTETEXTTYPES.contains(subBlockType)) {
					continue;
				}
				switch(subBlockType) {
					case 0x05:
						//regular dialogue
						data = parseTextBlock(sectionSeven);
						blockList.add(data);
						checkIfStringExists(data);
						break;
					case 0x26:
						//non conversation text (sidequest prompts, etc)
						data = parseNonDialogue(sectionSeven);
						blockList.add(data);
						checkIfStringExists(data);
						break;
					case 0x31:
						//text entry puzzle
						data = parseTextEntry(sectionSeven);
						blockList.add(data);
						break;
					case 0x29:
						//TODO: research this
					case 0x11:
						//0x11 is dialogue options
						break;
				}
			}
			if(blockList.size() > 0) {
				convoMap.put(definitionIndex, new Conversation(convoID, startPos, convoLength, blockList));
			}
			
			bytes = (long) sectionSeven.getInt();
			convoID = bytes != 0xFFFFFFFF ? (int) bytes : -1;
			definitionIndex++;
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
	
	private boolean hasFlag(int flag, int value) {
		if((flag & value) != 0) {
			return true;
		}
		return false;
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
		int id = buffer.getInt(buffer.position());
        byte ff = Integer.valueOf(0xFF).byteValue();
		byte[] noID = {ff, ff, ff, ff};
		byte[] bytes = new byte[4];
		buffer.get(bytes); //roundabout, but java doesn't like 0xFFFFFFFF as an int
		
		if(!noID.equals(bytes)) {
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
				System.out.println(fileName + " has section 2 subsection 0x41"); //double check this does the same stuff as 0x40
			case 0x40:
				getArrayLength(buffer);
				getArrayLength(buffer);
				getConvoIDs(buffer);
				buffer.position(buffer.position() + FORTYPOSTCONVO);
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
	
	private void checkIfStringExists(ConvoSubBlockData data) { //has this string already occurred in script
		String string = data.getTextString();
		
		if(!stringFirstBlockOccurranceMap.containsKey(string)) {
			stringFirstBlockOccurranceMap.put(string, data);
		}
		else {
			ConvoSubBlockData firstBlock = stringFirstBlockOccurranceMap.get(string);
			List<ConvoSubBlockData> sublist;
			if(!stringListMap.containsKey(string)) {
				sublist = new ArrayList<ConvoSubBlockData>();
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
	
	private ConvoSubBlockData parseTextBlock(ByteBuffer buffer) {
		//TODO: may want to do something about char 0x1c, which the jpn ver seems to use and sometimes appears in scripts
		final int SPRITEFLAG = 1 << 0;
		final int UNKNOWNFLAG1 = (1 << 11) + (1 << 1);
		final int UNKNOWNSUBFLAG1 = 1 << 11;
		final int UNKNOWNFLAG2 = 1 << 2;
		final int UNKNOWNFLAG3 = 1 << 3;
		final int UNKNOWNFLAG4 = 1 << 4;
		final int UNKNOWNFLAG5 = 1 << 5;
		final int SPEAKERFLAG = 1 << 6;
		final int UNKNOWNFLAG6 = 1 << 7;
		final int STRINGFLAG = (1 << 14) + (1 << 8);
		final int UNKNOWNSUBFLAG2 = 1 << 8;
		final int UNKNOWNFLAG7 = 1 << 12;
		final int UNKNOWNFLAG8 = 1 << 13;
		
		//should be pointing at the length already;
		int start = buffer.position() - 1;
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerLength = -1;
		int speakerStart = -1;
		
		int fullBlockLength = buffer.getShort();
		int mysteryByte = buffer.get() & 0xFF;
		if(mysteryByte != 0) {
			System.out.println(fileName + " has non one count? byte in text block");
		}
		//buffer.position(buffer.position() + 1); //skip the byte between lengths
		
		int textLength = buffer.getShort();
		int textStart = buffer.position();
		
		textBytes = new byte[textLength];
		buffer.get(textBytes);
		
		int flag = buffer.getInt();
	
		if(hasFlag(flag, SPRITEFLAG)) {
			int length = buffer.get() & 0xFF;
			buffer.position(buffer.position() + length);
			length = buffer.get() & 0xFF;
			buffer.position(buffer.position() + length);
		}
		if(hasFlag(flag, UNKNOWNFLAG1)) {
			buffer.position(buffer.position() + 4);
			if(hasFlag(flag, UNKNOWNSUBFLAG1)) {
				flag = flag & 0xFFFFFFFD;
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG2)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG3)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG4)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG5)) {
			buffer.position(buffer.position() + 2);
		}
		if(hasFlag(flag, SPEAKERFLAG)) {
			speakerStart = buffer.position();
			int length = buffer.get() & 0xFF;
			speakerBytes = new byte[length];
			buffer.get(speakerBytes);
		}
		if(hasFlag(flag, UNKNOWNFLAG6)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, STRINGFLAG)) {
			int count = buffer.get() & 0xFF;
			for(int i = 0; i < count; i++) {
				if(hasFlag(flag, UNKNOWNSUBFLAG2)) {
					System.out.println(fileName + " has unknown string in convo");
				}
				buffer.position(buffer.position() + 2);
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG7)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG8)) {
			int count = buffer.get() & 0xFF;
			buffer.position(buffer.position() + 4 * count);
		}
	
		return new ExtraStringConvoData(ConvoMagic.DIALOGUE, start, fullBlockLength, textLength, textStart, speakerStart, speakerLength,
				textBytes, speakerBytes);
	}
	
	private ConvoSubBlockData parseNonDialogue(ByteBuffer buffer) {
		int UNKNOWNFLAG1 = 0x3;
		int UNKNOWNFLAG2 = 0x40;
		int UNKNOWNFLAG3 = 0x80;
		
		//starts at full length
		int start = buffer.position() - 1;
		
		int fullBlockLength = buffer.getShort();
		int mysteryByte = buffer.get() & 0xFF;
		if(mysteryByte != 0) {
			System.out.println(fileName + " has non one count? byte in nondialogue block");
		}
		//buffer.position(buffer.position() + 1); //skip the byte between lengths
		int textStart = buffer.position();
		int textLength = buffer.get() & 0xFF;
		
		byte[] textBytes = new byte[textLength];
		buffer.get(textBytes);
		
		int flag = buffer.get() | 0xFF00;
		
		if(hasFlag(flag, UNKNOWNFLAG1)) {
			System.out.println(fileName + " has extra bytes in nondialogue text");
			
		}
		if(hasFlag(flag, UNKNOWNFLAG2)) {
			buffer.position(buffer.position() + 2);
		}
		if(hasFlag(flag, UNKNOWNFLAG3)) {
			buffer.position(buffer.position() + 1);
		}
		
		return new ConvoSubBlockData(ConvoMagic.NONDIALOGUE, start, fullBlockLength, textLength, textStart, textBytes);
	}
	
	private ConvoSubBlockData parseTextEntry(ByteBuffer buffer) {
		final int UNKNOWNFLAG = 1 << 0;
		
		int start = buffer.position() - 1;
		int fullBlockLength = buffer.getShort();
		byte[] answerBytes;
		byte[] descriptionBytes = null;
		int answerLength = 0;
		int answerStart = 0;
		int descriptionLength = -1;
		int descriptionStart = -1;
		
		//TODO: may want some special handling for the japanese ver, which seems to use 0A for a formatting thing
		
		buffer.position(buffer.position() + 1); //skip the 0x07
		int flag = buffer.get() & 0xFF;
		if(hasFlag(flag, UNKNOWNFLAG)) {
			int count = buffer.get() & 0xFF;
			buffer.position(buffer.position() + count * 4);
		}
		
		int stringCount = buffer.get() & 0xFF;
		
		//some text entries only have the answer with a generic "enter the answer" prompt and no description
		answerStart = buffer.position();
		answerLength = buffer.getShort();
		answerBytes = new byte[answerLength];
		buffer.get(answerBytes);
		
		if(stringCount == 2) {
			descriptionStart = buffer.position();
			descriptionLength = buffer.getShort();
			descriptionBytes = new byte[descriptionLength];
			buffer.get(descriptionBytes);
		}
		if(stringCount > 2) {
			System.out.println(fileName + " has text entry with 3+ strings");
		}
		
		return new ExtraStringConvoData(ConvoMagic.TEXTENTRY, start, fullBlockLength, descriptionLength, descriptionStart, answerStart, answerLength,
				descriptionBytes, answerBytes);
	}
	
	public String toString() {
		return scriptName + " (" + fileName + ")";
	}
	
	public byte[] getFullFileBytes() {
		return fullFileBytes;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public Map<Integer, Conversation> getConvoMap() {
		return convoMap;
	}
	
	public List<Integer> getUsedConvoIDs() {
		return usedConvoIDs;
	}
	
	public static class Conversation {
		//this is the index of the length, 4b before 0A 09 19 00
		private int id;
		private int startOffset;
		private int length;
		private List<ConvoSubBlockData> blockList;
		
		public Conversation(int id, int start, int length, List<ConvoSubBlockData> blockList) {
			this.id = id;
			this.startOffset = start;
			this.length = length;
			this.blockList = blockList;
		}

		public int getStartOffset() {
			return startOffset;
		}

		public void setStartOffset(int start) {
			this.startOffset = start;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}
		
		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public List<ConvoSubBlockData> getBlockList() {
			return blockList;
		}
	}
}
