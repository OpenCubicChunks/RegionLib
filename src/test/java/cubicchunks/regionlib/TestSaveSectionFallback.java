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
package cubicchunks.regionlib;

import static org.junit.Assert.assertEquals;

import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestSaveSectionFallback {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test public void testFallback() throws IOException {
        Path path = folder.newFolder("save").toPath();
        SaveCubeColumns save = SaveCubeColumns.create(path);
        ByteBuffer savedData = getData();

        save.save3d(new EntryLocation3D(0, 0, 0), savedData);
        ByteBuffer loadedData = save.load(new EntryLocation3D(0, 0, 0), true).get();

        savedData.rewind();
        assertEquals(savedData, loadedData);
    }

    @Test public void testFallbackBatchWrite() throws IOException {
        Path path = folder.newFolder("save").toPath();
        SaveCubeColumns save = SaveCubeColumns.create(path);
        ByteBuffer savedData = getData();

        //not using Collections.singletonMap because the map is required to be mutable
        Map<EntryLocation3D, ByteBuffer> map = new HashMap<>();
        map.put(new EntryLocation3D(0, 0, 0), savedData);
        save.save3d(map);
        ByteBuffer loadedData = save.load(new EntryLocation3D(0, 0, 0), true).get();

        savedData.rewind();
        assertEquals(savedData, loadedData);
    }

    private ByteBuffer getData() {
        // this exceeds max size of normal region
        int size = 1024*1024*256;
        Random rand = new Random(123456789);
        byte[] bytes = new byte[size];
        rand.nextBytes(bytes);
        return ByteBuffer.wrap(bytes);
    }
}
