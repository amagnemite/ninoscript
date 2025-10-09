package ninoscript;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ninoscript.DatParser.DatType;
import ninoscript.DatParser.StringPair;

public class DatParserDataAdapter extends DataAdapter {
	private Map<String, DatParser> datMap = new LinkedHashMap<String, DatParser>();
	private DatParser currentScript = null;
	private StringPair currentStringPair = null;

	public void addFile(File file, DatType type) {
		DatParser parser = new DatParser(file, type);
		datMap.put(file.getName(), parser);
		currentScript = parser;
	}

	public void resetCurrentData() {
		if(currentScript != null) {
			for(StringPair pair : currentScript.getStringList()) {
				pair.setNewString(pair.getOriginalString());
			}
		}
	}
	
	public void setCurrentScript(int index) {
		currentScript = datMap.get(index);
	}
	
	public int getMaxBlocks() {
		if(currentScript == null) {
			return 0;
		}
		else {
			return currentScript.getStringList().size() - 1;
		}
	}
	
	public void updateCurrentConvoBlock(int index) {
		currentStringPair = currentScript.getStringList().get(index);
	}

	public void writeFile() {
		File originalFile = currentScript.getOriginalFile();
		File tempFile;
		File backupFile = new File(originalFile.getAbsolutePath() + ".bak");
		DatType type = currentScript.getType();
		int entryCount = currentScript.getEntryCount();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		if(type.hasEntryCount()) {
			ByteBuffer twoByteBuffer = ByteBuffer.allocate(2);
			twoByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			
			out.writeBytes(twoByteBuffer.putShort(0, (short) entryCount).array());
		}
		
		if(type.stringLength() != 0) { //need to fetch the non string bytes from the original
			int fileSize;
			int stringLength = type.stringLength();
			byte[] fullFileBytes = null;
			byte[] stringBytes = null;
			byte[] extraDataBytes = new byte[type.entryLength() - type.stringLength() - DatParser.LONGNAMEOFFSETFROMEND];
			
			try {
				FileInputStream input = new FileInputStream(originalFile);
				fileSize = (int) Files.size(originalFile.toPath());
				fullFileBytes = new byte[fileSize];
				input.read(fullFileBytes);
				input.close();
			}
			catch (IOException e) {
				return;
			}
			
			ByteArrayInputStream originalStream = new ByteArrayInputStream(fullFileBytes);
			if(type.hasEntryCount()) {
				originalStream.skip(2);
			}
			
			for(StringPair pair : currentScript.getStringList()) {	
				String newString = pair.getNewString();
				try {
					stringBytes = newString.getBytes("Shift-JIS");
					//prevent writes that are too long or pads as necessary
					stringBytes = Arrays.copyOf(stringBytes, DatParser.LONGNAMEOFFSETFROMEND);
					
					out.write(stringBytes, 0, stringLength); //regular short ver first
					originalStream.skip(stringLength);
					originalStream.read(extraDataBytes);
					out.write(extraDataBytes);
					out.write(stringBytes);
					originalStream.skip(DatParser.LONGNAMEOFFSETFROMEND); //for now just use the same name between both
				}
				catch (IOException e) {
				}
			}
		}
		else {
			byte[] stringBytes = null;
			for(StringPair pair : currentScript.getStringList()) {
				String newString = pair.getNewString();
				try {
					stringBytes = newString.getBytes("Shift-JIS");
					//prevent writes that are too long or pads as necessary
					stringBytes = Arrays.copyOf(stringBytes, type.entryLength());
				}
				catch (UnsupportedEncodingException e) {
				}
				out.writeBytes(stringBytes);
			}
		}
		
		if(type.isEncoded()) {
			
		}
		
		try {
			tempFile = File.createTempFile(originalFile.getName(), ".tmp", originalFile.getParentFile());
			tempFile.deleteOnExit();
			FileOutputStream fw = new FileOutputStream(tempFile);
			out.writeTo(fw);
			fw.close();
			if(backupFile.exists()) {
				backupFile.delete();
			}
			originalFile.renameTo(backupFile);
			tempFile.renameTo(originalFile);
			
			DatParser script = new DatParser(originalFile, type); //originalFile points to the new file written
			datMap.put(originalFile.getName(), script);
			currentScript = script;
		}
		catch (IOException e) {
		}
	}

	public String getOriginalMainString() {
		return currentStringPair.getOriginalString();
	}

	public String getNewMainString(String key) {
		return currentStringPair.getNewString();
	}

	public void writeNewMainString(String newString) {
		if(currentStringPair != null) {
			currentStringPair.setNewString(newString);
		}
	}
}
