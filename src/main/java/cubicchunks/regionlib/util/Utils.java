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
package cubicchunks.regionlib.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Utils {
    // Files.createDirectories doesn't handle symlinks
    public static void createDirectories(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        createDirectories(dir.getParent());
        Files.createDirectory(dir);
    }

    /**
     * Fills the given {@link ByteBuffer} with bytes read from the given {@link ByteChannel}.
     *
     * @param src  the channel to write to
     * @param data the data to write
     */
    public static void readFully(ByteChannel src, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            src.read(data);
        }
    }

    /**
     * Writes the entire contents of the given {@link ByteBuffer} to the given {@link ByteChannel}.
     *
     * @param dst  the channel to write to
     * @param data the data to write
     */
    public static void writeFully(ByteChannel dst, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            dst.write(data);
        }
    }

    /**
     * Writes the entire contents of all of the given {@link ByteBuffer}s to the given {@link ByteChannel}.
     *
     * @param dst  the channel to write to
     * @param data the data to write
     */
    public static void writeFully(GatheringByteChannel dst, ByteBuffer[] data) throws IOException {
        long totalRemaining = Arrays.stream(data).mapToLong(ByteBuffer::remaining).sum();
        long totalWritten = 0L;
        while (totalWritten < totalRemaining) {
            totalWritten += dst.write(data);
        }
    }

    /**
     * Concatenates the given {@link Stream}s.
     *
     * @param streams the {@link Stream}s to concatenate
     * @param <T> the value type
     * @return a {@link Stream} containing the concatenated values
     */
    public static <T> Stream<T> concat(List<Stream<T>> streams) {
        switch (streams.size()) {
            case 0:
                return Stream.empty();
            case 1:
                return streams.get(0);
            case 2:
                return Stream.concat(streams.get(0), streams.get(1));
            default:
                //recursively split list in half in order to make the streams form a balanced binary tree.
                //  this can significantly improve stream performance for parallel workloads.
                int halfSize = streams.size() >> 1;
                return Stream.concat(
                        concat(streams.subList(0, halfSize)),
                        concat(streams.subList(halfSize, streams.size())));
        }
    }
}
