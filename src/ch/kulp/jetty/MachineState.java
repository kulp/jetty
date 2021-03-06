package ch.kulp.jetty;

import static ch.kulp.jetty.Operation.COMPARE_EQ;
import static ch.kulp.jetty.Operation.COMPARE_GE;
import static ch.kulp.jetty.Operation.COMPARE_LT;

import java.util.TreeMap;
import java.util.function.Function;

import ch.kulp.jetty.devices.LEDDisplayDevice;
import ch.kulp.jetty.devices.VGATextDevice;

public final class MachineState {
    static final int PAGESIZE = 0x1000;
    public int regs[] = new int[16];
    static final public int cmpTable[] = new int[100];
    static {
        // @formatter:off
        // LT : -1, 0, 1 -> -1,  0,  0
        cmpTable[(COMPARE_LT.val << 2) + -1] = -1;
        cmpTable[(COMPARE_LT.val << 2) +  0] =  0;
        cmpTable[(COMPARE_LT.val << 2) + +1] =  0;
        // EQ : -1, 0, 1 ->  0, -1,  0
        cmpTable[(COMPARE_EQ.val << 2) + -1] =  0;
        cmpTable[(COMPARE_EQ.val << 2) +  0] = -1;
        cmpTable[(COMPARE_EQ.val << 2) + +1] =  0;
        // GE : -1, 0, 1 ->  0, -1, -1
        cmpTable[(COMPARE_GE.val << 2) + -1] =  0;
        cmpTable[(COMPARE_GE.val << 2) +  0] = -1;
        cmpTable[(COMPARE_GE.val << 2) + +1] = -1;
        // @formatter:on
    }

    TreeMap<Integer, MappedDevice> memoryMap = new TreeMap<Integer, MappedDevice>();

    {
        VGATextDevice vga = new VGATextDevice();
        int base = vga.getMappedBase();
        int length = vga.getMappedLength();
        for (int page = base; page < base + length; page += PAGESIZE)
            memoryMap.put(page, vga);
    }

    {
        LEDDisplayDevice led = new LEDDisplayDevice();
        memoryMap.put(led.getMappedBase(), led);
    }

    public int fetch(int addr) {
        return vivifyBlock(addr).fetch(addr);
    }

    public void store(int addr, int rhs) {
        vivifyBlock(addr).store(addr, rhs);
    }

    private MappedDevice vivifyBlock(int addr) {
        Function<Integer, MappedDevice> lambda = k -> new MappedMemory(addr & ~(PAGESIZE - 1), PAGESIZE);
        return memoryMap.computeIfAbsent(addr & ~(PAGESIZE - 1), lambda);
    }
}
