package cubicchunks.regionlib.util;

public class WrappedException extends RuntimeException {

	private final Throwable t;

	public WrappedException(Throwable t) {
		this.t = t;
	}

	public <T> T get() {
		return (T) t;
	}
}
