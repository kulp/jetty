package ch.kulp.jetty;

public class Instruction {
    int type, dd, Z, X, Y, I;
    Operation op;

    boolean doesUpdateP() {
        return dd != 0b00 && dd != 0b11 && Z == 15;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (dd == 0b10)
            sb.append('[');

        sb.append((char) ('A' + Z));

        if (dd == 0b10)
            sb.append(']');

        sb.append(' ');

        if (dd == 0b01)
            sb.append("->");
        else
            sb.append("<-");

        sb.append(' ');

        if ((dd & 1) == 0b01)
            sb.append('[');

        boolean elideX = X == 0 && op.isIdentityWithA();
        boolean elideY = Y == 0 && op.isIdentityWithA();
        switch (type) {
            case 0:
                if (!elideX) {
                    sb.append((char) ('A' + X));
                }
                if (!elideY) {
                    if (!elideX) {
                        sb.append(' ');
                        sb.append(op.toString());
                        sb.append(' ');
                    }
                    sb.append((char) ('A' + Y));
                }
                if (I != 0 || (elideX && elideY)) {
                    if (!elideX || !elideY) {
                        sb.append(" + ");
                    }
                    sb.append(String.format("0x%08x", I));
                }
                break;
            case 1:
                if (!elideX) {
                    sb.append((char) ('A' + X));
                    sb.append(' ');
                    sb.append(op.toString());
                    sb.append(' ');
                }
                sb.append(String.format("0x%08x", I));
                if (Y != 0) {
                    sb.append(" + ");
                    sb.append((char) ('A' + Y));
                }
                break;
            case 2:
                sb.append(String.format("0x%08x", I));
                if (!elideX) {
                    sb.append(' ');
                    sb.append(op.toString());
                    sb.append(' ');
                    sb.append((char) ('A' + X));
                }
                if (Y != 0) {
                    sb.append(" + ");
                    sb.append((char) ('A' + Y));
                }
                break;
            case 3:
                sb.append(String.format("0x%08x", I));
                break;
        }

        if ((dd & 1) == 0b01)
            sb.append(']');

        return sb.toString();
    }

    static Instruction parse(int word) {
        Instruction insn = new Instruction();

        insn.type = (word >> 30) & 0x3;
        insn.dd = (word >> 28) & 0x3;
        insn.Z = (word >> 24) & 0xf;
        switch (insn.type) {
            case 0:
            case 1:
            case 2:
                insn.X = (word >> 20) & 0xf;
                insn.Y = (word >> 16) & 0xf;
                insn.op = Operation.get((word >> 12) & 0xf);
                insn.I = (word & 0xfff) | -(word & 0x800); // sign extend
                break;
            case 3:
                insn.X = (word >> 20) & 0xf;
                insn.Y = 0;
                insn.op = Operation.BITWISE_OR;
                insn.I = (word & 0xfffff) | -(word & 0x80000); // sign extend
        }

        return insn;
    }
}
