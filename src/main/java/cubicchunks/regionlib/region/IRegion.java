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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

/**
 * Stores all values within a fixed measure (area/volume/what ever) of keys.<br/>
 * Regions are used as a way of *chunking* the database.
 *
 * @param <R> The IRegionLocation type
 * @param <L> The IEntryLocation type
 */
public interface IRegion<R extends IRegionKey<R, L>, L extends IKey<R, L>> extends Closeable {

	/**
	 * Stores a value at a key within this region
	 *
	 * @param key A key within this region
	 * @param value The value to store
	 */
	void writeValue(L key, ByteBuffer value) throws IOException;

	/**
	 * Loads a value at a key if there was something stored there before, within this region
	 *
	 * @param key The key within this region
	 *
	 * @return The value at {@code key} if it exists
	 */
	Optional<ByteBuffer> readValue(L key) throws IOException;
}
