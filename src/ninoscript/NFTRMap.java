package ninoscript;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class NFTRMap {
	private static final int HEADERLENGTH = 0x10;

	public HashMap<String, Integer> parseNFTR(File file) throws FileNotFoundException, IOException {
		int index = 0;
		byte[] buffer = new byte[HEADERLENGTH];
		Map<String, Integer> charTileNumMap = new HashMap<String, Integer>();
		Map<Integer, Integer> tileNumLengthMap = new HashMap<Integer, Integer>();
		HashMap<String, Integer> charLengthMap = new HashMap<String, Integer>();
		IntFileInputStream inputStream = new IntFileInputStream(file);
		inputStream.read(buffer);
		
		if(buffer[0] != 0x52 && buffer[1] != 0x54 && buffer[2] != 0x46 && buffer[3] != 0x4E) {
			inputStream.close();
			return null;
		}
		index += HEADERLENGTH;
		
		inputStream.skip(20); //skip rest of NFTR header + FINF magic + FNIF size
		
		//these two just have an extra 8 for whatever reason
		int charWidthOffset = inputStream.readU32() - 8;
		int firstCharMapOffset = inputStream.readU32() - 8;
		index += 28;
		
		inputStream.skip(charWidthOffset - index);
		inputStream.skip(10);
		int lastTileNum = inputStream.readU16(); //number of tiles - 1
		inputStream.skip(4); //unknown val
		index = charWidthOffset + 16;
		
		//starting here is all the tile lengths
		for(int i = 0; i < lastTileNum; i++) {
			inputStream.skip(2);
			tileNumLengthMap.put(i, inputStream.readU8());
			index += 3;
		}
		
		inputStream.skip(firstCharMapOffset - index);
		index = firstCharMapOffset;
		int nextMapOffset = -1;
		
		while(nextMapOffset != -8) {
			inputStream.skip(8);
			//int mapSize = inputStream.readU32();
			short firstChar = Integer.valueOf(inputStream.readU16()).shortValue();
			short lastChar = Integer.valueOf(inputStream.readU16()).shortValue();
			int mapType = inputStream.readU32();
			nextMapOffset = inputStream.readU32() - 8;
			index += 0x14;
			
			switch(mapType) {
				case 0: //simply iterates the tilenums
					int firstTile = inputStream.readU16();
					int j = 0;
					for(short i = firstChar; i < lastChar + 1; i++) {
						byte[] stringByte = ByteBuffer.allocate(2).putShort(i).array();
						if(stringByte[0] == 0) {
							stringByte = Arrays.copyOfRange(stringByte, 1, 2);
						}
						charTileNumMap.put(new String(stringByte, "Shift_JIS"), firstTile + j);
						j++;
					}
					
					inputStream.skip(2);
					index += 4;
					break;
				case 1:
					for(short i = firstChar; i < lastChar + 1; i++) {
						int tileNum = inputStream.readU16();
						if(tileNum != 0xFFFF) {
							byte[] stringByte = ByteBuffer.allocate(2).putShort(i).array();
							if(stringByte[0] == 0) {
								stringByte = Arrays.copyOfRange(stringByte, 1, 2);
							}
							charTileNumMap.put(new String(stringByte, "Shift_JIS"), tileNum);
						}
						index += 2;
					}
					if(nextMapOffset != -8) {
						inputStream.skip(nextMapOffset - index);
					}
					index += (nextMapOffset - index);		
					break;
				case 2:
					index += 2;
					int pairings = inputStream.readU16();
					for(int i = 0; i < pairings; i++) {
						short charNum = Integer.valueOf(inputStream.readU16()).shortValue();
						int tileNum = inputStream.readU16();
						byte[] stringByte = ByteBuffer.allocate(2).putShort(charNum).array();
						charTileNumMap.put(new String(stringByte, "Shift_JIS"), tileNum);
						
						index += 4;
					}
					if(nextMapOffset != -8) {
						inputStream.skip(nextMapOffset - index);
					}
					index += (nextMapOffset - index);		
					break;
			}
		}
		inputStream.close();
		
		for(Entry<String, Integer> mapping : charTileNumMap.entrySet()) {
			charLengthMap.put(mapping.getKey(), tileNumLengthMap.get(mapping.getValue()));
		}
		return charLengthMap;
	}
}
