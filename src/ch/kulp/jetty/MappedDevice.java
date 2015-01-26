package ch.kulp.jetty;

public interface MappedDevice {
    public int fetch(int addr);

    public void store(int addr, int rhs);

    public int getMappedBase();

    public int getMappedLength();
}
