package ch.kulp.jetty;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.TreeMap;
import java.util.TreeSet;

import ch.kulp.jetty.TenyrMappedObjectReader.Record;

interface JettyRunner {
    int run(BasicBlock bb);
}

class JettyInterp implements JettyRunner {
    private static JettyInterp instance;

    public static JettyRunner getInstance() {
        if (instance == null)
            instance = new JettyInterp();
        return instance;
    }

    /**
     * @param ms
     * @return true if P was written
     */
    boolean step(MachineState ms) {
        int P = ms.regs[15]++;
        int word = ms.fetch(P);

        Instruction insn = Instruction.parse(word);
        int rhs = 0;

        int e0 = 0, e1 = 0, e2 = 0;
        switch (insn.type) {
            case 0:
            case 3:
                e0 = insn.I;
                e1 = ms.regs[insn.Y];
                e2 = ms.regs[insn.X];
                break;
            case 1:
                e0 = ms.regs[insn.Y];
                e1 = insn.I;
                e2 = ms.regs[insn.X];
                break;
            case 2:
                e0 = ms.regs[insn.Y];
                e1 = ms.regs[insn.X];
                e2 = insn.I;
                break;
        }

        // @formatter:off
        switch (insn.op) {
            case ADD:               rhs = (e2  +  e1) + e0; break;
            case SUBTRACT:          rhs = (e2  -  e1) + e0; break;
            case MULTIPLY:          rhs = (e2  *  e1) + e0; break;

            case SHIFT_LEFT:        rhs = (e2  << e1) + e0; break;
            case SHIFT_RIGHT_ARITH: rhs = (e2 >>> e1) + e0; break;
            case SHIFT_RIGHT_LOGIC: rhs = (e2  >> e1) + e0; break;

            case BITWISE_AND:       rhs = (e2  &  e1) + e0; break;
            case BITWISE_ANDN:      rhs = (e2  &~ e1) + e0; break;
            case BITWISE_OR:        rhs = (e2  |  e1) + e0; break;
            case BITWISE_ORN:       rhs = (e2  |~ e1) + e0; break;
            case BITWISE_XOR:       rhs = (e2  ^  e1) + e0; break;

            case COMPARE_LT:        rhs = ((e2 <  e1) ? -1 : 0) + e0; break;
            case COMPARE_EQ:        rhs = ((e2 == e1) ? -1 : 0) + e0; break;
            case COMPARE_GE:        rhs = ((e2 >= e1) ? -1 : 0) + e0; break;

            case PACK:              rhs = (e2 << 12) | (e1 & ~(-1 << 12)) + e0; break;
            case TEST_BIT:          rhs = (((e2 & (1 << e1)) != 0) ? -1 : 0) + e0; break;
        }
        // @formatter:on

        switch (insn.dd) {
            case 0b00:
                ms.regs[insn.Z] = rhs;
                break;
            case 0b01:
                ms.regs[insn.Z] = ms.fetch(rhs);
                break;
            case 0b10:
                ms.store(ms.regs[insn.Z], rhs);
                break;
            case 0b11:
                ms.store(rhs, ms.regs[insn.Z]);
                break;
        }

        return insn.doesUpdateP();
    }

    /**
     * @param ms
     * @return last instruction executed
     */
    public int run(BasicBlock bb) {
        int lastP;
        do {
            lastP = bb.ms.regs[15];
            // System.out.format("IP = 0x%08x\n", ms.regs[15]);
        } while (!step(bb.ms));
        return lastP;
    }
}

public class JettySim {

    private static final int SIMULATOR_LOAD_ADDRESS = 0x1000;
    private static final int RUN_COUNT_THRESHOLD = 10;

    TreeMap<Integer, BasicBlock> blockMap = new TreeMap<Integer, BasicBlock>();
    TreeSet<Integer> basicBlockEnds = new TreeSet<Integer>();
    TreeSet<Integer> basicBlockStarts = new TreeSet<Integer>();
    private TenyrMappedObjectReader reader;

    public JettySim(String string) throws IOException {
        reader = new TenyrMappedObjectReader(Paths.get(string));
    }

    public static void main(String[] args) {
        try {
            new JettySim(args[0]).run();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void run() {
        MachineState ms = new MachineState();
        ms.regs[15] = SIMULATOR_LOAD_ADDRESS;
        try {
            reader.parse();
        } catch (UnsupportedEncodingException | ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.exit(1);
        }
        for (Record record : reader.getRecords()) {
            int[] words = record.asWords();
            for (int i = record.addr; i < record.addr + record.size; i++) {
                int word = words[i];
                ms.store(i + SIMULATOR_LOAD_ADDRESS, word);
            }
        }

        long start = System.currentTimeMillis();
        do {
            // System.out.format("IP = 0x%08x\n", ms.regs[15]);
            BasicBlock bb = getBasicBlock(ms.regs[15], ms);
            int bbEnd = bb.runner.run(bb);
            basicBlockEnds.add(bbEnd);
            // System.out.format("a BasicBlock ends at 0x%08x\n", bbEnd);
            int target = ms.regs[15];
            basicBlockStarts.add(target);
            // System.out.format("a BasicBlock starts at 0x%08x\n", target);
            BasicBlock nextBB = getBasicBlock(target, ms);
            if (nextBB.runCount++ == 1) {
                int nextEnd = basicBlockEnds.ceiling(target);
                nextBB.length = nextEnd - target + 1;
                System.out.format("BasicBlock found from [0x%08x,0x%08x]\n", target, nextEnd);
            } else if (nextBB.runCount > 0) {
                if (nextBB.runCount > RUN_COUNT_THRESHOLD) {
                    nextBB.runner = JitRunner.getInstance();
                }
            }
        } while (ms.regs[15] != -1);

        long end = System.currentTimeMillis();
        long length = end - start;
        System.out.format("done in %d.%03d seconds\n", length / 1000, length % 1000);
    }

    private BasicBlock getBasicBlock(int addr, MachineState ms) {
        return blockMap.computeIfAbsent(addr, start -> new BasicBlock(start, ms));
    }
}
