import java.util.*;

class Histogram {	
	final double binSize;
	final long rangeStart, rangeEnd;
	final int bin;
	long[] table;
	long count;
	public Histogram(long rangeStart, long rangeEnd, int bin) {
		this.rangeStart = rangeStart;
		this.rangeEnd = rangeEnd+1;
		this.bin = bin;
		binSize = (double)(this.rangeEnd-rangeStart)/(double)bin;
		table = new long[bin];
		count = 0;
	}
	public void add(long x) {
		table[(int)((x-rangeStart)/binSize)]++;
	}
	public double[] toSimplex() {
		double[] simplex = new double[bin];
		for(int i = 0; i < bin; i++) {
			simplex[i] = (double)table[i]/(double)count;
		}
		return simplex;
	}
}