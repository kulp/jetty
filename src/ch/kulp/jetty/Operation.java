package ch.kulp.jetty;

public enum Operation {
    BITWISE_OR(0x0, "|", true, true),
    BITWISE_AND(0x1, "&", false, false),
    ADD(0x2, "+", true, true),
    MULTIPLY(0x3, "*", false, false),
    PACK(0x4, "^^", true, false),
    SHIFT_LEFT(0x5, "<<", false, true),
    COMPARE_LT(0x6, "<", false, false),
    COMPARE_EQ(0x7, "==", false, false),
    COMPARE_GE(0x8, ">=", false, false),
    BITWISE_ANDN(0x9, "&~", false, true),
    BITWISE_XOR(0xa, "^", true, true),
    SUBTRACT(0xb, "-", false, true),
    BITWISE_XORN(0xc, "^~", false, false),
    SHIFT_RIGHT_LOGIC(0xd, ">>", false, true),
    COMPARE_NE(0xe, "<>", false, false),
    SHIFT_RIGHT_ARITH(0xf, ">>>", false, true);

    int val;
    String opName;
    boolean inertX, inertY;

    Operation(int val, String opName, boolean inertX, boolean inertY) {
        this.val = val;
        this.opName = opName;
        this.inertX = inertX;
        this.inertY = inertY;
    }

    static Operation get(int val) {
        return values()[val];
    }

    @Override
    public String toString() {
        return opName;
    }

    public boolean isIdentityWithA() {
        return this == Operation.ADD || this == Operation.BITWISE_OR;
    }
}
