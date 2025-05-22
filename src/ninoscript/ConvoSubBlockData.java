package ninoscript;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import ninoscript.ScriptParser.ConvoMagic;

public class ConvoSubBlockData {
	//specific chunks of dialogue, other text, etc in a given convo
	protected ConvoMagic magic;
	protected int blockStartOffset;
	protected int oldFullBlockLength;
	protected int textStartOffset;
	protected int oldTextLength;
	protected boolean hasExtraString = false;
	protected boolean hasMainString = true;
	
	protected String textString = "";
	protected String newTextString;
	
	//if this block's text is shared, other matching blocks are in this list
	protected List<ConvoSubBlockData> sharedStringList = null;
	
	public ConvoSubBlockData(ConvoMagic magic, int blockStart, int fullBlockLength, int textStart, byte[] textBytes) {
		this.magic = magic;
		this.blockStartOffset = blockStart; //actual start of block, including id and length
		this.oldFullBlockLength = fullBlockLength;
		this.textStartOffset = textStart; //offset of the text's length
		
		try {
			if(textBytes != null) {
				textString = new String(textBytes, "Shift_JIS");
				oldTextLength = textBytes.length;
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

	public int getOldFullBlockLength() {
		return oldFullBlockLength;
	}

	public int getOldTextLength() {
		return oldTextLength;
	}

	public int getTextStart() {
		return textStartOffset;
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
		
		public static final int NOSIDE = 0;
		public static final int RIGHTSIDE = 1;
		public static final int LEFTSIDE = 2;
		
		private int extraStringOffset; //offset of the string's length
		private int oldExtraStringLength;
		
		private String extraString = null;
		private String newExtraInfoString;
		
		private int speakerSide = -1;
		
		public ExtraStringConvoData(ConvoMagic magic, int blockStart, int fullBlockLength, int mainStringStart,
				int extraStringStart, byte[] mainStringBytes, byte[] extraStringBytes) {
			this(magic, blockStart, fullBlockLength, mainStringStart, extraStringStart, 
					mainStringBytes, extraStringBytes, -1);
		}
		
		public ExtraStringConvoData(ConvoMagic magic, int blockStart, int fullBlockLength, int mainStringStart,
				int extraStringStart, byte[] mainStringBytes, byte[] extraStringBytes, int speakerSide) {
			super(magic, blockStart, fullBlockLength, mainStringStart, mainStringBytes);
			this.extraStringOffset = extraStringStart;
			this.speakerSide = speakerSide;
			
			if(mainStringBytes == null) {
				hasMainString = false;
			}
			
			try {
				if(extraStringBytes != null) {
					extraString = new String(extraStringBytes, "Shift_JIS");
					oldExtraStringLength = extraStringBytes.length;
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

		public int getOldExtraInfoLength() {
			return oldExtraStringLength;
		}
		
		public String getExtraInfoString() {
			return extraString;
		}

		public void setExtraInfoString(String extraInfoString) {
			this.extraString = extraInfoString;
		}
		
		public int getSpeakerSide() {
			return speakerSide;
		}
	}
	
	public static class MultipleChoiceConvoData extends ConvoSubBlockData {
		int choicesStart;
		List<String> originalStrings = new ArrayList<String>();
		List<String> newStrings = new ArrayList<String>();
		List<Integer> originalStringLengths = new ArrayList<Integer>();
		
		public MultipleChoiceConvoData(ConvoMagic magic, int blockStart, int fullBlockLength, int choicesStart, List<byte[]> stringBytes) {
			super(magic, blockStart, fullBlockLength, -1, null);
			this.choicesStart = choicesStart;
			try {
				for(byte[] bytes : stringBytes) {
					originalStrings.add(new String(bytes, "Shift_JIS"));
					originalStringLengths.add(bytes.length);
				}
			}
			catch (UnsupportedEncodingException e) {
			}
			newStrings.addAll(originalStrings);
		}
		
	}
}
