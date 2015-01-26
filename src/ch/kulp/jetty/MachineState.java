package ch.kulp.jetty;

import static ch.kulp.jetty.Operation.COMPARE_EQ;
import static ch.kulp.jetty.Operation.COMPARE_GE;
import static ch.kulp.jetty.Operation.COMPARE_LT;

import java.util.TreeMap;

public final class MachineState {
    static final int PAGESIZE = 0x1000;
    public int regs[] = new int[16];
    static final public int cmpTable[] = new int[100];
    static {
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
    }

    // TODO Device instead of just blocks of memory
    TreeMap<Integer, int[]> mem = new TreeMap<Integer, int[]>();

    public int fetch(int addr) {
        return vivifyBlock(addr)[addr & (PAGESIZE - 1)];
    }

    public void store(int addr, int rhs) {
        vivifyBlock(addr)[addr & (PAGESIZE - 1)] = rhs;
    }

    private int[] vivifyBlock(int addr) {
        return mem.computeIfAbsent(addr & ~(PAGESIZE - 1), k -> new int[PAGESIZE]);
    }
}
