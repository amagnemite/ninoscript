package ninoscript;

import java.util.List;

public class Conversation {	
	private int id;
	private int startOffset; //this is the offset of the length, 4b before 0A 09 19 00
	private int length;
	private List<ConvoSubBlockData> blockList;
	
	public Conversation(int id, int start, int length, List<ConvoSubBlockData> blockList) {
		this.id = id;
		this.startOffset = start;
		this.length = length;
		this.blockList = blockList;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(int start) {
		this.startOffset = start;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public List<ConvoSubBlockData> getBlockList() {
		return blockList;
	}
}