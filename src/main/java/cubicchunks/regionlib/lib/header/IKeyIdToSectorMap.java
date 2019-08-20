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
package cubicchunks.regionlib.lib.header;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import cubicchunks.regionlib.api.region.header.IHeaderDataEntry;
import cubicchunks.regionlib.api.region.header.IHeaderDataEntryProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.lib.RegionEntryLocation;

public interface IKeyIdToSectorMap<
	H extends IHeaderDataEntry,
	P extends IHeaderDataEntryProvider<H, K>,
	K extends IKey<K>> extends Iterable<RegionEntryLocation> {

	default Optional<RegionEntryLocation> getEntryLocation(K key) {
		return getEntryLocation(key.getId());
	}

	boolean isSpecial(RegionEntryLocation loc);

	Optional<Function<K, ByteBuffer>> trySpecialValue(K key);

	Optional<RegionEntryLocation> getEntryLocation(int id);

	/**
	 * @return optional special conflict resolution writer, if this entry location is reserved.
	 */
	Optional<BiConsumer<K, ByteBuffer>> setOffsetAndSize(K key, RegionEntryLocation location) throws IOException;

	void setSpecial(K key, Object marker);

	P headerEntryProvider();
}
