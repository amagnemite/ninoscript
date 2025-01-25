package n2dhandler;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
	ByteBuffer buffer;
	private Map<Integer, SubFile> subFiles = new HashMap<Integer, SubFile>();
	
	public N2D(File input) throws FileNotFoundException, IOException {
		file = input;
		IntFileInputStream inputStream = new IntFileInputStream(input);
		buffer = ByteBuffer.allocate((int) input.length());
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		inputStream.read(buffer.array());
		inputStream.close();
		
		buffer.position(8);
		int fileCount = buffer.getInt();
		
		for(int i = 0; i < fileCount; i++) {
			int offset = buffer.getInt();
			int size = buffer.getInt();
			
			if(offset == 0 || size == 0) {
				continue;
			}
			
			//for now, assume we're only dealing with n2ds and not the other npck types
			//use filecount over extension for the rare n2d with two tile files
			subFiles.put(i, new SubFile(offset, size));
		}
	}
	
	public void generateImages(File targetDir) {
		getCells(targetDir, subFiles.get(CELLVAL));
	}
	
	public int[][] getColors(SubFile nclr) {	
		int[][] colorArray;
		ByteBuffer nclrBuffer = ByteBuffer.allocate(nclr.size());
		nclrBuffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.position(nclr.offset());
		buffer.get(nclrBuffer.array());
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
		nclrBuffer.position(24);
		int colorDepth = nclrBuffer.getInt(); //3 = 4bpp, 4 = 8bpp
		nclrBuffer.getInt(); //0
		int paletteDataSize = nclrBuffer.getInt();
		int paletteDataOffset = nclrBuffer.getInt();
		
		int maxColors = colorDepth == FOURBPP ? 16 : 256; //16 colors, 16 palettes or 256 colors, 1 palette
		int colorByteSize = maxColors * 2; //4bpp = 32bytes, 8bpp = 512b?
		colorArray = new int[paletteDataSize / colorByteSize][];
		
		for(int i = 0; i < colorArray.length; i++) {
			byte[] b = new byte[paletteDataSize];
			nclrBuffer.get(b);
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

	
	private ByteBuffer getUncompressedBuffer(int size) throws IOException {
		ByteBuffer newBuffer;
		buffer.mark();
		int first = buffer.get();
		buffer.reset();
		
		if(first == 0x11) { //lz11 compressed, decompress first
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			LZ11.decode(buffer, output);
			newBuffer = ByteBuffer.allocate(output.size());
			newBuffer.order(ByteOrder.LITTLE_ENDIAN);
			newBuffer.put(output.toByteArray());
		}
		else {
			newBuffer = ByteBuffer.allocate(size);
			newBuffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.get(newBuffer.array());
		}
		return newBuffer;
	}
	
	private List<BufferedImage> getTiles(SubFile ncgr) {
		int[][] colorArray = getColors(subFiles.get(PALETTEVAL));
		List<BufferedImage> tiles = new ArrayList<BufferedImage>();
		
		ByteBuffer ncgrBuffer;
		buffer.position(ncgr.offset());
		
		try {
			ncgrBuffer = getUncompressedBuffer(ncgr.size());
		}
		catch (IOException e) {
			return null;
		}
		
		/*
		buffer.get(stringBuffer);
		int byteOrder = buffer.getShort();
		int version = buffer.getShort();
		int fileSize = buffer.getInt();
		int headerSize = buffer.getShort();
		int chunkCount = buffer.getShort();
		
		buffer.get(stringBuffer);
		int chunkSize = buffer.getInt();
		*/
		ncgrBuffer.position(24);
		
		int yPixelCount = ncgrBuffer.getShort(); //these could also be interpreted as tileCount?
		int xPixelCount = ncgrBuffer.getShort();
		int colorDepth = ncgrBuffer.getInt(); //3 = 4bpp, 4 = 8bpp
		//int unknown1 = buffer.getShort();
		//int unknown2 = buffer.getShort();	
		ncgrBuffer.position(ncgrBuffer.position() + 4);
		
		int tiledFlag = ncgrBuffer.getInt();
		TileOrder order = (tiledFlag & 0xFF) == 0 ? TileOrder.HORIZONTAL : TileOrder.LINEAL;
		if(order == TileOrder.LINEAL) {
			System.out.println(file + " is lineal");
		}
		
		int tileDataSize = ncgrBuffer.getInt();
		ncgrBuffer.position(ncgrBuffer.position() + 4);
		//int tileDataOffset = ncgrBuffer.getInt();
		
		byte[] tileData = new byte[tileDataSize];
		ncgrBuffer.get(tileData);
		
		if(xPixelCount != 0xFFFF) {
			xPixelCount *= 8;
			yPixelCount *= 8;
		}
		
		int bpp = colorDepth == FOURBPP ? 4 : 8;
		//byte[] tilePalette = new byte[tileData.length * (TILESIZE / bpp)];
		
		int pixelCount = tileDataSize * 8 / bpp; //# of bits / bits per pixel
		
		/*
		if(Math.pow((int) Math.sqrt(pixelCount), 2) == pixelCount) { //checking if it's a square
			xPixelCount = yPixelCount = (int) Math.sqrt(pixelCount);
		}
		else { //this needs to be replaced for tile layouts, since not all pixelcounts support 256w
			xPixelCount = pixelCount > 256 ? 256 : pixelCount;
			yPixelCount = pixelCount / xPixelCount;
		}
		*/
		
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
		int tileCount = pixelCount / (TILESIZE * TILESIZE);
		for(int i = 0; i < tileCount; i++) {
			BufferedImage tile = new BufferedImage(TILESIZE, TILESIZE, BufferedImage.TYPE_INT_ARGB);
			tile.setRGB(0, 0, TILESIZE, TILESIZE, pixels, index, TILESIZE);
			tiles.add(tile);
			index += TILESIZE * TILESIZE;
		}
		/*
		for(int h = 0; h < yPixelCount / TILESIZE; h++) {
			for(int w = 0; w < xPixelCount / TILESIZE; w++) {
				BufferedImage tile = new BufferedImage(TILESIZE, TILESIZE, BufferedImage.TYPE_INT_ARGB);
				//tile.setRGB(w * TILESIZE, h * TILESIZE, TILESIZE, TILESIZE, pixels, index, TILESIZE);
				tile.setRGB(0, 0, TILESIZE, TILESIZE, pixels, index, TILESIZE);
				tiles.add(tile);
				index += TILESIZE * TILESIZE;
			}
		}
		*/
		return tiles;
	}
	
	private void getCells(File targetDir, SubFile ncer) {
		List<BufferedImage> tiles = getTiles(subFiles.get(TILEONEVAL));
		List<Bank> banks = new ArrayList<Bank>();
		
		ByteBuffer ncerBuffer;
		buffer.position(ncer.offset());
		
		try {
			ncerBuffer = getUncompressedBuffer(ncer.size());
		}
		catch (IOException e) {
			return;
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
		ncerBuffer.position(24);
		
		int metatileCount = ncerBuffer.getShort();
		int metatileEntrySize = ncerBuffer.getShort(); //0 = 8 bytes, 1 = 16 bytes
		ncerBuffer.position(ncerBuffer.position() + 20);
		
		/*
		int metatileOffset = buffer.getInt();
		int boundrySize = buffer.getInt(); //multiplied by 64 to get area?
		//12 zeroes
		*/
		
		//starting here is the metatile table
		int cellTablePos = ncerBuffer.position() + metatileCount * (metatileEntrySize * 8 + 8);
		
		for(int i = 0; i < metatileCount; i++) {
			int objCount = ncerBuffer.getShort();
			ncerBuffer.getShort(); //read only data
			int objOffset = ncerBuffer.getInt();
			
			if(metatileEntrySize == 0x01) { //0x01 has 8 extra bytes of data
				ncerBuffer.position(ncerBuffer.position() + 8);
			}
			banks.add(new Bank(objCount, objOffset));
			ncerBuffer.mark();
			ncerBuffer.position(cellTablePos + objOffset);
			
			BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
			Graphics g = image.getGraphics();
			for(int j = 0; j < objCount; j++) { //iterate through the objs
				//x and y pos use signed ints
				byte yPos = ncerBuffer.get();
				int obj1TopHalf = ncerBuffer.get() & 0xFF;
				
				int paletteMode = (obj1TopHalf >> 5) & 0x1;
				int objShape = obj1TopHalf >> 6;
				
				int xPos = Byte.valueOf(ncerBuffer.get()).intValue(); //i love signed ints
				xPos = xPos < 0 ? xPos * -1 : xPos;
				int obj2TopHalf = ncerBuffer.get() & 0xFF;
				
				boolean isXPosNegative = (obj2TopHalf & 0x1) == 0x1 ? true : false; //100-1FF = left of screen, <= 0FF = right
				int objSize = obj2TopHalf >> 6;
				
				int obj3 = ncerBuffer.getShort();
				int tileNumber = obj3 & 0x1FF;
				
				int tileIterate = paletteMode == 0x1 ? tileNumber * 2 : tileNumber; //only even tiles are allowed in 256 color mode
				Size size = OBJSIZES[objShape][objSize];
				for(int h = 0; h < size.height() / TILESIZE; h++) {
					for(int w = 0; w < size.width() / TILESIZE; w++) {
						int y = 128 + yPos + TILESIZE * h;
						int x = isXPosNegative ? 256 - xPos : 256 + xPos;
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
			
			ncerBuffer.reset();
		}
	}
	
	record Bank(int cellCount, int objOffset) {
	}
	
	record Size(int width, int height) {
	}
	
	record SubFile(int offset, int size) {
	}
}
