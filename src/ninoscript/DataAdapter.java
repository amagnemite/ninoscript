package ninoscript;

import java.io.File;
import java.util.List;
import java.util.Map;

//bridge between data objects like scriptparser and regulartextpanel
//stores the currently active string/s
public abstract class DataAdapter {
	public abstract Map<ScriptParser, File> getScriptMap();
	public abstract List<String> generateIDList();
	public abstract void resetCurrentData();
	public abstract ScriptParser getCurrentScript();
	public abstract void setCurrentScript(ScriptParser sp);
	public abstract int getMaxBlocks();
	public abstract void updateCurrentConvoBlock(int index);
	public abstract void writeFile();
	public abstract String getOriginalMainString();
	public abstract String getNewMainString(String key);
	public abstract void writeNewMainString(String newString);
	
	public void updateCurrentScript(int index) {
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
