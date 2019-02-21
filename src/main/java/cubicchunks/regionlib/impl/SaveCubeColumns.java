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
package cubicchunks.regionlib.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import cubicchunks.regionlib.impl.save.SaveSection2D;
import cubicchunks.regionlib.impl.save.SaveSection3D;
import cubicchunks.regionlib.util.Utils;

/**
 * A save for 3d and 2d structures, like Cubes and Columns in CubicChunks.
 */
public class SaveCubeColumns implements Closeable {

	private final SaveSection2D saveSection2D;
	private final SaveSection3D saveSection3D;

	public SaveCubeColumns(SaveSection2D saveSection2D, SaveSection3D saveSection3D) {
		this.saveSection2D = saveSection2D;
		this.saveSection3D = saveSection3D;
	}

	public SaveSection2D getSaveSection2D() {
		return saveSection2D;
	}

	public SaveSection3D getSaveSection3D() {
		return saveSection3D;
	}

	/**
	 * Schedules entry for writing
	 *
	 * This can be accessed from multiple threads. (thread safe)
	 *
	 * @param location location of the entry
     * @param data the entry data to be saved
     * @throws IOException when an unexpected IO error occurs
	 */
	public void save3d(EntryLocation3D location, ByteBuffer data) throws IOException {
		this.saveSection3D.save(location, data);
	}

	/**
	 * Schedules entry for writing
	 *
	 * This can be accessed from multiple threads. (thread safe)
     *
     * @param location location of the entry
     * @param data the entry data to be saved
     * @throws IOException when an unexpected IO error occurs
	 */
	public void save2d(EntryLocation2D location, ByteBuffer data) throws IOException {
		this.saveSection2D.save(location, data);
	}

	/**
	 * Reads entry at given location.
	 *
	 * This can be accessed from multiple threads. (thread safe)
     *
     * @param location the location of the entry data to load
     * @throws IOException when an unexpected IO error occurs
     *
     * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(EntryLocation3D location) throws IOException {
		return saveSection3D.load(location);
	}

	/**
	 * Reads entry at given location.
	 * <p>
	 * This can be accessed from multiple threads. (thread safe)
     *
     * @param location the location of the entry data to load
     * @throws IOException when an unexpected IO error occurs
     *
     * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(EntryLocation2D location) throws IOException {
		return saveSection2D.load(location);
	}

	/**
	 * @param directory directory for the save
     * @throws IOException when an unexpected IO error occurs
	 */
	public static SaveCubeColumns create(Path directory) throws IOException {
		Utils.createDirectories(directory);

		Path part2d = directory.resolve("region2d");
		Utils.createDirectories(part2d);

		Path part3d = directory.resolve("region3d");
		Utils.createDirectories(part3d);

		SaveSection2D section2d = SaveSection2D.createAt(part2d);
		SaveSection3D section3d = SaveSection3D.createAt(part3d);

		return new SaveCubeColumns(section2d, section3d);
	}

	@Override public void close() throws IOException {
		this.saveSection2D.close();
		this.saveSection3D.close();
	}
}
