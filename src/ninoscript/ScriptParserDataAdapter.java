package ninoscript;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ninoscript.ConvoSubBlockData.ExtraStringConvoData;
import ninoscript.ConvoSubBlockData.MultipleChoiceConvoData;
import ninoscript.ScriptParser.ConvoMagic;

//contains all of the files that are loaded and the relevant current data
public class ScriptParserDataAdapter extends DataAdapter {
	private Map<String, ScriptParser> scriptMap = new LinkedHashMap<String, ScriptParser>();
	private ScriptParser currentScript = null;
	private Conversation currentConvo;
	private ConvoSubBlockData currentBlock;
	
	public String addFile(File file) {
		ScriptParser script = new ScriptParser(file);
		String string = script.toString();
		
		scriptMap.put(string, script); //will overwrite any existing instance
		
		currentScript = script;
		return string;
	}
	
	public List<String> generateIDList() {
		List<String> list = new ArrayList<String>();
		List<Integer> usedIDs = currentScript.getUsedConvoIDs();
		
		for(Entry<Integer, Conversation> entry : currentScript.getConvoMap().entrySet()) {
			String key = entry.getKey().toString();
			if(usedIDs.contains(entry.getValue().getId())) {
				list.add(key);
			}
			else {
				list.add(key + " (UNUSED)");
			}
		}
		return list;
	}
	
	public Map<String, ScriptParser> getScriptMap() {
		return scriptMap;
	}
	
	public void resetCurrentData() {
		if(currentScript != null) {
			for(Conversation convo : currentScript.getConvoMap().values()) {
				for(ConvoSubBlockData block : convo.getBlockList()) {
					block.resetNewStrings();
				}
			}
		}
	}

	public ScriptParser getCurrentScript() {
		return currentScript;
	}
	
	public void setCurrentScript(ScriptParser sp) {
		currentScript = sp;
	}
	
	public void updateCurrentScript(String string) {
		currentScript = scriptMap.get(string);
	}
	
	public int getMaxBlocks() {
		if(currentConvo == null) {
			return 0;
		}
		else {
			return currentConvo.getBlockList().size() - 1;
		}
	}
	
	public Conversation getCurrentConversation() {
		return currentConvo;
	}
	
	public void updateCurrentConversation(int index) {
		currentConvo = currentScript.getConvoMap().get(index);
	}
	
	public ConvoSubBlockData getCurrentConvoBlock() {
		return currentBlock;
	}
	
	public void writeNewMainString(String newString) {
		if(currentBlock == null) {
			return;
		}
		
		if(currentBlock.getSharedStringList() != null) { //multiple choice doesn't have as many shared strings
			for(ConvoSubBlockData block : currentBlock.getSharedStringList()) {
				block.setNewTextString(newString);
			}
		}
		else {
			currentBlock.setNewTextString(newString);
		}
	}
	
	public void writeNewMainExtraString(String newString) {
		//for now keep speakers separate, but i think most of them are the same
		if(currentBlock.hasExtraString()) {
			((ExtraStringConvoData) currentBlock).setNewExtraInfoString(newString);
		}
	}
	
	public void updateCurrentConvoBlock(int index) {		
		currentBlock = currentConvo == null ? null : currentConvo.getBlockList().get(index);
	}
	
	public String getOriginalMainString() {
		return currentBlock.getTextString();
	}
	
	public List<String> getOriginalStrings() {
		if(currentBlock.getClass() != MultipleChoiceConvoData.class) {
			return null;
		}
		MultipleChoiceConvoData block = (MultipleChoiceConvoData) currentBlock;
		return block.getOriginalStrings();
	}
	
	public List<String> getNewStrings() {
		if(currentBlock.getClass() != MultipleChoiceConvoData.class) {
			return null;
		}
		MultipleChoiceConvoData block = (MultipleChoiceConvoData) currentBlock;
		return block.getNewStrings();
	}
	
	public void writeFile() {
		//byte length of the string length (byte/short)
		final int DIALOGUEBYTELENGTH = 2;
		final int NONDIALOGUEBYTELENGTH = 1;
		final int TEXTENTRYBYTELENGTH = 2;
		final int MULTIPLECHOICEBYTELENGTH = 2;
		final int SPEAKERBYTELENGTH = 1;
		final byte[] CONVERSATIONMARKER = {0x0A, 0x09, 0x19, 0x00};
		
		File originalFile = currentScript.getOriginalFile();
		File tempFile;
		File backupFile = new File(originalFile.getAbsolutePath() + ".bak");
		FileOutputStream fw;
		int originalFileIndex = 0;
		byte[] fullFileBytes = currentScript.getFullFileBytes();
		
		//write gets upset if >255 and need to pad 00s anyway
		ByteBuffer fourByteBuffer = ByteBuffer.allocate(4);
		fourByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		ByteBuffer twoByteBuffer = ByteBuffer.allocate(2);
		twoByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		try {
			tempFile = File.createTempFile(originalFile.getName(), ".tmp", originalFile.getParentFile());
			tempFile.deleteOnExit();
			fw = new FileOutputStream(tempFile);
		
			for(Conversation convo : currentScript.getConvoMap().values()) {
				ByteArrayOutputStream convoStream = new ByteArrayOutputStream();
				int headerLength = convo.getStartOffset() != 0 ? 12 : 4; //id + length + magic, or just magic for single convo
				int convoLength = convo.getLength();
				
				//write anything non text up to the id of this convo
				//this writes everything else in the file for the first convo
				fw.write(fullFileBytes, originalFileIndex, convo.getStartOffset() - originalFileIndex);
				originalFileIndex += (convo.getStartOffset() + headerLength) - originalFileIndex; //skip id + length + magic
				
				for(ConvoSubBlockData block : convo.getBlockList()) {
					byte[] mainStringArray;
					byte[] extraInfoArray = null;
					ByteArrayOutputStream blockStream = new ByteArrayOutputStream();
					ConvoMagic magic = block.getMagic();
					int lengthSize = DIALOGUEBYTELENGTH;
					int blockOffset = 0;
					int firstTextStart = magic == ConvoMagic.TEXTENTRY ? ((ExtraStringConvoData) block).getExtraInfoStartOffset()
							: block.getTextStartOffset();
					int blockLength = block.getOldFullBlockLength();
					
					//write any non text blocks that occur before this one
					convoStream.write(fullFileBytes, originalFileIndex, block.getStartOffset() - originalFileIndex);
					originalFileIndex += block.getStartOffset() - originalFileIndex;
						
					try {
						//combine new strings with block
						int blockPostLengthOffset = block.getStartOffset() + 3; //3 = id + two byte overall length
						blockStream.write(fullFileBytes, blockPostLengthOffset, firstTextStart - blockPostLengthOffset); //get bytes between start and string
						blockOffset = firstTextStart;
						
						switch(magic) {
							case NONDIALOGUE:
								lengthSize = NONDIALOGUEBYTELENGTH;
							case DIALOGUE:				
								mainStringArray = block.getNewTextString().getBytes("Shift-JIS");
								if(lengthSize == NONDIALOGUEBYTELENGTH) {
									blockStream.write(Integer.valueOf(mainStringArray.length).byteValue());
								}
								else {
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
								}
								blockStream.writeBytes(mainStringArray);
								blockOffset += block.getOldTextLength() + lengthSize;
								
								if(block.hasExtraString()) {
									extraInfoArray = ((ExtraStringConvoData) block).getNewExtraInfoString().getBytes("Shift-JIS");
									blockStream.write(fullFileBytes, blockOffset, ((ExtraStringConvoData) block).getExtraInfoStartOffset() - blockOffset); //everything in between main and speaker
									blockStream.write(Integer.valueOf(extraInfoArray.length).byteValue());
									blockStream.writeBytes(extraInfoArray);
									blockOffset = ((ExtraStringConvoData) block).getExtraInfoStartOffset() + ((ExtraStringConvoData) block).getOldExtraInfoLength() + SPEAKERBYTELENGTH;
								}
								break;
							case TEXTENTRY:	
								extraInfoArray = ((ExtraStringConvoData) block).getNewExtraInfoString().getBytes("Shift-JIS");
								blockStream.writeBytes(twoByteBuffer.putShort(0, (short) extraInfoArray.length).array());
								blockStream.writeBytes(extraInfoArray);
								blockOffset += ((ExtraStringConvoData) block).getOldExtraInfoLength() + TEXTENTRYBYTELENGTH;
								
								if(block.hasMainString()) { //text entry strings are end to end
									mainStringArray = block.getNewTextString().getBytes("Shift-JIS");
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
									blockStream.writeBytes(mainStringArray);
									blockOffset = block.getTextStartOffset() + block.getOldTextLength() + TEXTENTRYBYTELENGTH;
								}
								break;
							case MULTIPLECHOICE:
								int stringCount = 0;
								for(String string : ((MultipleChoiceConvoData) block).getNewStrings()) {
									mainStringArray = string.getBytes("Shift-JIS");
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
									blockStream.writeBytes(mainStringArray);
									stringCount++;
								}
								blockOffset = block.getTextStartOffset() + stringCount * MULTIPLECHOICEBYTELENGTH
										+ ((MultipleChoiceConvoData) block).getOriginalTotalStringsLength();
								break;
						}
						
					}
					catch (UnsupportedEncodingException e) {
					}
					
					int bytesRead = blockOffset - (block.getStartOffset() + 3);
					if(bytesRead < blockLength) {
						blockStream.write(fullFileBytes, blockOffset, blockLength - bytesRead);
					}
					
					//should have all the bytes except for the block type and the total blockLength
					convoStream.write(block.getMagic().getValue());
					convoStream.writeBytes(twoByteBuffer.putShort(0, (short) blockStream.size()).array());
					blockStream.writeTo(convoStream);
					originalFileIndex += (blockLength + 3);
				}
				
				int bytesRead = originalFileIndex - (convo.getStartOffset() + headerLength);
				if(bytesRead < convoLength) { //write extra non text data
					convoStream.write(fullFileBytes, originalFileIndex, convoLength - bytesRead);
					originalFileIndex += (convoLength - bytesRead);
				}	
				
				//now should have everything but the convo header stream
				if(convo.getStartOffset() != 0) {
					fw.write(fourByteBuffer.putInt(0, convo.getId()).array());
					int totalSize = convoStream.size() + 4; //accounting for magic now
					fw.write(fourByteBuffer.putInt(0, totalSize).array());
				}
				fw.write(CONVERSATIONMARKER);
				convoStream.writeTo(fw);
			}
		
			//write anything else that's left
			if(originalFileIndex < fullFileBytes.length) {
				fw.write(fullFileBytes, originalFileIndex, fullFileBytes.length - originalFileIndex);
			}
			fw.close();
			if(backupFile.exists()) {
				backupFile.delete();
			}
			originalFile.renameTo(backupFile);
			tempFile.renameTo(originalFile);
			
			ScriptParser script = new ScriptParser(originalFile); //file objs are immutable and remain linked to the same path
			scriptMap.put(currentScript.toString(), script);
			currentScript = script;
		}
		catch (IOException e) {
		}
	}

	@Override
	public String getNewMainString(String key) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void writeNewExtraString(String string) {
		if(currentBlock != null && currentBlock.getClass() == ExtraStringConvoData.class) {
			((ExtraStringConvoData) currentBlock).setNewExtraInfoString(string);
		}
	}
}
