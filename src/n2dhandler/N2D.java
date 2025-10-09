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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
	private static final int SCREENVAL = 6;
	
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
		
		if(buffer.getInt() != 0x4B43504E) { //NPCK
			return;
		}
		
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
		if(subFiles.get(PALETTEVAL) == null) { //some n2ds don't have palettes, not sure how they work
			System.out.println("no palette");
			return;
		}
		
		if(subFiles.get(CELLVAL) != null) {
			getCells(targetDir, subFiles.get(CELLVAL));
		}
		else if(subFiles.get(SCREENVAL) != null) { //bgs use nscrs
			getScreen(targetDir, subFiles.get(SCREENVAL));
		}
		else { //also rare instances of only a palette with no tiles + cells/scr
			System.out.println("no tiles");
			return;
		}
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
		nclrBuffer.position(nclrBuffer.position() + 4);
		int totalPaletteDataSize = nclrBuffer.getInt(); //always 512
		nclrBuffer.position(nclrBuffer.position() + 4);
		//int paletteDataOffset = nclrBuffer.getInt();
		
		int colorsPerPalette = colorDepth == FOURBPP ? 16 : 256; //16 colors, 16 palettes or 256 colors, 1 palette
		int colorByteSize = colorsPerPalette * 2; //4bpp = 32bytes, 8bpp = 512b?
		colorArray = new int[totalPaletteDataSize / colorByteSize][];
		
		byte[] b = new byte[totalPaletteDataSize];
		nclrBuffer.get(b);
		int[] allColors = BGR555ToColor(b); //get all the colors at once, then break it up if 4bpp
		
		if(colorDepth == FOURBPP) {
			int index = 0;
			for(int i = 0; i < colorArray.length; i++) {
				colorArray[i] = Arrays.copyOfRange(allColors, index, index + 16);
			}
			index += 16;
		}
		else {
			colorArray[0] = allColors;
		}
		
		return colorArray;
	}
	
	private int[] BGR555ToColor(byte[] bytes) {
		final int TOTALCOLORS = 256;
		int[] colors = new int[TOTALCOLORS];
		for(int i = 0; i < TOTALCOLORS; i++) {
			int twoByte = (bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] & 0xFF) << 8;
			
			/*
			int r = (twoByte & 0x1F) * 255 / 31;
			int g = ((twoByte >> 5) & 0x1F) * 255 / 31;
			int b = ((twoByte >> 10) & 0x1F) * 255 / 31;
			*/
			//match how tinke calculates colors for compat
			int r = (twoByte & 0x1F) * 0x8;
			int g = ((twoByte >> 5) & 0x1F) * 0x8;
			int b = ((twoByte >> 10) & 0x1F) * 0x8;
			
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
	
	private Map<Integer, List<BufferedImage>> getTiles(SubFile ncgr) {
		int[][] colorArray = getColors(subFiles.get(PALETTEVAL));
		Map<Integer, List<BufferedImage>> tileLists = new HashMap<Integer, List<BufferedImage>>();
		ByteBuffer ncgrBuffer;
		buffer.position(ncgr.offset());
		
		boolean debugTiles = false;
		
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
		int pixelCount = tileDataSize * 8 / bpp; //# of bits / bits per pixel
		
		colorArray[0][0] = 0; //color 0 is generally meant to be transparent
		
		int[] pixels;
		if(colorDepth == FOURBPP) {
			List<Integer> uniquePalettes = new ArrayList<Integer>();
			
			for(int k = 0; k < 16; k++) {
				if(uniquePalettes.size() != 0) {
					boolean notUnique = false;
					for(Integer uniquePalette : uniquePalettes) {
						if(Arrays.equals(colorArray[k], colorArray[uniquePalette])) {
							tileLists.put(k, tileLists.get(uniquePalette));
							notUnique = true;
							break;
						}
					}
					if(notUnique) {
						continue;
					}
				}
				
				pixels = new int[tileData.length * 2];
				for(int i = 0; i < tileData.length; i++) {
					int curByte = tileData[i] & 0xFF;
					pixels[i * 2] = colorArray[k][curByte & 0xF];
					pixels[i * 2 + 1] = colorArray[k][curByte >> 4];
				}
				
				List<BufferedImage> tiles = new ArrayList<BufferedImage>();
				tileLists.put(k, tiles);
				uniquePalettes.add(k);
				makeTiles(pixelCount, pixels, tiles);
			}
		}
		else {
			pixels = new int[tileData.length];
			for(int i = 0; i < tileData.length; i++) {
				pixels[i] = colorArray[0][tileData[i] & 0xFF];
			}
			
			List<BufferedImage> tiles = new ArrayList<BufferedImage>();
			tileLists.put(0, tiles);
			makeTiles(pixelCount, pixels, tiles);
		}
		
		if(debugTiles) {
			if(Math.pow((int) Math.sqrt(pixelCount), 2) == pixelCount) { //checking if it's a square
				xPixelCount = yPixelCount = (int) Math.sqrt(pixelCount);
			}
			else {
				for(int i = 256; i > 0; i -= 8) {
					if(pixelCount < i) {
						continue;
					}
				
					int side = pixelCount / i;
					if(side % 8 == 0) {
						xPixelCount = side > i ? side : i;
						yPixelCount = side > i ? i : side;
						break;
					}
				}
			}
			
			int i = 0;
			int j = 0;
			for(List<BufferedImage> tiles : tileLists.values()) {
				BufferedImage image = new BufferedImage(xPixelCount, yPixelCount, BufferedImage.TYPE_INT_ARGB);
				Graphics g = image.getGraphics();
				
				for(int h = 0; h < yPixelCount / TILESIZE; h++) {
					for(int w = 0; w < xPixelCount / TILESIZE; w++) {
						g.drawImage(tiles.get(i), w * TILESIZE, h * TILESIZE, null);
						i++;
					}
				}
				try {
					ImageIO.write(image, "png", new File(file.getParent(), "tile test" + j + ".png"));
				}
				catch (IOException e) {
					
				}
				g.dispose();
			}
		}
			
		
		return tileLists;
	}
	
	private void makeTiles(int pixelCount, int[] pixels, List<BufferedImage> tiles) {
		int tileCount = pixelCount / (TILESIZE * TILESIZE);
		int index = 0;
		for(int i = 0; i < tileCount; i++) {
			BufferedImage tile = new BufferedImage(TILESIZE, TILESIZE, BufferedImage.TYPE_INT_ARGB);
			tile.setRGB(0, 0, TILESIZE, TILESIZE, pixels, index, TILESIZE);
			tiles.add(tile);
			index += TILESIZE * TILESIZE;
		}
	}
	
	private void getCells(File targetDir, SubFile ncer) {
		final int OAMSIZE = 6;
		Map<Integer, List<BufferedImage>> tileLists = getTiles(subFiles.get(TILEONEVAL));
		//List<Bank> banks = new ArrayList<Bank>();
		
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
			//banks.add(new Bank(objCount, objOffset));
			ncerBuffer.mark();
			
			BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
			Graphics g = image.getGraphics();
			int tilesUsed = 0;
			
			for(int j = objCount - 1; j >= 0; j--) { //iterate through the obj backward since defined as highest layer to lowest layer
				//x and y pos use signed ints
				ncerBuffer.position(cellTablePos + objOffset + j * OAMSIZE);
				byte yPos = ncerBuffer.get();
				int obj1TopHalf = ncerBuffer.get() & 0xFF;
				
				int rotationScalingFlag = obj1TopHalf & 0x1;
				int paletteMode = (obj1TopHalf >> 5) & 0x1;
				int objShape = obj1TopHalf >> 6;
				
				int xPos = Byte.valueOf(ncerBuffer.get()).intValue(); //i love signed ints
				xPos = xPos < 0 ? xPos * -1 : xPos;
				int obj2TopHalf = ncerBuffer.get() & 0xFF;
				
				boolean isXPosNegative = (obj2TopHalf & 0x1) == 0x1 ? true : false; //100-1FF = left of screen, <= 0FF = right
				int objSize = obj2TopHalf >> 6;
				int isHorizontalFlip = 0;
				int isVerticalFlip = 0;
				
				if(rotationScalingFlag == 0) {
					isHorizontalFlip = (obj2TopHalf >> 4) & 0x1;
					isVerticalFlip = (obj2TopHalf >> 5) & 0x1;
				}
				
				int obj3 = (ncerBuffer.get() & 0xFF) | (ncerBuffer.get() & 0xFF) << 8;
				int tileNumber = obj3 & 0x3FF;
				int paletteNumber = obj3 >> 12;
				
				Size size = OBJSIZES[objShape][objSize];
				List<BufferedImage> tiles = tileLists.get(paletteNumber);
				int tileIterate = 0 ;
				if(paletteMode == 0x1) {
					tileIterate = tileNumber * 2; //only even tiles are allowed in 256 color mode
				}
				else {
					tileIterate = tileNumber < tilesUsed ? tileNumber * 4 : tileNumber;
				}
				//not sure what's up with 16/16 tiles
				int maxVerticalTiles = size.height() / TILESIZE;
				int maxHorizontalTiles = size.width() / TILESIZE;
				
				for(int h = 0; h < maxVerticalTiles; h++) {
					for(int w = 0; w < maxHorizontalTiles; w++) {
						int y = 128 + yPos;
						int x = isXPosNegative ? 256 - xPos : 256 + xPos;
						BufferedImage tile = tiles.get(tileIterate);
						
						y = isVerticalFlip == 1 ? y + TILESIZE * (maxVerticalTiles - h) : y + TILESIZE * h;
						x = isHorizontalFlip == 1 ? x + TILESIZE * (maxHorizontalTiles - w) : x + TILESIZE * w;
						
						g.drawImage(tile, x, y,
								(1 - isHorizontalFlip * 2) * TILESIZE, (1 - isVerticalFlip * 2) * TILESIZE, null);
						
						tileIterate++;
					}
				}
				
				tilesUsed += maxVerticalTiles * maxHorizontalTiles;
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
	
	private void getScreen(File targetDir, SubFile nscr) {
		Map<Integer, List<BufferedImage>> tileLists = getTiles(subFiles.get(TILEONEVAL));
		ByteBuffer nscrBuffer;
		buffer.position(nscr.offset());
		
		try {
			nscrBuffer = getUncompressedBuffer(nscr.size());
		}
		catch (IOException e) {
			return;
		}
		
		nscrBuffer.position(24);
		int screenWidth = nscrBuffer.getShort();
		int screenHeight = nscrBuffer.getShort();
		nscrBuffer.position(nscrBuffer.position() + 4); //0
		int screenDataSize = nscrBuffer.getInt();
		
		int maxHorizontalTiles = screenWidth / TILESIZE;
		int maxVerticalTiles = screenHeight / TILESIZE;
		
		BufferedImage image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics g = image.getGraphics();
		
		//TODO: some nscrs use 1 byte rotation mode
		for(int h = 0; h < maxVerticalTiles; h++) {
			for(int w = 0; w < maxHorizontalTiles; w++) {
				int data = nscrBuffer.getShort();
				int tileNum = data & 0x3FF;
				int isHorizontalFlip = (data >> 10) & 0x1;
				int isVerticalFlip = (data >> 11) & 0x1;
				int paletteNum = (data >> 12) & 0xF;
				
				List<BufferedImage> tiles = tileLists.get(paletteNum);
				BufferedImage tile = tiles.get(tileNum);
				
				//flipping is on a singular tile basis here
				int y = isVerticalFlip == 1 ? TILESIZE * (h + 1) : TILESIZE * h;
				int x = isHorizontalFlip == 1 ? TILESIZE * (w + 1) : TILESIZE * w;
				
				g.drawImage(tile, x, y,
					(1 - isHorizontalFlip * 2) * TILESIZE, (1 - isVerticalFlip * 2) * TILESIZE, null);
			}
		}
		try {
			String filename = file.getName().substring(0, file.getName().lastIndexOf('.'));
			String foldername = file.getParent().substring(file.getParent().lastIndexOf('\\') + 1);
			ImageIO.write(image, "png", new File(targetDir.getAbsolutePath(), foldername + "." + filename + ".png"));
		}
		catch (IOException e) {
			
		}
		g.dispose();
	}
	
	
	record Bank(int cellCount, int objOffset) {
	}
	
	record Size(int width, int height) {
	}
	
	record SubFile(int offset, int size) {
	}
}
