package ninoscript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ItemLinkInfoParser {
	public static final int TEXTLENGTH = 81;
	private int lineCount = 0;
	private Map<String, ArrayList<Integer>> stringMap = new HashMap<String, ArrayList<Integer>>();
	
	public ItemLinkInfoParser(File file) {
		byte[] fullFileBytes;
		int fileSize;
		
		try {
			FileInputStream input = new FileInputStream(file);
			fileSize = (int) Files.size(file.toPath());
			fullFileBytes = new byte[fileSize];
			input.read(fullFileBytes);
			input.close();
		} 
		catch (FileNotFoundException e) {
			return;
		}
		catch (IOException e) {
			return;
		}
		
		byte[] line = new byte[TEXTLENGTH];
		for(int i = 0; i < fileSize; i += TEXTLENGTH) {
			line = Arrays.copyOfRange(fullFileBytes, i, i + TEXTLENGTH);
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
			stringMap.get(string).add(lineCount);
			lineCount++;
		}
	}
	
	public Map<String, ArrayList<Integer>> getMap() {
		return stringMap;
	}
}
