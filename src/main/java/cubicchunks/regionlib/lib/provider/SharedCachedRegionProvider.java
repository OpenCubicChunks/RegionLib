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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A region caching provider that uses a shared underlying cache for all instances
 */
public class SharedCachedRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Map<SharedCacheKey<?>, IRegion<?>> regionLocationToRegion = new ConcurrentHashMap<>(512);
    private static final int maxCacheSize = 256;

    private final IRegionFactory<K> sourceFactory;
    private volatile boolean closed;

    /**
     * Creates a RegionProvider using the given {@code regionFactory}
     *
     * @param sourceFactory {@link IRegionFactory} used as source of regions
     */
    public SharedCachedRegionProvider(IRegionFactory<K> sourceFactory) {
        this.sourceFactory = sourceFactory;
    }

    @Override
    public <R> Optional<R> fromExistingRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.fromRegion(key, func, false);
    }

    @Override
    public <R> R fromRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.fromRegion(key, func, true).get();
    }

    @Override
    public void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.forRegion(key, cons, true);
    }

    @Override
    public void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        this.forRegion(key, cons, false);
    }

    @Override
    public Stream<RegionKey> allRegions() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.sourceFactory.allRegions();
    }

    @Override
    public Stream<K> allKeys() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        return this.sourceFactory.allRegions()
                .flatMap(regionKey -> {
                    try {
                        IKeyProvider<K> keyProvider = this.sourceFactory.getKeyProvider();
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
    public void flush() throws IOException {
        synchronized (regionLocationToRegion) {
            if (this.closed) {
                throw new IllegalStateException("Already closed");
            }
            flushRegions();
        }
    }

    @Override public void close() throws IOException {
        synchronized (regionLocationToRegion) {
            if (this.closed) {
                throw new IllegalStateException("Already closed");
            }
            clearRegions();
            this.closed = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void forRegion(K location, CheckedConsumer<? super IRegion<K>, IOException> cons, boolean canCreate) throws IOException {
        IRegion<K> region;
        Lock readLock = lock.readLock();
        Lock writeLock = lock.writeLock();
        SharedCacheKey<?> sharedKey = new SharedCacheKey<>(location.getRegionKey(), this.sourceFactory);
        boolean createNew = false;

        readLock.lock();
        try {
            try {
                region = (IRegion<K>) regionLocationToRegion.computeIfAbsent(sharedKey, shared -> {
                    try {
                        return this.sourceFactory.getExistingRegion(sharedKey.getRegionKey()).orElse(null);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            if (region == null && canCreate) {
                createNew = true;
            }
            if (region != null) {
                cons.accept(region);
            }
        } finally {
            readLock.unlock();
        }
        if (createNew) {
            writeLock.lock();
            try {
                if (regionLocationToRegion.size() > MAX_CACHE_SIZE) {
                    clearRegions();
                }
                region = this.sourceFactory.getRegion(sharedKey.getRegionKey());
                regionLocationToRegion.put(sharedKey, region);
                cons.accept(region);
            } finally {
                writeLock.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <R> Optional<R> fromRegion(K location, CheckedFunction<? super IRegion<K>, R, IOException> func, boolean canCreate) throws IOException {
        IRegion<K> region;
        Lock readLock = lock.readLock();
        Lock writeLock = lock.writeLock();
        SharedCacheKey<?> sharedKey = new SharedCacheKey<>(location.getRegionKey(), this.sourceFactory);
        boolean createNew = false;

        readLock.lock();
        try {
            try {
                region = (IRegion<K>) regionLocationToRegion.computeIfAbsent(sharedKey, shared -> {
                    try {
                        return this.sourceFactory.getExistingRegion(sharedKey.getRegionKey()).orElse(null);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            if (region == null && canCreate) {
                createNew = true;
            }

            if (region != null) {
                return Optional.of(func.apply(region));
            }
        } finally {
            readLock.unlock();
        }
        if (createNew) {
            writeLock.lock();
            try {
                if (regionLocationToRegion.size() > MAX_CACHE_SIZE) {
                    clearRegions();
                }
                region = this.sourceFactory.getRegion(sharedKey.getRegionKey());
                regionLocationToRegion.put(sharedKey, region);
                return Optional.of(func.apply(region));
            } finally {
                writeLock.unlock();
            }
        }
        return Optional.empty();
    }

    public static synchronized void flushRegions() throws IOException {
        lock.writeLock().lock();
        try {
            Iterator<IRegion<?>> it = regionLocationToRegion.values().iterator();
            while (it.hasNext()) {
                it.next().flush();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static synchronized void clearRegions() throws IOException {
        lock.writeLock().lock();
        try {
            Iterator<IRegion<?>> it = regionLocationToRegion.values().iterator();
            while (it.hasNext()) {
                it.next().close();
            }
            regionLocationToRegion.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static class SharedCacheKey<K extends IKey<K>> {
        private final RegionKey regionKey;
        private final IRegionFactory<K> regionFactory;

        private SharedCacheKey(RegionKey regionKey, IRegionFactory<K> regionFactory) {
            this.regionKey = regionKey;
            this.regionFactory = regionFactory;
        }

        public RegionKey getRegionKey() {
            return this.regionKey;
        }

        public IRegionFactory<K> getRegionFactory() {
            return this.regionFactory;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (!(o instanceof SharedCacheKey)) {
                return false;
            }

            SharedCacheKey<?> that = (SharedCacheKey<?>) o;
            return this.regionKey.equals(that.regionKey) && this.regionFactory.equals(that.regionFactory);
        }

        @Override public int hashCode() {
            int result = getRegionKey().hashCode();
            result = 31 * result + getRegionFactory().hashCode();
            return result;
        }
    }
}
