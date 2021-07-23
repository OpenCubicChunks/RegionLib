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
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A shared cache for {@link IRegion}s.
 *
 * @see SharedCachedRegionProvider
 */
public class SharedCache implements Flushable, Closeable {
    /*
     * Implementation notes:
     *
     * This cache operates using a hard and soft limit. Once the soft limit is hit, a cleanup operation will be triggered which will evict some entries by their
     * insertion order. Since only one cleanup is permitted to run at a time, however, other threads will not block and will still be able to open more regions
     * or operate on already cached ones. The only time that all threads will block is if the cache totally fills up (the hard limit is reached), which is only
     * possible if other threads are opening regions faster than the thread currently running a cleanup is able to close them.
     *
     * It relies on the synchronization guarantees of ConcurrentHashMap#compute to ensure that no one region is accessed by more than one thread at a time.
     */

    public static final SharedCache DEFAULT = new SharedCache(Integer.parseUnsignedInt(System.getProperty("cubicchunks.regionlib.maxRegionCacheSize", "256")));

    private final int maxCacheSize;
    private final int softCleanupThreshold;

    private final Map<SharedCacheKey<?>, WrappedRegion<?>> cache;

    private final Semaphore availableTickets;
    private final Semaphore cleanupRunning = new Semaphore(1);

    //we assume this will never overflow (and even if it does, the results wouldn't be catastrophic - it would simply result in the whole cache being dropped)
    private final AtomicLong openCounter = new AtomicLong(Long.MIN_VALUE);

    public SharedCache(int maxCacheSize) {
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("maxCacheSize must be positive!");
        } else if (maxCacheSize < 2) {
            throw new IllegalArgumentException("maxCacheSize must be at least 2!");
        }

        this.maxCacheSize = maxCacheSize;
        this.softCleanupThreshold = Math.max(1, maxCacheSize - Math.max(1, Math.min(maxCacheSize >> 3, Runtime.getRuntime().availableProcessors() << 1)));
        this.cache = new ConcurrentHashMap<>(Math.max(maxCacheSize << 1, maxCacheSize)); //make it slightly bigger to prevent rehashing (and use Math.min() as an overflow guard)
        this.availableTickets = new Semaphore(maxCacheSize);
    }

    protected boolean cleanup0(boolean force, boolean full) throws IOException {
        int availableTickets = this.availableTickets.availablePermits();
        int usedTickets = this.maxCacheSize - availableTickets;

        if (force) {
            this.cleanupRunning.acquireUninterruptibly();
        } else if ((full ? usedTickets == 0 : usedTickets < this.softCleanupThreshold) //cache is empty/not full enough, there's nothing to do
                   || !this.cleanupRunning.tryAcquire()) { //cleanup is already running, so there's nothing to do
            return false;
        }

        try {
            long expirationThreshold = full
                    ? Long.MAX_VALUE //if a full cache purge is requested, use a threshold which will cause all entries to be dropped
                    : this.openCounter.get() - (usedTickets >> 1); //otherwise, drop half the elements

            AtomicReference<IOException> exceptionRef = new AtomicReference<>();
            this.cache.forEach((key, wrappedRegion) -> {
                if (wrappedRegion.openedTime <= expirationThreshold) { //region is past the expiration threshold, so we'll close it
                    this.cache.compute(key, (_k, _r) -> {
                        try { //actually close the region
                            wrappedRegion.region.close();
                        } catch (IOException e) { //remember exception for later, but still make sure we return null to avoid having invalid regions sitting in the cache
                            if (exceptionRef.get() == null) {
                                exceptionRef.set(new IOException());
                            }
                            exceptionRef.get().addSuppressed(e);
                        }

                        this.availableTickets.release(); //release the region's ticket to allow us to open a new region to replace it later
                        return null; //delete region from the map
                    });
                }
            });

            if (exceptionRef.get() != null) { //at least one exception was thrown, rethrow it
                throw exceptionRef.get();
            }

            return true;
        } finally {
            //make sure we always release our hold on the cleanup
            this.cleanupRunning.release();
        }
    }

    @Override
    public void close() throws IOException {
        this.cleanup0(true, true);
    }

    @Override
    public void flush() throws IOException {
        try {
            this.cache.forEach((key, wrappedRegion) -> this.cache.compute(key, (_k, region) -> {
                if (wrappedRegion != region) { //since we don't actually hold any locks, it's possible that the region might have been closed, so we can just skip it
                    return region;
                }

                try {
                    region.region.flush();
                    return region;
                } catch (IOException e) { //rethrow exception (no need for any special handling, since we're not actually replacing the region)
                    throw new UncheckedIOException(e);
                }
            }));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Runs the given function on the region supplied by the given {@link IRegionFactory} for the given {@link RegionKey}.
     *
     * @param regionKey     the region key
     * @param regionFactory the region factory
     * @param allowCreation whether or not the region is allowed to be created if it doesn't exist
     * @param callback      the function to run
     * @return whether or not the function was run. This will return {@code false} only if {@code allowCreation == false} and the region doesn't exist
     */
    @SuppressWarnings("unchecked")
    public <K extends IKey<K>> boolean forRegion(RegionKey regionKey, IRegionFactory<K> regionFactory, boolean allowCreation, CheckedConsumer<? super IRegion<K>, IOException> callback) throws IOException {
        SharedCacheKey<K> key = new SharedCacheKey<>(regionKey, regionFactory);
        AtomicReference<Boolean> done = new AtomicReference<>();
        AtomicReference<Boolean> cleanup = new AtomicReference<>();
        AtomicReference<IOException> exception = new AtomicReference<>();

        do {
            try {
                this.cache.compute(key, (k, wrappedRegion) -> {
                    if (wrappedRegion == null) { //the region isn't cached
                        if (!this.availableTickets.tryAcquire()) { //we were unable to acquire a ticket, meaning that the cache is totally full
                            cleanup.set(true); //trigger a blocking cleanup
                            return null;
                        } else { //try to open a new region and assign it to the map
                            Optional<IRegion<K>> region;
                            try {
                                region = allowCreation ? Optional.of(regionFactory.getRegion(regionKey)) : regionFactory.getExistingRegion(regionKey);
                            } catch (IOException e) {
                                this.availableTickets.release(); //we couldn't open the region, so the ticket needs to be released
                                throw new UncheckedIOException(e);
                            }

                            if (!region.isPresent()) {
                                this.availableTickets.release(); //the region was never opened (because it doesn't exist), so the ticket needs to be released
                                done.set(false); //the user callback wasn't run, but since the region doesn't exist there's nothing else to do
                                return null;
                            }

                            cleanup.set(false); //trigger a lazy cleanup (which won't block if a cleanup is already running)
                            wrappedRegion = new WrappedRegion<>(region.get(), this.openCounter.getAndIncrement());
                        }
                    }

                    try { //let the user callback handle the region
                        callback.accept((IRegion<K>) wrappedRegion.region);
                        done.set(true); //the user callback has been run
                    } catch (IOException e) { //don't rethrow the exception immediately, because we need
                        exception.set(e);
                    }

                    return wrappedRegion;
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }

            if (exception.get() != null) { //the user callback threw an exception, we should rethrow it
                throw exception.get();
            }

            if (cleanup.get() != null) { //a cleanup was requested
                this.cleanup0(cleanup.get(), false);
                cleanup.set(null);
            }
        } while (done.get() == null);
        return done.get();
    }

    private static class SharedCacheKey<K extends IKey<K>> {
        private final RegionKey regionKey;
        private final IRegionFactory<K> regionFactory;

        private SharedCacheKey(RegionKey regionKey, IRegionFactory<K> regionFactory) {
            this.regionKey = regionKey;
            this.regionFactory = regionFactory;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof SharedCacheKey) {
                SharedCacheKey<?> that = (SharedCacheKey<?>) o;
                return this.regionKey.equals(that.regionKey) && this.regionFactory.equals(that.regionFactory);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.regionKey.hashCode() * 31 + this.regionFactory.hashCode();
        }
    }

    private static class WrappedRegion<K extends IKey<K>> {
        private final IRegion<K> region;
        private final long openedTime;

        private WrappedRegion(IRegion<K> region, long openedTime) {
            this.region = region;
            this.openedTime = openedTime;
        }
    }
}
