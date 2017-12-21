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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.region.Region;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestExtStorage {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test public void testRegionPersistence() throws IOException {
        // TODO: make this test code easier to understand

        // writes 1000 random byte arrays, each time reading all previously written arrays to confirm they are the same
        // also measures time it took and amount of read/written bytes
        folder.newFolder("save");
        Path path = folder.newFile("save/test.3dr").toPath();
        EntryLocation3D key = new EntryLocation3D(0, 0, 0);
        Region<EntryLocation3D> save =
                new Region.Builder().setPath(path).setEntriesPerRegion(key.getKeyCount()).setSectorSize(512).build();

        Random rnd = new Random(42);
        ByteBuffer data = getData(rnd);

        EntryLocation3D loc = new EntryLocation3D(0, 0, 0);
        save.writeValue(loc, data);
        data.rewind();

        // re-open the Region
        save.close();
        save = new Region.Builder().setPath(path).setEntriesPerRegion(key.getKeyCount()).setSectorSize(512).build();

        ByteBuffer loaded;
        try {
            loaded = save.readValue(loc).get();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("ex=" + ex);
            throw new RuntimeException(ex);
        }

        // use assertArrayEquals so that it actually shows the difference when it's not equal
        assertArrayEquals("Reading array after writing ", data.array(), loaded.array());
    }

    private ByteBuffer getData(Random rnd) {
        int size = 1024 * 1024 * 5;//5MB
        byte[] arr = new byte[size];
        rnd.nextBytes(arr);
        return ByteBuffer.wrap(arr);
    }
}
