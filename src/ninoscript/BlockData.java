package ninoscript;

import java.io.UnsupportedEncodingException;
import java.util.List;

import ninoscript.ScriptReader.Magic;

public class BlockData {
	protected Magic magic;
	protected int blockStart;
	protected int fullBlockLength;
	protected int textStart;
	protected int textLength;
	protected boolean hasExtraInfo = false;
	
	protected byte[] textBytes;
	protected String textString;
	protected String newTextString;
	
	protected List<BlockData> sharedStringList = null;
	
	public BlockData(Magic magic, int blockStart, int fullBlockLength, int textLength, int textStart, byte[] textBytes) {
		this.magic = magic;
		this.blockStart = blockStart;
		this.fullBlockLength = fullBlockLength;
		this.textLength = textLength;
		this.textStart = textStart;
		this.textBytes = textBytes;
		
		try {
			if(textBytes == null) {
				textString = "";
			}
			else {
				textString = new String(textBytes, "Shift_JIS");
			}
		}
		catch (UnsupportedEncodingException e) {
		}
		
		newTextString = textString;
	}
	
	public boolean hasExtraInfo() {
		return hasExtraInfo;
	}

	public String getNewTextString() {
		return newTextString;
	}

	public void setNewTextString(String newTextString) {
		this.newTextString = newTextString;
	}

	public int getFullBlockLength() {
		return fullBlockLength;
	}

	public int getTextLength() {
		return textLength;
	}

	public int getTextStart() {
		return textStart;
	}

	public byte[] getTextBytes() {
		return textBytes;
	}
	
	public String getTextString() {
		return textString;
	}

	public void setTextString(String textString) {
		this.textString = textString;
	}
	
	public Magic getMagic() {
		return magic;
	}
	
	public int getBlockStart() {
		return blockStart;
	}
	
	public List<BlockData> getSharedStringList() {
		return sharedStringList;
	}

	public void setSharedStringList(List<BlockData> sharedStringList) {
		this.sharedStringList = sharedStringList;
	}
	
	public static class ExtraInfoBlockData extends BlockData { //blocks that have an extra string associated, so dialogue or text entry puzzles
		private int extraInfoStart; //actual start of the text, so -1 for length
		private int extraInfoLength;
		private byte[] extraInfoBytes;
		
		private String extraInfoString = null;
		private String newExtraInfoString;
		
		public ExtraInfoBlockData(Magic magic, int blockStart, int fullBlockLength, int textLength, int textStart,
				int extraInfoStart, int extraInfoLength, byte[] textBytes, byte[] extraInfoBytes) {
			super(magic, blockStart, fullBlockLength, textLength, textStart, textBytes);
			this.extraInfoStart = extraInfoStart;
			this.extraInfoLength = extraInfoLength;
			this.extraInfoBytes = extraInfoBytes;
			
			try {
				if(extraInfoBytes != null) {
					extraInfoString = new String(extraInfoBytes, "Shift_JIS");
					hasExtraInfo = true;
				}
			}
			catch (UnsupportedEncodingException e) {
			}
			
			newExtraInfoString = extraInfoString;
		}
		
		public String getNewExtraInfoString() {
			return newExtraInfoString;
		}

		public void setNewExtraInfoString(String newExtraInfoString) {
			this.newExtraInfoString = newExtraInfoString;
		}
		
		public int getExtraInfoStart() {
			return extraInfoStart;
		}

		public int getExtraInfoLength() {
			return extraInfoLength;
		}
		
		public byte[] getExtraInfoBytes() {
			return extraInfoBytes;
		}
		
		public String getExtraInfoString() {
			return extraInfoString;
		}

		public void setExtraInfoSTring(String extraInfoString) {
			this.extraInfoString = extraInfoString;
		}
	}
}
