package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

//handled separate from other dats in order to combine the strings together
public class ItemLinkInfoParser extends DatParser {
	private Map<String, ArrayList<Integer>> stringMap = new LinkedHashMap<String, ArrayList<Integer>>();
	
	public ItemLinkInfoParser(File file) {
		byte[] fullFileBytes;
		int fileSize;
		type = DatType.ITEMLINKINFO;
		int textLength = type.entryLength();
		
		try {
			FileInputStream input = new FileInputStream(file);
			fileSize = (int) Files.size(file.toPath());
			fullFileBytes = new byte[fileSize];
			input.read(fullFileBytes);
			input.close();
			entryCount = fileSize / textLength;
		} 
		catch (FileNotFoundException e) {
			return;
		}
		catch (IOException e) {
			return;
		}
		
		originalFile = file;
		
		byte[] line = new byte[textLength];
		for(int i = 0; i < entryCount; i++) {
			line = Arrays.copyOfRange(fullFileBytes, i, i + textLength);
			String string = "";
			try {
				string = new String(line, "Shift-JIS");
			}
			catch(UnsupportedEncodingException e) {
			}
			
			//remove trailing 0s, readd them at write
			string = string.replace((char) 0x0, (char) 0x20).trim();
			
			if(!stringMap.containsKey(string)) {
				stringMap.put(string, new ArrayList<Integer>());
			}
			stringMap.get(string).add(i);
		}
	}
	
	public Map<String, ArrayList<Integer>> getMap() {
		return stringMap;
	}
}
