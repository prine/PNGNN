import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Visualize the pixel diff values
 * 
 * @author Robin Oster
 *
 */
public class BestVisualizer {

	BufferedImage bufferedImage;
	
	private String filename;
	private int width;
	private int height;
	
	/**
	 * Constructor
	 * 
	 * @param filename
	 * @param width
	 * @param height
	 */
	public BestVisualizer(String filename, int width, int height) {
		this.filename = filename;
		this.width = width;
		this.height = height;
		
		bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}
	
	/**
	 * Create the images
	 * 
	 * @param diffs
	 */
	public void createImage(int[][] diffs) {
		
		int max = Integer.MIN_VALUE;
		int min = Integer.MAX_VALUE;
		
		// Determine the max and the min value
		for(int y = 0; y < this.height; y++) {
			for(int x = 0; x < this.width; x++) {
				if(diffs[y][x] > max)
					max = diffs[y][x];
				
				if(diffs[y][x] < min)
					min = diffs[y][x];
			}
		}
		
		// scale from 0..255
		for(int y = 0; y < this.height; y++) {
			for(int x = 0; x < this.width; x++) {
				
				int scaledValue = ((Math.abs(diffs[y][x]) - min)*(255/(max-min)) - 100) * 10 + 128;
				
				scaledValue = (scaledValue < 0) ? 0 : scaledValue;
				scaledValue = (scaledValue > 255) ? 255 : scaledValue;
				
				int a = 255;
				int r = 255 - scaledValue; 		
				int g = 255 - scaledValue;
				int b = 255 - scaledValue;
				
				if(scaledValue == -1)
					System.out.println("FOUND!!!");
				
				if(r == 255 && g == 255)
					System.out.println(diffs[y][x] + " => " + scaledValue);
				
				int pixel = (a << 24) | (r << 16) | (g << 8) | b;
				
				bufferedImage.setRGB(x, y, pixel);
			}
		}
	}
	
	public BufferedImage getBufferedImage() {
		return this.bufferedImage;
	}
	
	/**
	 * Write the image
	 */
	public void writeImage() {	
		try {
			ImageIO.write(bufferedImage, "png", (new File(filename.replace(".png", "_diff_map.png"))));
		} catch (IOException e) {
			System.out.println("Couldn't write file!");
		}
	}
}
