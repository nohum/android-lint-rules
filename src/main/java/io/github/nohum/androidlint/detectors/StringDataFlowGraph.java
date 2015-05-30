package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.checks.ControlFlowGraph;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.List;

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

    public List<String> getPossibleProviders() {
        return possibleProviders;
    }

    @Override
    protected void add(AbstractInsnNode from, AbstractInsnNode to) {
        super.add(from, to);
    }
}
