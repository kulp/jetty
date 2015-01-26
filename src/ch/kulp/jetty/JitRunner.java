package ch.kulp.jetty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import soot.*;
import soot.jimple.*;
import soot.util.Chain;
import soot.util.JasminOutputStream;

public class JitRunner implements JettyRunner {
    private static final IntConstant INTEGER_ZERO = IntConstant.v(0);
    private static JitRunner instance;
    HashMap<BasicBlock, Method> methodMap = new HashMap<BasicBlock, Method>();
    private static final ArrayType INT_ARR_TYPE = ArrayType.v(IntType.v(), 1);
    private Local regs;
    private Local localRegsCache[];

    static {
        // Resolve dependencies
        Scene.v().loadClassAndSupport("java.lang.Object");
        Scene.v().loadClassAndSupport("java.lang.System");
        // TODO remove this hack to set up our class path for MachineState
        String path = MachineState.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        Scene.v().setSootClassPath(path + ":" + Scene.v().getSootClassPath());
        // TODO we aren't supposed to use loadClassAndSupport() ?
        // https://mailman.cs.mcgill.ca/pipermail/soot-list/2014-October/007442.html
        Scene.v().loadClassAndSupport("ch.kulp.jetty.MachineState");
    }

    @Override
    public int run(BasicBlock bb) {
        try {
            Method method = methodMap.computeIfAbsent(bb, block -> generateMethod(block));
            method.invoke(null, new Object[] { bb });
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
            System.exit(1);
            return -1;
        }
        return -1;
    }

    private Method generateMethod(BasicBlock bb) {
        try {
            String className = String.format("gen_0x%08x", bb.baseAddr);
            String entryPoint = "run";
            ByteArrayClassLoader cl = new ByteArrayClassLoader(generateClassBytes(bb, className, entryPoint));
            Class<?> theClass = Class.forName(className, true, cl);
            return theClass.getMethod(entryPoint, BasicBlock.class);
        } catch (Exception e) {
            // Here we rethrow as unchecked exception to permit use in a
            // lambda above
            throw new RuntimeException(e);
        }
    }

    public static JettyRunner getInstance() {
        if (instance == null)
            instance = new JitRunner();
        return instance;
    }

    private byte[] generateClassBytes(BasicBlock bb, String className, String entryPointName) throws IOException {
        SootClass sClass = new SootClass(className, Modifier.PUBLIC);
        sClass.setSuperclass(Scene.v().getSootClass("java.lang.Object"));

        List<Type> paramTypeArr = Arrays.asList(RefType.v("ch.kulp.jetty.BasicBlock"));
        SootMethod method = new SootMethod(entryPointName, paramTypeArr, IntType.v(), Modifier.PUBLIC | Modifier.STATIC);
        sClass.addMethod(method);

        buildMethod(bb, method);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream streamOut = new JasminOutputStream(bos);
        PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(streamOut));
        JasminClass jasminClass = new JasminClass(sClass);
        jasminClass.print(writerOut);
        writerOut.flush();
        streamOut.close();

        return bos.toByteArray();
    }

    // newTemp() and asImm() modified from ASTNode
    private Local newTemp(Body body, Value value) {
        if (value == NullConstant.v())
            throw new UnsupportedOperationException("Cannot create a temporary local for null literal");
        Local local = Jimple.v().newLocal(null, value.getType());
        body.getLocals().add(local);
        if (value instanceof ParameterRef) {
            body.getUnits().add(Jimple.v().newIdentityStmt(local, (ParameterRef) value));
        } else {
            body.getUnits().add(Jimple.v().newAssignStmt(local, value));
        }
        return local;
    }

    private Immediate asImm(Body body, Value value) {
        if (value instanceof Immediate)
            return (Immediate) value;
        return newTemp(body, value);
    }

    private Value buildOperation(JimpleBody body, Operation op, Value x, Value y) {
        Value result = null;
        Jimple J = Jimple.v();
        Scene S = Scene.v();

        // @formatter:off
        switch (op) {
            case ADD:               result = J.newAddExpr(x, y); break;
            case SUBTRACT:          result = J.newSubExpr(x, y); break;
            case MULTIPLY:          result = J.newMulExpr(x, y); break;

            case SHIFT_LEFT:        result = J.newShlExpr(x, y); break;
            case SHIFT_RIGHT_ARITH: result = J.newShrExpr(x, y); break;
            case SHIFT_RIGHT_LOGIC: result = J.newUshrExpr(x, y); break;

            case BITWISE_AND:       result = J.newAndExpr(x, y); break;
            case BITWISE_ANDN:      result = J.newAndExpr(x, asImm(body, J.newXorExpr(y, IntConstant.v(-1)))); break;
            case BITWISE_OR:        result = J.newOrExpr(x, y); break;
            case BITWISE_ORN:       result = J.newXorExpr(x, asImm(body, J.newOrExpr(y, IntConstant.v(-1)))); break;
            case BITWISE_XOR:       result = J.newXorExpr(x, y); break;

            case COMPARE_EQ:
            case COMPARE_GE:
            case COMPARE_LT: {
                CmpExpr compared = J.newCmpExpr(asImm(body, J.newCastExpr(x, LongType.v())),
                        asImm(body, J.newCastExpr(y, LongType.v())));
                Immediate cmpTable = asImm(body, J.newStaticFieldRef(S.makeFieldRef(
                        S.getSootClass("ch.kulp.jetty.MachineState"), "cmpTable", INT_ARR_TYPE, true)));
                result = J.newArrayRef(cmpTable, asImm(body, J.newAddExpr(asImm(body, compared), IntConstant.v(op.val << 2))));
                break;
            }

            case TEST_BIT:
                result = J.newNegExpr(asImm(body, J.newAndExpr(asImm(body, J.newUshrExpr(x, y)), IntConstant.v(1))));
            case PACK: {
                result = J.newOrExpr(
                        asImm(body, J.newShlExpr(x, IntConstant.v(12))),
                        asImm(body, J.newAndExpr(y, IntConstant.v(0xfff)))
                    );
                break;
            }
        }
        // @formatter:on

        return result;
    }

    private Value getReg(BasicBlock bb, JimpleBody body, int i, int offset) {
        switch (i) {
            case 0:
                return INTEGER_ZERO;
            case 15:
                cacheReg(body, i);
                return asImm(body, Jimple.v().newAddExpr(localRegsCache[i], IntConstant.v(offset + 1)));
            default:
                cacheReg(body, i);
                return localRegsCache[i];
        }
    }

    private void buildInstruction(BasicBlock bb, JimpleBody body, int word, int offset, Local msRef) {
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        Instruction insn = Instruction.parse(word);

        Value A = null, B = null, C = null;
        switch (insn.type) {
            case 0:
                A = getReg(bb, body, insn.X, offset);
                B = getReg(bb, body, insn.Y, offset);
                C = IntConstant.v(insn.I);
                break;
            case 1:
                A = getReg(bb, body, insn.X, offset);
                B = IntConstant.v(insn.I);
                C = getReg(bb, body, insn.Y, offset);
                break;
            case 2:
                A = IntConstant.v(insn.I);
                B = getReg(bb, body, insn.X, offset);
                C = getReg(bb, body, insn.Y, offset);
                break;
            case 3:
                A = INTEGER_ZERO;
                B = INTEGER_ZERO;
                C = IntConstant.v(insn.I);
                break;
        }

        Value rhs;
        boolean skipX = insn.op.inertX && A.equivTo(INTEGER_ZERO);
        boolean skipY = insn.op.inertY && B.equivTo(INTEGER_ZERO);
        if (skipX && skipY) {
            rhs = C;
        } else {
            Value leftHalf;
            if (skipX) {
                leftHalf = B;
            } else if (skipY) {
                leftHalf = A;
            } else {
                leftHalf = buildOperation(body, insn.op, A, B);
            }

            Local temp = Jimple.v().newLocal(null, IntType.v());
            locals.add(temp);
            units.add(Jimple.v().newAssignStmt(temp, leftHalf));

            if (C.equivTo(INTEGER_ZERO)) {
                rhs = temp;
            } else {
                rhs = buildOperation(body, Operation.ADD, temp, C);
            }
        }
        SootMethodRef toFetch = Scene.v().getMethod("<ch.kulp.jetty.MachineState: int fetch(int)>").makeRef();
        SootMethodRef toStore = Scene.v().getMethod("<ch.kulp.jetty.MachineState: void store(int,int)>").makeRef();

        switch (insn.dd) {
            case 0b00:
                units.add(setReg(body, insn.Z, rhs));
                break;
            case 0b01: {
                VirtualInvokeExpr invocation = Jimple.v().newVirtualInvokeExpr(msRef, toFetch, asImm(body, rhs));
                units.add(setReg(body, insn.Z, invocation));
                break;
            }
            case 0b10: {
                VirtualInvokeExpr invocation = Jimple.v().newVirtualInvokeExpr(msRef, toStore,
                        getReg(bb, body, insn.Z, offset), asImm(body, rhs));
                units.add(Jimple.v().newInvokeStmt(invocation));
                break;
            }
            case 0b11: {
                VirtualInvokeExpr invocation = Jimple.v().newVirtualInvokeExpr(msRef, toStore, asImm(body, rhs),
                        getReg(bb, body, insn.Z, offset));
                units.add(Jimple.v().newInvokeStmt(invocation));
                break;
            }
        }
    }

    private Stmt setReg(JimpleBody body, int i, Value val) {
        if (i != 0) {
            cacheReg(body, i);
            return Jimple.v().newAssignStmt(localRegsCache[i], val);
        } else {
            return Jimple.v().newNopStmt();
        }
    }

    private void cacheReg(JimpleBody body, int i) {
        Chain<Local> locals = body.getLocals();

        if (i != 0 && localRegsCache[i] == null) {
            PatchingChain<Unit> units = body.getUnits();

            localRegsCache[i] = Jimple.v().newLocal(Character.toString((char) ('A' + i)), IntType.v());

            ArrayRef aref = Jimple.v().newArrayRef(regs, IntConstant.v(i));
            units.add(Jimple.v().newAssignStmt(localRegsCache[i], aref));
        }

        if (!locals.contains(localRegsCache[i]))
            locals.add(localRegsCache[i]);
    }

    private Local getRegsReference(JimpleBody body, Local ms) {
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        SootFieldRef regsFieldRef = Scene.v().makeFieldRef(Scene.v().getSootClass("ch.kulp.jetty.MachineState"),
                "regs", INT_ARR_TYPE, false);
        FieldRef regsField = Jimple.v().newInstanceFieldRef(ms, regsFieldRef);
        Local regs = Jimple.v().newLocal(null, INT_ARR_TYPE);
        locals.add(regs);
        units.add(Jimple.v().newAssignStmt(regs, regsField));

        return regs;
    }

    private Local getMachineStateReferences(JimpleBody body) {
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        String bbClassName = "ch.kulp.jetty.BasicBlock";
        Type typeStr = RefType.v(bbClassName);
        Local arg = Jimple.v().newLocal("bb_", typeStr);
        locals.add(arg);
        units.add(Jimple.v().newIdentityStmt(arg, Jimple.v().newParameterRef(typeStr, 0)));
        SootClass bbSootClass = Scene.v().getSootClass(bbClassName);
        RefType msType = RefType.v("ch.kulp.jetty.MachineState");
        SootFieldRef msFieldRef = Scene.v().makeFieldRef(bbSootClass, "ms", msType, false);
        FieldRef msField = Jimple.v().newInstanceFieldRef(arg, msFieldRef);
        Local ms = Jimple.v().newLocal(null, msType);
        locals.add(ms);
        units.add(Jimple.v().newAssignStmt(ms, msField));

        return ms;
    }

    private void buildMethod(BasicBlock bb, SootMethod method) {
        JimpleBody body = Jimple.v().newBody(method);
        localRegsCache = new Local[16];
        method.setActiveBody(body);
        PatchingChain<Unit> units = body.getUnits();
        Local msRef = getMachineStateReferences(body);

        regs = getRegsReference(body, msRef);
        for (int addr = bb.baseAddr; addr != bb.baseAddr + bb.length; addr++) {
            buildInstruction(bb, body, bb.ms.fetch(addr), addr - bb.baseAddr, msRef);
        }

        flushRegsCache(body, regs, localRegsCache);

        units.add(Jimple.v().newReturnStmt(localRegsCache[15]));
    }

    private void flushRegsCache(JimpleBody body, Local regs, Local[] regsCache) {
        PatchingChain<Unit> units = body.getUnits();

        for (int i = 0; i < regsCache.length; i++) {
            if (regsCache[i] != null) {
                ArrayRef aref = Jimple.v().newArrayRef(regs, IntConstant.v(i));
                units.add(Jimple.v().newAssignStmt(aref, regsCache[i]));
            }
        }
    }
}
