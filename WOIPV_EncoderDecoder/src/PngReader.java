
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

import ch.fhnw.students.woipv.neuralnetwork.NeuralNetwork;
import ch.fhnw.students.woipv.parser.JNNSParser;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngChunk;
import ar.com.hjg.pngj.PngHelper;
import ar.com.hjg.pngj.PngIDatChunkInputStream;
import ar.com.hjg.pngj.PngjException;
import ar.com.hjg.pngj.PngjInputException;

/**
 * Reads a PNG image, line by line
 */
public class PngReader {
	public static final int FILTER_NONE = 0;
	public static final int FILTER_SUB = 1;
	public static final int FILTER_UP = 2;
	public static final int FILTER_AVERAGE = 3;
	public static final int FILTER_PAETH = 4;
	public static final int FILTER_NN = 5;
	
	public final ImageInfo imgInfo;
	public final String filename;
	private final InputStream is;
	private final InflaterInputStream idatIstream;
	private final PngIDatChunkInputStream iIdatCstream;
	private int offset = 0;
	private CRC32 crcengine;

	// chunks: agregar exclusivamente con addChunkToList()
	private static final int MAX_BYTES_CHUNKS_TO_LOAD = 64000;
	private int bytesChunksLoaded;
	private List<PngChunk> chunks1 = new ArrayList<PngChunk>(); // pre idat
	private List<PngChunk> chunks2 = new ArrayList<PngChunk>(); // post idat

	private final int bytesPerRow; // sin inlcuir byte de filtro
	private final int valsPerRow; // smaples per row= cols x channels

	private int rowNum = -1; // numero de linea leida (actual)
	private ImageLine imgLine;
	private int[] rowb = null; // linea covnertida a byte; empieza en 1; (el 0 se usara para tipo de filtro)
	private int[] rowbprev = null; // rowb previa
	private byte[] rowbfilter = null; // linea actual filtrada
	private double dpi=0.0; 
	
	private int[][] allRows;
	
	private NeuralNetwork neuralNetwork;

	private JNNSParser parser;
	
	private double sumDiff = 0;
	
	private int inputSizeX = 8;
	private int inputSizeY = 4;
	
	/**
	 * The constructor loads the header and first chunks, 
	 * stopping at the beginning of the image data (IDAT chunks)
	 * @param filename   Path of image file
	 */
	public PngReader(String filename) {
		
		parser = new JNNSParser();
		neuralNetwork = parser.getNeuralNetwork();
		
		this.filename = filename;
		crcengine = new CRC32();
		File file = new File(filename);
		if (!file.exists() || !file.canRead())
			throw new PngjInputException("Can't open file for reading (" + filename + ") [" + file.getAbsolutePath() +"]");
		try {
			is = new BufferedInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			throw new PngjInputException("Can't open file for reading (" + filename + ")");
		}
		// reads header (magic bytes)
		byte[] pngid = new byte[PngHelper.pngIdBytes.length];
		PngHelper.readBytes(is, pngid, 0, pngid.length);
		offset += pngid.length;
		if (!Arrays.equals(pngid, PngHelper.pngIdBytes))
			throw new PngjInputException("Bad file id (" + filename + ")");
		// reads first chunks
		int clen = PngHelper.readInt4(is);
		offset += 4;
		if (clen != 13)
			throw new RuntimeException("IDHR chunk len != 13 ?? " + clen);
		byte[] chunkid = new byte[4];
		PngHelper.readBytes(is, chunkid, 0, 4);
		offset += 4;
		PngChunk ihdr = new PngChunk(clen, chunkid, crcengine);
		if (!ihdr.id.equals(PngHelper.IHDR_TEXT))
			throw new PngjInputException("IHDR not found as first chunk??? [" + ihdr + "]");
		ihdr.readChunk(is);
		offset += ihdr.len + 4;
		addChunkToList(ihdr, chunks1, true);
		ByteArrayInputStream ihdr_s = ihdr.getAsByteStream();
		int cols = PngHelper.readInt4(ihdr_s);
		int rows = PngHelper.readInt4(ihdr_s);
		int bitspc = PngHelper.readByte(ihdr_s);// bit depth: number of bits per channel
		int colormodel = PngHelper.readByte(ihdr_s); // 6 (alpha) 2 sin alpha
		int compmeth = PngHelper.readByte(ihdr_s); // 
		int filmeth = PngHelper.readByte(ihdr_s); //
		
		int interlaced = PngHelper.readByte(ihdr_s);
		if (interlaced != 0)
			throw new PngjInputException("Interlaced no implemented");
		if (filmeth != 0 || compmeth != 0)
			throw new PngjInputException("compmethod o filtermethod unrecognized");
		boolean alpha = (colormodel & 0x04) != 0;
		boolean palette = (colormodel & 0x01) != 0;
		boolean grayscale = (colormodel == 0 || colormodel == 4);
		if (bitspc != 8 && bitspc != 16)
			throw new RuntimeException("Bit depth not supported " + bitspc);
		imgInfo = new ImageInfo(cols, rows, bitspc, alpha, grayscale, palette);
		imgLine = new ImageLine(imgInfo);
		this.bytesPerRow = imgInfo.bytesPixel * imgInfo.cols;
		this.valsPerRow = imgInfo.cols * imgInfo.channels;
		// allocation
		rowb = new int[bytesPerRow + 1];
		rowbprev = new int[bytesPerRow + 1];
		rowbfilter = new byte[bytesPerRow + 1];
		int idatLen = readFirstChunks();
		if (idatLen < 0)
			throw new PngjInputException("first idat chunk not found!");
		iIdatCstream = new PngIDatChunkInputStream(is, idatLen, offset);
		idatIstream = new InflaterInputStream(iIdatCstream);
		
		// stores all pixels => for the NN Filter
        allRows = new int[imgInfo.rows][imgInfo.cols + 1];
	}

	/**
	 * devuelve flag overflow ( true si no lo agregamos con datos porque se
	 * excedio capacidad en memoria)
	 */
	private boolean addChunkToList(PngChunk chunk, List<PngChunk> list, boolean includedata) {
		boolean overflow = false;
		// procesamiento extra para ciertos chunks
		if( chunk.id.equals(PngHelper.IPHYS_TEXT)) {
			ByteArrayInputStream b= chunk.getAsByteStream();
			int resx= PngHelper.readInt4(b);
			int resy= PngHelper.readInt4(b);
			int mode = PngHelper.readByte(b); // 1: meters
			if(mode==1 & resx==resy) 
				this.dpi = resx * 2.54/100.0; 
		}	
		//// prosamiento comun
		if (includedata && bytesChunksLoaded + chunk.len > MAX_BYTES_CHUNKS_TO_LOAD) {
			overflow = true;
			includedata = false;
		}
		if (includedata) {
			bytesChunksLoaded += chunk.len;
			if (chunk.data == null || chunk.len != chunk.data.length)
				throw new PngjException("error en longitud de chunk a almacenar");
		} else {
			chunk.data = null; // por las dudas
		}
		list.add(chunk);
		return overflow;
	}

	/**
	 * lee (y procesa ?) los chunks anteriores al primer IDAT. Se llama
	 * inmediatamente despues de haber leido IDHR (crc incluido) devuelve el
	 * largo del primer chunk IDAT encontrado. Queda posicionado despues del
	 * IDAT id
	 * */
	private int readFirstChunks() {
		int clen = 0;
		boolean found = false;
		while (!found) {
			clen = PngHelper.readInt4(is);
			offset += 4;
			byte[] chunkid = new byte[4];
			if (clen < 0)
				break;
			PngHelper.readBytes(is, chunkid, 0, 4);
			offset += 4;
			if (Arrays.equals(chunkid, PngHelper.IDAT)) {
				found = true;
				break;
			} else if (Arrays.equals(chunkid, PngHelper.IEND)) { 
				break; //?? 
			}
			PngChunk chunk = new PngChunk(clen, chunkid, crcengine);
			chunk.readChunk(is);
			offset += chunk.len + 4;
			addChunkToList(chunk, chunks1, true);
		}
		return found ? clen : -1;
	}

	/**
	 * lee (y procesa ?) los chunks posteriores al ultimo IDAT (crc incluido).
	 * 
	 * */
	private void readLastChunks() {
		//PngHelper.logdebug("idat ended? " + iIdatCstream.isEnded());
		if(!iIdatCstream.isEnded() )
			iIdatCstream.forceChunkEnd();
		int clen = iIdatCstream.getLenLastChunk();
		byte[] chunkid = iIdatCstream.getIdLastChunk();
		boolean endfound = false;
		boolean first = true;
		while (!endfound) {
			if (!first) {
				clen = PngHelper.readInt4(is);
				offset += 4;
				if (clen < 0)
					throw new PngjInputException("bad len " + clen);
				chunkid = new byte[4];
				PngHelper.readBytes(is, chunkid, 0, 4);
				offset += 4;
			}
			first = false;
			if (Arrays.equals(chunkid, PngHelper.IDAT)) {
				throw new PngjInputException("extra IDA CHUNKS ??");
			} else if (Arrays.equals(chunkid, PngHelper.IEND)) {
				endfound = true;
			}
			PngChunk chunk = new PngChunk(clen, chunkid, crcengine);
			chunk.readChunk(is);
			offset += chunk.len + 4;
			addChunkToList(chunk, chunks2, true);
		}
		if (!endfound)
			throw new PngjInputException("end chunk not found");
		PngHelper.logdebug("end chunk found ok offset=" + offset);
	}

	
	/** 
	 * calls readRow(int[] buffer, int nrow),  usin LineImage as buffer
	 * @return the ImageLine that also is available inside this object
	 */
	public ImageLine readRow(int nrow) {
		readRow(imgLine.scanline,nrow);
		imgLine.incRown();
		return imgLine;
	}
	
	/**
	 * Reads a line and returns it as a int array
	 * Buffer can be prealocated (in this case it must have enough len!)
	 * or can be null
	 * See also the other overloaded method
	 * @param buffer  
	 * @param nrow
	 * @return  The same buffer if it was allocated, a newly allocated one otherwise
	 */
	public int[] readRow(int[] buffer, int nrow) {
		if (nrow < 0 || nrow >= imgInfo.rows)
			throw new PngjInputException("invalid line");
		if (nrow != rowNum + 1)
			throw new PngjInputException("invalid line (expected: " + (rowNum + 1));
		rowNum++;
		if(buffer==null)
			buffer = new int[valsPerRow];
		// swap
		int[] tmp = rowb;
		rowb = rowbprev;
		rowbprev = tmp;
		
		// allRows[rowNum] = buffer;
		
		// carga en rowbfilter los bytes "raw", con el filtro
		PngHelper.readBytes(idatIstream, rowbfilter, 0, bytesPerRow + 1);
		rowb[0] = rowbfilter[0];
		unfilterRow();
		convertRowFromBytes(buffer);
		return buffer;
	}



	private void convertRowFromBytes(int[] buffer) {
		// http://www.libpng.org/pub/png/spec/1.2/PNG-DataRep.html
		int i, j;
		if (imgInfo.bitDepth == 8) {
			for (i = 0, j = 1; i < valsPerRow; i++) {
				buffer[i] = (rowb[j++]);
			}
		} else { // 16 bitspc
			for (i = 0, j = 1; i < valsPerRow; i++) {
				buffer[i] = (rowb[j++] << 8) + rowb[j++];
			}
		}
	}

	private void unfilterRow() {
		int filterType = rowbfilter[0];
		switch (filterType) {
		case FILTER_NONE:
			unfilterRowNone();
			break;
		case FILTER_SUB:
			unfilterRowSub();
			break;
		case FILTER_UP:
			unfilterRowUp();
			break;
		case FILTER_AVERAGE:
			unfilterRowAverage();
			break;
		case FILTER_PAETH:
			unfilterRowPaeth();
			break;
		case FILTER_NN:
			unfilterRowNN();
			break;
			
		default:
			throw new PngjInputException("Filter type " + filterType + " not implemented");
		}
	}

	private void unfilterRowNone() {
		for (int i = 1; i <= bytesPerRow; i++) {
			rowb[i] = (int) (rowbfilter[i] & 0xFF);
		}
	}

	private void unfilterRowSub() {
		int i, j;
		for (i = 1; i <= imgInfo.bytesPixel; i++) {
			rowb[i] = (int) (rowbfilter[i] & 0xFF);
		}
		for (j = 1, i = imgInfo.bytesPixel + 1; i <= bytesPerRow; i++, j++) {
			rowb[i] = ((int) (rowbfilter[i] & 0xFF) + rowb[j]) & 0xFF;
		}
	}

	private void unfilterRowUp() {
		for (int i = 1; i <= bytesPerRow; i++) {
			rowb[i] = ((int) (rowbfilter[i] & 0xFF) + rowbprev[i]) & 0xFF;
		}
	}

	private void unfilterRowAverage() {
		int i, j, x;
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= bytesPerRow; i++, j++) {
			x = j > 0 ? rowb[j] : 0;
			rowb[i] = ((int) (rowbfilter[i] & 0xFF) + (x + rowbprev[i]) / 2) & 0xFF;
		}
	}

	private void unfilterRowPaeth() {
		int i, j, x, y;
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= bytesPerRow; i++, j++) {
			x = j > 0 ? rowb[j] : 0;
			y = j > 0 ? rowbprev[j] : 0;
			rowb[i] = ((int) (rowbfilter[i] & 0xFF) + PngHelper.filterPaethPredictor(x, rowbprev[i], y)) & 0xFF;
		}
	}
	
	private void unfilterRowNN() {
		
		double[] inputValues = new double[(inputSizeX * inputSizeY) - inputSizeY];
		
		if(rowNum < inputSizeY - 1) {
			
			// Don't have enough information for using the neural network
			// Just copy the real value or use grayscale value
			for(int i = 1; i <= bytesPerRow; i++) {
				rowb[i] = (byte)(rowbfilter[i]);
				allRows[rowNum][i-1] = ((int)rowbfilter[i]) & 0xFF;
			}
			
		} else {
			
			for(int i = 0; i < imgInfo.cols; i++) {
				if((i < inputSizeX/2) || (i > (imgInfo.cols - ((inputSizeX/2) + 1)))) {
					// Don't have enough information for using the neural network
					// Just copy the real value or use grayscale....
				  allRows[rowNum][i] = rowb[i+1] = ((int)rowbfilter[i+1]) & 0xFF;
				} else {
					// use neural network
					int vCounter = 0;
					
					// Get the 16x8 pixel image from allRows
					for(int y = rowNum - (inputSizeY-1); y <= rowNum; y++) {
						
						int maxX = i + inputSizeX/2; 
		
						if(rowNum == y) {
							
							// Last row only use the first 8
							// Because we want to find out the 8th pixel
							maxX = i;
						} 
						
						for(int x = i - inputSizeX/2; x < maxX; x++) {
							inputValues[vCounter] = calculateTransformedValue(allRows[y][x]);
							vCounter++;
						}
						
					}
					
					
					/*
					FOR DEBUGGING
					==========================================
					sumDiff += Math.abs(rowbfilter[i+1]);
					
					
					boolean same = true;
					
					double iSumDiff = 0;
					
					for(int z = 0; z < inputValues.length; z++) {
						if(PngWriter.bla[rowNum][i][z] != inputValues[z]) {
							same = false;
							iSumDiff += (Math.abs(PngWriter.copyInputValues[rowNum][i][z] - inputValues[z]));
						}
					}
					
					
					if(!same) {
						System.out.println("ROW [" + rowNum + "][" + i + "] => " + iSumDiff);
						System.out.println("Writer [" + rowNum + "]: " + Arrays.toString(PngWriter.copyInputValues[rowNum][i]));
						System.out.println("Reader [" + rowNum + "]: " + Arrays.toString(inputValues));
						System.exit(0);
					}
					==========================================
					*/
					
					
					allRows[rowNum][i] = rowb[i+1] = ((int)((rowbfilter[i+1]) + (byte)calculateTransformedValueBack(neuralNetwork.calculate(inputValues).getNeurons().get(0).result))) & 0xFF;
				}
			}
		}	
	}

	/**
	 * This should be called after having read the last line.
	 */
	public void end() {
		offset = (int) iIdatCstream.getOffset();
		try {
			idatIstream.close();
		} catch (Exception e) {
		}
		readLastChunks();
		try {
			is.close();
		} catch (Exception e) {
			throw new PngjInputException("error closing input stream!", e);
		}
	}

	/**
	 * Get first chunks (before IDAT)
	 */
	public List<PngChunk> getChunks1() {
		return chunks1;
	}

	/**
	 * Get last chunks (after IDAT)
	 */
	public List<PngChunk> getChunks2() {
		return chunks2;
	}

	public double getDpi() {
		return dpi;
	}
	/**
	 * dots per centimeter
	 */
	public double getDpcm() {
		return dpi/2.54;
	}

	public void showChunks() {
		for (PngChunk c : chunks1) {
			System.out.println(c);
		}
		System.out.println("-----------------");
		for (PngChunk c : chunks2) {
			System.out.println(c);
		}
	}

	public String toString() { // info basica
		return "filename=" + filename + " " + imgInfo.toString();
	}

	/** para debug */
	public static void showLineInfo(ImageLine line) {
		System.out.println(line);
		//System.out.println(line.computeStats());
		System.out.println(line.infoFirstLastPixels());
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
	
	public double getDiffSum() {
		return this.sumDiff;
	}
}
