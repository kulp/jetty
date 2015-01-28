package ch.kulp.jetty;

public class MappedMemory implements MappedDevice {
    int base;
    int[] store;

    public MappedMemory(int base, int pagesize) {
        this.base = base;
        this.store = new int[pagesize];
    }

    @Override
    public int fetch(int addr) {
        return store[addr - base];
    }

    @Override
    public void store(int addr, int rhs) {
        store[addr - base] = rhs;
    }

    @Override
    public int getMappedBase() {
        return base;
    }

    @Override
    public int getMappedLength() {
        return store.length;
    }
}
