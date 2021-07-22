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
package cubicchunks.regionlib.impl.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import cubicchunks.regionlib.api.storage.SaveSection;
import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.lib.ExtRegion;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.lib.provider.SharedCachedRegionProvider;
import cubicchunks.regionlib.lib.provider.SimpleRegionProvider;

public class SaveSection2D extends SaveSection<SaveSection2D, EntryLocation2D> {
	/**
	 * Creates a 2D save section with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection2D(IRegionProvider<EntryLocation2D> regionProvider) {
		super(regionProvider);
	}

	/**
	 * Creates a 2D save section with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection2D(IRegionProvider<EntryLocation2D>... regionProvider) {
		super(Arrays.asList(regionProvider));
	}


	public static SaveSection2D createAt(Path directory) {
		return new SaveSection2D(
				new SharedCachedRegionProvider<>(
						SimpleRegionProvider.createDefault(new EntryLocation2D.Provider(), directory, 512)
				),
				new SharedCachedRegionProvider<>(
						new SimpleRegionProvider<>(new EntryLocation2D.Provider(), directory,
								(keyProvider, regionKey) -> new ExtRegion<>(directory, Collections.emptyList(), keyProvider, regionKey),
								(dir, key) -> Files.exists(dir.resolve(key.getRegionKey().getName() + ".ext"))
						)
				));
	}
}
