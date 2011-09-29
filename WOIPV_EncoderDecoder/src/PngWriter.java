
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import ch.fhnw.students.woipv.neuralnetwork.NeuralNetwork;
import ch.fhnw.students.woipv.parser.JNNSParser;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngChunk;
import ar.com.hjg.pngj.PngHelper;
import ar.com.hjg.pngj.PngIDatChunkOutputStream;
import ar.com.hjg.pngj.PngjException;
import ar.com.hjg.pngj.PngjOutputException;

/**
 * For writing a PNG image
 */
public class PngWriter {

	public static final int FILTER_NONE = 0;
	public static final int FILTER_SUB = 1;
	public static final int FILTER_UP = 2;
	public static final int FILTER_AVERAGE = 3;
	public static final int FILTER_PAETH = 4;
	public static final int FILTER_NN = 5; // neural network filter

	private String filename; 
	private boolean overwrite = false; 

	public final ImageInfo imgInfo;

	private final int valsPerRow; // cols * channels
	private final int bytesPerRow; // en png. sin incluir el byte de tipo de filtrado !
	private int compLevel = 6; // 0 - 9
	private int filterType = FILTER_NONE;   
	private Double dpi = null; // dots per inch
	private PngTxtInfo txtInfo = new PngTxtInfo(); // for new textual text chunks, optional!

	private boolean initialized = false;
	private final CRC32 crcEngine;
	private int rowNum = -1; // numero de linea actual

	private int[] scanline = null; // linea actual, one sample per element (layout differnt from rowb!)bytesPixel
	private int[] rowb = null; // linea covnertida a byte; empieza en 1; (el 0 se usara para tipo de filtro)
	private int[] rowbprev = null; // rowb previa
	private byte[] rowbfilter = null; // linea actual filtrada

	private OutputStream os;
	private PngIDatChunkOutputStream datStream;
	private DeflaterOutputStream datStreamDeflated;
	
	private NeuralNetwork neuralNetwork;
	private JNNSParser parser;
	
	private int[][] allRows;
	
	private int sumDiff = 0;
	
	private boolean firstTime = true;
	
	public static double[][][] copyInputValues;
	
	public int[][] diffValues;
	
	
	private int inputSizeX = 8;
	private int inputSizeY = 4;
		

	public class PngTxtInfo {
		public String title;
		public String author;
		public String description;
		public String creation_time;// = (new Date()).toString();
		public String software;
		public String disclaimer;
		public String warning;
		public String source;
		public String comment;

		public void writeChunks() {
			writeChunk("Title", title);
			writeChunk("Author", author);
			writeChunk("Description", description);
			writeChunk("Creation Time", creation_time);
			writeChunk("Software", software);
			writeChunk("Disclaimer", disclaimer);
			writeChunk("Software", software);
			writeChunk("Warning", warning);
			writeChunk("Source", source);
			writeChunk("Comment", comment);
		}

		private void writeChunk(String name, String val) {
			if (val == null)
				return;
			PngChunk p = PngChunk.createTextChunk(name, val, crcEngine);
			p.writeChunk(os);
		}

	}

    public PngWriter(OutputStream outputStream, ImageInfo imgInfo) {
        this.os = outputStream;
        this.imgInfo = imgInfo;
        this.bytesPerRow = imgInfo.cols * imgInfo.bytesPixel;
        this.valsPerRow = imgInfo.cols * imgInfo.channels;

        crcEngine = new CRC32();
        // prealocamos
        scanline = new int[valsPerRow];
        rowb = new int[bytesPerRow + 1];
        rowbprev = new int[bytesPerRow + 1];
        rowbfilter = new byte[bytesPerRow + 1];
        
        // stores all pixels => for the NN Filter
        allRows = new int[imgInfo.rows][imgInfo.cols];
        
        copyInputValues = new double[imgInfo.rows][imgInfo.cols][];
        
        diffValues = new int[imgInfo.rows][imgInfo.cols + 1];
        
        // setup the parser and the neural network
        parser = new JNNSParser();
        neuralNetwork = parser.getNeuralNetwork();
    }

	public PngWriter(String filename, ImageInfo imgInfo) {
		this.filename = filename;
		this.imgInfo = imgInfo;
		this.bytesPerRow = imgInfo.cols * imgInfo.bytesPixel;
		this.valsPerRow = imgInfo.cols * imgInfo.channels;

		crcEngine = new CRC32();
		// prealocamos
		scanline = new int[valsPerRow];
		rowb = new int[bytesPerRow + 1];
		rowbprev = new int[bytesPerRow + 1];
		rowbfilter = new byte[bytesPerRow + 1];
		
        // stores all pixels => for the NN Filter
        allRows = new int[imgInfo.rows][imgInfo.cols];
        
        copyInputValues = new double[imgInfo.rows][imgInfo.cols][];
        
        diffValues = new int[imgInfo.rows][imgInfo.cols + 1];
        
        // setup the parser and the neural network
        parser = new JNNSParser();
        neuralNetwork = parser.getNeuralNetwork();
	}

	/**
	 * To be called after setting parameters and before writing lines. If not
	 * called explicityly will be called implicitly. 
	 * 
	 * @param reader  Optional. If not null, pallette and some ancillary chunks will be copied.
	 * 
	 * TODO: mejorar tratamiento de chunks, como palette.
	 * http://www.w3.org/TR/PNG/#table53
	 */
	public void prepare(PngReader reader) {
		if (initialized)
			return;
		if(this.os == null) {
            File f = new File(filename);
            if (f.exists() && !overwrite)
                throw new PngjException("File exists (and overwrite=false) " + filename);
            try {
                this.os = new FileOutputStream(f);
            } catch (FileNotFoundException e) {
                throw new PngjOutputException("error opening " + filename + " for writing", e);
            }
        }
		datStream = new PngIDatChunkOutputStream(this.os, 8192);
		datStreamDeflated = new DeflaterOutputStream(datStream, new Deflater(compLevel));
		writeHeader();
		// chunks varios. para el IPHYS tiene prioridad el dpi nuestro
		boolean physChunkDone = false;
		if (dpi != null) {
			writePhysChunk();
			physChunkDone = true;
		}
		if (reader != null) {
			for (PngChunk chunk : reader.getChunks1()) {
				if (!chunk.id.equals("PLTE") && (chunk.isCritical() || !chunk.isSafeToCopy()))
					continue;
				if (chunk.id.equals(PngHelper.IPHYS_TEXT) && physChunkDone)
					continue;
				chunk.writeChunk(this.os);
			}
		}
		txtInfo.writeChunks();
		initialized = true;
	}

	public void doInit() {
		prepare(null);
	}

	/**
	 * Write id header and also "IHDR" chunk
	 */
	private void writeHeader() {
		PngHelper.writeBytes(os, PngHelper.pngIdBytes);
		// http://www.libpng.org/pub/png/spec/1.2/PNG-Chunks.html
		ByteArrayOutputStream tstream = new ByteArrayOutputStream();
		PngHelper.writeInt4(tstream, imgInfo.cols);
		PngHelper.writeInt4(tstream, imgInfo.rows);
		PngHelper.writeByte(tstream, (byte) (imgInfo.bitDepth));
		int colormodel = 0;
		if (imgInfo.alpha)
			colormodel += 0x04;
		if (imgInfo.indexed)
			colormodel += 0x01;
		if (!imgInfo.greyscale)
			colormodel += 0x02;

		PngHelper.writeByte(tstream, (byte) colormodel); // direct model :

		PngHelper.writeByte(tstream, (byte) 0); // compression method (siempre 0=deflate)
		PngHelper.writeByte(tstream, (byte) 0); // filter method (siempre 0)
		PngHelper.writeByte(tstream, (byte) 0); // no interlace
		byte[] b = tstream.toByteArray();
		if (b.length != 13)
			throw new PngjOutputException("BAD IDHR!");
		PngHelper.writeChunk(os, b, 0, b.length, PngHelper.IHDR, crcEngine);

	}

	/**
	 * writes physical chunk: image resolution (from dpi attribute)
	 */
	protected void writePhysChunk() {
		ByteArrayOutputStream tstream = new ByteArrayOutputStream();
		int pixelsPerMeter = (int) (dpi * 100.0 / 2.54 + 0.5);
		PngHelper.writeInt4(tstream, pixelsPerMeter);
		PngHelper.writeInt4(tstream, pixelsPerMeter);
		PngHelper.writeByte(tstream, (byte) 1); // meters
		byte[] b = tstream.toByteArray();
		PngHelper.writeChunk(os, b, 0, b.length, PngHelper.IPHYS, crcEngine);
	}

	/**
	 * writes ending chunk.
	 */
	protected void endchunk() { // chunk vacio
		byte[] b = new byte[] {};
		PngHelper.writeChunk(os, b, 0, 0, PngHelper.IEND, crcEngine);
	}

	/**
	 * Writes a full image row. This must be called sequentially from n=0 to
	 * n=rows-1 One integer per sample , in the natural order: R G B R G B
	 * ... (or R G B A R G B A... if has alpha) The values should be between 0
	 * and 255 for 8 bitspc images, and between 0- 65535 form 16 bitspc images
	 * (this applies also to the alpha channel if present) The array can be
	 * reused.
	 * 
	 * @param newrow
	 *            Array of pixel values
	 * @param n
	 *            Number of row, from 0 (top) to rows-1 (bottom)
	 */
	public void writeRow(int[] newrow, int n) {
		if (!initialized)
			doInit();
		if (n < 0 || n > imgInfo.rows)
			throw new RuntimeException("invalid value for row ");
		rowNum++;
		if (rowNum != n)
			throw new RuntimeException("write order must be strict for rows " + n + " (expected=" + rowNum + ")");
		scanline = newrow;
		// swap
		int[] tmp = rowb;
		rowb = rowbprev;
		rowbprev = tmp;
		
		convertRowToBytes();
		
		allRows[n] = Arrays.copyOf(newrow, newrow.length);
		// allRows[n] = newrow;
		
		
		filterRow();
	
		try {
			datStreamDeflated.write(rowbfilter, 0, bytesPerRow + 1);
		} catch (IOException e) {
			throw new PngjOutputException(e);
		}
	}

	/**
	 * this uses the row number from the imageline!
	 */
	public void writeRow(ImageLine imgline) {
		writeRow(imgline.scanline, imgline.getRown());
	}

	/**
	 * Finalizes the image creation and closes the file stream. This MUST be
	 * called after writing the lines.
	 */
	public void end() {
		if (rowNum != imgInfo.rows - 1)
			throw new PngjOutputException("all rows have not been written");
		try {
			datStreamDeflated.finish();
			datStream.flush();
			endchunk();
			os.close();
		} catch (IOException e) {
			throw new PngjOutputException(e);
		}
	}

	private void filterRow() {
		// warning: filters operation rely on: "previos row" (rowbprev) is initialized to 0 the first time 
		rowbfilter[0] = (byte) filterType;
		switch (filterType) {
		case FILTER_NONE:
			filterRowNone();
			break;
		case FILTER_SUB:
			filterRowSub();
			break;
		case FILTER_UP:
			filterRowUp();
			break;
		case FILTER_AVERAGE:
			filterRowAverage();
			break;
		case FILTER_PAETH:
			filterRowPaeth();
			break;
		case FILTER_NN:
			filterRowNN();
			break;
		default:
			throw new PngjOutputException("Filter type " + filterType + " not implemented");
		}
	}

	private void filterRowNone() {
		for (int i = 1; i <= bytesPerRow; i++) {
			rowbfilter[i] = (byte) rowb[i];
		}
	}

	private void filterRowSub() {
		int i, j;
		for (i = 1; i <= imgInfo.bytesPixel; i++) {
			rowbfilter[i] = (byte) rowb[i];
		}
		for (j = 1, i = imgInfo.bytesPixel + 1; i <= bytesPerRow; i++, j++) {
			
			rowbfilter[i] = (byte) (rowb[i] - rowb[j]);
			sumDiff += Math.abs((byte) (rowb[i] - rowb[j]));
		}
	}

	private void filterRowUp() {
		for (int i = 1; i <= bytesPerRow; i++) {
			rowbfilter[i] = (byte) (rowb[i] - rowbprev[i]);
			sumDiff += Math.abs((byte) (rowb[i] -  rowbprev[i]));
		}
	}

	private void filterRowAverage() {
		int i, j, x;
		for (i = 1; i <= bytesPerRow; i++) {
			if (rowb[i] < 0 || rowb[i] > 255)
				throw new PngjOutputException("??" + rowb[i]);
			if (rowbprev[i] < 0 || rowbprev[i] > 255)
				throw new PngjOutputException("??" + rowbprev[i]);
		}
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= bytesPerRow; i++, j++) {
			x = j > 0 ? rowb[j] : 0;
			rowbfilter[i] = (byte) (rowb[i] - (rowbprev[i] + x) / 2);
			sumDiff += Math.abs((byte) (rowbfilter[i]));
		}
	}

	private void filterRowPaeth() {
		int i, j, x, y;
		for (i = 1; i <= bytesPerRow; i++) {
			if (rowb[i] < 0 || rowb[i] > 255)
				throw new PngjOutputException("??" + rowb[i] + " i=" + i + " row=" + rowNum);
			if (rowbprev[i] < 0 || rowbprev[i] > 255)
				throw new PngjOutputException("??" + rowbprev[i]);
		}
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= bytesPerRow; i++, j++) {
			x = j > 0 ? rowb[j] : 0;
			y = j > 0 ? rowbprev[j] : 0;
			rowbfilter[i] = (byte) (rowb[i] - PngHelper.filterPaethPredictor(x, rowbprev[i], y));
			sumDiff += Math.abs((byte) (rowbfilter[i]));
		}
	}
	
	private void filterRowNN() {
		
		double[] inputValues = new double[(16*8)-8];
		
		int rowSum = 0;
		
		if(rowNum < (inputSizeY - 1)) {
			
			// Don't have enough information for using the neural network
			// Just copy the real value or use grayscale value
			for(int i = 1; i <= bytesPerRow; i++) {
				rowbfilter[i] = (byte)rowb[i];
				
				// no difference between it just copies the values, so write the difference 0
				diffValues[rowNum][i] = 0;
			}
			
		} else {
		
			for(int i = 0; i < imgInfo.cols; i++) {
				
				// do not use the neural network for the first 8 and the last 8 pixels
				if((i < inputSizeX/2) || (i > (imgInfo.cols - (inputSizeX/2 + 1)))) {
					// Don't have enough information for using the neural network
					// Just copy the real value or use grayscale....
					rowbfilter[i+1] = (byte)rowb[i+1];
					
					// no difference between it just copies the values, so write the difference 0
					diffValues[rowNum][i] = 0;
				} else {
					// use neural network
					int vCounter = 0;
					
					// Get the 16x8 pixel image from allRows
					for(int y = rowNum - (inputSizeY - 1); y <= rowNum; y++) {
						
						int maxZ = i + inputSizeX/2; 
		
						if(rowNum == y) {
							
							// Last row only use the first 8
							// Because we want to find out the 8th pixel
							maxZ = i;
						} 
						
						for(int x = i - inputSizeX/2; x < maxZ; x++) {
							inputValues[vCounter] = calculateTransformedValue(allRows[y][x]);
							vCounter++;
						}
						
					}
					
					// FOR DEBUGGING
					// copyInputValues[rowNum][i] = Arrays.copyOf(inputValues, inputValues.length);
					
					rowbfilter[i+1] = (byte)(rowb[i+1] - (byte)calculateTransformedValueBack(neuralNetwork.calculate(inputValues).getNeurons().get(0).result));
					
					
					diffValues[rowNum][i] = rowbfilter[i+1];
					
					sumDiff += Math.abs(rowbfilter[i+1]);
					rowSum += Math.abs(rowbfilter[i+1]);
				}
			}
		}
		
		// FOR DEBUGGING
		// System.out.println("ROW #" + rowNum + " => " + rowSum);
	}


	private void convertRowToBytes() {
		// http://www.libpng.org/pub/png/spec/1.2/PNG-DataRep.html
		int i, j, x;
		rowb[0] = (int) filterType;
		if (imgInfo.bitDepth == 8) {
			for (i = 0, j = 1; i < valsPerRow; i++) {
				rowb[j++] = ((int) scanline[i]) & 0xFF;
			}
		} else { // 16 bitspc
			for (i = 0, j = 1; i < valsPerRow; i++) {
				x = (int) (scanline[i]) & 0xFFFF;
				rowb[j++] = ((x & 0xFF00) >> 8);
				rowb[j++] = (x & 0xFF);
			}
		}
	}

	// /// several getters / setters - all this setters are optional
	// ////////////
	// see also the txtInfo property ////////////////////////////////////////

	/**
	 * if this is set, the file will be overwritten, if not an exception will be
	 * thrown
	 */
	public void setOverrideFile(boolean overrideFile) {
		this.overwrite = overrideFile;
	}

	/**
	 * Set physical resolution, in DPI (dots per inch) optional, only informative
	 */
	public void setDpi(Double dpi) {
		this.dpi = dpi;
	}

	/**
	 * Sets filter type: the recommend is the default FILTER_PAETH If the filter
	 * is not implemented an excpetion will be thrown when writing the image.
	 * Can be changed for each line.
	 */
	public void setFilterType(int filterType) {
		if (filterType < 0 || filterType > 5)
			throw new PngjException("filterType  invalid (" + filterType + ") Must be 0..5");
		this.filterType = filterType;
	}

	public int getFilterType() {
		return filterType;
	}

	/**
	 * compression level: between 0 and 9 (default:6)
	 */
	public void setCompLevel(int compLevel) {
		if (compLevel < 0 || compLevel > 9)
			throw new PngjException("Compression level invalid (" + compLevel + ") Must be 0..9");
		this.compLevel = compLevel;
	}

	public int getCompLevel() {
		return compLevel;
	}

	public int getCols() {
		return imgInfo.cols;
	}

	public int getRows() {
		return imgInfo.rows;
	}

	public String getFilename() {
		return filename;
	}
	
	public OutputStream getOutputStream() {
		return os;
	}
	
	private double calculateTransformedValue(int actVal) {
		// transform and round
		// return (double)Math.round((((double)actVal/255.0) * 0.6 + 0.2)*10000) / 10000;
		return (double)(actVal/255.0) * 0.6 + 0.2;
	}


	private int calculateTransformedValueBack(double actVal) {
		
		// round
		// actVal = (double)Math.round(actVal * 100000) / 100000.0d;

		/*
		if(actVal >= 0.8)
			return 255;
		
		if(actVal < 0)
			return 0;
			*/
		
		return (int)(((actVal-0.2)*(1/0.6)) * 255);
	}
	
	public int getDiffSum() {
		return this.sumDiff;
	}
}
