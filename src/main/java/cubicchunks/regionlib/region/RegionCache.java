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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.IEntryLocation;

public class RegionCache<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> {

	private final RegionFactory<R, L> regionFactory;
	private final Map<R, Region<R, L>> regionLocationToRegion;
	private final int maxSize;

	public RegionCache(RegionFactory<R, L> regionFactory, int maxSize) {
		this.regionLocationToRegion = new HashMap<>(maxSize*2); // methods below are synchronized anyway, no need for concurrent map
		this.regionFactory = regionFactory;
		this.maxSize = maxSize;
	}

	public synchronized Region<R, L> getRegion(R location) throws IOException {
		return getRegion(location, RegionFactory.CreateType.CREATE).get(); // it can't be null here
	}

	public synchronized Optional<Region<R, L>> getRegionIfExists(R location) throws IOException {
		return getRegion(location, RegionFactory.CreateType.LOAD);
	}

	private synchronized Optional<Region<R, L>> getRegion(R location, RegionFactory.CreateType createType) throws IOException {
		if (regionLocationToRegion.size() > maxSize) {
			this.clearRegions();
		}
		if (!regionLocationToRegion.containsKey(location)) {
			regionFactory.createRegion(location, createType).ifPresent(r -> regionLocationToRegion.put(location, r));
		}
		return Optional.of(regionLocationToRegion.get(location));
	}

	private void clearRegions() throws IOException {
		Iterator<Region<R, L>> it = regionLocationToRegion.values().iterator();
		while (it.hasNext()) {
			it.next().close();
			it.remove();
		}
	}
}
