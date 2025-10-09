package ninoscript;

import java.util.List;

public class Conversation {
	private int startOffset; //offset of the id, 8 bytes before the magic
	private int id;
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
		return length - 4; //length without the magic included, so the actual data length
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