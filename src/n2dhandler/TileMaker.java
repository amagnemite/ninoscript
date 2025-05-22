package n2dhandler;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

public class TileMaker {
	
	private static final int FOURTILES = 32;
	private static final int TWOTILES = 16;
	private static final int ONETILE = 8;
	private static final int TILEHEIGHT = 8;
	
	public TileMaker() {
		
	}
	
	public static void makeTiles(File[] files, File saveDir) {
		Iterator<File> iterator = Arrays.asList(files).iterator();
		List<BufferedImage> tiles = new ArrayList<BufferedImage>();
		int totalWidth = 0;
		
		while(iterator.hasNext()) {
			BufferedImage image = null;
			try {
				image = ImageIO.read(iterator.next());
			}
			catch (IOException e) {
				
			}
			int unallocatedWidth = image.getWidth();
			int x = 0;
			
			while(unallocatedWidth > 0) {
				if(unallocatedWidth >= FOURTILES) {
					getSubimages(image, tiles, x, FOURTILES);
					unallocatedWidth -= FOURTILES;
					x += FOURTILES;
					totalWidth += FOURTILES * 2;
					continue;
				}
				//after this point should be final chunks
				
				if(unallocatedWidth > TWOTILES) {
					makePaddedTiles(image, tiles, x, 0, unallocatedWidth, FOURTILES);
					makePaddedTiles(image, tiles, x, TILEHEIGHT, unallocatedWidth, FOURTILES);
					
					unallocatedWidth = 0;
					totalWidth += FOURTILES  * 2;
					continue;
				}
				
				if(unallocatedWidth == TWOTILES) {
					getSubimages(image, tiles, x, TWOTILES);
					unallocatedWidth -= TWOTILES;
					totalWidth += TWOTILES  * 2;
					continue;
				}
				
				//TODO: total tile width needs to be divisible by 32
				
				if(iterator.hasNext() || unallocatedWidth > 8) {
					makePaddedTiles(image, tiles, x, 0, unallocatedWidth, TWOTILES);
					makePaddedTiles(image, tiles, x, TILEHEIGHT, unallocatedWidth, TWOTILES);
					
					unallocatedWidth = 0;
					totalWidth += TWOTILES  * 2;
					continue;
				}
				
				if(unallocatedWidth == ONETILE) {
					getSubimages(image, tiles, x, ONETILE);
					unallocatedWidth -= ONETILE;
					totalWidth += ONETILE * 2;
				}
				else {
					makePaddedTiles(image, tiles, x, 0, unallocatedWidth, ONETILE);
					makePaddedTiles(image, tiles, x, TILEHEIGHT, unallocatedWidth, ONETILE);
					
					unallocatedWidth = 0;
					totalWidth += ONETILE * 2;
				}
			}
		}
		BufferedImage finalImage = new BufferedImage(totalWidth, TILEHEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics g = finalImage.getGraphics();
		int x = 0;
		for(BufferedImage image : tiles) {
			g.drawImage(image, x, 0, null);
			x += image.getWidth();
		}
		g.dispose();
		try {
			ImageIO.write(finalImage, "png", new File(saveDir, "tiles" + ".png"));
		}
		catch (IOException e) {
			
		}
	}
	
	private static void getSubimages(BufferedImage image, List<BufferedImage> list, int x, int width) {
		list.add(image.getSubimage(x, 0, width, TILEHEIGHT));
		int secondHeight = TILEHEIGHT;
		if(image.getHeight() < TILEHEIGHT * 2) {
			secondHeight = image.getHeight() - TILEHEIGHT;
		}
		list.add(image.getSubimage(x, 8, width, secondHeight));
	}
	
	private static void makePaddedTiles(BufferedImage image, List<BufferedImage> list, int x, int y, int width, int rectWidth) {
		BufferedImage subimage = new BufferedImage(rectWidth, TILEHEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics g = subimage.getGraphics();
		g.setColor(new Color(248, 0, 248));
		
		int processedHeight = TILEHEIGHT;
		if(y != 0) {
			if(image.getHeight() < TILEHEIGHT * 2) {
				processedHeight = image.getHeight() - TILEHEIGHT;
			}
		}
		
		g.drawImage(image.getSubimage(x, y, width, processedHeight), 0, 0, null);
		g.fillRect(width, 0, rectWidth - width, TILEHEIGHT);
		list.add(subimage);
		
		g.dispose();
	}
}
