package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

//parses most of the .dat files
public class DatParser {
	public static final int LONGNAMEOFFSETFROMEND = 0x20; //long names are 32 bytes long, placed at the end of the existing data
	protected DatType type;
	protected int entryCount = 0;
	private List<StringPair> strings = new ArrayList<StringPair>(); //can't be map due to duplicate strings
	protected File originalFile;
	
	public enum DatType {
		ITEMLINKINFO (false, false, 0x51, 0x0, "ItemLinkInfo.dat"),
		EQUIPITEMLINKINFO (false, false, 0x51, 0x0, "EquipItemLinkInfo.dat"),
		
		EQUIPITEMINFO (false, false, 0xC4, 0x0, "EquipItemInfo.dat"),
		ITEMINFO (false, false, 0xC4, 0x0, "ItemInfo.dat"),
		SPITEMINFO (false, false, 0xC4, 0x0, "SpItemInfo.dat");
		
		private final boolean hasEntryCount;
		private final boolean isEncoded;
		private final int entryLength;
		private final int stringLength; //for param files that contain string names and non string data
		private final boolean hasLongName;
		private final String fileName;
		DatType(boolean hasEntryCount, boolean isEncoded, int entryLength, int stringLength, String fileName) {
			this.hasEntryCount = hasEntryCount;
			this.isEncoded = isEncoded;
			this.entryLength = entryLength;
			this.stringLength = stringLength;
			this.fileName = fileName;
			hasLongName = stringLength > 0 ? true : false;
		}
		
		public boolean hasEntryCount() {
			return hasEntryCount;
		}
		
		public boolean isEncoded() {
			return isEncoded;
		}
		
		public int entryLength() {
			return entryLength;
		}
		
		public int stringLength() {
			return stringLength;
		}
		
		public boolean hasLongName() {
			return hasLongName;
		}
		
		public String fileName() {
			return fileName;
		}
	}
	
	public DatParser() {
		
	}
	
	public DatParser(File file, DatType type) {
		this.type = type;
		byte[] fullFileBytes;
		int fileSize;
		
		try {
			FileInputStream input = new FileInputStream(file);
			fileSize = (int) Files.size(file.toPath());
			fullFileBytes = new byte[fileSize];
			input.read(fullFileBytes);
			input.close();
		}
		catch (IOException e) {
			return;
		}
		
		originalFile = file;
		
		if(type.isEncoded()) {
			for(int i = 0; i < fileSize; i++) {
				fullFileBytes[i] = (byte) ~fullFileBytes[i];
			}
		}
		
		IntByteArrayInputStream stream = new IntByteArrayInputStream(fullFileBytes);
		
		entryCount = type.hasEntryCount() ? stream.readU16() : fileSize / type.entryLength();
		int bytesToRead = type.stringLength() > 0 ? type.stringLength() : type.entryLength();
		int bytesToSkip = type.stringLength() > 0 ? type.entryLength() - type.stringLength() : 0;
		
		byte[] line = new byte[bytesToRead];
		for(int i = 0; i < entryCount; i++) {
			String string = "";
			try {
				stream.read(line);
				string = new String(line, "Shift-JIS");
			}
			catch (IOException e) {
			}
			
			string = string.replace((char) 0x0, (char) 0x20).trim();
			strings.add(new StringPair(string, string));
			stream.skip(bytesToSkip);
		}
	}
	
	public List<StringPair> getStringList() {
		return strings;
	}
	
	public DatType getType() {
		return type;
	}
	
	public int getEntryCount() {
		return entryCount;
	}
	
	public File getOriginalFile() {
		return originalFile;
	}
	
	public class StringPair {
		private String originalString;
		private String newString;
		
		public StringPair(String originalString, String newString) {
			this.originalString = originalString;
			this.newString = newString;
		}
		
		public String getOriginalString() {
			return originalString;
		}
		
		public String getNewString() {
			return newString;
		}
		
		public void setNewString(String newString) {
			this.newString = newString;
		}
	}
}
