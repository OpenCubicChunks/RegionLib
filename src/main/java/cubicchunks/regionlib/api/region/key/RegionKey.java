package cubicchunks.regionlib.api.region.key;

public class RegionKey {

    private String name;

    public RegionKey(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RegionKey regionKey = (RegionKey) o;

        if (!name.equals(regionKey.name)) {
            return false;
        }

        return true;
    }

    @Override public int hashCode() {
        return name.hashCode();
    }
}
