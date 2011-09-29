
import ar.com.hjg.pngj.ImageInfo;

/**
 * Simple wrapper for an image scanline Can be (and usually is) reused while
 * iterating over the image lines
 */
public class ImageLine {

	public final ImageInfo imgInfo;

	/**
	 * scaline length, in number of samples
	 */
	public final int len;
	/**
	 * copied from imgInfo
	 */
	public final int channels;

	/**
	 * copied from imgInfo
	 */
	public final int bitDepth;
	/**
	 * scanline samples, one integer for each sample (0-255 or 0-65535) in
	 * sequence : (R G B R G B...) or (R G B A R G B A...) or (g g g ...) or (y a y a y a)
	 */
	public final int[] scanline;
	
	/**
	 * tracks the present row number (from 0 to rows-1)
	 */
	private int rown = -1;

	public ImageLine(ImageInfo imgInfo) {
		this.imgInfo = imgInfo;
		channels = imgInfo.channels;
		len = channels * imgInfo.cols;
		scanline = new int[len];
		this.bitDepth = imgInfo.bitDepth;
	}

	public String toString() {
		return "row=" + rown + " cols=" + imgInfo.cols + " bpc=" + imgInfo.bitDepth + " size=" + len;
	}

	private final static double BIG_VALUE = Double.MAX_VALUE * 0.5;
	private final static double BIG_VALUE_NEG = Double.MAX_VALUE * (-0.5);

	/**
	 * Just for basic info or debugging Shows values for first and last pixel.
	 * Does not include alpha
	 */
	public String infoFirstLastPixels() {
		return imgInfo.channels == 1 ? String.format("first=(%d) last=(%d)", scanline[0], scanline[len - 1]) : String.format(
				"first=(%d %d %d) last=(%d %d %d)", scanline[0], scanline[1], scanline[2], scanline[len - imgInfo.channels],
				scanline[len - imgInfo.channels + 1], scanline[len - imgInfo.channels + 2]);
	}

	/**
	 * packed  R G B  
	 * only for bitdepth=8! (does not check!)	 
	 * 
	 **/
	public int getPixelRGB8(int column) {
		int offset = column*channels;
		return (scanline[offset]<<16) + (scanline[offset+1]<<8) + (scanline[offset+2]);
	}

	public int getPixelARGB8(int column) {
		int offset = column*channels;
		return (scanline[offset+3]<<24) + (scanline[offset]<<16) + (scanline[offset+1]<<8) + (scanline[offset+2]);
	}
	
	public int getPixelYA(int column) {
		int offset = column*channels;
		
		//System.out.println("SCANLINE VALUE:" + scanline[offset]);
		
		//System.out.println("In GA translate: " + Integer.toBinaryString((scanline[offset]<<8) + (scanline[offset+1])));
		//System.out.println("GRAYSCALE: " + Integer.toHexString(scanline[offset]));
		//System.out.println("ALPHA: " + Integer.toBinaryString(scanline[offset+1]));
		
				// Y (VALUE into RGB)    	  // A (ALPHA)
		return (scanline[offset] * 0x010101 | 0xFF000000);
	}

	/**
	 * 
	 **/
	public void setPixelsRGB8(int[] packed) {
		for(int i=0;i<imgInfo.cols;i++) {
			scanline[i*channels]  =  ((packed[i]&0xFF0000)>>16);
			scanline[i*channels+1] = ((packed[i]&0xFF00)>>8);
			scanline[i*channels+2] = ((packed[i]&0xFF));
		}
	}

	public void setPixelRGB8(int col,int packed) {
			scanline[col*channels]  =  ((packed&0xFF0000)>>16);
			scanline[col*channels+1] = ((packed&0xFF00)>>8);
			scanline[col*channels+2] = ((packed&0xFF));
	}
	
	public void setPixelYA(int col,int packed) {
		scanline[col*channels]  =  ((packed & 0xFF00)>>8);
		scanline[col*channels+1] = ((packed & 0x00FF));
}

	public void setValD(int i, double d) {
		scanline[i] = double2int(d);
	}

	public double int2double(int p) {
		return bitDepth == 16 ? p / 65535.0 : p / 255.0; // tal vez convendria reemplazar por multiplicacion ?
	}

	public double int2doubleClamped(int p) {
		double d = bitDepth == 16 ? p / 65535.0 : p / 255.0; // tal vez convendria reemplazar por multiplicacion ?
		return d <= 0.0 ? 0 : (d >= 1.0 ? 1.0 : d);
	}

	public int double2int(double d) {
		d = d <= 0.0 ? 0 : (d >= 1.0 ? 1.0 : d);
		return bitDepth == 16 ? (int) (d * 65535.0 + 0.5) : (int) (d * 255.0 + 0.5); // 
	}

	public int double2intClamped(double d) {
		d = d <= 0.0 ? 0 : (d >= 1.0 ? 1.0 : d);
		return bitDepth == 16 ? (int) (d * 65535.0 + 0.5) : (int) (d * 255.0 + 0.5); // 
	}

	public int getRown() {
		return rown;
	}

	public void incRown() {
		this.rown++;
	}

	public void setRown(int n) {
		this.rown = n;
	}

}
