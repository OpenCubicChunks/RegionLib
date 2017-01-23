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
package cubicchunks.regionlib.region.header;

import java.util.function.ToIntFunction;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.IKeyIdToSectorMap;
import cubicchunks.regionlib.region.RegionEntryLocation;

public class EntryLocationHeaderEntryProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>>
	implements IHeaderDataEntryProvider<IntHeaderEntry, R, L> {

	private IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<R, L>, R, L> sectorMap;
	private ToIntFunction<RegionEntryLocation> pack;

	public EntryLocationHeaderEntryProvider(
		IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<R, L>, R, L> sectorMap, ToIntFunction<RegionEntryLocation> pack) {
		this.sectorMap = sectorMap;

		this.pack = pack;
	}

	@Override public int getEntryByteCount() {
		return Integer.BYTES;
	}

	@Override public IntHeaderEntry apply(L key) {
		return new IntHeaderEntry(
			sectorMap.getEntryLocation(key)
				.map(l -> pack.applyAsInt(l))
				.orElse(0)
		);
	}
}
