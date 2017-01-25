package cubicchunks.regionlib.impl.save;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import cubicchunks.regionlib.SaveSection;
import cubicchunks.regionlib.impl.MinecraftChunkLocation;
import cubicchunks.regionlib.region.Region;
import cubicchunks.regionlib.region.header.TimestampHeaderEntryProvider;
import cubicchunks.regionlib.region.provider.CachedRegionProvider;
import cubicchunks.regionlib.region.provider.IRegionProvider;
import cubicchunks.regionlib.region.provider.SimpleRegionProvider;

public class MinecraftSaveSection extends SaveSection<MinecraftSaveSection, MinecraftChunkLocation> {
	/**
	 * Creates a SaveSection with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public MinecraftSaveSection(IRegionProvider<MinecraftChunkLocation> regionProvider) {
		super(regionProvider);
	}

	public static MinecraftSaveSection createAt(Path directory) {
		return new MinecraftSaveSection(new CachedRegionProvider<MinecraftChunkLocation>(
			new SimpleRegionProvider<>(directory, (path, key) ->
				Region.<MinecraftChunkLocation>builder()
					.setPath(path)
					.setSectorSize(4096)
					.setEntriesPerRegion(key.getKeyCount())
					.addHeaderEntry(new TimestampHeaderEntryProvider<>(TimeUnit.MILLISECONDS))
					.build()
			), 128
		));
	}
}
