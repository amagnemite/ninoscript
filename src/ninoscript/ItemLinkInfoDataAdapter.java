package ninoscript;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import ninoscript.DatParser.DatType;

public class ItemLinkInfoDataAdapter extends DataAdapter {
	//deals with both files at once instead of one file at a time unlike the other two
	private Map<String, String> strings = new LinkedHashMap<String, String>(); //old string, new string
	private Map<String, File> loadedFiles = new HashMap<String, File>();
	private Map<String, ItemLinkInfoParser> parsers = new HashMap<String, ItemLinkInfoParser>();
	
	private String currentOldString = null;
	
	public void addFile(File file) {
		//TODO: add a way to reload files and reset their keyvals
		String fileName = file.getName();
		if(!loadedFiles.containsKey(fileName)) {
			loadedFiles.put(fileName, file);
			
			ItemLinkInfoParser parser = new ItemLinkInfoParser(file);
			parsers.put(fileName, parser);
			
			for(String string : parser.getMap().keySet()) {
				if(!strings.containsKey(string)) {
					strings.put(string, string);
				}
			}			
		}
	}

	public void resetCurrentData() {
		for(String string : strings.keySet()) {
			strings.put(string, string);
		}
	}
	
	public int getMaxBlocks() {
		return strings.size() - 1;
	}

	public void updateCurrentConvoBlock(int index) {
		currentOldString = (String) strings.keySet().toArray()[index];
	}

	public void writeFile() {
		Map<String, File> reloadMap = new HashMap<String, File>();
		
		for(String fileName : loadedFiles.keySet()) {
			File originalFile = loadedFiles.get(fileName);
			File tempFile;
			File backupFile = new File(originalFile.getAbsolutePath() + ".bak");
			ItemLinkInfoParser parser = parsers.get(fileName);
			Map<Integer, byte[]> fileOrder = new TreeMap<Integer, byte[]>();

			for(Entry<String, ArrayList<Integer>> entry : parser.getMap().entrySet()) {
				String newString = strings.get(entry.getKey());
				byte[] stringBytes = null;
				
				try {
					stringBytes = newString.getBytes("Shift-JIS");
					//prevent writes that are too long or pads as necessary
					stringBytes = Arrays.copyOf(stringBytes, DatType.ITEMLINKINFO.entryLength());
				}
				catch (UnsupportedEncodingException e) {
				}
				
				for(int index : entry.getValue()) {
					//fill arraylist with strings in file order
					fileOrder.put(index, stringBytes);
				}
			}
			
			try {
				tempFile = File.createTempFile(originalFile.getName(), ".tmp", originalFile.getParentFile());
				tempFile.deleteOnExit();
				FileOutputStream fw = new FileOutputStream(tempFile);
				for(byte[] bytes : fileOrder.values()) {
					fw.write(bytes);
				}
				fw.close();
				if(backupFile.exists()) {
					backupFile.delete();
				}
				originalFile.renameTo(backupFile);
				tempFile.renameTo(originalFile);
				reloadMap.put(fileName, originalFile);		
			}
			catch (IOException e) {
			}
		}
		strings.clear();
		loadedFiles.clear();
		parsers.clear();
		for(Entry<String, File> entry : reloadMap.entrySet()) {
			addFile(entry.getValue());
		}
	}

	public String getOriginalMainString() {
		return currentOldString;
	}
	
	public String getNewMainString(String key) {
		return strings.get(key);
	}

	public void writeNewMainString(String newString) {
		if(currentOldString != null) {
			strings.put(currentOldString, newString);
		}
	}
	
	public Set<String> getLoadedFilenames() {
		return loadedFiles.keySet();
	}
}
