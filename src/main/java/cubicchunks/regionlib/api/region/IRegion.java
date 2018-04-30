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
package cubicchunks.regionlib.api.region;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import cubicchunks.regionlib.UnsupportedDataException;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.util.CheckedConsumer;

/**
 * The low level storage for data, represents it's logical organization. Different regions may not necessarily correspong to different files, and a
 * region may be backed by more than one file. A region can store a constant, known amount of entries, specified by {@link IKeyProvider}.
 *
 * Stores all values within a fixed measure (usually area or volume) of keys (locations).
 * Regions are used as a way of *chunking* the database.
 *
 * @param <K> The IKey type
 */
public interface IRegion<K extends IKey<K>> extends Closeable {

	/**
	 * Stores a value at a key within this region
	 *
	 * @param key A key within this region
	 * @param value The value to store
	 *
	 * @throws UnsupportedDataException if the data cannot be written due to internal constraints of the storage format. The stored data must remain
	 * unchanged after this exception is thrown
	 */
	void writeValue(K key, ByteBuffer value) throws IOException;

	/**
	 * Loads a value at a key if there was something stored there before, within this region
	 *
	 * @param key The key within this region
	 *
	 * @return The value at {@code key} if it exists
	 */
	Optional<ByteBuffer> readValue(K key) throws IOException;

	/**
	 * Returns true if something was stored there before within this region.
	 */
	boolean hasValue(K key);

	/**
	 * Applies the consumer to all existing region keys
	 */
	void forEachKey(CheckedConsumer<? super K, IOException> cons) throws IOException;
}
