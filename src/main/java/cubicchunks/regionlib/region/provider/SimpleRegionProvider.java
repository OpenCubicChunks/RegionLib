package cubicchunks.regionlib.region.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.region.Region;

/**
 * A simple implementation of IRegionProvider, this is intended to be used together with CachedRegionProvider or other
 * caching implementation
 *
 * @param <R> The region key type
 * @param <L> The key type
 */
public class SimpleRegionProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>> implements IRegionProvider<R, L> {

	private Path directory;
	private RegionFactory<R, L> regionBuilder;
	private Map<R, IRegion<R, L>> toReturn;

	public SimpleRegionProvider(Path directory, RegionFactory<R, L> regionBuilder) {
		this.directory = directory;
		this.regionBuilder = regionBuilder;
		this.toReturn = new HashMap<>();
	}

	@Override public IRegion<R, L> getRegion(R regionKey) throws IOException {
		Path regionPath = directory.resolve(regionKey.getRegionName());

		IRegion<R, L> reg = regionBuilder.create(regionPath, regionKey);

		this.toReturn.put(regionKey, reg);
		return reg;
	}

	@Override public Optional<IRegion<R, L>> getRegionIfExists(R regionKey) throws IOException {
		Path regionPath = directory.resolve(regionKey.getRegionName());
		if (!Files.exists(regionPath)) {
			return Optional.empty();
		}
		IRegion<R, L> reg = regionBuilder.create(regionPath, regionKey);
		this.toReturn.put(regionKey, reg);
		return Optional.of(reg);
	}

	@Override public void returnRegion(R key) throws IOException {
		IRegion<?, ?> reg = toReturn.remove(key);
		if (reg == null) {
			throw new IllegalArgumentException("No region found");
		}
		reg.close();
	}

	@Override public void close() throws IOException {
		if (!toReturn.isEmpty()) {
			System.err.println("Warning: leaked " + toReturn.size() + " regions! Closing them now");
			for (IRegion<?, ?> r : toReturn.values()) {
				r.close();
			}
			toReturn.clear();
			toReturn = null;
		}
	}

	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> SimpleRegionProvider<R, L> createDefault(Path directory, int sectorSize) {
		return new SimpleRegionProvider<>(directory, (p, r) ->
			new Region.Builder<R, L>()
				.setPath(p)
				.setEntriesPerRegion(r.getKeyCount())
				.setSectorSize(sectorSize)
				.build()
		);
	}

	@FunctionalInterface
	public interface RegionFactory<R extends IRegionKey<R, L>, L extends IKey<R, L>> {
		IRegion<R, L> create(Path path, R key) throws IOException;
	}
}
