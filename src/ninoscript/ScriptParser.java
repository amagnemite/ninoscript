package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import ninoscript.ConvoSubBlockData.*;

public class ScriptParser {
	public enum ConvoMagic {
		DIALOGUE (Integer.valueOf(0x05).byteValue()),
		NONDIALOGUE (Integer.valueOf(0x26).byteValue()),
		TEXTENTRY (Integer.valueOf(0x31).byteValue()),
		MULTIPLECHOICE (Integer.valueOf(0x11).byteValue());
			
		//0x29 might also be text
		private byte value;
		
		ConvoMagic(byte value) {
			this.value = value;
		}
		
		public byte getValue() {
			return value;
		}
	}
	
	private static final int EVENTSTATESECTIONINDEX = 1;
	private static final int MAPENTITYSECTIONINDEX = 5;
	
	private static final int INTERNALSCRIPTNAMEOFFSET = 8;
	private static final int SECTION = 8;
	//private static final int VISUALBLOCKS = 8; //blocks 0-7 of section 5 seem to be visual related
	private static final int SINGLECONVOID = 0;
	private static final Set<Integer> TWOBYTETEXTTYPES = new TreeSet<Integer>(Arrays.asList(0x5, 0x11, 0x26, 0x29, 0x31));

	private boolean isSingleConvo = false;
	private String fileName;
	private byte[] fullFileBytes;
	private String scriptName = "";
	private Map<Integer, Conversation> convoMap = new TreeMap<Integer, Conversation>(); //all ids, convos
	private List<Integer> usedConvoIDs = new ArrayList<Integer>(); //ids that are actually used
	
	//only keep track of the first block that contains a reused string, since we can just cross reference in the stringlistmap
	private Map<String, ConvoSubBlockData> stringFirstBlockOccurranceMap = new HashMap<String, ConvoSubBlockData>();
	
	private Map<String, List<ConvoSubBlockData>> stringListMap = new HashMap<String, List<ConvoSubBlockData>>();
	private File originalFile;
	
	//TODO: make a separate constructor for single convo files
	
	public ScriptParser(File file) {
		int scriptNameLength = 0;
		byte[] nameBytes = null;
		IntByteArrayInputStream eventStateSectionBuffer = null;
		IntByteArrayInputStream mapEntitySectionBuffer = null;
		IntByteArrayInputStream convoSectionBuffer = null;
		Map<Integer, List<Integer>> connectedIDs = new HashMap<Integer, List<Integer>>();
		
		final int UNKNOWNTWOBYTETYPE = 0x1F;
		final int CONDITIONALCONVOTYPE = 0x21;
		
		final int BLOCKZEROLENGTH = 12;
		final int BLOCKONEPRECONVOLENGTH = 2;
		final int BLOCKONESTRINGCOUNT = 2;
		final int BLOCKONEPOSTCONVOLENGTH = 7;
		final int SECTIONONEPRECONVOCOUNT = 14;
		
		final int SECTIONONEFLAG1 = 1 << 4;
		final int SECTIONONEFLAG2 = 1 << 6;
		final int SECTIONONEFLAG3 = 1 << 1;
		final int SECTIONONEFLAG4 = 1 << 2;
		final int SECTIONONEFLAG5 = 1 << 3;
		final int SECTIONFIVEFLAG1 = 1 << 1;
		final int SECTIONFIVEFLAG2 = 0x8000;
			
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
		
		originalFile = file;
		fileName = file.getName();
		
		int offset = !isSingleConvo ? 4 : 0;
		if(!isSingleConvo) {
			//parse section zero
			scriptNameLength = fullFileBytes[INTERNALSCRIPTNAMEOFFSET] & 0xFF;
			nameBytes = Arrays.copyOfRange(fullFileBytes, INTERNALSCRIPTNAMEOFFSET + 1, INTERNALSCRIPTNAMEOFFSET + scriptNameLength + 1);
			
			try {
				scriptName = new String(nameBytes, "Shift-JIS");
			}
			catch (UnsupportedEncodingException e) {
			}
			
			for(int j = 0; j < SECTION - 1; j++) { //4 after initial magic
				int length = (fullFileBytes[offset] & 0xFF) | (fullFileBytes[offset + 1] & 0xFF) << 8 |
						(fullFileBytes[offset + 2] & 0xFF) << 16 | (fullFileBytes[offset + 3] & 0xFF) << 24;
				offset += 4;
				
				if(j == EVENTSTATESECTIONINDEX) {
					eventStateSectionBuffer = new IntByteArrayInputStream(fullFileBytes, offset, length);
				}
				else if(j == MAPENTITYSECTIONINDEX) {
					mapEntitySectionBuffer = new IntByteArrayInputStream(fullFileBytes, offset, length);
				}
				offset += length;
			}
		}
		
		//section 7/convo section doesn't have an overall length
		convoSectionBuffer = new IntByteArrayInputStream(fullFileBytes, offset, fullFileBytes.length - offset);
			
		//TODO: check to make sure all (non single convo) scripts can have a 5/7
		
		if(!isSingleConvo) {
			int flag;
			int structCount = eventStateSectionBuffer.readU8();
			
			//section 1
			for(int i = 0; i < structCount; i++) {
				eventStateSectionBuffer.skip(2); //length and id
				flag = eventStateSectionBuffer.readU8();
				if(flag != 0) {
					if(hasFlag(flag, SECTIONONEFLAG1)) {
						int val = eventStateSectionBuffer.readU8();
						if(val == 0) {
							int stringLength = eventStateSectionBuffer.readU8();
							eventStateSectionBuffer.skip(stringLength);
							eventStateSectionBuffer.skip(1);
						}
					}
					if(hasFlag(flag, SECTIONONEFLAG2)) {
						int arrayLength = eventStateSectionBuffer.readU8();;
						eventStateSectionBuffer.skip(arrayLength);
						int val = eventStateSectionBuffer.readU8();
						if((hasFlag(val, SECTIONONEFLAG3))) {
							eventStateSectionBuffer.skip(1);
						}
						if((hasFlag(val, SECTIONONEFLAG4))) {
							eventStateSectionBuffer.skip(2);
						}
						if((hasFlag(val, SECTIONONEFLAG5))) {
							eventStateSectionBuffer.skip(2);
						}
					}
				}
				eventStateSectionBuffer.skip(SECTIONONEPRECONVOCOUNT);
				//System.out.println("section one convo ids");
				getConvoIDs(eventStateSectionBuffer);
				//System.out.println("");
			}
			
			int countByte = mapEntitySectionBuffer.readU8();
			
			//section 5, block 0
			for(int j = 0; j < countByte; j++) {
				getSubFuncOneLength(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(BLOCKZEROLENGTH);
			}
			
			//section 5, npcs / block 1
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) { //section one, npcs
				int firstByte = getSubFuncOneLength(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(BLOCKONEPRECONVOLENGTH);		
				getMultipleArrayLength(mapEntitySectionBuffer, BLOCKONESTRINGCOUNT);			
				getConvoIDs(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(BLOCKONEPOSTCONVOLENGTH);
				
				flag = mapEntitySectionBuffer.readU32();
				if(hasFlag(flag, 1 << 10)) {
					int loopCount = mapEntitySectionBuffer.readU8();
					getMultipleArrayLength(mapEntitySectionBuffer, loopCount);
					if(hasFlag(flag, 1 << 11)) {
						mapEntitySectionBuffer.skip(1);
					}
				}
				if(hasFlag(flag, 1 << 14)) {
					mapEntitySectionBuffer.skip(6);
				}
				if(hasFlag(flag, 1 << 3)) {
					mapEntitySectionBuffer.skip(8);
				}
				if(hasFlag(flag, 1 << 15)) {
					mapEntitySectionBuffer.skip(4);
				}
				if(hasFlag(flag, 1 << 5)) {
					skipArrayLength(mapEntitySectionBuffer);
				}
				if(hasFlag(flag, 1 << 8)) {
					mapEntitySectionBuffer.skip(1);
				}
				if(hasFlag(flag, 1 << 9)) {
					mapEntitySectionBuffer.skip(4);
				}
				if(hasFlag(flag, 1 << 16)) {
					mapEntitySectionBuffer.skip(1);
				}
				if(firstByte == 1) {
					mapEntitySectionBuffer.skip(28);
				}
			}
			
			//section five, map props/other entities / block 2
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) { 
				parseMapEntities(mapEntitySectionBuffer);
			}
			
			//section five, adjacent scripts / block 3
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) {
				getSubFuncTwoLength(mapEntitySectionBuffer);
				skipArrayLength(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(1);
				flag = mapEntitySectionBuffer.readU8();
				if(hasFlag(flag, 1 << 3)) {
					mapEntitySectionBuffer.skip(1);
				}
				getSubFuncThreeLength(mapEntitySectionBuffer);
			}
			
			//section five, block 4
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) {
				getSubFuncTwoLength(mapEntitySectionBuffer);
				getConvoIDs(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(2);
				getSubFuncThreeLength(mapEntitySectionBuffer);
			}
			
			//section five, block 5
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) {
				getSubFuncTwoLength(mapEntitySectionBuffer);
				mapEntitySectionBuffer.skip(2);
				getSubFuncThreeLength(mapEntitySectionBuffer);
				flag = mapEntitySectionBuffer.readU16();
				if(hasFlag(flag, SECTIONFIVEFLAG1)) {
					mapEntitySectionBuffer.skip(2);
				}
				if(hasFlag(flag, SECTIONFIVEFLAG2)) {
					mapEntitySectionBuffer.skip(2);
				}
			}
			
			//section five, block 6
			countByte = mapEntitySectionBuffer.readU8();
			for(int j = 0; j < countByte; j++) {
				System.out.println(fileName + " has subsection 7 of block 5");
				//note 7 uses 2 count bytes
			}	
		}
		else {
			usedConvoIDs.add(SINGLECONVOID);
		}
		
		int definitionIndex = 0; //convos as they're defined in the script, not their id
		int convoID = !isSingleConvo ? convoSectionBuffer.readU32() : SINGLECONVOID;
		//offset is pointing to the start of the convo section
		
		while(convoID != -1) {
			List<ConvoSubBlockData> blockList = new ArrayList<ConvoSubBlockData>();
			int specificConvoStartPos = offset; //including the id
			int convoLength = !isSingleConvo ? convoSectionBuffer.readU32(): fullFileBytes.length - 4;
			offset = !isSingleConvo ? offset + 8 : offset; //id + length
			int specificConvoEndPos = offset + convoLength;
			
			if(convoLength == 0) { //apparently there's null convo blocks?
				convoID = convoSectionBuffer.readU32();
				continue;
			}
			convoSectionBuffer.skip(4); //magic 0A 09 19 00
			offset += 4;
			
			while(offset < specificConvoEndPos) {
				int subBlockType = convoSectionBuffer.readU8();
				offset++;
				
				if(!TWOBYTETEXTTYPES.contains(subBlockType)) {
					if(subBlockType == UNKNOWNTWOBYTETYPE) { //doesn't seem to contain strings, but it has a 2 byte length
						int unknownLength = convoSectionBuffer.readU16();
						convoSectionBuffer.skip(unknownLength);
						offset += 2 + unknownLength;
						continue;
					}
					else if(subBlockType == CONDITIONALCONVOTYPE) {
						if(!connectedIDs.containsKey(convoID)) {
							connectedIDs.put(convoID, new ArrayList<Integer>());
						}
						convoSectionBuffer.skip(2); //length + fixed 1?
						int chainedID = convoSectionBuffer.readU32();
						offset += 6;
						//System.out.println("0x21 " + chainedID);
						connectedIDs.get(convoID).add(chainedID);
						continue;
					}
					else {
						int len = convoSectionBuffer.readU8();
						convoSectionBuffer.skip(len);
						offset += 1 + len;
						continue;
					}
				}
				
				switch(subBlockType) {
					case 0x05: //regular dialogue		
						offset = parseTextBlock(convoSectionBuffer, offset, blockList);
						break;
					case 0x26: //non conversation text (sidequest prompts, etc)		
						offset = parseNonDialogue(convoSectionBuffer, offset, blockList);
						break;
					case 0x31: //text entry puzzle				
						offset = parseTextEntry(convoSectionBuffer, offset, blockList);
						break;
					case 0x29:
						//TODO: research this
						System.out.println(fileName + " " + convoID + " has 0x29 sub");
						int len = convoSectionBuffer.readU16();
						convoSectionBuffer.skip(len);
						offset += 2 + len;
						break;
					case 0x11: //dialogue options
						offset = parseMultipleChoiceOptions(convoSectionBuffer, offset, blockList);
						break;
				}
			}
			if(blockList.size() > 0) {
				convoMap.put(definitionIndex, new Conversation(convoID, specificConvoStartPos, convoLength, blockList));
				definitionIndex++;
			}
			convoID = !isSingleConvo ? convoSectionBuffer.readU32() : -1; //offset handled at top of loop
		}
		for(Conversation convo : convoMap.values()) {
			if(usedConvoIDs.contains(convo.getId()) && connectedIDs.containsKey(convo.getId())) {
				//only add connecting ids if their originating id is used
				usedConvoIDs.addAll(connectedIDs.get(convo.getId()));
				connectedIDs.remove(convo.getId());
			}
		}
		//it's possible for convos without text to chain into convos with text, so double check here
		boolean doneChecking = false;
		while(!doneChecking) {
			doneChecking = true;
			Iterator<Entry<Integer, List<Integer>>> iterator = connectedIDs.entrySet().iterator();
			while(iterator.hasNext()) {
				Entry<Integer, List<Integer>> entry = iterator.next();
				if(usedConvoIDs.contains(entry.getKey())) {
					doneChecking = false;
					usedConvoIDs.addAll(entry.getValue());
					iterator.remove();
				}
			}
		}
	}
	
	private int skipArrayLength(IntByteArrayInputStream stream) {
		int length = stream.readU8();
		stream.skip(length);
		return 1 + length;
	}
	
	private int getMultipleArrayLength(IntByteArrayInputStream stream, int loopCount) {
		int bytesRead = 0;
		for(int l = 0; l < loopCount; l++) {
			bytesRead += skipArrayLength(stream);
		}
		return bytesRead;
	}
	
	private boolean hasFlag(int flag, int value) {
		if((flag & value) != 0) {
			return true;
		}
		return false;
	}
	
	private int getSubFuncOneLength(IntByteArrayInputStream stream) {
		final int FIXEDBYTES = 14;
		int firstByte = stream.readU8(); //firstByte

		getSubFuncTwoLength(stream);
		
		stream.skip(FIXEDBYTES);
		return firstByte;
	}
	
	private void getSubFuncTwoLength(IntByteArrayInputStream stream) { //some structs call this directly
		final int UNKNOWNFLAG1 = 0x3C;
		final int UNKNOWNFLAG2 = 0x3;
		
		int flagVal = stream.readU8();
		int loopCount = 0;
		
		if(!hasFlag(flagVal, UNKNOWNFLAG1)) {
			if(hasFlag(flagVal, UNKNOWNFLAG2)) {
				loopCount = stream.readU8();
				stream.skip(loopCount * 8); //read 8 bytes per loop
			}
		}
		else {
			loopCount = stream.readU8();
			getMultipleArrayLength(stream, loopCount); //read u8 length + array[length] per loop
		}
	}
	
	private void getSubFuncThreeLength(IntByteArrayInputStream stream) {
		final int CASETWOBYTES = 24;
		final int CASEONEBYTES = 16;
		final int CASETHREEBYTES = 12;
		final int CASEFOURBYTES = 28;
		
		stream.skip(1);
		int switchVal = stream.readU8();
		switch(switchVal) {
			case 0:
			case 2:
				stream.skip(CASETWOBYTES);
				break;
			case 1:
				stream.skip(CASEONEBYTES);
				break;
			case 3:
				int loopCount = stream.readU8();
				stream.skip(CASETHREEBYTES * (loopCount / 3));
				break;
			case 4:
				stream.skip(CASEFOURBYTES);
				break;
		}
	}
	
	private void getSubFuncFourLength(IntByteArrayInputStream stream) {
		final int UNKNOWNFLAG1 = 0x81;
		final int UNKNOWNFLAG2 = 1 << 1;
		final int UNKNOWNFLAG3 = 1 << 2;
		final int UNKNOWNFLAG4 = 0x40;
		
		stream.skip(4);
		int flag = stream.readU8();
		if(hasFlag(flag, UNKNOWNFLAG1) && hasFlag(flag, UNKNOWNFLAG2)) {
			skipArrayLength(stream);
			if(hasFlag(flag, UNKNOWNFLAG3)) {
				stream.skip(1);
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG4)) {
			stream.skip(1);
		}
	}
	
	private void getConvoIDs(IntByteArrayInputStream stream) {
		final int EXTRADATABYTES = 12;
		
		int id = stream.readU32();
		if(id != -1) {
			//System.out.println("convo id: " + id);
			usedConvoIDs.add(id);
		}
		
		int loopCount = stream.readU8();
		for(int j = 0; j < loopCount; j++) {
			stream.skip(EXTRADATABYTES);
			id = stream.readU32();
			//System.out.println("extra id: " + id);
			if(!usedConvoIDs.contains(id)) { //block one has some double defs?
				usedConvoIDs.add(id);
			}
		}
	}
	
	private void parseMapEntities(IntByteArrayInputStream stream) {
		final int COMMONREAD = 2;
		final int ZEROPOSTSTRING = 9;
		final int ONEPOSTSTRING = 8;
		final int THREEPOSTFUNC = 6;
		final int FOURPOSTFUNC = 4;
		final int SIXPRECONVO = 20;
		final int DPREFLAG = 5;
		final int FREAD = 49;
		final int FORTYPOSTCONVO = 7;
		final int FORTYONEPOSTCONVO = 11;
		final int ELEVENPRECONVO = 6;
		
		final int FOURFLAGONE = 1 << 3;
		final int FOURFLAGTWO = 1 << 6;
		final int SIXFLAGONE = 1 << 5;
		final int SIXFLAGTWO = 1 << 6;
		final int AFLAGONE = 1 << 0;
		final int AFLAGTWO = 1 << 1;
		final int DFLAG = 1 << 3;
		final int FORTYFLAGONE = 0x8000;
		final int FORTYFLAGTWO = 1 << 5;
		final int FORTYFLAGTHREE = 1 << 8;
		final int FORTYFLAGFOUR = 1 << 0;
		final int FORTYFLAGFIVE = 1 << 1;
		
		int flag;
		int firstByte = getSubFuncOneLength(stream);	
		stream.skip(COMMONREAD);
		
		switch(firstByte) {
			case 0:
				skipArrayLength(stream);
				stream.skip(ZEROPOSTSTRING);
				break;
			case 1:
			case 2:
				skipArrayLength(stream);
				stream.skip(ONEPOSTSTRING);
				break;
			case 3:
				stream.skip(1);
				getSubFuncFourLength(stream);
				stream.skip(THREEPOSTFUNC);
				getConvoIDs(stream);
				break;
			case 4:
				stream.skip(1);
				getSubFuncFourLength(stream);
				stream.skip(FOURPOSTFUNC);
				flag = stream.readU8();
				if((flag & FOURFLAGONE) != 0) {
					stream.skip(1);
				}
				if((flag & FOURFLAGTWO) != 0) {
					stream.skip(1);
				}
				getConvoIDs(stream);
				break;
			case 5:
				skipArrayLength(stream);
				stream.skip(4 + 4 + 1);
				skipArrayLength(stream);
				stream.skip(1);
				break;
			case 6:
				skipArrayLength(stream);
				stream.skip(SIXPRECONVO);
				skipArrayLength(stream);
				stream.skip(1);
				getConvoIDs(stream);
				flag = stream.readU8();
				if(hasFlag(flag, SIXFLAGONE)) {
					stream.skip(1);
				}
				if(hasFlag(flag, SIXFLAGTWO)) {
					stream.skip(1);
				}
				break;
			case 7:
				System.out.println(fileName + " has section 2 subsection 7");
				break;
			case 8:
				flag = stream.readU8();
				skipArrayLength(stream); //string
				if(hasFlag(flag, 0x2)) {
					skipArrayLength(stream); //string 2
				}
				break;
			case 9:
				stream.skip(2);
				getConvoIDs(stream);
				break;
			case 0xA:
				flag = stream.readU8();
				if((flag & AFLAGONE) != 0) {
					stream.skip(2);
				}
				if((flag & AFLAGTWO) != 0) {
					stream.skip(2);
				}
				break;
			case 0xB:
				stream.skip(1);
				getSubFuncFourLength(stream);
				stream.skip(4); //2 2 byte
				getConvoIDs(stream);
				break;
			case 0xC:
				stream.skip(2); //2 1 byte
				getSubFuncFourLength(stream);
				stream.skip(4); //2 2 byte
				getConvoIDs(stream);
				break;
			case 0xD:
				stream.skip(DPREFLAG);
				flag = stream.readU8();
				if(hasFlag(flag, DFLAG)) {
					getMultipleArrayLength(stream, stream.readU8());
				}
				break;
			case 0xE:
				stream.skip(4);
				skipArrayLength(stream);
				flag = stream.readU16();
				if(hasFlag(flag, 1 << 2)) {
					stream.skip(1);
				}
				if(hasFlag(flag, 1 << 3)) {
					stream.skip(1);
				}
				if(hasFlag(flag, 1 << 5)) {
					stream.skip(2);
				}
				if(hasFlag(flag, 1 << 6)) {
					stream.skip(2);
				}
				if(hasFlag(flag, 1 << 7)) {
					stream.skip(2);
				}
				if(hasFlag(flag, 1 << 9)) {
					stream.skip(4);
				}
				break;
			case 0xF:
				stream.skip(FREAD);
				break;
			case 0x10:
				System.out.println(fileName + " has section 2 subsection 0x10");
				break;
			case 0x11:
				stream.skip(ELEVENPRECONVO);
				getConvoIDs(stream);
				break;
			case 0x12:
				stream.skip(1);
				skipArrayLength(stream); //string name
				stream.skip(2);
				break;
			case 0x13:
				System.out.println(fileName + " has section 2 subsection 0x13");
				break;
			case 0x41:
				skipArrayLength(stream);
				skipArrayLength(stream);
				getConvoIDs(stream);
				stream.skip(FORTYONEPOSTCONVO);
				break;
			case 0x40:
				skipArrayLength(stream);
				skipArrayLength(stream);
				getConvoIDs(stream);
				stream.skip(FORTYPOSTCONVO);
				flag = stream.readU16();
				if((flag & FORTYFLAGONE) != 0) {
					stream.skip(4);
				}
				if((flag & FORTYFLAGTWO) != 0) {
					skipArrayLength(stream);
				}
				if((flag & FORTYFLAGTHREE) != 0) {
					stream.skip(1);
				}
				flag = stream.readU8();
				if((flag & FORTYFLAGFOUR) != 0) {
					stream.skip(4);
				}
				if((flag & FORTYFLAGFIVE) != 0) {
					stream.skip(4);
				}
				break;
			default:
				System.out.println(fileName + " has unknown map entity type " + firstByte);
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
	
	private int parseTextBlock(IntByteArrayInputStream stream, int offset, List<ConvoSubBlockData> blockList) {
		//TODO: may want to do something about char 0x1c, which the jpn ver seems to use and sometimes appears in scripts
		final int SPRITEFLAG = 1 << 0;
		final int UNKNOWNFLAG1 = (1 << 11) + (1 << 1);
		final int UNKNOWNSUBFLAG1 = 1 << 11;
		final int UNKNOWNFLAG2 = 1 << 2;
		final int SPEAKERDIRECTIONFLAG = 1 << 3;
		final int UNKNOWNFLAG4 = 1 << 4;
		final int UNKNOWNFLAG5 = 1 << 5;
		final int SPEAKERNAMEFLAG = 1 << 6;
		final int UNKNOWNFLAG6 = 1 << 7;
		final int STRINGFLAG = (1 << 14) + (1 << 8);
		final int UNKNOWNSUBFLAG2 = 1 << 8;
		final int UNKNOWNSUBFLAG3 = 1 << 9;
		final int UNKNOWNFLAG7 = 1 << 12;
		final int UNKNOWNFLAG8 = 1 << 13;
		
		//parsed type byte, should be pointing at the length already;
		int start = offset - 1;
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerStart = -1;
		int speakerDirectionByte = -1;
		
		int fullBlockLength = stream.readU16();
		int mysteryByte = stream.readU8();
		if(mysteryByte != 1) {
			System.out.println(fileName + " has non one count? byte in text block");
		}
		offset += 3;
		
		int textStart = offset;
		int textLength = stream.readU16();
		
		textBytes = new byte[textLength];
		stream.read(textBytes, 0, textLength);
		offset += 2 + textLength;
		
		int flag = stream.readU32();
		offset += 4;
	
		if(hasFlag(flag, SPRITEFLAG)) {
			int length = stream.readU8();
			stream.skip(length);
			offset += 1 + length;
			
			length = stream.readU8();
			stream.skip(length);
			offset += 1 + length;
		}
		if(hasFlag(flag, UNKNOWNFLAG1)) {
			stream.skip(4);
			offset += 4;
			if(hasFlag(flag, UNKNOWNSUBFLAG1)) {
				flag = flag & 0xFFFFFFFD;
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG2)) {
			stream.skip(1);
			offset += 1;
		}
		if(hasFlag(flag, SPEAKERDIRECTIONFLAG)) {
			speakerDirectionByte = stream.readU8();
			offset += 1;
		}
		if(hasFlag(flag, UNKNOWNFLAG4)) {
			stream.skip(1);
			offset += 1;
		}
		if(hasFlag(flag, UNKNOWNFLAG5)) {
			stream.skip(2);
			offset += 2;
		}
		if(hasFlag(flag, SPEAKERNAMEFLAG)) {
			speakerStart = offset;
			int length = stream.readU8();
			speakerBytes = new byte[length];
			stream.read(speakerBytes, 0, length);
			offset += 1 + length;
		}
		if(hasFlag(flag, UNKNOWNFLAG6)) {
			stream.skip(1);
			offset += 1;
		}
		if(hasFlag(flag, STRINGFLAG)) {
			int count = stream.readU8();
			offset += 1;
			for(int i = 0; i < count; i++) {
				if(hasFlag(flag, UNKNOWNSUBFLAG2)) {
					offset += skipArrayLength(stream);
				}
				stream.skip(2);
				offset += 2;
			}
			if(hasFlag(flag, UNKNOWNSUBFLAG3)) {
				stream.skip(2 + 2);
				offset += 2 + 2;
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG7)) {
			stream.skip(1);
			offset += 1;
		}
		if(hasFlag(flag, UNKNOWNFLAG8)) {
			int count = stream.readU8();
			stream.skip(4 * count);
			offset += 1 + 4 * count;
		}
		
		ExtraStringConvoData newBlock = new ExtraStringConvoData(ConvoMagic.DIALOGUE, start, fullBlockLength, textStart, speakerStart,
				textBytes, speakerBytes, speakerDirectionByte);
		
		blockList.add(newBlock);
		checkIfStringExists(newBlock);
		return offset;
	}
	
	private int parseNonDialogue(IntByteArrayInputStream stream, int offset, List<ConvoSubBlockData> blockList) {
		int UNKNOWNFLAG1 = 0x3;
		int UNKNOWNFLAG2 = 1 << 6;
		int UNKNOWNFLAG3 = 1 << 7;
		int UNKNOWNFLAG4 = 1 << 2;
		
		//starts at full length
		int start = offset - 1;
		
		int fullBlockLength = stream.readU16();
		int mysteryByte = stream.readU8();
		if(mysteryByte != 1) {
			System.out.println(fileName + " has non one count? byte in nondialogue block");
		}
		offset += 3;
		
		int textStart = offset;
		int textLength = stream.readU8();
		byte[] textBytes = new byte[textLength];
		stream.read(textBytes, 0, textLength);
		offset += 1 + textLength;
		
		int flag = stream.readU8() | 0xFF00;
		offset++;
		
		if(hasFlag(flag, UNKNOWNFLAG1)) {
			stream.skip(2);
			offset += 2;
			offset += skipArrayLength(stream); //sound effect string
			if(hasFlag(flag, UNKNOWNFLAG4)) {
				stream.skip(2);
				offset += 2;
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG2)) {
			stream.skip(2);
			offset += 2;
		}
		if(hasFlag(flag, UNKNOWNFLAG3)) {
			stream.skip(1);
			offset += 1;
		}
		
		ConvoSubBlockData data = new ConvoSubBlockData(ConvoMagic.NONDIALOGUE, start, fullBlockLength, textStart, textBytes);
		blockList.add(data);
		checkIfStringExists(data);
		
		return offset;
	}
	
	private int parseTextEntry(IntByteArrayInputStream stream, int offset, List<ConvoSubBlockData> blockList) {
		final int UNKNOWNFLAG = 1 << 0;
		
		byte[] answerBytes;
		byte[] descriptionBytes = null;
		int answerLength = 0;
		int answerStart = 0;
		int descriptionLength = -1;
		int descriptionStart = -1;
		
		int start = offset - 1;
		int fullBlockLength = stream.readU16();
		offset += 2;
		
		//TODO: may want some special handling for the japanese ver, which seems to use 0A for a formatting thing
		int mysteryVal = stream.readU8(); //generally 0x07, but
		offset++;
		if(mysteryVal != 0x07) {
			System.out.println(Integer.toHexString(mysteryVal) + " text entry with " + fullBlockLength + " length");
			if(fullBlockLength != 2) {
				
			}
			stream.skip(fullBlockLength - 1);
			offset += fullBlockLength - 1;
			return offset;
		}
		
		int flag = stream.readU8();
		offset++;
		if(hasFlag(flag, UNKNOWNFLAG)) {
			int count = stream.readU8();
			stream.skip(count * 4);
			offset += 1 + count * 4;
		}
		
		int stringCount = stream.readU8();
		offset++;
		
		//some text entries only have the answer with a generic "enter the answer" prompt and no description
		answerStart = offset;
		answerLength = stream.readU16();
		answerBytes = new byte[answerLength];
		stream.read(answerBytes, 0, answerLength);
		offset += 2 + answerLength;
		
		if(stringCount == 2) {
			descriptionStart = offset;
			descriptionLength = stream.readU16();
			descriptionBytes = new byte[descriptionLength];
			stream.read(descriptionBytes, 0, descriptionLength);
			offset += 2 + descriptionLength;
		}
		if(stringCount > 2) {
			System.out.println(fileName + " has text entry with 3+ strings");
		}
		
		ExtraStringConvoData data = new ExtraStringConvoData(ConvoMagic.TEXTENTRY, start, fullBlockLength, descriptionStart, answerStart, 
				descriptionBytes, answerBytes);
		blockList.add(data);
		
		return offset;
	}
	
	private int parseMultipleChoiceOptions(IntByteArrayInputStream stream, int offset, List<ConvoSubBlockData> blockList) {
		List<byte[]> answersBytes = new ArrayList<byte[]>();
		
		int start = offset - 1;
		int fullBlockLength = stream.readU16();
		stream.skip(1);
		int count = stream.readU8();
		offset += 4;
		
		int choicesStart = offset;
		
		if(count > 3) {
			System.out.println(count + " multiple choice");
		}
		
		for(int i = 0; i < count; i++) {
			int answerLength = stream.readU16();
			byte[] array = new byte[answerLength];
			stream.read(array, 0, answerLength);
			answersBytes.add(array);
			offset += 2 + answerLength;
		}
		stream.skip(2);
		offset += 2;
		
		MultipleChoiceConvoData data = new MultipleChoiceConvoData(ConvoMagic.MULTIPLECHOICE, start, fullBlockLength, choicesStart, answersBytes);
		blockList.add(data);
		return offset;
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
	
	public File getOriginalFile() {
		return originalFile;
	}
}
