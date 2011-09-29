import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.JFrame;
import javax.swing.JPanel;


public class Test extends JPanel {

	private static final String IMAGESET_PATH = "resources/img/testdata_in/lena/";
	private static final String ORIG_FILENAME = "resources/img/testdata_in/natural/lena_purepng.png";
	private static final String DEST_FILENAME = ORIG_FILENAME.replace(".png", "_new.png");
	
	private BufferedImage image;
	
	private DiffVisualizer diffVisualizer;
	
	private boolean singleImage = true;
	
	PngReader pngr;
	PngWriter pngw;
	
	private HashMap<Integer, Double> bppMap = new HashMap<Integer, Double>();
	private ArrayList<String> ownedPictures = new ArrayList<String>();
	private ArrayList<String> gotOwnedPictures = new ArrayList<String>();
	
	private int imageCounter = 0;
	
	/**
	 * Allowed FileExtensions are saved here
	 * 
	 * @author Robin Oster (robin.oster@students.fhnw.ch)
	 *
	 */
	private enum AllowedExtension {
		
		PNG(".png");
		//JPEG(".jpg");
		// TIFF(".tiff");
		
		private final String extension;
		
		private AllowedExtension(String extension) {
			this.extension = extension;
		}
	}
	
	public Test() {
		
		if(singleImage) {
			
			pngr = new PngReader(ORIG_FILENAME);
			pngw = new PngWriter(DEST_FILENAME, pngr.imgInfo);
			pngw.setFilterType(PngWriter.FILTER_NN);
			
			pngw.setOverrideFile(true);
			pngw.prepare(pngr); 
			
			/*
			System.out.println("Grayscale: " + pngr.imgInfo.greyscale);
			System.out.println("Channel: " + pngr.imgInfo.channels);
			System.out.println("Alpha: " + pngr.imgInfo.alpha);
			System.out.println("Bit-depth: " + pngr.imgInfo.bitDepth);
			System.out.println("Indexed: " +  pngr.imgInfo.indexed);
			System.out.println("Bytes Pixel: " +  pngr.imgInfo.bytesPixel);
			*/
			
			diffVisualizer = new DiffVisualizer(ORIG_FILENAME, pngw.imgInfo.cols, pngw.imgInfo.rows);
			
			for (int row = 0; row < pngr.imgInfo.rows; row++) {
				ImageLine l1 = pngr.readRow(row);
				pngw.writeRow(l1);
			}
			
			
			// only visualize the diff map with the NN Filter
			if(pngw.getFilterType() == PngWriter.FILTER_NN) {
				diffVisualizer.createImage(pngw.diffValues);
				diffVisualizer.writeImage();
			}
			
			pngr.end();
			pngw.end();
						
			
			// System.out.println("TOTAL ERROR SUM: " + pngw.getDiffSum());
			// System.out.println("TOTAL AVERAGE ERROR PER PIXEL: " + (double)pngw.getDiffSum()/(double)(pngw.imgInfo.cols*pngw.imgInfo.rows));
		    System.out.println("Bpp: " + (double)(new File(DEST_FILENAME).length()*8)/(pngw.imgInfo.cols*pngw.imgInfo.rows));
			
			// JFRAME stuff
			JFrame frame = new JFrame("WOIPV - Neural Network Compression");
			frame.setSize(new Dimension(1024, 768));
			frame.setLayout(new BorderLayout());
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			
			
			this.setPreferredSize(new Dimension(800, 600));
			
			frame.add(this, BorderLayout.CENTER);
			
			// Read and Display the newly created image
			readAndDisplayImage();
			
			frame.setVisible(true);
		} else {
			listFilesForFolder(new File(IMAGESET_PATH));
			
			System.out.println("--------------------------------------------------");
			System.out.println("SUMMARY: ");
			System.out.println("¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡");
			
			// Print Summary
			for(Entry<Integer, Double> entry : bppMap.entrySet()) {
				
				System.out.println("Filter: " + getFilterName(entry.getKey()) + " => " + entry.getValue()/(double)imageCounter);
			}
			
			System.out.println("--------------------------------------------------");
			System.out.println("OWNED Pictures: ");
			System.out.println("¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡");
			
			
			// Print all files which are owned by my filter
			for(String ownedFilename : ownedPictures) {
				System.out.println("Owned Filename: " + ownedFilename);
			}
			
			System.out.println("--------------------------------------------------");
			System.out.println("GOT OWNED Pictures: ");
			System.out.println("¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡");
			
			// Print all files which are owned by my filter
			for(String gotOwnedFilename : gotOwnedPictures) {
				System.out.println("Got owned Filename: " + gotOwnedFilename);
			}
		}
	}
	
	
	private void doTest(String filename) {
		
		System.out.println("----------------------------------------");
		System.out.println("File: " + filename);
		
		imageCounter++;
		
		double bppValues[] = new double[6];
		
		for(int i = 0; i < 6; i++) {
			pngr = new PngReader(filename);
			
			String destFilename = filename.replace("testdata_in", "testdata_out"); 
			
			pngw = new PngWriter(destFilename, pngr.imgInfo);
			pngw.setFilterType(i);
			
			pngw.setOverrideFile(true);
			pngw.prepare(pngr); 
			
			/*
			System.out.println("Grayscale: " + pngr.imgInfo.greyscale);
			System.out.println("Channel: " + pngr.imgInfo.channels);
			System.out.println("Alpha: " + pngr.imgInfo.alpha);
			System.out.println("Bit-depth: " + pngr.imgInfo.bitDepth);
			System.out.println("Indexed: " +  pngr.imgInfo.indexed);
			System.out.println("Bytes Pixel: " +  pngr.imgInfo.bytesPixel);
			*/
			
			// diffVisualizer = new DiffVisualizer(ORIG_FILENAME, pngw.imgInfo.cols, pngw.imgInfo.rows);
			
			for (int row = 0; row < pngr.imgInfo.rows; row++) {
				ImageLine l1 = pngr.readRow(row);
				pngw.writeRow(l1);
			}
			
			/*
			// only visualize the diff map with the NN Filter
			if(pngw.getFilterType() == PngWriter.FILTER_NN) {
				diffVisualizer.createImage(pngw.diffValues);
				diffVisualizer.writeImage();
			}
			*/
			
			
			pngr.end();
			pngw.end();
						
			
			// System.out.println("TOTAL ERROR SUM: " + pngw.getDiffSum());
			// System.out.println("TOTAL AVERAGE ERROR PER PIXEL: " + (double)pngw.getDiffSum()/(double)(pngw.imgInfo.cols*pngw.imgInfo.rows));
			double bpp = (double)(new File(destFilename).length()*8)/(pngw.imgInfo.cols*pngw.imgInfo.rows);
			
			double filesize = new File(destFilename).length()*8;

			bppValues[i] = bpp;
			
			// System.out.println("Filter: " + getFilterName(i) + " => " + bpp);
		    
			if(bppMap.containsKey(i)) {
				
				// add the calculated bpp
				bppMap.put(i, bppMap.get(i) + bpp);
			} else {
				bppMap.put(i, bpp);
			}
			
			
			
			// Check if the NN Filter is better than all the other filters
			if(i == PngWriter.FILTER_NN) {
				boolean nnBetterThanAll = true;
				boolean nnWorst = true;
				
				// Compare with the other filters
				for(int z = 0; z < 5; z++) {
					if(bppValues[z] < bpp)
						nnBetterThanAll = false;
					
					if(bppValues[z] > bpp)
						nnWorst = false;
				}
				
				if(nnBetterThanAll)
					ownedPictures.add(filename);
				
				if(nnWorst)
					gotOwnedPictures.add(filename);
			}
		}
	}
	
	
	private void readAndDisplayImage() {
		PngReader pngr = new PngReader(DEST_FILENAME);
		
		image = new BufferedImage(pngr.imgInfo.cols, pngr.imgInfo.rows, BufferedImage.TYPE_4BYTE_ABGR);
		
		for (int row = 0; row < pngr.imgInfo.rows; row++) {
			ImageLine l1 = pngr.readRow(row);
			
			for(int col = 0; col < pngr.imgInfo.cols-1; col++) {
				image.setRGB(col, row, l1.getPixelYA(col));
			}
		}
		
		repaint();
	}
	
	
	/**
	 * Get file the file extension of a filepath
	 * 
	 * @param filePath
	 * 
	 * @return String extension
	 */
	private boolean checkFileExtension(String filePath) {
		int dotPosition = filePath.lastIndexOf(".");
		String extension = "";
		if (dotPosition != -1) {
		    extension = filePath.substring(dotPosition);
		}

		for(AllowedExtension allowedExtension : AllowedExtension.values()) {
			if(allowedExtension.extension.equals(extension)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * List files of folder
	 * 
	 * @param folder
	 */
	public void listFilesForFolder(final File folder) {
		
	    for (final File fileEntry : folder.listFiles()) {
	        if (fileEntry.isDirectory()) {
	            listFilesForFolder(fileEntry);
	        } else {
	        	
	        	// check if the file extension is correct
	        	if(checkFileExtension(fileEntry.getPath())) {
		        	// writeData(fileEntry.getAbsolutePath());
	        		// do something
	        		doTest(fileEntry.getAbsolutePath());
	        	}
	        }
	    }
	}

	@Override
	public void paint(Graphics g) {
		
		if(singleImage) {
			super.paint(g);
			
			// Draw the image
			g.drawImage(image, 0, 0, null);
			
			// Draw diff image
			g.drawImage(diffVisualizer.getBufferedImage(), pngr.imgInfo.cols, 0, null);
		}
	}
	
	private String getFilterName(int i) {
		switch(i) {
			case 0:
				return "None";
			case 1:
				return "Sub";
			case 2:
				return "Up";
			case 3:
				return "Average";
			case 4:
				return "Paeth";
			case 5:
				return "Neural Network";
			default:
				return "Not recognized filter";
		}
	}

	public static void main(String[] args) {
		new Test();
	}
}
