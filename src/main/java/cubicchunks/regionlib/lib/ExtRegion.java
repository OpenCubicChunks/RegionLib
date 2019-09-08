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

import cubicchunks.regionlib.UnsupportedDataException;
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
import java.nio.channels.SeekableByteChannel;
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

    private boolean initialized = false;

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
        this.initialized = true;
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
        if (value == null && (!initialized || exists.isEmpty() || !exists.get(key.getId()))) {
            // fast path, make sure we don't create the directory when writing nothing
            // (SaveSection makes sure to erase ext region is normal write succeeds)
            return;
        }
        if (!initialized) {
            Utils.createDirectories(this.directory);
            initialized = true;
        }
        Path file = directory.resolve(String.valueOf(key.getId()));
        if (!Files.exists(file)) {
            if (value == null) {
                exists.clear(key.getId());
                return;
            }
            Files.createFile(file);
        } else if (value == null) {
            Files.delete(file);
            exists.clear(key.getId());
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

    @Override public void writeSpecial(K key, Object marker) throws IOException {
        throw new UnsupportedOperationException("ExtRegion doesn't support special values");
    }

    @Override public Optional<ByteBuffer> readValue(K key) throws IOException {
        if (!initialized || !exists.get(key.getId())) {
            return Optional.empty();
        }
        Path file = directory.resolve(String.valueOf(key.getId()));
        if (!Files.exists(file)) {
            exists.set(key.getId(), false);
            return Optional.empty();
        }
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            if (size > Integer.MAX_VALUE) {
                throw new UnsupportedDataException("Size " + size + " is too big");
            }
            ByteBuffer buf = ByteBuffer.wrap(new byte[(int) (size - totalHeaderSize)]);
            channel.position(totalHeaderSize).read(buf);
            buf.rewind();
            return Optional.of(buf);
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
