package ninoscript;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class BTX {
	private static final int NAMELENGTH = 16;
	
	private static final int A3I5 = 1;
	private static final int A3I5STEPS = 32;
	
	private int totalFilesize;
	private int tex0Offset;
	
	private int texChunkSize;
	private int texDataSize;
	private int texDataOffset;
	
	private int compressedTextureDataSize;
	private int compressedTexelDataOffset;
	private int compressedTexelAttrOffset;
	
	private int paletteSize;
	private int paletteDictOffset;
	private int paletteOffset;
	
	private int textureCount;
	private int textureInfoSize;
	private UnknownBlock textureUnknownBlock;
	
	private int texDictSize;
	private List<TextureDictionaryEntry> texDict = new ArrayList<TextureDictionaryEntry>();
	private List<String> texNames = new ArrayList<String>();
	
	private int paletteCount;
	private int paletteInfoSize;
	private UnknownBlock paletteUnknownBlock;
	
	private int paletteDictSize;
	private List<PaletteDictionaryEntry> paletteDict = new ArrayList<PaletteDictionaryEntry>();
	private List<String> paletteNames = new ArrayList<String>();
	
	private List<Integer> colors = new ArrayList<Integer>();
	private ByteBuffer textureDataBuffer;
	
	public BTX(byte[] data) throws IOException {
	//public BTX(File file) throws IOException {
		/*
		FileInputStream input = new FileInputStream(file);
		byte[] buffer = new byte[input.available()];
		input.read(buffer);
		input.close();
		*/
		
		byte[] magic = new byte[4];
		IntByteArrayInputStream stream = new IntByteArrayInputStream(data);
		
		stream.mark(-1);
		stream.read(magic);
		if(magic[0] == 'B' && magic[1] == 'T' && magic[2] == 'X' && magic[3] == '0') { //standalone
			stream.skip(4);
			totalFilesize = stream.readU32();
			stream.skip(4);
			tex0Offset = stream.readU32();
			stream.mark(-1);
			stream.read(magic);
		}
		
		if(magic[0] != 'T' && magic[1] != 'E' && magic[2] != 'X' && magic[3] != '0') {
			stream.close();
			return;
		}
		
		texChunkSize = stream.readU32();
		stream.skip(4);
		texDataSize = stream.readU16() * 8;
		stream.skip(6);
		texDataOffset = stream.readU32();
		stream.skip(4);
		compressedTextureDataSize = stream.readU16() * 8;
		if(compressedTextureDataSize > 0) {
			System.out.println("has compressed?");
		}
		stream.skip(6);
		compressedTexelDataOffset = stream.readU32();
		compressedTexelAttrOffset = stream.readU32();
		stream.skip(4);
		paletteSize = stream.readU32() * 8;
		paletteDictOffset = stream.readU32();
		paletteOffset = stream.readU32();
		
		//texture dict
		stream.skip(1); //unknown
		textureCount = stream.readU8();
		textureInfoSize = stream.readU16();
		stream.skip(2); //header size
		textureUnknownBlock = new UnknownBlock(stream.readU16(), new ArrayList<UnknownBlockSubData>());
		stream.skip(4); //const
		for(int i = 0; i < textureCount; i++) {
			textureUnknownBlock.subdata().add(new UnknownBlockSubData(stream.readU16(), stream.readU16()));
		}
		
		stream.skip(2); //header size
		texDictSize = stream.readU16();
		for(int i = 0; i < textureCount; i++) {
			int textureOffset = stream.readU16() * 8;
			int textureData = stream.readU16();
			
			int width = 8 << ((textureData >> 4) & 0x7);
			int height = 8 <<  ((textureData >> 7) & 0x7);
			int format = (textureData >> 10) & 0x7;
			int paletteMode = (textureData >> 13) & 0x1;
			
			stream.skip(4); //width + height occurs twice
			texDict.add(new TextureDictionaryEntry(textureOffset, width, height, format, paletteMode));
		}
		for(int i = 0; i < textureCount; i++) {
			byte[] charBytes = new byte[16];
			stream.read(charBytes);
			texNames.add(new String(charBytes, "US-ASCII"));
		}
		
		//palette dict
		stream.skip(1);
		paletteCount = stream.readU8();
		paletteInfoSize = stream.readU16();
		stream.skip(2);
		paletteUnknownBlock = new UnknownBlock(stream.readU16(), new ArrayList<UnknownBlockSubData>());
		stream.skip(4); //const
		for(int i = 0; i < paletteCount; i++) {
			paletteUnknownBlock.subdata().add(new UnknownBlockSubData(stream.readU16(), stream.readU16()));
		}
		
		stream.skip(2); //header size
		paletteDictSize = stream.readU16();
		for(int i = 0; i < paletteCount; i++) {
			int paletteOffset = (stream.readU16() & 0x1FFF) * 8;
			int unknown = stream.readU16();
			
			paletteDict.add(new PaletteDictionaryEntry(paletteOffset, unknown));
		}
		for(int i = 0; i < paletteCount; i++) {
			byte[] charBytes = new byte[16];
			stream.read(charBytes);
			paletteNames.add(new String(charBytes, "US-ASCII"));
		}
		
		stream.reset();
		stream.skip(paletteOffset);
		int color = stream.readU16();
		while(color != -1) {
			/*
			int r = (color & 0x1F) * 0x8;
			int g = ((color >> 5) & 0x1F) * 0x8;
			int b = ((color >> 10) & 0x1F) * 0x8;
			//to rgb8888
			colors.add(b | g << 8 | r << 16 | 255 << 24); //no transparency
			*/
			colors.add(color);
			color = stream.readU16();
		}
		
		stream.reset();
		stream.skip(texDataOffset);
		textureDataBuffer = ByteBuffer.allocate(texDataSize);
		textureDataBuffer.order(ByteOrder.LITTLE_ENDIAN);
		stream.read(textureDataBuffer.array(), 0, texDataSize);
	}
	
	public void convertNewImage(File png) {
		//assume that original textures have one palette and all pixels in the new image match it
		BufferedImage image;
		
		try {
			image = ImageIO.read(png);
		}
		catch(IOException e) {
			return;
		}
		
		Map<Integer, Integer> RGB8888toPaletteIndex = new HashMap<Integer, Integer>();
		int width = image.getWidth();
		int height = image.getHeight();
		int byteSize = width * height;
		int[] pixels = new int[width * height];
		image.getRGB(0, 0, width, height, pixels, 0, width);
		TextureDictionaryEntry texEntry = texDict.get(0);
		textureDataBuffer = ByteBuffer.allocate(width * height);
		
		for(int pixel : pixels) {
			int alpha = (pixel >> 24) & 0xFF;
			if(!RGB8888toPaletteIndex.containsKey(pixel)) { //might be worth condensing array and getting all the colors first
				int b = (pixel & 0xFF) / 8;
				int g = ((pixel >> 8) & 0xFF) / 8;
				int r = ((pixel >> 16) & 0xFF) / 8;
				int bgr555 = r | g << 5 | b << 10 | 0 << 15; //last bit isn't used
				
				int index = colors.indexOf(bgr555);
				if(alpha == 0 && index == -1) {
					//fully transparent pixels might have a non palette color, so just map to 0
					RGB8888toPaletteIndex.put(pixel, 0);
				}
				else {
					RGB8888toPaletteIndex.put(pixel, index);
				}
			}
			
			//for now assume there's only one texture in the image
			switch(texEntry.format()) {
				case A3I5:
					int transparency = alpha / A3I5STEPS; //3 bit alpha
					int newPixel = (RGB8888toPaletteIndex.get(pixel) & 0x1F) | (transparency & 0x7) << 5;
					textureDataBuffer.put(Integer.valueOf(newPixel).byteValue());
					break;
			}
		}
		if(totalFilesize != 0) {
			totalFilesize = (totalFilesize - texDataSize) + byteSize;
		}
		texChunkSize = (texChunkSize - texDataSize) + byteSize;
		paletteOffset = (paletteOffset - texDataSize) + byteSize;
		texDict.set(0, new TextureDictionaryEntry(texEntry.textureOffset(), width, height, texEntry.format(), texEntry.paletteMode()));
	}
	
	public void write(File file, byte[] preTextureBytes) {;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.writeBytes(preTextureBytes);
		
		ByteBuffer fourByteBuffer = ByteBuffer.allocate(4);
		fourByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		ByteBuffer twoByteBuffer = ByteBuffer.allocate(2);
		twoByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		if(totalFilesize != 0) {
			byte[] header = {'B', 'T', 'X', '0', Integer.valueOf(0xFF).byteValue(), Integer.valueOf(0xFE).byteValue(), 0x01, 0x00};
			byte[] header2 = {0x10, 0x0, 0x1, 0x0, 0x14, 0x0, 0x0, 0x0}; //check to make sure offset from btx0 header to tex0 header is always the same
			output.writeBytes(header);
			output.writeBytes(fourByteBuffer.putInt(0, totalFilesize).array());
			output.writeBytes(header2);
		}
		
		byte[] header = {'T', 'E', 'X', '0'};
		byte[] padding = {0, 0 , 0, 0};
		output.writeBytes(header);
		output.writeBytes(fourByteBuffer.putInt(0, texChunkSize).array());
		output.writeBytes(padding);
		output.writeBytes(twoByteBuffer.putShort(0, (short) (texDataSize / 8)).array());
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x3c).array()); //tex dict offset
		output.writeBytes(padding);
		output.writeBytes(fourByteBuffer.putInt(0, texDataOffset).array());
		output.writeBytes(padding);
		output.writeBytes(twoByteBuffer.putShort(0, (short) (compressedTextureDataSize / 8)).array());
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x3c).array()); //compressed tex dict offset
		output.writeBytes(padding);
		output.writeBytes(fourByteBuffer.putInt(0, compressedTexelDataOffset).array());
		output.writeBytes(fourByteBuffer.putInt(0, compressedTexelAttrOffset).array());
		output.writeBytes(padding);
		output.writeBytes(fourByteBuffer.putInt(0, paletteSize / 8).array());
		output.writeBytes(fourByteBuffer.putInt(0, paletteDictOffset).array());
		output.writeBytes(fourByteBuffer.putInt(0, paletteOffset).array());
		
		//texture info section
		output.write(0x0);
		output.write(Integer.valueOf(textureCount).byteValue());
		output.writeBytes(twoByteBuffer.putShort(0, (short) textureInfoSize).array());
		
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x8).array()); //size of unknown block header
		output.writeBytes(twoByteBuffer.putShort(0, (short) textureUnknownBlock.sectionSize()).array()); //size of unknown block
		output.writeBytes(fourByteBuffer.putInt(0, 0x17F).array()); //const
		for(UnknownBlockSubData data : textureUnknownBlock.subdata()) {
			output.writeBytes(twoByteBuffer.putShort(0, (short) data.unknown1()).array());
			output.writeBytes(twoByteBuffer.putShort(0, (short) data.unknown2()).array());
		}
		
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x8).array()); //general info block header size
		output.writeBytes(twoByteBuffer.putShort(0, (short) texDictSize).array());
		
		for(TextureDictionaryEntry entry : texDict) {
			output.writeBytes(twoByteBuffer.putShort(0, (short) (entry.textureOffset / 8)).array());
			int width = Integer.numberOfTrailingZeros(entry.width() / 8);
			int height = Integer.numberOfTrailingZeros(entry.height() / 8);
			int parameters = 0 | width << 4 | height << 7 | entry.format() << 10 | entry.paletteMode() << 13;
			output.writeBytes(twoByteBuffer.putShort(0, (short) parameters).array());

			//TODO: check if this is consistent across files
			parameters = entry.width() | entry.height() << 11 | 0x80 << 24;
			output.writeBytes(fourByteBuffer.putInt(0, parameters).array());
		}
		
		for(String name : texNames) {
			try {
				byte[] stringBytes = name.getBytes("US-ASCII");
				output.writeBytes(stringBytes);
			}
			catch (UnsupportedEncodingException e) {
			}
		}
		
		//palette info section
		output.write(0x0);
		output.write(Integer.valueOf(paletteCount).byteValue());
		output.writeBytes(twoByteBuffer.putShort(0, (short) paletteInfoSize).array());
		
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x8).array()); //size of unknown block header
		output.writeBytes(twoByteBuffer.putShort(0, (short) paletteUnknownBlock.sectionSize()).array()); //size of unknown block
		output.writeBytes(fourByteBuffer.putInt(0, 0x17F).array()); //const
		for(UnknownBlockSubData data : paletteUnknownBlock.subdata()) {
			output.writeBytes(twoByteBuffer.putShort(0, (short) data.unknown1()).array());
			output.writeBytes(twoByteBuffer.putShort(0, (short) data.unknown2()).array());
		}
		
		//not 0x8?
		output.writeBytes(twoByteBuffer.putShort(0, (short) 0x4).array()); //general info block header size
		output.writeBytes(twoByteBuffer.putShort(0, (short) paletteDictSize).array());
		
		for(PaletteDictionaryEntry entry : paletteDict) {
			output.writeBytes(twoByteBuffer.putShort(0, (short) (entry.paletteOffset() >> 3)).array());
			output.writeBytes(twoByteBuffer.putShort(0, (short) entry.unknown()).array());
		}
		
		for(String name : paletteNames) {
			try {
				byte[] stringBytes = name.getBytes("US-ASCII");
				output.writeBytes(stringBytes);
			}
			catch (UnsupportedEncodingException e) {
			}
		}
		
		output.writeBytes(textureDataBuffer.array());
		for(int color : colors) {
			output.writeBytes(twoByteBuffer.putShort(0, (short) color).array());
		}
		
		FileOutputStream fileStream;
		try {
			fileStream = new FileOutputStream(file);
			output.writeTo(fileStream);
			fileStream.close();
		}
		catch(IOException e) {
			return;
		}
	}
	
	record UnknownBlock(int sectionSize, List<UnknownBlockSubData> subdata) {
		
	}
	
	record UnknownBlockSubData(int unknown1, int unknown2) {
		
	}
	
	record TextureDictionaryEntry(int textureOffset, int width, int height, int format, int paletteMode) {
		
	}
	
	record PaletteDictionaryEntry(int paletteOffset, int unknown) {
		
	}
}
