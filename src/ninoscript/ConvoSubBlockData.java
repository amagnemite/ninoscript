package ninoscript;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import ninoscript.ConvoSubBlockData.ExtraStringConvoData;
import ninoscript.ScriptParser.ConvoMagic;

public class ConvoSubBlockData {
	//specific chunks of dialogue, other text, etc in a given convo
	protected ConvoMagic magic;
	protected int blockStartOffset; //actual start of block, including id and length
	protected int oldFullBlockLength; //does not include magic + length
	protected int textStartOffset; //offset of the text's length
	protected int oldTextLength;
	protected boolean hasExtraString = false;
	protected boolean hasMainString = true;
	
	protected String textString = "";
	protected String newTextString;
	
	//if this block's text is shared, other matching blocks are in this list
	protected List<ConvoSubBlockData> sharedStringList = null;
	
	public ConvoSubBlockData(ConvoMagic magic, int blockStart, int fullBlockLength, int textStart, byte[] textBytes) {
		this.magic = magic;
		this.blockStartOffset = blockStart;
		this.oldFullBlockLength = fullBlockLength;
		this.textStartOffset = textStart;
		
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
	
	public void resetNewStrings() {
		//wipes any new stuff that wasn't saved to file
		if(getClass() == MultipleChoiceConvoData.class) {
			((MultipleChoiceConvoData) this).resetNewStrings();
		}
		setNewTextString(getTextString());
		if(hasExtraString()) {
			((ExtraStringConvoData) this).setNewExtraInfoString(((ExtraStringConvoData) this).getExtraInfoString());
		}
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

	public int getTextStartOffset() {
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
	
	public int getStartOffset() {
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
		
		public int getExtraInfoStartOffset() {
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
		List<String> originalStrings = new ArrayList<String>();
		List<String> newStrings = new ArrayList<String>();
		List<Integer> originalStringLengths = new ArrayList<Integer>();
		
		public MultipleChoiceConvoData(ConvoMagic magic, int blockStart, int fullBlockLength, int choicesStart, List<byte[]> stringBytes) {
			super(magic, blockStart, fullBlockLength, choicesStart, null);
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
		
		public void resetNewStrings() {
			for(int i = 0; i < newStrings.size(); i++) {
				newStrings.set(i, originalStrings.get(i));
			}
		}
		
		public List<String> getOriginalStrings() {
			return originalStrings;
		}
		
		public List<String> getNewStrings() {
			return newStrings;
		}
		
		public int getOriginalTotalStringsLength() {
			int total = 0;
			for(int length : originalStringLengths) {
				total += length;
			}
			return total;
		}
	}
}
