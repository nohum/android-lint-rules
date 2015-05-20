package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.JavaContext;
import lombok.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringDataFlowDetector {

    private static final boolean DEBUG = true;

    private JavaContext context;

    private List<String> results;

    public StringDataFlowDetector(JavaContext context) {
        this.context = context;
        results = new ArrayList<>(5);
    }

    private void log(String format, Object... args) {
        if (DEBUG) {
            System.out.println(String.format(format, args));
        }
    }

    private void addResult(String data) {
        results.add(data);
    }

    public void startInspectionOnNode(Expression start) {
        results.clear();

        if (handleSimpleFieldDereferences(start)) {
            return;
        }

        // first pass: find everything that is interesting for data-flow analysis
        FirstInspectionVisitor firstPass = new FirstInspectionVisitor();
        start.accept(firstPass);

        if (!firstPass.foundSomething()) {
            log("startInspectionOnNode: have not found anything in first pass");
            return;
        }

        log("  found literals: %s", Arrays.toString(firstPass.stringLiterals.toArray()));
        log("  found selects: %s", Arrays.toString(firstPass.selects.toArray()));
        log("  found variables: %s", Arrays.toString(firstPass.variableReferences.toArray()));
        log("  found method invocations: %s", Arrays.toString(firstPass.methodInvocations.toArray()));

        for (StringLiteral literal : firstPass.stringLiterals) {
            addResult(literal.astValue());
        }

        // selects are in the following form: LocationManager.GPS_PROVIDER or R.id.my_custom_id
        // and could therefore be resolved by the Java parser
        for (Select select : firstPass.selects) {
            handleSimpleFieldDereferences(select);
        }

        handleVariableReferences(firstPass.variableReferences);
    }

    public List<String> getResults() {
        return results;
    }

    private void handleVariableReferences(List<VariableReference> variableReferences) {
        for (VariableReference variableReference : variableReferences) {
            if (!isStringReference(variableReference)) {
                log("handleVariableReferences: discarding %s (not a string)", variableReference.astIdentifier());
                continue;
            }

            VariableValueVisitor variableVisitor = new VariableValueVisitor();
            variableVisitor.findValuesForVariableReference(variableReference);

            for (String result : variableVisitor.retrieveResults()) {
                addResult(result);
            }
        }
    }

    private boolean isStringReference(VariableReference variableReference) {
        JavaParser.ResolvedNode resolvedNode = context.resolve(variableReference);
        if (!(resolvedNode instanceof JavaParser.ResolvedVariable)) {
            return false;
        }

        JavaParser.TypeDescriptor type = ((JavaParser.ResolvedVariable) resolvedNode).getType();
        return type.getName().equals(JavaParser.TYPE_STRING);
    }

    /**
     * The context provides a Java parser that is able to do simple field and variable dereferences.
     * In most cases we will find something like that, so use that to shorten the analysis time.
     *
     * @return boolean true if fields have been dereferenced
     */
    private boolean handleSimpleFieldDereferences(Expression expression) {
        if (expression instanceof StringLiteral) {
            StringLiteral argument = (StringLiteral) expression;
            String parameter = argument.astValue();

            log("handleSimpleFieldDereferences: string literal found: %s", parameter);
            addResult(parameter);

            return true;
        }

        JavaParser.ResolvedNode resolvedNode = context.resolve(expression);
        // resolving nodes may also fail completely, e.g. for inline if expressions
        log("handleSimpleFieldDereferences: resolved expression to: %s", resolvedNode);

        if (resolvedNode instanceof JavaParser.ResolvedField) {
            JavaParser.ResolvedField field = (JavaParser.ResolvedField) resolvedNode;
            Object value = field.getValue();
            log("handleSimpleFieldDereferences: resolved field to value: %s", value);

            if (value instanceof String) {
                addResult((String) value);
                return true;
            }
        }

        // local variables are not supported here (by the parser) ...
        return false;
    }

    private class FirstInspectionVisitor extends ForwardingAstVisitor {

        private List<StringLiteral> stringLiterals = new ArrayList<>();
        private List<VariableReference> variableReferences = new ArrayList<>();
        private List<MethodInvocation> methodInvocations = new ArrayList<>();
        private List<Select> selects = new ArrayList<>();

        public boolean foundSomething() {
            return !(stringLiterals.isEmpty() && variableReferences.isEmpty() && methodInvocations.isEmpty()
                    && selects.isEmpty());
        }

        @Override
        public boolean visitStringLiteral(StringLiteral node) {
            stringLiterals.add(node);
            return super.visitStringLiteral(node);
        }

        @Override
        public boolean visitVariableReference(VariableReference node) {
            variableReferences.add(node);
            return super.visitVariableReference(node);
        }

        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            methodInvocations.add(node);
            return super.visitMethodInvocation(node);
        }

        @Override
        public boolean visitSelect(Select node) {
            selects.add(node);
            return super.visitSelect(node);
        }
    }

    private class VariableValueVisitor extends ForwardingAstVisitor {

        private List<String> results;

        public void findValuesForVariableReference(VariableReference variable) {
            Node surroundingMethod = JavaContext.findSurroundingMethod(variable);
            if (surroundingMethod == null) {
                // should not be possible. additionally, field references have already been
                // handled prior by the parent class
                return;
            }

            // look for variable definition entry and variable writes
            // pay attention to conditionals

            // later: recurse if variable is written by method
            // stop at boundary of compilation unit
        }

        public List<String> retrieveResults() {
            return results;
        }

    }
}
