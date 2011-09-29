
public class OtherTests {

	public static void main(String[] args) {
		int x = 0xFF00FF;
		
		System.out.println(x);
		System.out.println(Integer.toBinaryString(x));
		System.out.println(Integer.toBinaryString(x<<8));
		
		System.out.println((x & 0xFF0000)>>16); // RED
		System.out.println((x & 0x00FF00)>>8); //  GREEN
		System.out.println((x & 0x0000FF)); // BLUE
	}
}
