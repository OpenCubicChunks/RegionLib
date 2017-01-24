package cubicchunks.regionlib.impl;

import cubicchunks.regionlib.IRegionKey;

public class MinecraftRegionLocation implements IRegionKey<MinecraftRegionLocation, MinecraftChunkLocation> {

	private final int x;
	private final int z;

	public MinecraftRegionLocation(int x, int z) {
		this.x = x;
		this.z = z;
	}

	public int getX() {
		return x;
	}

	public int getZ() {
		return z;
	}

	@Override
	public String getRegionName() {
		return "r." + x + "." + z + ".mca";
	}

	@Override
	public int getKeyCount() {
		return EntryLocation2D.ENTRIES_PER_REGION;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		MinecraftRegionLocation that = (MinecraftRegionLocation) o;

		if (x != that.x) {
			return false;
		}
		return z == that.z;

	}

	@Override
	public int hashCode() {
		int result = x;
		result = 31*result + z;
		return result;
	}

	@Override
	public String toString() {
		return "MinecraftRegionLocation{" +
			"name='" + getRegionName() + '\'' +
			'}';
	}

	public static MinecraftRegionLocation fromName(String name) {
		if (!name.matches("r\\.-?\\d+\\.-?\\d+\\.mca")) {
			throw new IllegalArgumentException("Invalid name " + name);
		}
		String[] s = name.split("\\.");
		return new MinecraftRegionLocation(Integer.parseInt(s[1]), Integer.parseInt(s[2]));
	}
}
