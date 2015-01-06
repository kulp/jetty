package ch.kulp.jetty;

public class ByteArrayClassLoader extends ClassLoader {
	private byte bytes[];

	public ByteArrayClassLoader(byte bytes[]) {
		super();
		this.bytes = bytes;
	}

	@Override
	protected Class<?> findClass(final String name) throws ClassNotFoundException {
		if (bytes != null) {
			return defineClass(name, bytes, 0, bytes.length);
		}
		return super.findClass(name);
	}

}
