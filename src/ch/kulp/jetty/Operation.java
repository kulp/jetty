package ch.kulp.jetty;

public enum Operation {
    // @formatter:off
    BITWISE_OR        ( 0x0 , "|"   , true  , true  ),
    BITWISE_AND       ( 0x1 , "&"   , false , false ),
    BITWISE_XOR       ( 0x2 , "^"   , true  , true  ),
    SHIFT_RIGHT_ARITH ( 0x3 , ">>"  , false , true  ),

    ADD               ( 0x4 , "+"   , true  , true  ),
    MULTIPLY          ( 0x5 , "*"   , false , false ),
    COMPARE_EQ        ( 0x6 , "=="  , false , false ),
    COMPARE_LT        ( 0x7 , "<"   , false , false ),

    BITWISE_ORN       ( 0x8 , "|~"  , false , true  ),
    BITWISE_ANDN      ( 0x9 , "&~"  , false , true  ),
    PACK              ( 0xa , "^^"  , true  , false ),
    SHIFT_RIGHT_LOGIC ( 0xb , ">>>" , false , true  ),

    SUBTRACT          ( 0xc , "-"   , false , true  ),
    SHIFT_LEFT        ( 0xd , "<<"  , false , true  ),
    TEST_BIT          ( 0xe , "@"   , false , false ),
    COMPARE_GE        ( 0xf , ">="  , false , false );
    // @formatter:on

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
