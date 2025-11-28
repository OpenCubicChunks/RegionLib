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
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.MultiUnsupportedDataException;
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
public interface IRegion<K extends IKey<K>> extends Flushable, Closeable {

	/**
	 * Stores a value at a key within this region
	 *
	 * @param key A key within this region
	 * @param value The value to store. Null to remove existing value.
	 *
	 * @throws UnsupportedDataException if the data cannot be written due to internal constraints of the storage format. The stored data must remain
	 * unchanged after this exception is thrown
	 */
	void writeValue(K key, ByteBuffer value) throws IOException;

	/**
	 * Stores multiple values at their corresponding keys within this region
	 *
	 * @param entries the key-value pairs to store. A value of {@code null} will remove an existing value
	 *
	 * @throws MultiUnsupportedDataException if the data cannot be written due to internal constraints of the storage format. The exception contains
	 * all of the keys whose values failed to be written, and stored data for failed key-value pairs must remain unchanged
	 */
	default void writeValues(Map<K, ByteBuffer> entries) throws IOException {
		List<UnsupportedDataException.WithKey> exceptions = new ArrayList<>();
		for (Iterator<Map.Entry<K, ByteBuffer>> itr = entries.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<K, ByteBuffer> entry = itr.next();
			try {
				//attempt to write one value at a time
				this.writeValue(entry.getKey(), entry.getValue());
			} catch (UnsupportedDataException.WithKey e) {
				//remember for later
				exceptions.add(e);
			} catch (UnsupportedDataException e) {
				exceptions.add(new UnsupportedDataException.WithKey(e, entry.getKey()));
			}
		}

		//throw all pending exceptions at once if there were any
		if (!exceptions.isEmpty()) {
			throw new MultiUnsupportedDataException(exceptions);
		}
	}

	void writeSpecial(K key, Object marker) throws IOException;

	/**
	 * Loads a value at a key if there was something stored there before, within this region
	 *
	 * @param key The key within this region
	 *
	 * @return The value at {@code key} if it exists
	 */
	Optional<ByteBuffer> readValue(K key) throws IOException;

    /**
     * Reads multiple values at their corresponding keys within this region
     *
     * @param keys The locations to read
     * @return An object containing all locations that were successfully read and all exceptions from locations that failed
     */
    default BatchReadResult<K> readValues(Collection<K> keys) {
        HashMap<K, ByteBuffer> out = new HashMap<>(keys.size());
        HashMap<K, IOException> errors = new HashMap<>();

        for (K key : keys) {
            try {
                //attempt to read one value at a time
                Optional<ByteBuffer> result = readValue(key);

                if (result.isPresent()) out.put(key, result.get());
            } catch (IOException e) {
                errors.put(key, e);
            }
        }

        return new BatchReadResult<>(out, errors);
    }

	/**
	 * Returns true if something was stored there before within this region.
	 */
	boolean hasValue(K key);

	/**
	 * Applies the consumer to all existing region keys
	 */
	void forEachKey(CheckedConsumer<? super K, IOException> cons) throws IOException;
}
