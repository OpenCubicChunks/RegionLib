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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

import cubicchunks.regionlib.CurruptedDataException;
import cubicchunks.regionlib.IEntryLocation;
import cubicchunks.regionlib.IOWriteTask;
import cubicchunks.regionlib.SaveSection;
import cubicchunks.regionlib.region.IRegionLocation;
import cubicchunks.regionlib.region.Region;
import cubicchunks.regionlib.region.RegionCache;
import cubicchunks.regionlib.region.RegionFactory;

/**
 * A save for 3d and 2d structures.
 * <p>
 * This will not automatically save data to disk, instead it gives {@link IOWriteTask} objects
 * to the given consumer, which should handle writing them (using {@link IOWriteTask#write()})
 */
public class SaveCubeColumns {

	private final SaveSection<RegionLocation2D, EntryLocation2D> saveSection2D;
	private final SaveSection<RegionLocation3D, EntryLocation3D> saveSection3D;

	public SaveCubeColumns(SaveSection<RegionLocation2D, EntryLocation2D> saveSection2D,
	                       SaveSection<RegionLocation3D, EntryLocation3D> saveSection3D) {
		this.saveSection2D = saveSection2D;
		this.saveSection3D = saveSection3D;
	}

	/**
	 * Schedules entry for writing
	 * <p>
	 * This can be accessed from multiple threads. (thread safe)
	 */
	public void save3d(EntryLocation3D location, byte[] data) throws IOException {
		this.saveSection3D.save(location, data);
	}

	/**
	 * Schedules entry for writing
	 * <p>
	 * This can be accessed from multiple threads. (thread safe)
	 */
	public void save2d(EntryLocation2D location, byte[] data) throws IOException {
		this.saveSection2D.save(location, data);
	}

	/**
	 * Reads entry at given location.
	 * <p>
	 * This can be accessed from multiple threads. (thread safe)
	 */
	public Optional<byte[]> load(EntryLocation3D location) throws IOException, CurruptedDataException {
		return saveSection3D.load(location);
	}

	/**
	 * Reads entry at given location.
	 * <p>
	 * This can be accessed from multiple threads.
	 */
	public Optional<byte[]> load(EntryLocation2D location) throws IOException, CurruptedDataException {
		return saveSection2D.load(location);
	}

	/**
	 * @param location location for the save
	 */
	public static SaveCubeColumns create(File location) throws IOException {
		final int sectorSize = 512;
		final int maxSectorsLoaded = 256;

		FileUtils.createDirectory(location);

		File part2d = new File(location, "region2d");
		FileUtils.createDirectory(part2d);

		// diamond operator won't work here with javac for some strange reason, even if IDEA thinks it's ok
		RegionCache<RegionLocation2D, EntryLocation2D> regionCache2d = new RegionCache<RegionLocation2D, EntryLocation2D>(
			regionFactoryIn(part2d, EntryLocation2D.ENTRIES_PER_REGION, sectorSize), maxSectorsLoaded);

		File part3d = new File(location, "region3d");
		FileUtils.createDirectory(part3d);

		RegionCache<RegionLocation3D, EntryLocation3D> regionCache3d = new RegionCache<RegionLocation3D, EntryLocation3D>(
			regionFactoryIn(part3d, EntryLocation3D.ENTRIES_PER_REGION, sectorSize), maxSectorsLoaded);

		SaveSection<RegionLocation2D, EntryLocation2D> section2d = new SaveSection<>(regionCache2d);
		SaveSection<RegionLocation3D, EntryLocation3D> section3d = new SaveSection<>(regionCache3d);

		return new SaveCubeColumns(section2d, section3d);
	}

	private static <R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> RegionFactory<R, L> regionFactoryIn(File directory, int entriesPerRegion, int sectorSize) {
		return (l, t) -> {
			File regionFile = new File(directory, l.getRegionName());
			if (t == RegionFactory.CreateType.LOAD && !regionFile.exists()) {
				return Optional.empty();
			}
			if (!regionFile.exists()) {
				regionFile.createNewFile();
			}

			return Optional.of(new Region<>(regionFile.toPath(), entriesPerRegion, sectorSize));
		};
	}
}
