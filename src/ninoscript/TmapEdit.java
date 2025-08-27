package ninoscript;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;

public class TmapEdit {
	//does not fully support all variants of .tmap
	private static final int LAYERCOUNT = 4; //bg, foreground, layer 2, collision
	private int tileWidthCount;
	private int tileHeightCount;
	private File pngFile;
	private File tmap;
	byte[] tmapBuffer;
	
	public void convertPNGToTMAP(int replacingLayer) throws FileNotFoundException, IOException {
		ByteBuffer input;
		IntFileInputStream inputStream = new IntFileInputStream(tmap);
		BufferedImage png = ImageIO.read(pngFile);
		byte[] buffer = new byte[4];
		BTX[][] tiles;
		
		input = ByteBuffer.allocate(inputStream.available());
		input.order(ByteOrder.LITTLE_ENDIAN);
		inputStream.read(input.array());
		inputStream.close();
		
		input.get(buffer);
		if(buffer[0] != 0x74 && buffer[1] != 0x6D && buffer[2] != 0x61 && buffer[3] != 0x70) {
			return;
		}
		
		int tilesPerLayer = input.getInt();
		tileWidthCount = input.getInt();
		tileHeightCount = input.getInt();
		
		tiles = new BTX[LAYERCOUNT][];
		for(int layer = 0; layer < LAYERCOUNT; layer++) {
			tiles[layer] = new BTX[tilesPerLayer];
			
			for(int j = 0; j < tilesPerLayer; j++) { //getting the tiles
				input.position(0x10 + layer * tilesPerLayer * 8 + j * 8);
				int offset = input.getInt();
				int size = input.getInt();
				
				if(offset == 0 || size == 0) {
					continue;
				}
				
				input.position(offset);
				byte[] data = new byte[size];
				input.get(data);
			}
		}
		
		
		
		
	}
}
