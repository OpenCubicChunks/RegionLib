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
package cubicchunks.regionlib.lib.provider;

import cubicchunks.regionlib.api.region.IRegion;
import cubicchunks.regionlib.api.region.IRegionFactory;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A region caching provider that uses a shared underlying cache for all instances
 */
public class SharedCachedRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {
    private final SharedCache cache;
    private final IRegionFactory<K> regionFactory;
    private volatile boolean closed;

    /**
     * Creates a RegionProvider using the given {@code regionFactory}
     *
     * @param regionFactory {@link IRegionFactory} used as source of regions
     */
    public SharedCachedRegionProvider(IRegionFactory<K> regionFactory) {
        this(regionFactory, SharedCache.DEFAULT);
    }

    /**
     * Creates a RegionProvider using the given {@code regionFactory} and {@code cache}
     *
     * @param regionFactory {@link IRegionFactory} used as source of regions
     */
    public SharedCachedRegionProvider(IRegionFactory<K> regionFactory, SharedCache cache) {
        this.regionFactory = regionFactory;
        this.cache = cache;
    }

    @Override
    public <R> Optional<R> fromExistingRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        AtomicReference<Optional<R>> resultReference = new AtomicReference<>(Optional.empty());
        this.cache.forRegion(key.getRegionKey(), this.regionFactory, false, region -> resultReference.set(Optional.of(func.apply(region))));
        return resultReference.get();
    }

    @Override
    public <R> R fromRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        AtomicReference<R> resultReference = new AtomicReference<>();
        this.cache.forRegion(key.getRegionKey(), this.regionFactory, true, region -> resultReference.set(func.apply(region)));
        return resultReference.get();
    }

    @Override
    public void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.cache.forRegion(key.getRegionKey(), this.regionFactory, true, cons);
    }

    @Override
    public void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.cache.forRegion(key.getRegionKey(), this.regionFactory, false, cons);
    }

    @Override
    public Stream<RegionKey> allRegions() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.regionFactory.allRegions();
    }

    @Override
    public Stream<K> allKeys() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.regionFactory.allRegions()
                .flatMap(regionKey -> {
                    try {
                        IKeyProvider<K> keyProvider = this.regionFactory.getKeyProvider();
                        int keyCount = keyProvider.getKeyCount(regionKey);

                        if (keyCount == 0) { //there are no keys, break out early!
                            return Stream.empty();
                        }

                        //find all keys in this region (IRegionProvider#fromRegionAndId is slow)
                        List<K> keys = new ArrayList<>(keyCount);
                        for (int id = 0; id < keyCount; id++) {
                            keys.add(keyProvider.fromRegionAndId(regionKey, id));
                        }

                        //lock the region once to filter out keys which aren't present in the region (ArrayList#removeIf is fast)
                        this.forExistingRegion(keys.get(0), region -> keys.removeIf(((Predicate<K>) region::hasValue).negate()));

                        return keys.stream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    @Override
    public Stream<Map.Entry<K, ByteBuffer>> allEntries() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.regionFactory.allRegions()
                .flatMap(regionKey -> {
                    IKeyProvider<K> keyProvider = this.regionFactory.getKeyProvider();
                    return IntStream.range(0, keyProvider.getKeyCount(regionKey)).mapToObj(id -> keyProvider.fromRegionAndId(regionKey, id));
                })
                .map(key -> {
                    try {
                        return (Map.Entry<K, ByteBuffer>) new AbstractMap.SimpleEntry<>(key, this.fromExistingRegion(key, region -> region.readValue(key).orElse(null)).orElse(null));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .filter(entry -> entry.getValue() != null);
    }

    @Override
    public void flush() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.cache.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.closed = true;
        this.cache.close();
    }
}
