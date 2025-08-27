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
	
	private static final int EVENTSTATEBLOCK = 1;
	private static final int MAPENTITYBLOCK = 5;
	
	private static final int INTERNALSCRIPTNAMEOFFSET = 8;
	private static final int BLOCKS = 8;
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
	
	//TODO: make a separate constructor for single convo files
	
	public ScriptParser(File file) {
		int scriptNameLength = 0;
		byte[] nameBytes = null;
		ConvoSubBlockData data;
		ByteBuffer sectionOne = null;
		ByteBuffer sectionFive = null;
		ByteBuffer sectionSeven = null;
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
		
		fileName = file.getName();
		
		int offset = !isSingleConvo ? 4 : 0;
		if(!isSingleConvo) { //parse section zero
			scriptNameLength = fullFileBytes[INTERNALSCRIPTNAMEOFFSET] & 0xFF;
			nameBytes = Arrays.copyOfRange(fullFileBytes, INTERNALSCRIPTNAMEOFFSET + 1, INTERNALSCRIPTNAMEOFFSET + scriptNameLength + 1);
			
			try {
				scriptName = new String(nameBytes, "Shift-JIS");
			}
			catch (UnsupportedEncodingException e) {
			}
			
			for(int j = 0; j < BLOCKS - 1; j++) { //4 after initial magic
				int length = (fullFileBytes[offset] & 0xFF) | (fullFileBytes[offset + 1] & 0xFF) << 8 |
						(fullFileBytes[offset + 2] & 0xFF) << 16 | (fullFileBytes[offset + 3] & 0xFF) << 24;
				
				if(j == EVENTSTATEBLOCK) {
					sectionOne = ByteBuffer.allocate(length);
					sectionOne.order(ByteOrder.LITTLE_ENDIAN);
					sectionOne.put(fullFileBytes, offset + 4, length);
					sectionOne.position(0); //pos gets incremented after puts
				}
				else if(j == MAPENTITYBLOCK) {
					sectionFive = ByteBuffer.allocate(length);
					sectionFive.order(ByteOrder.LITTLE_ENDIAN);
					sectionFive.put(fullFileBytes, offset + 4, length);
					sectionFive.position(0); //pos gets incremented after puts
				}
				offset += 4 + length;
			}
		}
		
		//section 7/convo section doesn't have an overall length
		sectionSeven = ByteBuffer.allocate(fullFileBytes.length - offset);
		sectionSeven.order(ByteOrder.LITTLE_ENDIAN);
		sectionSeven.put(fullFileBytes, offset, fullFileBytes.length - offset);
			
		//TODO: check to make sure all (non single convo) scripts can have a 5/7
		
		if(!isSingleConvo) {
			int flag;
			int structCount = sectionOne.get() & 0xFF;
			
			for(int i = 0; i < structCount; i++) {
				sectionOne.position(sectionOne.position() + 2); //length and id
				flag = sectionOne.get() & 0xFF;
				if(flag != 0) {
					if(hasFlag(flag, SECTIONONEFLAG1)) {
						int val = sectionOne.get() & 0xFF;
						if(val == 0) {
							int stringLength = sectionOne.get() & 0xFF;
							sectionOne.position(sectionOne.position() + stringLength);
							sectionOne.position(sectionOne.position() + 1);
						}
					}
					if(hasFlag(flag, SECTIONONEFLAG2)) {
						int arrayLength = sectionOne.get() & 0xFF;
						sectionOne.position(sectionOne.position() + arrayLength);
						int val = sectionOne.get() & 0xFF;
						if((hasFlag(val, SECTIONONEFLAG3))) {
							sectionOne.position(sectionOne.position() + 1);
						}
						if((hasFlag(val, SECTIONONEFLAG4))) {
							sectionOne.position(sectionOne.position() + 2);
						}
						if((hasFlag(val, SECTIONONEFLAG5))) {
							sectionOne.position(sectionOne.position() + 2);
						}
					}
				}
				sectionOne.position(sectionOne.position() + SECTIONONEPRECONVOCOUNT);
				//System.out.println("section one convo ids");
				getConvoIDs(sectionOne);
				//System.out.println("");
			}
			
			int countByte = sectionFive.get() & 0xFF;
		
			for(int j = 0; j < countByte; j++) { //section zero
				getSubFuncOneLength(sectionFive);
				sectionFive.position(sectionFive.position() + BLOCKZEROLENGTH);
			}
				
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section one, npcs
				//gets read again in func but need to ref here, so don't increment pos	
				int firstByte = sectionFive.get(sectionFive.position()) & 0xFF;
				getSubFuncOneLength(sectionFive);
				sectionFive.position(sectionFive.position() + BLOCKONEPRECONVOLENGTH);		
				getMultipleArrayLength(sectionFive, BLOCKONESTRINGCOUNT);			
				getConvoIDs(sectionFive);
				sectionFive.position(sectionFive.position() + BLOCKONEPOSTCONVOLENGTH);
				
				flag = sectionFive.getInt();
				if(hasFlag(flag, 1 << 10)) {
					int loopCount = sectionFive.get() & 0xFF;
					getMultipleArrayLength(sectionFive, loopCount);
					if(hasFlag(flag, 1 << 11)) {
						sectionFive.position(sectionFive.position() + 1);
					}
				}
				if(hasFlag(flag, 1 << 14)) {
					sectionFive.position(sectionFive.position() + 6);
				}
				if(hasFlag(flag, 1 << 3)) {
					sectionFive.position(sectionFive.position() + 8);
				}
				if(hasFlag(flag, 1 << 15)) {
					sectionFive.position(sectionFive.position() + 4);
				}
				if(hasFlag(flag, 1 << 5)) {
					getArrayLength(sectionFive);
				}
				if(hasFlag(flag, 1 << 8)) {
					sectionFive.position(sectionFive.position() + 1);
				}
				if(hasFlag(flag, 1 << 9)) {
					sectionFive.position(sectionFive.position() + 4);
				}
				if(hasFlag(flag, 1 << 16)) {
					sectionFive.position(sectionFive.position() + 1);
				}
				if(firstByte == 1) {
					sectionFive.position(sectionFive.position() + 28);
				}
			}
			
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section two, map props/other entities
				parseSubsectionTwo(sectionFive);
			}
			
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section three, adjacent scripts
				getSubFuncTwoLength(sectionFive);
				getArrayLength(sectionFive);
				sectionFive.position(sectionFive.position() + 1);
				flag = sectionFive.get() & 0xFF;
				if(hasFlag(flag, 1 << 3)) {
					sectionFive.position(sectionFive.position() + 1);
				}
				getSubFuncThreeLength(sectionFive);
			}
			
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section four
				getSubFuncTwoLength(sectionFive);
				getConvoIDs(sectionFive);
				sectionFive.position(sectionFive.position() + 2);
				getSubFuncThreeLength(sectionFive);
			}
			
			
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section five
				getSubFuncTwoLength(sectionFive);
				sectionFive.position(sectionFive.position() + 2);
				getSubFuncThreeLength(sectionFive);
				flag = sectionFive.getShort();
				if(hasFlag(flag, SECTIONFIVEFLAG1)) {
					sectionFive.position(sectionFive.position() + 2);
				}
				if(hasFlag(flag, SECTIONFIVEFLAG2)) {
					sectionFive.position(sectionFive.position() + 2);
				}
			}
			
			countByte = sectionFive.get() & 0xFF;
			for(int j = 0; j < countByte; j++) { //section six
				System.out.println(fileName + " has subsection 7 of block 5");
				//note 7 uses 2 count bytes
			}	
		}
		else {
			usedConvoIDs.add(SINGLECONVOID);
		}
		
		sectionSeven.position(0);
		int definitionIndex = 0; //convos as they're ordered in the script, not their id
		int convoID = !isSingleConvo ? sectionSeven.getInt() : SINGLECONVOID;
		
		while(convoID != -1) {
			List<ConvoSubBlockData> blockList = new ArrayList<ConvoSubBlockData>();
			int startPos = sectionSeven.position();
			int convoLength = !isSingleConvo ? sectionSeven.getInt() : fullFileBytes.length - 4;
			int finalPos = sectionSeven.position() + convoLength;
			
			if(convoLength == 0) { //apparently there's null convo blocks?
				convoID = sectionSeven.getInt();
				continue;
			}
			sectionSeven.position(sectionSeven.position() + 4); //magic 0A 09 19 00
			
			while(sectionSeven.position() < finalPos) {
				int subBlockType = sectionSeven.get() & 0xFF;
				
				if(!TWOBYTETEXTTYPES.contains(subBlockType)) {
					if(subBlockType == UNKNOWNTWOBYTETYPE) { //doesn't seem to contain strings, but it has a 2 byte length
						sectionSeven.position(sectionSeven.getShort() + sectionSeven.position());
						continue;
					}
					else if(subBlockType == CONDITIONALCONVOTYPE) {
						if(!connectedIDs.containsKey(convoID)) {
							connectedIDs.put(convoID, new ArrayList<Integer>());
						}
						sectionSeven.position(sectionSeven.position() + 2); //length + fixed 1?
						int chainedID = sectionSeven.getInt();
						//System.out.println("0x21 " + chainedID);
						connectedIDs.get(convoID).add(chainedID);
					}
					else {
						int len = sectionSeven.get() & 0xFF;
						sectionSeven.position(sectionSeven.position() + len);
						continue;
					}
				}
				
				switch(subBlockType) {
					case 0x05: //regular dialogue		
						data = parseTextBlock(sectionSeven, offset);
						blockList.add(data);
						checkIfStringExists(data);
						break;
					case 0x26: //non conversation text (sidequest prompts, etc)		
						data = parseNonDialogue(sectionSeven, offset);
						blockList.add(data);
						checkIfStringExists(data);
						break;
					case 0x31: //text entry puzzle				
						data = parseTextEntry(sectionSeven, offset);
						if(data != null) { //at least one instance where no data?
							blockList.add(data);
						}
						break;
					case 0x29:
						//TODO: research this
						System.out.println(fileName + " " + convoID + " has 0x29 sub");
						sectionSeven.position(sectionSeven.position() + sectionSeven.getShort());
						break;
					case 0x11: //dialogue options
						data = parseMultipleChoiceOptions(sectionSeven, offset);
						blockList.add(data);
						break;
				}
			}
			if(blockList.size() > 0) {
				convoMap.put(definitionIndex, new Conversation(convoID, offset + startPos, convoLength, blockList));
				definitionIndex++;
			}
			convoID = !isSingleConvo ? sectionSeven.getInt() : -1;
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
		final int FIXEDBYTES = 14;
		buffer.position(buffer.position() + 1); //firstByte
		
		getSubFuncTwoLength(buffer);
		
		buffer.position(buffer.position() + FIXEDBYTES);
	}
	
	private void getSubFuncTwoLength(ByteBuffer buffer) { //some structs call this directly
		final int UNKNOWNFLAG1 = 0x3C;
		final int UNKNOWNFLAG2 = 0x3;
		
		int flagVal = buffer.get() & 0xFF;
		int loopCount = 0;
		
		if(!hasFlag(flagVal, UNKNOWNFLAG1)) {
			if(hasFlag(flagVal, UNKNOWNFLAG2)) {
				loopCount = buffer.get() & 0xFF;
				buffer.position(buffer.position() + loopCount * 8); //read 8 bytes per loop
			}
		}
		else {
			loopCount = buffer.get() & 0xFF;
			getMultipleArrayLength(buffer, loopCount); //read u8 length + array[length] per loop
		}
	}
	
	private void getSubFuncThreeLength(ByteBuffer buffer) {
		final int CASETWOBYTES = 24;
		final int CASEONEBYTES = 16;
		final int CASETHREEBYTES = 12;
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
				int loopCount = buffer.get() & 0xFF;
				buffer.position(buffer.position() + CASETHREEBYTES * (loopCount / 3));
				break;
			case 4:
				buffer.position(buffer.position() + CASEFOURBYTES);
				break;
		}
	}
	
	private void getSubFuncFourLength(ByteBuffer buffer) {
		final int UNKNOWNFLAG1 = 0x81;
		final int UNKNOWNFLAG2 = 1 << 1;
		final int UNKNOWNFLAG3 = 1 << 2;
		final int UNKNOWNFLAG4 = 0x40;
		
		buffer.position(buffer.position() + 4);
		int flag = buffer.get() & 0xFF;
		if(hasFlag(flag, UNKNOWNFLAG1) && hasFlag(flag, UNKNOWNFLAG2)) {
			getArrayLength(buffer);
			if(hasFlag(flag, UNKNOWNFLAG3)) {
				buffer.position(buffer.position() + 1);
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG4)) {
			buffer.position(buffer.position() + 1);
		}
	}
	
	private void getConvoIDs(ByteBuffer buffer) {
		final int EXTRADATABYTES = 12;
		
		int id = buffer.getInt();
		if(id != -1) {
			//System.out.println("convo id: " + id);
			usedConvoIDs.add(id);
		}
		
		int loopCount = buffer.get() & 0xFF;
		for(int j = 0; j < loopCount; j++) {
			buffer.position(buffer.position() + EXTRADATABYTES);
			id = buffer.getInt();
			//System.out.println("extra id: " + id);
			if(!usedConvoIDs.contains(id)) { //block one has some double defs?
				usedConvoIDs.add(id);
			}
		}
	}
	
	private void parseSubsectionTwo(ByteBuffer buffer) {
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
		
		int firstByte = buffer.get(buffer.position()) & 0xFF;
		int flag;
		getSubFuncOneLength(buffer);
		buffer.position(buffer.position() + COMMONREAD);
		
		switch(firstByte) {
			case 0:
				getArrayLength(buffer);
				buffer.position(buffer.position() + ZEROPOSTSTRING);
				break;
			case 1:
			case 2:
				getArrayLength(buffer);
				buffer.position(buffer.position() + ONEPOSTSTRING);
				break;
			case 3:
				buffer.position(buffer.position() + 1);
				getSubFuncFourLength(buffer);
				buffer.position(buffer.position() + THREEPOSTFUNC);
				getConvoIDs(buffer);
				break;
			case 4:
				buffer.position(buffer.position() + 1);
				getSubFuncFourLength(buffer);
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
				getArrayLength(buffer);
				buffer.position(buffer.position() + 4 + 4 + 1);
				getArrayLength(buffer);
				buffer.position(buffer.position() + 1);
				break;
			case 6:
				getArrayLength(buffer);
				buffer.position(buffer.position() + SIXPRECONVO);
				getArrayLength(buffer);
				buffer.position(buffer.position() + 1);
				getConvoIDs(buffer);
				flag = buffer.get() & 0xFF; 
				if(hasFlag(flag, SIXFLAGONE)) {
					buffer.position(buffer.position() + 1);
				}
				if(hasFlag(flag, SIXFLAGTWO)) {
					buffer.position(buffer.position() + 1);
				}
				break;
			case 7:
				System.out.println(fileName + " has section 2 subsection 7");
				break;
			case 8:
				flag = buffer.get() & 0xFF;
				getArrayLength(buffer); //string
				if(hasFlag(flag, 0x2)) {
					getArrayLength(buffer); //string 2
				}
				break;
			case 9:
				buffer.position(buffer.position() + 2);
				getConvoIDs(buffer);
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
				buffer.position(buffer.position() + 1);
				getSubFuncFourLength(buffer);
				buffer.position(buffer.position() + 4); //2 2 byte
				getConvoIDs(buffer);
				break;
			case 0xC:
				buffer.position(buffer.position() + 2); //2 1 byte
				getSubFuncFourLength(buffer);
				buffer.position(buffer.position() + 4); //2 2 byte
				getConvoIDs(buffer);
				break;
			case 0xD:
				buffer.position(buffer.position() + DPREFLAG);
				flag = buffer.get() & 0xFF;
				if(hasFlag(flag, DFLAG)) {
					getMultipleArrayLength(buffer, buffer.get() & 0xFF);
				}
				break;
			case 0xE:
				buffer.position(buffer.position() + 4);
				getArrayLength(buffer);
				flag = buffer.getShort();
				if(hasFlag(flag, 1 << 2)) {
					buffer.position(buffer.position() + 1);
				}
				if(hasFlag(flag, 1 << 3)) {
					buffer.position(buffer.position() + 1);
				}
				if(hasFlag(flag, 1 << 5)) {
					buffer.position(buffer.position() + 2);
				}
				if(hasFlag(flag, 1 << 6)) {
					buffer.position(buffer.position() + 2);
				}
				if(hasFlag(flag, 1 << 7)) {
					buffer.position(buffer.position() + 2);
				}
				if(hasFlag(flag, 1 << 9)) {
					buffer.position(buffer.position() + 4);
				}
				break;
			case 0xF:
				buffer.position(buffer.position() + FREAD);
				break;
			case 0x10:
				System.out.println(fileName + " has section 2 subsection 0x10");
				break;
			case 0x11:
				buffer.position(buffer.position() + ELEVENPRECONVO);
				getConvoIDs(buffer);
				break;
			case 0x12:
				System.out.println(fileName + " has section 2 subsection 0x12");
				break;
			case 0x13:
				System.out.println(fileName + " has section 2 subsection 0x13");
				break;
			case 0x41:
				getArrayLength(buffer);
				getArrayLength(buffer);
				getConvoIDs(buffer);
				buffer.position(buffer.position() + FORTYONEPOSTCONVO);
				break;
			case 0x40:
				getArrayLength(buffer);
				getArrayLength(buffer);
				getConvoIDs(buffer);
				buffer.position(buffer.position() + FORTYPOSTCONVO);
				flag = buffer.getShort();
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
	
	private ConvoSubBlockData parseTextBlock(ByteBuffer buffer, int fullOffset) {
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
		
		//should be pointing at the length already;
		int start = buffer.position() - 1 + fullOffset;
		byte[] textBytes;
		byte[] speakerBytes = null;
		int speakerStart = -1;
		int speakerDirectionByte = -1;
		
		int fullBlockLength = buffer.getShort();
		int mysteryByte = buffer.get() & 0xFF;
		if(mysteryByte != 1) {
			System.out.println(fileName + " has non one count? byte in text block");
		}
		
		int textStart = buffer.position() + fullOffset;
		int textLength = buffer.getShort();
		
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
		if(hasFlag(flag, SPEAKERDIRECTIONFLAG)) {
			speakerDirectionByte = buffer.get() & 0xFF;
		}
		if(hasFlag(flag, UNKNOWNFLAG4)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG5)) {
			buffer.position(buffer.position() + 2);
		}
		if(hasFlag(flag, SPEAKERNAMEFLAG)) {
			speakerStart = buffer.position() + fullOffset;
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
					getArrayLength(buffer);
				}
				buffer.position(buffer.position() + 2);
			}
			if(hasFlag(flag, UNKNOWNSUBFLAG3)) {
				buffer.position(buffer.position() + 2 + 2);
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG7)) {
			buffer.position(buffer.position() + 1);
		}
		if(hasFlag(flag, UNKNOWNFLAG8)) {
			int count = buffer.get() & 0xFF;
			buffer.position(buffer.position() + 4 * count);
		}
	
		return new ExtraStringConvoData(ConvoMagic.DIALOGUE, start, fullBlockLength, textStart, speakerStart,
				textBytes, speakerBytes, speakerDirectionByte);
	}
	
	private ConvoSubBlockData parseNonDialogue(ByteBuffer buffer, int fullOffset) {
		int UNKNOWNFLAG1 = 0x3;
		int UNKNOWNFLAG2 = 1 << 6;
		int UNKNOWNFLAG3 = 1 << 7;
		int UNKNOWNFLAG4 = 1 << 2;
		
		//starts at full length
		int start = buffer.position() - 1 + fullOffset;
		
		int fullBlockLength = buffer.getShort();
		int mysteryByte = buffer.get() & 0xFF;
		if(mysteryByte != 1) {
			System.out.println(fileName + " has non one count? byte in nondialogue block");
		}
		//buffer.position(buffer.position() + 1); //skip the byte between lengths
		int textStart = buffer.position() + fullOffset;
		int textLength = buffer.get() & 0xFF;
		
		byte[] textBytes = new byte[textLength];
		buffer.get(textBytes);
		
		int flag = (buffer.get() & 0xFF) | 0xFF00;
		
		if(hasFlag(flag, UNKNOWNFLAG1)) {
			buffer.position(buffer.position() + 2);
			getArrayLength(buffer); //sound effect string
			if(hasFlag(flag, UNKNOWNFLAG4)) {
				buffer.position(buffer.position() + 2);
			}
		}
		if(hasFlag(flag, UNKNOWNFLAG2)) {
			buffer.position(buffer.position() + 2);
		}
		if(hasFlag(flag, UNKNOWNFLAG3)) {
			buffer.position(buffer.position() + 1);
		}
		
		return new ConvoSubBlockData(ConvoMagic.NONDIALOGUE, start, fullBlockLength, textStart, textBytes);
	}
	
	private ConvoSubBlockData parseTextEntry(ByteBuffer buffer, int fullOffset) {
		final int UNKNOWNFLAG = 1 << 0;
		
		int start = buffer.position() - 1 + fullOffset;
		int fullBlockLength = buffer.getShort();
		byte[] answerBytes;
		byte[] descriptionBytes = null;
		int answerLength = 0;
		int answerStart = 0;
		int descriptionLength = -1;
		int descriptionStart = -1;
		
		//TODO: may want some special handling for the japanese ver, which seems to use 0A for a formatting thing
		int mysteryVal = buffer.get() & 0xFF; //generally 0x07, but
		if(mysteryVal != 0x07) {
			System.out.println(Integer.toHexString(mysteryVal) + " text entry with " + fullBlockLength + " length");
			if(fullBlockLength != 2) {
				
			}
			buffer.position(buffer.position() + (fullBlockLength - 1));
			return null;
		}
		
		int flag = buffer.get() & 0xFF;
		if(hasFlag(flag, UNKNOWNFLAG)) {
			int count = buffer.get() & 0xFF;
			buffer.position(buffer.position() + count * 4);
		}
		
		int stringCount = buffer.get() & 0xFF;
		
		//some text entries only have the answer with a generic "enter the answer" prompt and no description
		answerStart = buffer.position() + fullOffset;
		answerLength = buffer.getShort();
		answerBytes = new byte[answerLength];
		buffer.get(answerBytes);
		
		if(stringCount == 2) {
			descriptionStart = buffer.position() + fullOffset;
			descriptionLength = buffer.getShort();
			descriptionBytes = new byte[descriptionLength];
			buffer.get(descriptionBytes);
		}
		if(stringCount > 2) {
			System.out.println(fileName + " has text entry with 3+ strings");
		}
		
		return new ExtraStringConvoData(ConvoMagic.TEXTENTRY, start, fullBlockLength, descriptionStart, answerStart, 
				descriptionBytes, answerBytes);
	}
	
	private MultipleChoiceConvoData parseMultipleChoiceOptions(ByteBuffer buffer, int fullOffset) {
		List<byte[]> answersBytes = new ArrayList<byte[]>();
		
		int start = buffer.position() - 1 + fullOffset;
		int fullBlockLength = buffer.getShort();
		buffer.position(buffer.position() + 1);
		int count = buffer.get() & 0xFF;
		int choicesStart = buffer.position() + fullOffset;
		
		if(count > 3) {
			System.out.println(count + " multiple choice");
		}
		
		for(int i = 0; i < count; i++) {
			int answerLength = buffer.getShort();
			byte[] array = new byte[answerLength];
			buffer.get(array);
			answersBytes.add(array);
		}
		buffer.position(buffer.position() + 2);
		
		return new MultipleChoiceConvoData(ConvoMagic.MULTIPLECHOICE, start, fullBlockLength, choicesStart, answersBytes);
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
}
