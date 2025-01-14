package n2dhandler;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import ninoscript.IntFileInputStream;

//adapted from https://github.com/pleonex/tinke/blob/master/Plugins/NINOKUNI/NINOKUNI/Pack/NPCK.cs
//and other parts from tinke
//n2ds are npcks
public class N2D {
	private static final int FOURBPP = 3;
	private static final int PALETTEVAL = 0;
	private static final int TILEONEVAL = 1;
	//private static final int TILETWOVAL = 2;
	private static final int CELLVAL = 3;
	
	private static final int TILESIZE = 8;
	//final int EIGHTBPP = 4;
	
	private static final Size[][] OBJSIZES = {
		{new Size(8, 8), new Size(16, 16), new Size(32, 32), new Size(64, 64)},
		{new Size(16, 8), new Size(32, 8), new Size(32, 16), new Size(64, 32)},
		{new Size(8, 16), new Size(8, 32), new Size(16, 32), new Size(32, 64)}
	};
	private enum TileOrder {
		HORIZONTAL,
		LINEAL
	}
	
	File file;
	private Map<Integer, SubFile> subFiles = new HashMap<Integer, SubFile>();
	
	public N2D(File input) throws FileNotFoundException, IOException {
		file = input;
		IntFileInputStream inputStream = new IntFileInputStream(input);
		
		inputStream.skip(8);
		int fileCount = inputStream.readU32();
		
		for(int i = 0; i < fileCount; i++) {
			int offset = inputStream.readU32();
			int size = inputStream.readU32();
			
			if(offset == 0 || size == 0) {
				continue;
			}
			
			//for now, assume we're only dealing with n2ds and not the other npck types
			//use filecount over extension for the rare n2d with two tile files
			subFiles.put(i, new SubFile(offset, size));
		}
		inputStream.close();
	}
	
	public void generateImages(File targetDir) {
		getCells(targetDir, subFiles.get(CELLVAL));
	}
	
	public int[][] getColors(SubFile nclr) {	
		int[][] colorArray;
		IntFileInputStream stream;
		ByteBuffer buffer = ByteBuffer.allocate(nclr.size());
		//byte[] stringBuffer = new byte[4];
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		try {
			stream = new IntFileInputStream(file);
			stream.skip(nclr.offset());
			stream.read(buffer.array());
			stream.close();
		}
		catch (IOException e) {
			return null;
		}
		/*
		buffer.get(stringBuffer);
		String headerMagic = new String(stringBuffer);
		int byteOrder = buffer.getShort();
		int version = buffer.getShort();
		int fileSize = buffer.getInt();
		int headerSize = buffer.getShort();
		int chunkCount = buffer.getShort();
		
		buffer.get(stringBuffer);
		String chunkMagic = new String(stringBuffer);
		int chunkSize = buffer.getInt();
		*/
		buffer.position(24);
		int colorDepth = buffer.getInt(); //3 = 4bpp, 4 = 8bpp
		buffer.getInt(); //0
		int paletteDataSize = buffer.getInt();
		int paletteDataOffset = buffer.getInt();
		
		int maxColors = colorDepth == FOURBPP ? 16 : 256; //16 colors, 16 palettes or 256 colors, 1 palette
		int colorByteSize = maxColors * 2; //4bpp = 32bytes, 8bpp = 512b?
		colorArray = new int[paletteDataSize / colorByteSize][];
		
		for(int i = 0; i < colorArray.length; i++) {
			byte[] b = new byte[paletteDataSize];
			buffer.get(b);
			colorArray[i] = BGR555ToColor(b);
		}
		
		return colorArray;
	}
	
	private int[] BGR555ToColor(byte[] bytes) {
		int[] colors = new int[bytes.length / 2];
		for(int i = 0; i < bytes.length / 2; i++) {
			int twoByte = (bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] & 0xFF) << 8;
			
			int r = (twoByte & 0x1F) * 255 / 31;
			int g = ((twoByte >> 5) & 0x1F) * 255 / 31;
			int b = ((twoByte >> 10) & 0x1F) * 255 / 31;
			
			//to rgb8888
			colors[i] = b | g << 8 | r << 16 | 255 << 24; //no transparency
		}
		return colors;
	}
	
	private List<BufferedImage> getTiles(SubFile ncgr) {
		int[][] colorArray = getColors(subFiles.get(PALETTEVAL));
		List<BufferedImage> tiles = new ArrayList<BufferedImage>();
		IntFileInputStream stream;
		ByteBuffer buffer = ByteBuffer.allocate(ncgr.size());
		byte[] stringBuffer = new byte[4];
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		
		try {
			stream = new IntFileInputStream(file);
			stream.skip(ncgr.offset());
			stream.read(buffer.array());
			stream.close();
		}
		catch (IOException e) {
			return null;
		}
		
		buffer.get(stringBuffer);
		String headerMagic = new String(stringBuffer);
		int byteOrder = buffer.getShort();
		int version = buffer.getShort();
		int fileSize = buffer.getInt();
		int headerSize = buffer.getShort();
		int chunkCount = buffer.getShort();
		
		buffer.get(stringBuffer);
		String chunkMagic = new String(stringBuffer);
		int chunkSize = buffer.getInt();
		int yPixelCount = buffer.getShort(); //these could also be interpreted as tileCount?
		int xPixelCount = buffer.getShort();
		int colorDepth = buffer.getInt();
		int unknown1 = buffer.getShort();
		int unknown2 = buffer.getShort();
		int tiledFlag = buffer.getInt();
		TileOrder order = (tiledFlag & 0xFF) == 0 ? TileOrder.HORIZONTAL : TileOrder.LINEAL;
		if(order == TileOrder.LINEAL) {
			System.out.println(file + " is lineal");
		}
		
		int tileDataSize = buffer.getInt();
		int tileDataOffset = buffer.getInt();
		//buffer.get(0x18 + tileDataOffset); //start of RAHC + 8 + paletteDataOffset
		byte[] tileData = new byte[tileDataSize];
		buffer.get(tileData);
		
		if(xPixelCount != 0xFFFF) {
			xPixelCount *= 8;
			yPixelCount *= 8;
		}
		
		int bpp = colorDepth == FOURBPP ? 4 : 8;
		//byte[] tilePalette = new byte[tileData.length * (TILESIZE / bpp)];
		
		int pixelCount = tileDataSize * 8 / bpp; //# of bits / bits per pixel
		if(Math.pow(Math.sqrt(pixelCount), 2) == pixelCount) { //checking if it's a square
			xPixelCount = yPixelCount = (int) Math.sqrt(pixelCount);
		}
		else {
			xPixelCount = pixelCount > 256 ? 256 : pixelCount;
			yPixelCount = pixelCount / xPixelCount;
		}
		
		//BufferedImage image = new BufferedImage(xPixelCount, yPixelCount, BufferedImage.TYPE_INT_ARGB);
		colorArray[0][0] = 0; //color 0 is generally meant to be transparent
		
		int[] pixels;
		if(colorDepth == FOURBPP) {
			pixels = new int[tileData.length * 2];
			for(int i = 0; i < tileData.length; i++) {
				int curByte = tileData[i] & 0xFF;
				pixels[i * 2] = colorArray[curByte & 0xC0][curByte & 0x30];
				pixels[i * 2 + 1] = colorArray[curByte & 0xC][curByte & 0x3];
			}
		}
		else {
			pixels = new int[tileData.length];
			for(int i = 0; i < tileData.length; i++) {
				pixels[i] = colorArray[0][tileData[i]];
			}
		}
		
		int index = 0;
		for(int h = 0; h < yPixelCount / TILESIZE; h++) {
			for(int w = 0; w < xPixelCount / TILESIZE; w++) {
				BufferedImage tile = new BufferedImage(TILESIZE, TILESIZE, BufferedImage.TYPE_INT_ARGB);
				//tile.setRGB(w * TILESIZE, h * TILESIZE, TILESIZE, TILESIZE, pixels, index, TILESIZE);
				tile.setRGB(0, 0, TILESIZE, TILESIZE, pixels, index, TILESIZE);
				tiles.add(tile);
				index += TILESIZE * TILESIZE;
			}
		}
		
		return tiles;
	}
	
	private void getCells(File targetDir, SubFile ncer) {
		List<BufferedImage> tiles = getTiles(subFiles.get(TILEONEVAL));
		List<Bank> banks = new ArrayList<Bank>();
		IntFileInputStream stream;
		ByteBuffer buffer = ByteBuffer.allocate(ncer.size());
		//byte[] stringBuffer = new byte[4];
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		try {
			stream = new IntFileInputStream(file);
			stream.skip(ncer.offset());
			stream.read(buffer.array());
			stream.close();
		}
		catch (IOException e) {
			//return null;
		}
		
		/*
		buffer.get(stringBuffer);
		String headerMagic = new String(stringBuffer);
		int byteOrder = buffer.getShort();
		int version = buffer.getShort();
		int fileSize = buffer.getInt();
		int headerSize = buffer.getShort();
		int chunkCount = buffer.getShort();
		buffer.get(stringBuffer);
		String chunkMagic = new String(stringBuffer);
		int chunkSize = buffer.getInt();
		*/
		buffer.position(24);
		
		int metatileCount = buffer.getShort();
		int metatileEntrySize = buffer.getShort(); //0 = 8 bytes, 1 = 16 bytes
		buffer.position(buffer.position() + 20);
		
		/*
		int metatileOffset = buffer.getInt();
		int boundrySize = buffer.getInt(); //multiplied by 64 to get area?
		//12 zeroes
		*/
		
		//starting here is the metatile table
		int cellTablePos = buffer.position() + metatileCount * (metatileEntrySize * 8 + 8);
		
		for(int i = 0; i < metatileCount; i++) {
			int objCount = buffer.getShort();
			buffer.getShort(); //read only data
			int objOffset = buffer.getInt();
			
			if(metatileEntrySize == 0x01) {
				buffer.position(buffer.position() + 8);
			}
			banks.add(new Bank(objCount, objOffset));
			buffer.mark();
			buffer.position(cellTablePos + objOffset);
			
			BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
			Graphics g = image.getGraphics();
			for(int j = 0; j < objCount; j++) { //iterate through the objs
				byte yPos = buffer.get();
				int obj1TopHalf = buffer.get() & 0xFF;
				
				int paletteMode = (obj1TopHalf >> 5) & 0x1;
				int objShape = obj1TopHalf >> 6;
				
				int xPos = Byte.valueOf(buffer.get()).intValue(); //i love signed ints
				xPos = xPos < 0 ? xPos * -1 : xPos;
				int obj2TopHalf = buffer.get() & 0xFF;
				
				int isXPosNegative = obj2TopHalf & 0x1;
				int objSize = obj2TopHalf >> 6;
				
				int obj3 = buffer.getShort();
				int tileNumber = obj3 & 0x1FF;
				
				int tileIterate = paletteMode == 0x1 ? tileNumber * 2 : tileNumber; //only even tiles are allowed in 256 color mode
				Size size = OBJSIZES[objShape][objSize];
				for(int h = 0; h < size.height() / TILESIZE; h++) {
					for(int w = 0; w < size.width() / TILESIZE; w++) {
						int y = 128 + yPos + TILESIZE * h;
						int x = isXPosNegative == 0x1 ? 256 - xPos : 256 + xPos;
						x += TILESIZE * w;
						
						g.drawImage(tiles.get(tileIterate), x, y, null);
						
						tileIterate++;
					}
				}
			}
			try {
				String filename = file.getName().substring(0, file.getName().lastIndexOf('.'));
				String foldername = file.getParent().substring(file.getParent().lastIndexOf('\\') + 1);
				ImageIO.write(image, "png", new File(targetDir.getAbsolutePath(), foldername + "." + filename + "." + i + ".png"));
			}
			catch (IOException e) {
				
			}
			g.dispose();
			
			buffer.reset();
		}
	}
	
	record Bank(int cellCount, int objOffset) {
	}
	
	record Size(int width, int height) {
	}
	
	record SubFile(int offset, int size) {
	}
}
