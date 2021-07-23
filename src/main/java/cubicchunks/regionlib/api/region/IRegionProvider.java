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

import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a by-key access to Regions, also allows to get access to all existing regions.
 * Can be used as a source of regions (creation/loading/caching).
 *
 * @param <K> The key type
 */
public interface IRegionProvider<K extends IKey<K>> extends Flushable, Closeable {

    /**
     * Calls the given consumer with region at that location. Creates new region if one doesn't exist yet.
     * The region will be closed automatically as needed.
     *
     * @param key      The key for the IRegion
     * @param consumer Consumer that accepts the IRegion
     */
    void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException;

    /**
     * Calls the given consumer with region at that location. Doesn't create new region if one doesn't exist yet.
     * The region will be closed automatically as needed.
     *
     * @param key The key for the IRegion
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
     * @param key      The key for the IRegion
     * @param consumer Accepts the given region, if it already exists
     */
    void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException;

    /**
     * Gets a {@link Stream} over the keys of all the existing regions.
     * <p>
     * All regions that had been created at the time of this method invocation are guaranteed to be present in the returned {@link Stream}, no guarantees
     * are made for regions created during iteration.
     * <p>
     * Note that the returned {@link Stream} must be closed (using {@link Stream#close()}) once no longer needed.
     *
     * @return a {@link Stream} over the keys of all the existing regions
     */
    Stream<RegionKey> allRegions() throws IOException;

    /**
     * Gets a {@link Stream} over all already saved keys.
     * <p>
     * Keys saved while the {@link Stream} is being evaluated are not guaranteed to be listed.
     * <p>
     * Note that the returned {@link Stream} must be closed (using {@link Stream#close()}) once no longer needed.
     *
     * @return a {@link Stream} over all already saved keys
     */
    Stream<K> allKeys() throws IOException;
}
