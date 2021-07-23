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
package cubicchunks.regionlib.lib.factory;

import cubicchunks.regionlib.api.region.IRegion;
import cubicchunks.regionlib.api.region.IRegionFactory;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.lib.Region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A simple implementation of {@link IRegionFactory}.
 *
 * @param <K> The key type
 */
public class SimpleRegionFactory<K extends IKey<K>> implements IRegionFactory<K> {
    public static <K extends IKey<K>> SimpleRegionFactory<K> createDefault(IKeyProvider<K> keyProvider, Path directory, int sectorSize) {
        return new SimpleRegionFactory<>(keyProvider, directory, (keyProv, r) ->
                new Region.Builder<K>()
                        .setDirectory(directory)
                        .setRegionKey(r)
                        .setKeyProvider(keyProv)
                        .setSectorSize(sectorSize)
                        .build(),
                (keyProv, key) -> Files.exists(directory.resolve(key.getName()))
        );
    }

    private final IKeyProvider<K> keyProvider;
    private final Path directory;
    private final RegionFactory<K> regionBuilder;
    private final SimpleRegionFactory.RegionExistsPredicate<K> regionExists;

    public SimpleRegionFactory(IKeyProvider<K> keyProvider, Path directory,
                               RegionFactory<K> regionBuilder, RegionExistsPredicate<K> regionExists) {
        this.keyProvider = keyProvider;
        this.directory = directory;
        this.regionBuilder = regionBuilder;
        this.regionExists = regionExists;
    }

    @Override
    public IKeyProvider<K> getKeyProvider() {
        return this.keyProvider;
    }

    @Override
    public IRegion<K> getRegion(RegionKey key) throws IOException {
        return this.regionBuilder.create(this.keyProvider, key);
    }

    @Override
    public Optional<IRegion<K>> getExistingRegion(RegionKey key) throws IOException {
        return this.regionExists.test(this.keyProvider, key)
                ? Optional.of(this.getRegion(key))
                : Optional.empty();
    }

    @Override
    public Stream<RegionKey> allRegions() throws IOException {
        return Files.list(this.directory)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(RegionKey::new)
                .filter(this.keyProvider::isValid);
    }

    @FunctionalInterface
    public interface RegionFactory<K extends IKey<K>> {
        IRegion<K> create(IKeyProvider<K> keyProvider, RegionKey key) throws IOException;
    }

    @FunctionalInterface
    public interface RegionExistsPredicate<K extends IKey<K>> {
        boolean test(IKeyProvider<K> regionPath, RegionKey key) throws IOException;
    }
}
