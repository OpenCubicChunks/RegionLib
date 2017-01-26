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
package cubicchunks.regionlib.region.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

/**
 * Acts as a source of regions (creation/loading/caching)
 *
 * @param <K> The key type
 */
public interface IRegionProvider<K extends IKey<K>> extends Closeable {

	/**
	 * Calls the given consumer with region at that location. Creates new region if one doesn't exist yet.
	 * The region will be closed automatically as needed.
	 *
	 * @param key The key for the IRegion
	 * @param consumer Consumer that accepts the IRegion
	 */
	void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException;

	/**
	 * Calls the given consumer with region at that location. Doesn't create new region if one doesn't exist yet.
	 * The region will be closed automatically as needed.
	 *
	 * @param key The key for the IRegion
	 *
	 * @return An Optional containing the IRegion at {@code regionKey} if it exists
	 */
	<R> Optional<R> fromExistingRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException;

	/**
	 * Calls the given function with region at that location and returns value from that function. Creates new region if
	 * one doesn't exist yet. The region will be closed automatically as needed.
	 *
	 * @param key The key for the IRegion
	 */
	<R> R fromRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException;

	/**
	 * Calls the given function with region at that location and returns value from that function.. Doesn't create new
	 * region if one doesn't exist yet. The region will be closed automatically as needed.
	 *
	 * @param key The key for the IRegion
	 *
	 * @return An Optional containing the IRegion at {@code regionKey} if it exists
	 */
	void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException;

	/**
	 * Gets an IRegion at a given region key, or create one if it does not exist. The region will not be closed
	 * automatically. Region instance returned by this method won't be returned again, instead new one will be created
	 * (not cached).
	 *
	 * @param key The key for the IRegion
	 *
	 * @return The IRegion at {@code regionKey}
	 */
	IRegion<K> getRegion(K key) throws IOException;

	/**
	 * Gets an IRegion at a given region key, creates new one if it doesn't exist. The region will not be closed
	 * automatically. Region instance returned by this method won't be returned again, instead new one will be created
	 * (not cached).
	 *
	 * @param key The key for the IRegion
	 *
	 * @return An Optional containing the IRegion at {@code regionKey} if it exists
	 */
	Optional<IRegion<K>> getExistingRegion(K key) throws IOException;

	/**
	 * Calls the given consumer for all existing region names.
	 */
	void forAllRegions(CheckedConsumer<? super String, IOException> consumer) throws IOException;
}
