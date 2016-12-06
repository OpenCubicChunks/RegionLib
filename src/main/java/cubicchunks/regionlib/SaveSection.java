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
package cubicchunks.regionlib;

import java.io.IOException;
import java.util.Optional;

import cubicchunks.regionlib.region.IRegionLocation;
import cubicchunks.regionlib.region.Region;
import cubicchunks.regionlib.region.RegionCache;

public class SaveSection<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> {

	private final RegionCache<R, L> regionCache;

	public SaveSection(RegionCache<R, L> regionCache) {
		this.regionCache = regionCache;
	}

	public void save(Entry<R, L> entry) throws IOException{
		this.regionCache.getRegion(entry.getLocation().getRegionLocation()).writeEntry(entry);
	}

	public Optional<Entry<R, L>> load(L location) throws IOException, CurruptedDataException {
		Optional<Region<R, L>> region = this.regionCache.getRegionIfExists(location.getRegionLocation());
		if (region.isPresent()) {
			return region.get().readEntry(location);
		}
		return Optional.empty();
	}
}
