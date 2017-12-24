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

import cubicchunks.regionlib.api.region.key.IKeyProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.lib.Region;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class TestRegion {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test public void testRegionPersistence() throws IOException {
		// TODO: make this test code easier to understand

		// writes 1000 random byte arrays, each time reading all previously written arrays to confirm they are the same
		// also measures time it took and amount of read/written bytes
		Path path = folder.newFolder("save").toPath();
		IKeyProvider<EntryLocation3D> keyProvider = new EntryLocation3D.Provider();
		EntryLocation3D key = new EntryLocation3D(0, 0, 0);
		Region<EntryLocation3D> save =
				new Region.Builder()
						.setDirectory(path)
						.setKeyProvider(keyProvider)
						.setRegionKey(key.getRegionKey())
						.setSectorSize(512)
						.build();

		Random rnd = new Random(42);
		ByteBuffer[] dataArray = new ByteBuffer[1000];
		long totalBytes = 0;
		long totalRead = 0;
		for (int i = 0; i < dataArray.length; i++) {
			dataArray[i] = getData(rnd);
			totalBytes += dataArray[i].remaining();
		}
		System.out.println("START");
		long time = -System.nanoTime();
		EntryLocation3D[] entryLocations = new EntryLocation3D[dataArray.length];

		Map<EntryLocation3D, Integer> previousWrites = new HashMap<>();

		for (int i = 0; i < dataArray.length; i++) {
			ByteBuffer data = dataArray[i];
			EntryLocation3D loc = new EntryLocation3D(rnd.nextInt(5), rnd.nextInt(5), rnd.nextInt(5));
			if (previousWrites.containsKey(loc)) {
				dataArray[previousWrites.get(loc)] = null;
			}
			previousWrites.put(loc, i);
			entryLocations[i] = loc;
			save.writeValue(entryLocations[i], data);
			data.rewind();

			// re-open the Region
			save.close();
			save = new Region.Builder()
					.setDirectory(path)
					.setKeyProvider(keyProvider)
					.setRegionKey(key.getRegionKey())
					.setSectorSize(512)
					.build();

			for (int readI = 0; readI <= i; readI++) {
				if (dataArray[readI] == null) {
					continue;
				}
				ByteBuffer loaded;
				String msg = "Reading array " + readI + " loc=" + entryLocations[readI] + " after writing " + i + " loc=" + entryLocations[i];
				try {
					loaded = save.readValue(entryLocations[readI]).get();
				} catch (Exception ex) {
					ex.printStackTrace();
					fail(msg + " ex=" + ex);
					throw new RuntimeException(ex);
				}
				totalRead += loaded.remaining();

				// use assertArrayEquals so that it actually shows the difference when it's not equal
				assertArrayEquals("Reading array " + readI + " after writing " + i, dataArray[readI].array(), loaded.array());
			}

		}
		System.out.println((System.nanoTime() + time)/1000000000.0 + " seconds");
		System.out.println("Wrote total " + totalBytes + " bytes");
		System.out.println("Read total " + totalRead + " bytes");
	}

	private ByteBuffer getData(Random rnd) {
		int size = rnd.nextInt(2000);
		byte[] arr = new byte[size];
		rnd.nextBytes(arr);
		return ByteBuffer.wrap(arr);
	}
}
