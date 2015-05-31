package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.checks.ControlFlowGraph;
import com.sun.org.apache.bcel.internal.generic.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Data-flow analysis using the builtin ControlFlowGraph, operating on byte-code.
 */
public class StringDataFlowGraph extends ControlFlowGraph {

    private static final boolean DEBUG = true;

    private boolean thisDiscarded = false;

    private List<String> possibleProviders;

    private MethodInsnNode subjectMethodCall;

    private int desiredArgumentCount;

    /**
     * @param methodCall the desired method
     * @param desiredArgumentCount specifies the argument index to record (when calling the method, zero-based).
     */
    public StringDataFlowGraph(MethodInsnNode methodCall, int desiredArgumentCount) {
        possibleProviders = new ArrayList<>();
        this.subjectMethodCall = methodCall;
        this.desiredArgumentCount = desiredArgumentCount;
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

        Queue<AbstractInsnNode> varsOnStack = new ArrayDeque<AbstractInsnNode>();
        Node node = getNode(method.instructions.getFirst());
        inspectNode(node, varsOnStack, 0);

        return possibleProviders;
    }

    private void inspectNode(Node node, Queue<AbstractInsnNode> varsOnStack, int debugIndentation) {
        if (isNotMarkerInstruction(node)) {
            logIndent(debugIndentation, "inspecting node: %s", nodeToString(node.instruction));
        }

        if (isGeneralVarAddInstruction(node)) {
            logIndent(debugIndentation, "node adds var on stack (curr: %d) -> %s", varsOnStack.size(),
                    nodeToString(node.instruction));
            varsOnStack.add(node.instruction);
        }

        // special case! may store opcode for loading as well as storing
        handleSpecialVarInstruction(node, varsOnStack, debugIndentation);
        handleMethodCallInstruction(node, varsOnStack, debugIndentation);
        handleSuccessors(node, varsOnStack, debugIndentation);
    }

    private boolean isNotMarkerInstruction(Node node) {
        return node.instruction.getClass() != LabelNode.class && node.instruction.getClass() != LineNumberNode.class;
    }

    private void handleMethodCallInstruction(Node node, Queue<AbstractInsnNode> varsOnStack, int debugIndentation) {
        if (node.instruction.getClass() == MethodInsnNode.class) {
            MethodInsnNode currentMethodCall = (MethodInsnNode) node.instruction;
            Type[] args = Type.getArgumentTypes(currentMethodCall.desc);

            logIndent(debugIndentation, "call is on stack: %s with %d args", currentMethodCall.name, args.length);

            boolean recordArgument = false;
            if (currentMethodCall.equals(subjectMethodCall)) {
                logIndent(debugIndentation, "-- this call is actually our desired call");
                recordArgument = true;
            }

            for (int i = 0; i < args.length; ++ i) {
                AbstractInsnNode argNode = varsOnStack.poll();
                if (recordArgument && desiredArgumentCount == i) {
                    if (argNode == null) {
                        logIndent(debugIndentation, "warning: not recording argument (is null)");
                    } else if (argNode.getClass() == LdcInsnNode.class) {
                        possibleProviders.add(String.valueOf(((LdcInsnNode) argNode).cst));
                    } else {
                        logIndent(debugIndentation, "warning: not recording argument (is not LDC)");
                    }
                }

                logIndent(debugIndentation, "call argument %d: %s", i, nodeToString(argNode));
            }
            logIndent(debugIndentation, "-- after this call, %d arguments are on the stack", varsOnStack.size());
        }
    }

    private void handleSpecialVarInstruction(Node node, Queue<AbstractInsnNode> varsOnStack, int debugIndentation) {
        if (node.instruction.getClass() == VarInsnNode.class) {
            VarInsnNode varInsnNode = (VarInsnNode) node.instruction;

            if (varInsnNode.getOpcode() == Opcodes.ILOAD || varInsnNode.getOpcode() == Opcodes.LLOAD
                    || varInsnNode.getOpcode() == Opcodes.FLOAD || varInsnNode.getOpcode() == Opcodes.DLOAD
                    || varInsnNode.getOpcode() == Opcodes.ALOAD) {
                logIndent(debugIndentation, "node is VarInsnNode which loads var (curr: %d)", varsOnStack.size());

                if (thisDiscarded) {
                    varsOnStack.add(node.instruction);
                } else if (varInsnNode.getOpcode() == Opcodes.ALOAD) {
                    logIndent(debugIndentation, "node is VarInsnNode - first call, variable should be \"this\" - discarding!");
                    thisDiscarded = true;
                }
            } else if (varInsnNode.getOpcode() == Opcodes.ISTORE || varInsnNode.getOpcode() == Opcodes.LSTORE
                    || varInsnNode.getOpcode() == Opcodes.FSTORE || varInsnNode.getOpcode() == Opcodes.DSTORE
                    || varInsnNode.getOpcode() == Opcodes.ASTORE) {
                logIndent(debugIndentation, "node is VarInsnNode which stores var (curr: %d)", varsOnStack.size());
                varsOnStack.poll();
            }
        }
    }

    private void handleSuccessors(Node node, Queue<AbstractInsnNode> varsOnStack, int debugIndentation) {
        for (Node successor : node.successors) {
            int level = debugIndentation;
            // these nodes clutter the output, suppress them
            if (isNotMarkerInstruction(node)) {
                ++ level;
            }

            inspectNode(successor, copyQueue(varsOnStack), level);
        }
    }

    private boolean isGeneralVarAddInstruction(Node node) {
        return node.instruction.getClass() == InsnNode.class || node.instruction.getClass() == FieldInsnNode.class
            || node.instruction.getClass() == IntInsnNode.class || node.instruction.getClass() == LdcInsnNode.class;
    }

    private Queue<AbstractInsnNode> copyQueue(Queue<AbstractInsnNode> varsOnStack) {
        return new ArrayDeque<AbstractInsnNode>(varsOnStack);
    }

    private String nodeToString(AbstractInsnNode instruction) {
        if (instruction == null) {
            return null;
        }

        if (instruction.getClass() == LdcInsnNode.class) {
            return "LDC " + ((LdcInsnNode) instruction).cst;
        }

        if (instruction.getClass() == MethodInsnNode.class) {
            return "Call Method " + ((MethodInsnNode) instruction).name + " " + ((MethodInsnNode) instruction).desc;
        }

        if (instruction.getClass() == VarInsnNode.class) {
            VarInsnNode varInsnNode = (VarInsnNode) instruction;
            String type = " <unknown op>";

            if (varInsnNode.getOpcode() == Opcodes.ILOAD || varInsnNode.getOpcode() == Opcodes.LLOAD
                    || varInsnNode.getOpcode() == Opcodes.FLOAD || varInsnNode.getOpcode() == Opcodes.DLOAD
                    || varInsnNode.getOpcode() == Opcodes.ALOAD) {
                type = " <load " + varInsnNode.getOpcode() + ">";
            } else if (varInsnNode.getOpcode() == Opcodes.ISTORE || varInsnNode.getOpcode() == Opcodes.LSTORE
                    || varInsnNode.getOpcode() == Opcodes.FSTORE || varInsnNode.getOpcode() == Opcodes.DSTORE
                    || varInsnNode.getOpcode() == Opcodes.ASTORE) {
                type = " <store " + varInsnNode.getOpcode() + ">";
            }

            return instruction.getClass().toString() + type;
        }

        return instruction.getClass().toString();
    }
}
