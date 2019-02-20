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
package cubicchunks.regionlib.lib;

import cubicchunks.regionlib.api.region.IRegion;
import cubicchunks.regionlib.api.region.header.IHeaderDataEntry;
import cubicchunks.regionlib.api.region.header.IHeaderDataEntryProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// TODO: Optimize?
public class ExtRegion<K extends IKey<K>> implements IRegion<K> {

    private final Path directory;
    private final List<IHeaderDataEntryProvider<?, K>> headerData;
    private final IKeyProvider<K> keyProvider;
    private final RegionKey regionKey;
    private final int totalHeaderSize;

    private final BitSet exists;

    public ExtRegion(Path saveDirectory, List<IHeaderDataEntryProvider<?, K>> headerData, IKeyProvider<K> keyProvider, RegionKey regionKey)
            throws IOException {
        this.directory = saveDirectory.resolve(regionKey.getName() + ".ext");
        this.headerData = headerData;
        this.keyProvider = keyProvider;
        this.regionKey = regionKey;
        int headerSize = 0;
        for (IHeaderDataEntryProvider<?, ?> p : headerData) {
            headerSize += p.getEntryByteCount();
        }
        this.totalHeaderSize = headerSize;
        this.exists = new BitSet(keyProvider.getKeyCount(regionKey));
        if (!Files.exists(this.directory)) {
            return;
        }
        try(Stream<Path> stream = Files.list(this.directory)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                try {
                    int i = Integer.parseInt(name);
                    if (i >= 0 && i < keyProvider.getKeyCount(regionKey)) {
                        exists.set(i);
                    }
                } catch (NumberFormatException ex) {
                }
            });
        }
    }

    @Override public void writeValue(K key, ByteBuffer value) throws IOException {
        if (!Files.exists(this.directory)) {
            Utils.createDirectories(this.directory);
        }
        Path file = directory.resolve(String.valueOf(key.getId()));
        if (!Files.exists(file)) {
            if (value == null) {
                return;
            }
            Files.createFile(file);
        } else if (value == null) {
            Files.delete(file);
            return;
        }

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file.toFile()))) {
            for (IHeaderDataEntryProvider<?, K> h : headerData) {
                IHeaderDataEntry entry = h.apply(key);
                ByteBuffer buf = ByteBuffer.allocate(h.getEntryByteCount());
                entry.write(buf);
                os.write(buf.array());
            }
            os.write(value.array());
        }
        exists.set(key.getId());
    }

    @Override public Optional<ByteBuffer> readValue(K key) throws IOException {
        if (!exists.get(key.getId())) {
            return Optional.empty();
        }
        Path file = directory.resolve(String.valueOf(key.getId()));
        if (!Files.exists(file)) {
            exists.set(key.getId(), false);
            return Optional.empty();
        }
        int bytes = (int) (Files.size(file) - totalHeaderSize);
        try (InputStream is = new BufferedInputStream(new FileInputStream(file.toFile()))) {
            is.skip(totalHeaderSize);
            byte[] data = new byte[bytes];
            is.read(data);
            return Optional.of(ByteBuffer.wrap(data));
        }
    }

    @Override public boolean hasValue(K key) {
        return exists.get(key.getId());
    }

    @Override public void forEachKey(CheckedConsumer<? super K, IOException> cons) throws IOException {
        try {
            exists.stream().mapToObj(i -> keyProvider.fromRegionAndId(regionKey, i)).forEach(x -> {
                        try {
                            cons.accept(x);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
            );
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    @Override public void close() throws IOException {
        // no-op
    }
}
