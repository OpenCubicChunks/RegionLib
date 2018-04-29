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

import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;

public class MinecraftChunkLocation implements IKey<MinecraftChunkLocation> {

	public static final int LOC_BITS = 5;
	public static final int LOC_BITMASK = (1 << LOC_BITS) - 1;
	public static final int ENTRIES_PER_REGION = (1 << LOC_BITS)*(1 << LOC_BITS);

	private final int entryX;
	private final int entryZ;
	private String extension;

	public MinecraftChunkLocation(int entryX, int entryZ, String extension) {
		this.entryX = entryX;
		this.entryZ = entryZ;
		this.extension = extension;
	}

	public int getEntryX() {
		return entryX;
	}

	public int getEntryZ() {
		return entryZ;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		MinecraftChunkLocation that = (MinecraftChunkLocation) o;

		if (entryX != that.entryX) {
			return false;
		}
		return entryZ == that.entryZ;

	}

	@Override
	public int hashCode() {
		int result = entryX;
		result = 31*result + entryZ;
		return result;
	}

	@Override
	public RegionKey getRegionKey() {
		int regX = entryX >> LOC_BITS;
		int regZ = entryZ >> LOC_BITS;
		return new RegionKey("r." + regX + "." + regZ + "." + extension);
	}

	@Override
	public int getId() {
		return ((entryZ & LOC_BITMASK) << LOC_BITS) | (entryX & LOC_BITMASK);
	}

	@Override
	public String toString() {
		return "EntryLocation2D{" + "entryX=" + entryX + ", entryZ=" + entryZ + ", extension=" + extension + "}";
	}

	public static class Provider implements IKeyProvider<MinecraftChunkLocation> {

		private String extension;

		public Provider(String extension) {
			this.extension = extension;
		}

		@Override public MinecraftChunkLocation fromRegionAndId(RegionKey regionKey, int id) throws IllegalArgumentException {
			if (!regionKey.getName().matches("r\\.-?\\d+\\.-?\\d+\\." + extension)) {
				throw new IllegalArgumentException("Invalid name " + regionKey.getName() + ", expected pattern r\\.-?\\d+\\.-?\\d+\\." + extension);
			}
			String[] s = regionKey.getName().split("\\.");

			int relativeX = id & LOC_BITMASK;
			int relativeZ = id >>> LOC_BITS;
			return new MinecraftChunkLocation(
					Integer.parseInt(s[1]) << LOC_BITS | relativeX,
					Integer.parseInt(s[2]) << LOC_BITS | relativeZ, extension);
		}

		@Override public int getKeyCount(RegionKey key) {
			return ENTRIES_PER_REGION;
		}
	}
}
