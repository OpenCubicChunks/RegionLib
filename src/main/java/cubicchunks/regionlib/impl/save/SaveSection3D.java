package cubicchunks.regionlib.impl.save;

import java.nio.file.Path;

import cubicchunks.regionlib.SaveSection;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.region.provider.CachedRegionProvider;
import cubicchunks.regionlib.region.provider.IRegionProvider;

public class SaveSection3D extends SaveSection<SaveSection3D, EntryLocation3D> {
	/**
	 * Creates a SaveSection with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection3D(IRegionProvider<EntryLocation3D> regionProvider) {
		super(regionProvider);
	}

	public static SaveSection3D createAt(Path directory) {
		return new SaveSection3D(CachedRegionProvider.makeProvider(directory));
	}
}
