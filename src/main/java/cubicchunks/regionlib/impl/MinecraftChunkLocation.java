package cubicchunks.regionlib.impl;

import cubicchunks.regionlib.IKey;

public class MinecraftChunkLocation implements IKey<MinecraftRegionLocation, MinecraftChunkLocation> {

	public static final int LOC_BITS = 5;
	public static final int LOC_BITMASK = (1 << LOC_BITS) - 1;
	public static final int ENTRIES_PER_REGION = (1 << LOC_BITS)*(1 << LOC_BITS);

	private final int entryX;
	private final int entryZ;

	public MinecraftChunkLocation(int entryX, int entryZ) {
		this.entryX = entryX;
		this.entryZ = entryZ;
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
	public MinecraftRegionLocation getRegionKey() {
		return new MinecraftRegionLocation(entryX >> LOC_BITS, entryZ >> LOC_BITS);
	}

	@Override
	public int getId() {
		return ((entryX & LOC_BITMASK) << LOC_BITS) | (entryZ & LOC_BITMASK);
	}

	@Override
	public String toString() {
		return "EntryLocation2D{" +
			"entryX=" + entryX +
			", entryZ=" + entryZ +
			'}';
	}
}
