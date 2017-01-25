package cubicchunks.regionlib.impl.save;

import java.nio.file.Path;

import cubicchunks.regionlib.SaveSection;
import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.region.provider.CachedRegionProvider;
import cubicchunks.regionlib.region.provider.IRegionProvider;

public class SaveSection2D extends SaveSection<SaveSection2D, EntryLocation2D> {
	/**
	 * Creates a SaveSection2D with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection2D(IRegionProvider<EntryLocation2D> regionProvider) {
		super(regionProvider);
	}


	public static SaveSection2D createAt(Path directory) {
		return new SaveSection2D(CachedRegionProvider.makeProvider(directory));
	}
}
