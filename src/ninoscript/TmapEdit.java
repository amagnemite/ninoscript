package ninoscript;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.ImageIO;

public class TmapEdit {
	//does not fully support all variants of .tmap
	private int tileWidthCount;
	private int tileHeightCount;
	private File pngFile;
	private File tmap;
	byte[] tmapBuffer;
	
	public void convertPNGToTMAP(int replacingLayer) throws FileNotFoundException, IOException {
		IntFileInputStream inputStream = new IntFileInputStream(tmap);
		BufferedImage png = ImageIO.read(pngFile);
		byte[] buffer = new byte[4];
		BTX[] tiles;
		
		if(buffer[0] != 0x74 && buffer[1] != 0x6D && buffer[2] != 0x61 && buffer[3] != 0x70) {
			inputStream.close();
			return;
		}
		
		int tileCount = inputStream.readU32();
		tileWidthCount = inputStream.readU32();
		tileHeightCount = inputStream.readU32();
		tiles = new BTX[tileCount];
		
		for(int i = 0; i < tileHeightCount; i++) {
			
		}
		
	}
}
