/*
 *  This file is part of RegionLib, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2016 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.regionlib.impl;

import cubicchunks.regionlib.IRegionKey;

/**
 * A 2D implementation of IRegionLocation
 */
public class RegionLocation2D implements IRegionKey<RegionLocation2D, EntryLocation2D> {
	private final int x;
	private final int z;

	public RegionLocation2D(int x, int z) {
		this.x = x;
		this.z = z;
	}

	public int getX() {
		return x;
	}

	public int getZ() {
		return z;
	}

	@Override public String getRegionName() {
		return x + "." + z + ".2dr";
	}

	@Override public int getKeyCount() {
		return EntryLocation2D.ENTRIES_PER_REGION;
	}

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		RegionLocation2D that = (RegionLocation2D) o;

		if (x != that.x) return false;
		return z == that.z;

	}

	@Override public int hashCode() {
		int result = x;
		result = 31*result + z;
		return result;
	}

	@Override public String toString() {
		return "RegionLocation2D{" +
			"name='" + getRegionName() + '\'' +
			'}';
	}

	public static RegionLocation2D fromName(String name) {
		if (!name.matches("-?\\d+\\.-?\\d+\\.2dr")) {
			throw new IllegalArgumentException("Invalid name " + name);
		}
		String[] s = name.split("\\.");
		return new RegionLocation2D(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
	}
}
