package ch.kulp.jetty;

public class BasicBlock {
    public BasicBlock(Integer addr, MachineState ms_) {
        baseAddr = addr;
        length = -1;
        ms = ms_;
        runner = JettyInterp.getInstance();
    }

    public int baseAddr;
    public int length;
    public MachineState ms;
    JettyRunner runner;
    public int runCount;
}
