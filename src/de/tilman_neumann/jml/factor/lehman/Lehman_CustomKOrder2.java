/*
 * java-math-library is a Java library focused on number theory, but not necessarily limited to it. It is based on the PSIQS 4.0 factoring project.
 * Copyright (C) 2018 Tilman Neumann (www.tilman-neumann.de)
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses/>.
 */
package de.tilman_neumann.jml.factor.lehman;

import java.math.BigInteger;

import org.apache.log4j.Logger;

import de.tilman_neumann.jml.factor.FactorAlgorithm;
import de.tilman_neumann.jml.gcd.Gcd63;
import de.tilman_neumann.util.ConfigUtil;
import de.tilman_neumann.jml.factor.tdiv.TDiv63Inverse;

/**
 * A variant of Lehman's algorithm that allows to arrange the k's in arrays of different priorities.
 * Some multiples of k that work very well are 315, 45, 105, ..., 15, 9, 3, and are tested in that order.
 * 
 * For large k we also use congruences of a == kN (mod 2^s) instead of Lehman's a == (k+N) (mod 2^s),
 * which seem to be slightly more discriminative.
 * 
 * @authors Tilman Neumann + Thilo Harich
 */
public class Lehman_CustomKOrder2 extends FactorAlgorithm {
	private static final Logger LOG = Logger.getLogger(Lehman_CustomKOrder2.class);

	/** This is a constant that is below 1 for rounding up double values to long. */
	private static final double ROUND_UP_DOUBLE = 0.9999999665;

	private static final int K_MAX = 1<<20;
	private static final int ARRAY_COUNT = 6;

	private final TDiv63Inverse tdiv = new TDiv63Inverse(K_MAX);

	private double[][] sqrts;
	private double[][] sqrtInvs;
	private int[][] kArrays;
	private int[] kArraySizes;
	private static final float[] kLimitMultipliers = new float[] {16, 1, 1, 1, 1, 1};

	private long N;
	private long fourN;
	private double sqrt4N;
	private boolean doTDivFirst;
	private final Gcd63 gcdEngine = new Gcd63();
	
	/**
	 * Full constructor.
	 * @param doTDivFirst If true then trial division is done before the Lehman loop.
	 * This is recommended if arguments N are known to have factors < cbrt(N) frequently.
	 */
	public Lehman_CustomKOrder2(boolean doTDivFirst) {
		this.doTDivFirst = doTDivFirst;
		// arrange k in different arrays
		sqrts = new double[ARRAY_COUNT][K_MAX+1];
		sqrtInvs = new double[ARRAY_COUNT][K_MAX+1];
		kArrays = new int[ARRAY_COUNT][K_MAX+1];
		kArraySizes = new int[ARRAY_COUNT];
		int k = 1;
		for ( ; k <= K_MAX; k++) {
			if (k%315==0 || k%495==0) {
				if (k%2==0) addToArray(k, 1); else addToArray(k, 0);
			} else if (k%45==0 || k%105==0) {
				if (k%2==0) addToArray(k, 2); else addToArray(k, 1);
			} else if (k%15==0 || k%63==0) {
				if (k%2==0) addToArray(k, 3); else addToArray(k, 2);
			} else if (k%9==0 || k%21==0) {
				if (k%2==0) addToArray(k, 4); else addToArray(k, 3);
			} else if (k%3==0) {
				if (k%2==0) addToArray(k, 5); else addToArray(k, 4);
			} else {
				addToArray(k, 5);
			}
		}
		// very big multipliers need more k-values
		int kMaxWithMultiplier = (int)(kLimitMultipliers[0]*K_MAX);
		for (; k <=kMaxWithMultiplier; k++) {
			if (k%315==0 & k%2==1) {
				addToArray(k, 0);
			}
		}
			
		// shrink arrays to counts
		for (int i=0; i<ARRAY_COUNT; i++) {
			int count = kArraySizes[i];
			int[] kArrayTmp = new int[count];
			System.arraycopy(kArrays[i], 0, kArrayTmp, 0, count);
			kArrays[i] = kArrayTmp;
			double[] sqrtsTmp = new double[count];
			System.arraycopy(sqrts[i], 0, sqrtsTmp, 0, count);
			sqrts[i] = sqrtsTmp;
			double[] sqrtInvsTmp = new double[count];
			System.arraycopy(sqrtInvs[i], 0, sqrtInvsTmp, 0, count);
			sqrtInvs[i] = sqrtInvsTmp;
		}
	}

	private void addToArray(int k, int arrayIndex) {
		final double sqrtK = Math.sqrt(k);
		int count = kArraySizes[arrayIndex];
		kArrays[arrayIndex][count] = k;
		sqrts[arrayIndex][count] = sqrtK;
		sqrtInvs[arrayIndex][count] = 1.0/sqrtK;
		kArraySizes[arrayIndex]++;
	}

	@Override
	public String getName() {
		return "Lehman_CustomKOrder2(" + doTDivFirst + ")";
	}

	@Override
	public BigInteger findSingleFactor(BigInteger N) {
		return BigInteger.valueOf(findSingleFactor(N.longValue()));
	}

	public long findSingleFactor(long N) {
		// N==9 would require to check if the gcd is 1 < gcd < N before returning it as a factor
		if (N==9) return 3;
				
		this.N = N;
		final int cbrt = (int) Math.cbrt(N);

		// do trial division before Lehman loop ?
		long factor;
		tdiv.setTestLimit(cbrt);
		if (doTDivFirst && (factor = tdiv.findSingleFactor(N))>1) return factor;

		fourN = N<<2;
		sqrt4N = Math.sqrt(fourN);

		final int kLimit = cbrt;
		// For kTwoA = kLimit / 64 the range for a is at most 2. kLimit / 128 seems to work as well...
		final int kTwoA = (cbrt + 127) >> 7;
		
		final double sixthRootTerm = 0.25 * Math.pow(N, 1/6.0); // double precision is required for stability
		for (int i=0; i<ARRAY_COUNT; i++) {
			int kLimit2 = (int) (kLimit * kLimitMultipliers[i]);
			if ((factor = test(kTwoA, kLimit2, kArrays[i], sqrts[i], sqrtInvs[i], sixthRootTerm)) > 1) return factor;
		}

		// do trial division now?
		if (!doTDivFirst && (factor = tdiv.findSingleFactor(N))>1) return factor;
		
		// If sqrt(4kN) is very near to an exact integer then the fast ceil() in the 'aStart'-computation
		// may have failed. Then we need a "correction loop":
		for (int i=0; i<ARRAY_COUNT; i++) {
			if ((factor = correctionLoop(kLimit, kArrays[i], sqrts[i])) > 1) return factor;
		}
		
		return 1; // fail
	}

	private long test(int kTwoA, int kLimit, int[] kArray, double[] sqrts, double[] sqrtInvs, double sixthRootTerm) {
		long a, aStart, aLimit;
		int i, k;
		
		// small k
		for (i=0; (k = kArray[i])<kTwoA; i++) {
			final double sqrt4kN = sqrt4N * sqrts[i];
			aStart = (long) (sqrt4kN + ROUND_UP_DOUBLE); // much faster than ceil() !
			aLimit = (long) (sqrt4kN + sixthRootTerm * sqrtInvs[i]);
			if ((k & 1) == 0) {
				// k even -> make sure aLimit is odd
				a = aLimit | 1;
				final long fourkN = k * fourN;
				for (; a >= aStart; a-=2) {
					final long test = a*a - fourkN;
					final long b = (long) Math.sqrt(test);
					if (b*b == test) {
						return gcdEngine.gcd(a+b, N);
					}
				}
			} else {
				final long kN = k*N;
				final long fourkN = kN << 2;
				final long kNp1 = kN + 1;
				if ((kNp1 & 3) == 0) {
					a = aLimit + ((kNp1 - aLimit) & 7);
					for (; a >= aStart; a-=8) {
						final long test = a*a - fourkN;
						final long b = (long) Math.sqrt(test);
						if (b*b == test) {
							return gcdEngine.gcd(a+b, N);
						}
					}
				} else if ((kNp1 & 7) == 6) {
					a = aLimit + ((kNp1 - aLimit) & 31);
					for (; a >= aStart; a-=32) {
						final long test = a*a - fourkN;
						final long b = (long) Math.sqrt(test);
						if (b*b == test) {
							return gcdEngine.gcd(a+b, N);
						}
					}
					a = aLimit + ((-kNp1 - aLimit) & 31);
					for (; a >= aStart; a-=32) {
						final long test = a*a - fourkN;
						final long b = (long) Math.sqrt(test);
						if (b*b == test) {
							return gcdEngine.gcd(a+b, N);
						}
					}
				} else { // (kN+1) == 2 (mod 8)
					a = aLimit + ((kNp1 - aLimit) & 15);
					for (; a >= aStart; a-=16) {
						final long test = a*a - fourkN;
						final long b = (long) Math.sqrt(test);
						if (b*b == test) {
							return gcdEngine.gcd(a+b, N);
						}
					}
					a = aLimit + ((-kNp1 - aLimit) & 15);
					for (; a >= aStart; a-=16) {
						final long test = a*a - fourkN;
						final long b = (long) Math.sqrt(test);
						if (b*b == test) {
							return gcdEngine.gcd(a+b, N);
						}
					}
				}
			}
		}

		// big k
		for ( ; (k = kArray[i])<kLimit; i++) { // XXX may throw ArrayIndexOutOfBoundsException if N or kLimit are too big
			long kN = k*N;
			a = (long) (sqrt4N * sqrts[i] + ROUND_UP_DOUBLE);
			if ((k & 1) == 0) {
				// k even -> make sure aLimit is odd
				a |= 1L;
			} else {
				final long kNp1 = kN + 1;
				if ((kNp1 & 3) == 0) {
					a += (kNp1 - a) & 7;
				} else if ((kNp1 & 7) == 6) {
					final long adjust1 = (kNp1 - a) & 31;
					final long adjust2 = (-kNp1 - a) & 31;
					a += adjust1<adjust2 ? adjust1 : adjust2;
				} else { // (kN+1) == 2 (mod 8)
					final long adjust1 = (kNp1 - a) & 15;
					final long adjust2 = (-kNp1 - a) & 15;
					a += adjust1<adjust2 ? adjust1 : adjust2;
				}
			}
			
			final long test = a*a - (kN << 2);
			final long b = (long) Math.sqrt(test);
			if (b*b == test) {
				return gcdEngine.gcd(a+b, N);
			}
		}
		return 1;
	}
	
	private long correctionLoop(int kLimit, int[] kArray, double[] sqrts) {
		int i=0, k;
		for (; (k = kArray[i])<kLimit; i++) {
			long a = (long) (sqrt4N * sqrts[i] + ROUND_UP_DOUBLE) - 1;
			long test = a*a - k*fourN;
			long b = (long) Math.sqrt(test);
			if (b*b == test) {
				return gcdEngine.gcd(a+b, N);
			}
	    }
		return 1;
	}
	
	/**
	 * Test.
	 * @param args ignored
	 */
	public static void main(String[] args) {
		ConfigUtil.initProject();

		// These test number were too hard for previous versions:
		long[] testNumbers = new long[] {
				// odd semiprimes
				5640012124823L,
				7336014366011L,
				19699548984827L,
				52199161732031L,
				73891306919159L,
				112454098638991L,
				
				32427229648727L,
				87008511088033L,
				92295512906873L,
				338719143795073L,
				346425669865991L,
				1058244082458461L,
				1773019201473077L,
				6150742154616377L,

				44843649362329L,
				67954151927287L,
				134170056884573L,
				198589283218993L,
				737091621253457L,
				1112268234497993L,
				2986396307326613L,
				
				26275638086419L,
				62246008190941L,
				209195243701823L,
				290236682491211L,
				485069046631849L,
				1239671094365611L,
				2815471543494793L,
				5682546780292609L,
				
				// special case
				9,
			};
		
		Lehman_CustomKOrder2 lehman = new Lehman_CustomKOrder2(false);
		for (long N : testNumbers) {
			long factor = lehman.findSingleFactor(N);
			LOG.info("N=" + N + " has factor " + factor);
		}
	}
}