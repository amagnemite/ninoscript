package ninoscript;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//bridge between data objects like scriptparser and regulartextpanel
//stores the currently active string/s
public abstract class DataAdapter {
	public abstract void resetCurrentData();
	public abstract int getMaxBlocks();
	public abstract void updateCurrentConvoBlock(int index);
	public abstract void writeFile();
	public abstract String getOriginalMainString();
	public abstract String getNewMainString(String key);
	public abstract void writeNewMainString(String newString);
	
	public List<String> generateIDList() {
		return new ArrayList<String>(Arrays.asList("0"));
	}
	
	public void updateCurrentScript(String string) {
	}
	
	public Conversation getCurrentConversation() {
		return null;
	}
	
	public void updateCurrentConversation(int index) {
	}
	
	public List<String> getOriginalStrings() {
		return null;
	}
	public List<String> getNewStrings() {
		return null;
	}
	
	public String getOriginalExtraString() {
		return null;
	}
	public void writeNewExtraString(String newString) {
	}
}
