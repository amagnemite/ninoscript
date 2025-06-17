package ninoscript;

import java.util.Map;

import javax.swing.JPanel;

@SuppressWarnings("serial")
public abstract class DataPanel extends JPanel {
	
	protected Map<String, Integer> currentFontMap = null;
	
	public abstract void loadStrings(ConvoSubBlockData currentBlock, Map<String, Integer> currentFontMap);
	public abstract void saveStrings(ConvoSubBlockData currentBlock);
	public abstract void loadOriginalString(ConvoSubBlockData currentBlock);
	public abstract void removeStringFormatting(ConvoSubBlockData currentBlock);
	public abstract void clearComponents();
	
	protected String stripFormatting(String string) {
		String newString = null;
		int stringIndex = 0;
		
		while(stringIndex != -1) { //furigana first
			stringIndex = string.indexOf('<');
			int colonIndex = string.indexOf(':', stringIndex);
			
			if(stringIndex != -1) {
				newString = string.substring(stringIndex + 1, colonIndex) + string.substring(string.indexOf('>') + 1);
				if(stringIndex != 0) { //if first char isn't <, append the extra chars
					newString = string.substring(0, stringIndex) + newString;
				}
				string = newString;
			}
		}
		stringIndex = 0;
		while(stringIndex != -1) { //does opening and closing color tags separately
			stringIndex = string.indexOf('{'); //
			int closeBraceIndex = string.indexOf('}', stringIndex);
			if(stringIndex != -1) {
				newString = string.substring(closeBraceIndex + 1);
				
				if(stringIndex != 0) {
					newString = string.substring(0, stringIndex) + newString;
				}
				string = newString;
			}
		}
		return string;
	}
	
	protected int getPixelLength(String string) {
		int pixelLen = 0;
		for(int i = 0; i < string.length(); i++) {
			String chara = string.substring(i, i+1);
			if(currentFontMap.containsKey(chara)) {
				pixelLen += currentFontMap.get(chara);
			}
		}
		return pixelLen;
	}
	
	protected void setCurrentFontMap(Map<String, Integer> currentFontMap) {
		this.currentFontMap = currentFontMap;
	}
}
