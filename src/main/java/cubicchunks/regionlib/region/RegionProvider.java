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
package cubicchunks.regionlib.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.IEntryLocation;
import cubicchunks.regionlib.IRegionLocation;

public class RegionProvider<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> implements IRegionProvider<R, L> {

	private final Map<R, IRegion<R, L>> regionLocationToRegion;

	private final Path directory;
	private final int sectorSize;
	private final int maxSize;

	public RegionProvider(Path directory) {
		this(directory, 512, 126);
	}

	public RegionProvider(Path directory, int sectorSize, int maxSize) {
		this.regionLocationToRegion = new HashMap<>(maxSize*2); // methods below are synchronized anyway, no need for concurrent map
		this.directory = directory;
		this.sectorSize = sectorSize;
		this.maxSize = maxSize;
	}

	@Override public synchronized IRegion<R, L> getRegion(R location) throws IOException {
		return getRegion(location, true); // it can't be null here
	}

	@Override public synchronized Optional<IRegion<R, L>> getRegionIfExists(R location) throws IOException {
		return Optional.of(getRegion(location, false));
	}

	private synchronized IRegion<R, L> getRegion(R location, boolean canCreate) throws IOException {
		if (regionLocationToRegion.size() > maxSize) {
			this.clearRegions();
		}

		IRegion<R, L> region = regionLocationToRegion.get(location);
		if (region == null) {
			Path regionPath = directory.resolve(location.getRegionName());

			if (!canCreate && !Files.exists(regionPath)) {
				return null;
			}

			region = new Region<>(regionPath, location.getEntryCount(), sectorSize);
			regionLocationToRegion.put(location, region);
		}
		return region;
	}

	private void clearRegions() throws IOException {
		Iterator<IRegion<R, L>> it = regionLocationToRegion.values().iterator();
		while (it.hasNext()) {
			it.next().close();
			it.remove();
		}
	}
}
