package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.checks.ControlFlowGraph;
import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.sun.org.apache.bcel.internal.generic.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Data-flow analysis using the builtin ControlFlowGraph, operating on byte-code.
 */
public class StringDataFlowGraph extends ControlFlowGraph {

    private static final boolean DEBUG = true;

    private List<String> possibleProviders;

    private MethodInsnNode methodCall;

    public StringDataFlowGraph(MethodInsnNode methodCall) {
        possibleProviders = new ArrayList<>();
        this.methodCall = methodCall;
    }

    private void log(String format, Object... args) {
        if (DEBUG) {
            System.out.println(String.format(format, args));
        }
    }

    private void logIndent(int level, String format, Object... args) {
        if (DEBUG) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= level; ++ i) {
                builder.append(" ");
            }

            builder.append(format);
            System.out.println(String.format(builder.toString(), args));
        }
    }

    public List<String> getPossibleProviders(MethodNode method) {
        log("getPossibleProviders: %s -----------------------------------", method.name);


//        Type[] args = Type.getArgumentTypes(method.desc);
//        log("getPossibleProviders: initial var count on stack: %d", args.length);

        Queue<AbstractInsnNode> varsOnStack = Collections.asLifoQueue(new ArrayDeque<AbstractInsnNode>());

        Node node = getNode(method.instructions.getFirst());
        inspectNode(node, varsOnStack, 0);

        return possibleProviders;
    }

    private void inspectNode(Node node, Queue<AbstractInsnNode> varsOnStack, int debugIndentation) {
        if (node.instruction.getClass() == InsnNode.class || node.instruction.getClass() == FieldInsnNode.class
            || node.instruction.getClass() == IntInsnNode.class || node.instruction.getClass() == LdcInsnNode.class) {
            logIndent(debugIndentation, "next node adds var on stack (curr: %d)", varsOnStack.size());
            varsOnStack.add(node.instruction);
        }

        // special case! may store opcode for loading as well as storing
        if (node.instruction.getClass() == VarInsnNode.class) {
            VarInsnNode varInsnNode = (VarInsnNode) node.instruction;

            if (varInsnNode.getOpcode() == Opcodes.ILOAD || varInsnNode.getOpcode() == Opcodes.LLOAD
                    || varInsnNode.getOpcode() == Opcodes.FLOAD || varInsnNode.getOpcode() == Opcodes.DLOAD
                    || varInsnNode.getOpcode() == Opcodes.ALOAD) {
                logIndent(debugIndentation, "found VarInsnNode which loads var (curr: %d)", varsOnStack.size());
                varsOnStack.add(node.instruction);
            } else if (varInsnNode.getOpcode() == Opcodes.ISTORE || varInsnNode.getOpcode() == Opcodes.LSTORE
                    || varInsnNode.getOpcode() == Opcodes.FSTORE || varInsnNode.getOpcode() == Opcodes.DSTORE
                    || varInsnNode.getOpcode() == Opcodes.ASTORE) {
                logIndent(debugIndentation, "found VarInsnNode which stores var (curr: %d)", varsOnStack.size());
                varsOnStack.remove();
            }
        }

        if (node.instruction.getClass() == MethodInsnNode.class) {
            MethodInsnNode methodCallNode = (MethodInsnNode) node.instruction;
            Type[] args = Type.getArgumentTypes(methodCallNode.desc);

            logIndent(debugIndentation, "call is on stack: %s with %d args", methodCallNode.name, args.length);
            for (int i = 0; i < args.length; ++ i) {
                logIndent(debugIndentation, "call argument %d: %s", i, nodeToString(varsOnStack.remove()));
            }
        }

        for (Node successor : node.successors) {
            int level = debugIndentation;
            // these nodes clutter the output, suppress them
            if (node.instruction.getClass() != LabelNode.class && node.instruction.getClass() != LineNumberNode.class) {
                logIndent(debugIndentation, "inspecting node: %s", nodeToString(node.instruction));
                ++ level;
            }

            Queue<AbstractInsnNode> copy = Collections.asLifoQueue(new ArrayDeque<AbstractInsnNode>(varsOnStack));
            inspectNode(successor, copy, level);
        }
    }

    private String nodeToString(AbstractInsnNode instruction) {
        if (instruction.getClass() == LdcInsnNode.class) {
            return "LDC " + ((LdcInsnNode) instruction).cst;
        }

        if (instruction.getClass() == MethodInsnNode.class) {
            return "Method " + ((MethodInsnNode) instruction).name + " " + ((MethodInsnNode) instruction).desc;
        }

        if (instruction.getClass() == VarInsnNode.class) {
            VarInsnNode varInsnNode = (VarInsnNode) instruction;
            String type = "<unknown op>";

            if (varInsnNode.getOpcode() == Opcodes.ILOAD || varInsnNode.getOpcode() == Opcodes.LLOAD
                    || varInsnNode.getOpcode() == Opcodes.FLOAD || varInsnNode.getOpcode() == Opcodes.DLOAD
                    || varInsnNode.getOpcode() == Opcodes.ALOAD) {
                type = "<load>";
            } else if (varInsnNode.getOpcode() == Opcodes.ISTORE || varInsnNode.getOpcode() == Opcodes.LSTORE
                    || varInsnNode.getOpcode() == Opcodes.FSTORE || varInsnNode.getOpcode() == Opcodes.DSTORE
                    || varInsnNode.getOpcode() == Opcodes.ASTORE) {
                type = "<store>";
            }

            return instruction.getClass().toString() + " " + type;
        }

        return instruction.getClass().toString();
    }

    @Override
    protected void add(AbstractInsnNode from, AbstractInsnNode to) {
        super.add(from, to);
    }
}
