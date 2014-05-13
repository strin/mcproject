// from java.util.Random
class Fingerprint {
  final long m = 0xFFFFFFFFFFFFL;
  final long a = 25214903917L;
  final long c = 11L;
  /*static {
      System.loadLibrary("Fingerprint");
  }*/
  long getFingerprint(long iterations, long startSeed) {
	return getFingerprintWithoutAnd(iterations, startSeed);
  }
  public native long getFingerprintCXX(long iterations, long startSeed);
  long getFingerprintOld(long iterations, long startSeed) {
    long seed = startSeed;
    for(long i = 0; i < iterations; i++) {
      seed = (seed*a + c) & m;
    }
    return ( seed >> 12 ) & 0xFFFFL;
  }
  long getFingerprintWithoutAnd(long iterations, long seed) {
    for(long i = 0; i < iterations; i++) {
      seed = seed*a + c;
    }
    return ( seed >> 12 ) & 0xFFFFL;
  }
}

class TestFingerPrint {
	public static void main(String[] args) {
		Fingerprint fingerprint = new Fingerprint();
		int[] test_num = {31, 23, 103, 3234};
		for(int test = 0; test <= 100000; test++) {
			//System.out.println("fingerprint old "+fingerprint.getFingerprintOld(100, test));	
			//System.out.println("fingerprint  "+fingerprint.getFingerprintWithoutAnd(100, test));	
			//System.out.println("fingerprint  "+fingerprint.getFingerprintCXX(100, test));	
			//fingerprint.getFingerprintWithoutAnd(10000, test);	
			//fingerprint.getFingerprintCXX(10000, test);	
			fingerprint.getFingerprintOld(10000, test);	
		}
	}
}
