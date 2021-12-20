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
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.lib.provider.SharedCachedRegionProvider;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides access to regions in a directory.
 * <p>
 * Note that all {@link IRegion} instances returned will be newly created. Users are responsible for ensuring that multiple regions corresponding
 * to the same key are not open at once. Therefore, this should be used with some caching implementation of {@link IRegionProvider} (such as
 * {@link SharedCachedRegionProvider}).
 *
 * @param <K> The key type
 */
public interface IRegionFactory<K extends IKey<K>> {
    /**
     * @return the {@link IKeyProvider} used by this factory
     */
    IKeyProvider<K> getKeyProvider();

    /**
     * Opens an {@link IRegion} at a given region key, creating a new one if none exists.
     *
     * @param key the key for the {@link IRegion}
     * @return the {@link IRegion} at the given key
     */
    IRegion<K> getRegion(RegionKey key) throws IOException;

    /**
     * Opens an existing {@link IRegion} at a given region key.
     *
     * @param key the key for the {@link IRegion}
     * @return the {@link IRegion} at the given key, or an empty {@link Optional} if none exists
     */
    Optional<IRegion<K>> getExistingRegion(RegionKey key) throws IOException;

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
}
