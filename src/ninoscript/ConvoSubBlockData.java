package ninoscript;

import java.io.UnsupportedEncodingException;
import java.util.List;

import ninoscript.ScriptParser.ConvoMagic;

public class ConvoSubBlockData {
	//specific chunks of dialogue, other text, etc in a given convo
	protected ConvoMagic magic;
	protected int blockStartOffset;
	protected int fullBlockLength;
	protected int textStartOffset;
	protected int textLength;
	protected boolean hasExtraString = false;
	protected boolean hasMainString = true;
	
	protected byte[] textBytes;
	protected String textString;
	protected String newTextString;
	
	//if this block's text is shared, other matching blocks are in this list
	protected List<ConvoSubBlockData> sharedStringList = null;
	
	public ConvoSubBlockData(ConvoMagic magic, int blockStart, int fullBlockLength, int textLength, int textStart, byte[] textBytes) {
		this.magic = magic;
		this.blockStartOffset = blockStart; //actual start of block, including id and length
		this.fullBlockLength = fullBlockLength;
		this.textLength = textLength;
		this.textStartOffset = textStart; //offset of the text's length
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
	
	public boolean hasExtraString() {
		return hasExtraString;
	}
	
	public boolean hasMainString() {
		return hasMainString;
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
		return textStartOffset;
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
	
	public ConvoMagic getMagic() {
		return magic;
	}
	
	public int getBlockStart() {
		return blockStartOffset;
	}
	
	public List<ConvoSubBlockData> getSharedStringList() {
		return sharedStringList;
	}

	public void setSharedStringList(List<ConvoSubBlockData> sharedStringList) {
		this.sharedStringList = sharedStringList;
	}
	
	public static class ExtraStringConvoData extends ConvoSubBlockData {
		//blocks that have an extra string associated, like dialogue's speakers or text entry puzzles with an answer + description
		private int extraStringOffset; //offset of the string's length
		private int extraStringLength;
		private byte[] extraStringBytes;
		
		private String extraString = null;
		private String newExtraInfoString;
		
		private byte[] speakerBytes;
		
		public ExtraStringConvoData(ConvoMagic magic, int blockStart, int fullBlockLength, int mainStringLength, int mainStringStart,
				int extraStringStart, int extraStringLength, byte[] mainStringBytes, byte[] extraStringBytes) {
			super(magic, blockStart, fullBlockLength, mainStringLength, mainStringStart, mainStringBytes);
			this.extraStringOffset = extraStringStart;
			this.extraStringLength = extraStringLength;
			this.extraStringBytes = extraStringBytes;
			
			if(mainStringBytes == null) {
				hasMainString = false;
			}
			
			try {
				if(extraStringBytes != null) {
					extraString = new String(extraStringBytes, "Shift_JIS");
					hasExtraString = true;
				}
			}
			catch (UnsupportedEncodingException e) {
			}
			
			newExtraInfoString = extraString;
		}
		
		public String getNewExtraInfoString() {
			return newExtraInfoString;
		}

		public void setNewExtraInfoString(String newExtraInfoString) {
			this.newExtraInfoString = newExtraInfoString;
		}
		
		public int getExtraInfoStart() {
			return extraStringOffset;
		}

		public int getExtraInfoLength() {
			return extraStringLength;
		}
		
		public byte[] getExtraInfoBytes() {
			return extraStringBytes;
		}
		
		public String getExtraInfoString() {
			return extraString;
		}

		public void setExtraInfoSTring(String extraInfoString) {
			this.extraString = extraInfoString;
		}
		
		public byte[] getSpeakerBytes() {
			return speakerBytes;
		}
	}
}
