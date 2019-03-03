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
package de.tilman_neumann.jml.factor;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.apache.log4j.Logger;

import de.tilman_neumann.jml.factor.siqs.KnuthSchroeppel;
import de.tilman_neumann.jml.primes.probable.BPSWTest;

import static de.tilman_neumann.jml.base.BigIntConstants.*;
import static org.junit.Assert.*;

/**
 * Generation of random N that are not too easy to factor.
 * The standard case are semiprimes N where the smaller factor of N is >= cbrt(N).
 * 
 * @author Tilman Neumann
 */
public class TestsetGenerator {
	private static final Logger LOG = Logger.getLogger(TestsetGenerator.class);
	private static final boolean DEBUG = false;
	private static final boolean SELECT = false;
	
	private static final BPSWTest bpsw = new BPSWTest();
	private static final KnuthSchroeppel multiplierFinder = new KnuthSchroeppel(); // used to compute the multiplier k

	/** random generator */
	private static final SecureRandom RNG = new SecureRandom();
	
	/**
	 * Generate N_count random numbers of the given size and nature.
	 * @param N_count number of test numbers to generate
	 * @param bits size of test numbers to generate
	 * @param mode the nature of test numbers to generate
	 * @return test set
	 */
	public static BigInteger[] generate(int N_count, int bits, TestNumberNature mode) {
		BigInteger[] NArray = new BigInteger[N_count];
		switch (mode) {
		case RANDOM_COMPOSITES: {
			if (bits<3) throw new IllegalArgumentException("There are no composites with " + bits + " bits.");
			for (int i=0; i<N_count; ) {
				BigInteger N = new BigInteger(bits, RNG);
				if(N.bitLength()==bits && !bpsw.isProbablePrime(N)) {
					NArray[i++] = N;
				}
			}
			return NArray;
		}
		case RANDOM_ODD_COMPOSITES: {
			if (bits<4) throw new IllegalArgumentException("There are no odd composites with " + bits + " bits.");
			for (int i=0; i<N_count; ) {
				BigInteger N = new BigInteger(bits, RNG).or(I_1); // odd random number
				if(N.bitLength()==bits && !bpsw.isProbablePrime(N)) {
					NArray[i++] = N;
				}
			}
			return NArray;
		}
		case MODERATE_SEMIPRIMES: {
			if (bits<4) throw new IllegalArgumentException("There are no odd semiprimes with " + bits + " bits.");
			int minBits = (bits+2)/3; // analogue to 3rd root(N)
			int maxBits = (bits+1)/2;
			for (int i=0; i<N_count; ) {
				// generate random N with 2 prime factors
				int n1bits = uniformRandomInteger(minBits, maxBits);
				BigInteger n1 = new BigInteger(n1bits, RNG);
				n1 = bpsw.nextProbablePrime(n1);
				if (n1.bitLength()!=n1bits) continue;
				BigInteger Nrand = new BigInteger(bits, RNG);
				BigInteger n2 = bpsw.nextProbablePrime(Nrand.divide(n1));
				BigInteger N = n1.multiply(n2);
				if (N.bitLength() != bits) continue;
				NArray[i++] = N;
			}
			return NArray;
		}
		case MODERATE_SEMIPRIMES2: {
			if (bits<4) throw new IllegalArgumentException("There are no odd semiprimes with " + bits + " bits.");
			int minBits = (bits+2)/3; // analogue to 3rd root(N)
			int maxBits = (bits+1)/2;
			for (int i=0; i<N_count; ) {
				// generate random N with 2 prime factors
				int n1bits = uniformRandomInteger(minBits, maxBits);
				BigInteger n1 = new BigInteger(n1bits, RNG);
				if (n1bits>0) n1 = n1.setBit(n1bits-1);
				n1 = bpsw.nextProbablePrime(n1);
				int n2bits = bits-n1.bitLength();
				BigInteger n2 = new BigInteger(n2bits, RNG);
				if (n2bits>0) n2 = n2.setBit(n2bits-1);
				n2 = bpsw.nextProbablePrime(n2);
				BigInteger N = n1.multiply(n2);
			
				// Skip cases where the construction above failed to produce the correct bit length
				if (N.bitLength() != bits) continue;
				
				if (SELECT) {
					// skip N that do not match the selection criterion
					int k = multiplierFinder.computeMultiplier(N);
					int kNMod = BigInteger.valueOf(k).multiply(N).mod(I_8).intValue();
					if (kNMod == 1) continue;
				}
				
				if (DEBUG) {
					assertTrue(n1bits >= minBits);
					assertTrue(n1bits <= maxBits);
					LOG.debug("TestsetGenerator: minBits = " + minBits + ", maxBits = " + maxBits + ", n1.bitLength() = " + n1.bitLength());
					assertTrue(n1.bitLength() >= minBits);
					assertTrue(n1.bitLength() <= maxBits);
					int resultBits = N.bitLength();
					LOG.debug("TestsetGenerator: wanted bits = " + bits + ", result bits = " + resultBits);
					assertTrue(resultBits >= bits-1);
					assertTrue(resultBits <= bits+1);
				}
				NArray[i++] = N;
			}
			return NArray;
		}
		case QUITE_HARD_SEMIPRIMES: {
			if (bits<4) throw new IllegalArgumentException("There are no odd semiprimes with " + bits + " bits.");
			int minBits = (bits-1)/2;
			for (int i=0; i<N_count; ) {
				// generate random N with 2 prime factors
				BigInteger n1 = new BigInteger(minBits, RNG);
				n1 = n1.setBit(minBits-1);
				n1 = bpsw.nextProbablePrime(n1);
				int n2bits = bits-n1.bitLength();
				BigInteger n2 = new BigInteger(n2bits, RNG);
				n2 = n2.setBit(n2bits-1);
				n2 = bpsw.nextProbablePrime(n2);
				BigInteger N = n1.multiply(n2);
				if (DEBUG) LOG.debug("bits=" + bits + ", N1Bits=" + n1.bitLength() + ", N2Bits=" + n2.bitLength());
				
				// Skip cases where the construction above failed to produce the correct bit length
				if (N.bitLength() != bits) continue;
				NArray[i++] = N;
			}
			return NArray;
		}
		default: throw new IllegalArgumentException("TestsetGeneratorMode " + mode);
		}
	}
	
	/**
	 * Creates a random integer from the uniform distribution U[minValue, maxValue-1].
	 * Works also for negative arguments; the only requirement is maxValue>minValue.
	 * @param minValue
	 * @param maxValue
	 * @return
	 */
	private static int uniformRandomInteger(int minValue, int maxValue) {
		int normedMaxValue = Math.max(1, maxValue - minValue);
		return RNG.nextInt(normedMaxValue) + minValue;
	}
}
