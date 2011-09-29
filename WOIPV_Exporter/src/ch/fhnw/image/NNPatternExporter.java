/**
 * 
 */
package ch.fhnw.image;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * NNPatternExporter loads a bunch of images and create a pattern file (.pat) which is
 * used by the Neural Network program (JavaNNS)
 * 
 * @author Robin Oster (robin.oster@students.fhnw.ch)
 *
 */
public class NNPatternExporter {
	
	private int sizeX = 8;
	private int sizeY = 4;
	
	private static final int AMOUNT_OF_SUB_IMAGES = 750;
	
	private StringBuffer buffer = new StringBuffer();
	private Writer output;
	
	private boolean displayOutput = false;
	
	private static final File IMAGE_PATH = new File("/Users/prine/Documents/FHNW/Studium/Module/6.Semester/woipv/Neural Networks/PNG-NN/images/bigsetboth/");
	
	private static final String OUTPUT_FILE = "/Users/prine/Documents/FHNW/Studium/Module/6.Semester/woipv/Neural Networks/PNG-NN/compression_validation.pat";
	
	private File outputFile;
	
	private int patternCounter = 1;
		
	/**
	 * Allowed FileExtensions are saved here
	 * 
	 * @author Robin Oster (robin.oster@students.fhnw.ch)
	 *
	 */
	private enum AllowedExtension {
		
		PNG(".png"),
		JPEG(".jpg");
		// TIFF(".tiff");
		
		private final String extension;
		
		private AllowedExtension(String extension) {
			this.extension = extension;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		NNPatternExporter imagePreparer = new NNPatternExporter();
		imagePreparer.listFilesForFolder(IMAGE_PATH);
	}
	
	private void init() {
	    outputFile = new File(OUTPUT_FILE);
	    
		try {
			output = new BufferedWriter(new FileWriter(outputFile));
			System.out.println(output);
		} catch (IOException e) {
			System.out.println("Can't create BufferedWrite: " + e.getMessage());
		}
	    
	    // write header data for the pattern file
	    buffer = new StringBuffer();
		SimpleDateFormat sf = new SimpleDateFormat("E M dd HH:mm:ss yyyy");
		String dateString = sf.format(new Date());
		buffer.append("SNNS pattern definition file V3.2 \n");
		buffer.append("generated at " + dateString + "\n\n\n");
		
		// calculate the amount of Images..
		int numberOfPictures = countNumberOfPictures();
		
		buffer.append("No. of patterns : " + numberOfPictures*AMOUNT_OF_SUB_IMAGES + "\n");
		buffer.append("No. of input units : " + ((sizeX * sizeY) - (sizeX/2)) +  "\n");
		buffer.append("No. of output units : 1\n\n");
		buffer.append("");
		
	}
	
	private int countNumberOfPictures() {
		int i = 0;
		
	    for (final File fileEntry : IMAGE_PATH.listFiles()) {
	        if (fileEntry.isDirectory()) {
	            listFilesForFolder(fileEntry);
	        } else {
	        	
	        	// check if the file extension is correct
	        	if(checkFileExtension(fileEntry.getPath())) {
		        	i++;
	        	}
	        }
	    }
	    
	    return i;
	}
	
	public NNPatternExporter() {
		
		// initialize
		init();
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
		        	writeData(fileEntry.getAbsolutePath());
	        	}
	        }
	    }
	    
		System.out.println("#Patters: " + (patternCounter - 1));
	    
	    // close the stream and destroy the StringBuffer..
	    /*
		try {
			System.out.println("Closing stream..");
			output.close();
		} catch (IOException e) {
			System.out.println("Can't close output stream");
		}
		*/
	}
	
	/**
	 * Calculate transformed value
	 * @param actVal
	 * @return
	 */
	private double calculateTransformedValue(double actVal) {
		return ((actVal/255.0) * 0.6) + 0.2;
	}
	
	/**
	 * Write data into the pattern file for the neural Network
	 * 
	 * @param fileName
	 */
	private void writeData(String fileName) {
		int argb;
		
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
		symbols.setDecimalSeparator('.');
		
		DecimalFormat df = new DecimalFormat("#.#######", symbols);
		
		try {
			
			BufferedImage temp = ImageIO.read(new File(fileName));
			
			if(temp != null) {
				
				if(displayOutput) System.out.println("Filename: " + fileName);
				
				int randX = 0;
				int randY = 0;
				
				// how many times should it take out a small area for the sample file
				for(int x = 0; x < AMOUNT_OF_SUB_IMAGES; x++) {
					
					randX = Math.abs(new Random().nextInt() % temp.getWidth());
					randY = Math.abs(new Random().nextInt() % temp.getHeight());
					
					// find a spot in the picture
					while(((randX + sizeX) > temp.getWidth()) || (randY + sizeY) > temp.getHeight()) {
						randX = Math.abs(new Random().nextInt() % temp.getWidth());
						randY = Math.abs(new Random().nextInt() % temp.getHeight());
					}
					
		        	buffer.append("# Input pattern " + patternCounter + ": \n");
					
		        	int counter = 1;
		        	int saveI = 0;
		        	int saveY = 0;
		        	
					for(int i = randY; i < randY + sizeY; i++) {
						
						int offsetX = sizeX;
						
			        	if((counter % sizeY) == 0)
			        		offsetX = sizeX - (sizeX/2);
						
						for(int y = randX; y < randX + offsetX; y++) {
							
							// System.out.println("(" + i + ", " + y + ")");
							// System.out.println("image size: (" + temp.getWidth() + ", " + temp.getHeight() + ")");
							
							argb = temp.getRGB(y, i);
							
							// get int grayscale value in the picture
						    int grayscale = (argb) & 0xff;
						    
						    String outputString = df.format(calculateTransformedValue(grayscale));
						    
							if(displayOutput) System.out.print(outputString + " ");
							buffer.append(outputString + " ");
							
							saveY = y;
						}
						
						if(displayOutput) System.out.print("\n");
						buffer.append("\n");
						
						saveI = i;
						
						counter++;
					}
					
				    
				    try {
				    	
				    	buffer.append("# Output pattern " + patternCounter + "\n");
				    	
						// get the predefine output pixel and store it also in the buffer
				    	argb = temp.getRGB(saveY + 1, saveI);
					    int grayscale = (argb) & 0xff;
					    String outputString = df.format(calculateTransformedValue(grayscale));
					    buffer.append(outputString + "\n");
					    
					    // write the whole string buffer in the defined file
					    output.append(buffer.toString());
					    output.flush();
					    
					    // After every write of the buffer we have to clear it for the next pattern
						buffer = new StringBuffer();
						
						if(displayOutput) System.out.println("Write pattern nr. " + patternCounter);
					} catch (IOException e) {
						System.out.println("Couldn't write data into the file.." + e.getMessage());
					}
					
					patternCounter++;
				}
			}
			
		} catch (IOException e) {
			System.out.println("Couldn't read the file " + fileName);
		}
	}
}
