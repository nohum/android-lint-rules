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

        log("found literals: %s", Arrays.toString(firstPass.stringLiterals.toArray()));
        log("found selects: %s", Arrays.toString(firstPass.selects.toArray()));
        log("found variables: %s", Arrays.toString(firstPass.variableReferences.toArray()));
        log("found method invocations: %s", Arrays.toString(firstPass.methodInvocations.toArray()));

        for (StringLiteral literal : firstPass.stringLiterals) {
            addResult(literal.astValue());
        }

        // selects are in the following form: LocationManager.GPS_PROVIDER or R.id.my_custom_id
        // and could therefore be resolved by the Java parser
        for (Select select : firstPass.selects) {
            handleSimpleFieldDereferences(select);
        }

        handleVariableReferences(firstPass.variableReferences);

        // TODO method invocations?
    }

    public List<String> getResults() {
        return results;
    }

    private void handleVariableReferences(List<VariableReference> variableReferences) {
        for (VariableReference variableReference : variableReferences) {
            if (!isStringReference(variableReference)) {
//                log("handleVariableReferences: discarding %s (not a string)", variableReference.astIdentifier());
                continue;
            }

            VariableValueVisitor variableVisitor = new VariableValueVisitor();
            variableVisitor.findValuesForVariableReference(variableReference);

            log("  handleVariableReferences: result for %s: %s", variableReference.astIdentifier(),
                    Arrays.toString(variableVisitor.retrieveResults().toArray()));

//            for (String result : variableVisitor.retrieveResults()) {
//                addResult(result);
//            }
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

            addResult(parameter);
            return true;
        }

        JavaParser.ResolvedNode resolvedNode = context.resolve(expression);
        // resolving nodes may also fail completely, e.g. for inline if expressions
        if (resolvedNode instanceof JavaParser.ResolvedField) {
            JavaParser.ResolvedField field = (JavaParser.ResolvedField) resolvedNode;
            Object value = field.getValue();
            log("  handleSimpleFieldDereferences: resolved field %s to value: %s", resolvedNode, value);

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

        private List<Expression> results = new ArrayList<>();

        private List<Expression> conditionalResults = new ArrayList<>();

        private VariableReference subject;

        private boolean collectionAllowed = false;

        private int conditionalStage = 0;

        public void findValuesForVariableReference(VariableReference variable) {
            Node surroundingMethod = JavaContext.findSurroundingMethod(variable);
            if (surroundingMethod == null) {
                // should not be possible. additionally, field references have already been
                // handled prior by the parent class
                return;
            }

            Block methodBody = null;
            if (surroundingMethod instanceof MethodDeclaration) {
                methodBody = ((MethodDeclaration) surroundingMethod).astBody();

                log("    findSurroundingMethod: variable ref = %s, surrounded by = %s", variable,
                        ((MethodDeclaration) surroundingMethod).astMethodName());
            } else if (surroundingMethod instanceof ConstructorDeclaration) {
                methodBody = ((ConstructorDeclaration) surroundingMethod).astBody();

                log("    findSurroundingMethod: variable ref = %s, surrounded by = %s.<init>", variable,
                        ((ConstructorDeclaration) surroundingMethod).astTypeName());
            }

            if (methodBody == null) {
                throw new IllegalStateException("method body was empty");
            }

            subject = variable;

            // look for variable definition entry and variable writes
            // pay attention to conditionals
            methodBody.accept(this);

            // later: recurse if variable is written by method signature
            // stop at boundary of compilation unit
        }

        public List<Expression> retrieveResults() {
            List<Expression> result = new ArrayList<Expression>(results);
            result.addAll(conditionalResults);
            return result;
        }

        private boolean isAcceptableResult(Expression expression) {
            return expression instanceof Select || expression instanceof StringLiteral
                    || expression instanceof MethodInvocation;
        }

        private void addToResult(Expression node) {
            if (conditionalStage > 0) {
                conditionalResults.add(node);
            } else {
                results.clear();
                results.add(node);
            }
        }

        @Override
        public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
            if (!node.astName().astValue().equals(subject.astIdentifier().astValue())) {
                return true;
            }

            if (isAcceptableResult(node.astInitializer())) {
                log("    VariableValueVisitor.visitVariableDefinitionEntry: adding %s", node);
                results.add(node.astInitializer());
                return true;
            }

            collectionAllowed = true;
            return false;
        }

        @Override
        public void endVisit(Node node) {
            if (node instanceof VariableDefinitionEntry || node instanceof BinaryExpression) {
                log("    endVisit: expression");
                collectionAllowed = false;
            } else if (node instanceof InlineIfExpression || node instanceof If) {
                log("    endVisit: if");
                -- conditionalStage;
            }
        }

        @Override
        public boolean visitBinaryExpression(BinaryExpression node) {
            log("    VariableValueVisitor.visitBinaryExpression: %s", node);

            if (!(node.astLeft() instanceof VariableReference)) {
                return true;
            }

            VariableReference reference = ((VariableReference) node.astLeft());
            String assignmentVarName = reference.astIdentifier().astValue();
            if (!assignmentVarName.equals(subject.astIdentifier().astValue())) {
                return true;
            }

            if (!node.astOperator().isAssignment()) {
                log("    VariableValueVisitor.visitBinaryExpression: not an assignment (type = %s)", node.astOperator());
                return true; // we are only interested in assignments
            }

            collectionAllowed = true;
            return false; // inspect statement further
        }

        @Override
        public boolean visitInlineIfExpression(InlineIfExpression node) {
            log("    VariableValueVisitor.visitInlineIfExpression: %s", node);

            ++ conditionalStage;
            return false;
        }

        @Override
        public boolean visitIf(If node) {
            log("    VariableValueVisitor.visitIf: if (%s)", node.astCondition());

            ++ conditionalStage;
            return super.visitIf(node);
        }

        @Override
        public boolean visitExpressionStatement(ExpressionStatement node) {
            log("    VariableValueVisitor.visitExpressionStatement (if): %s", node);
            if (conditionalStage == 0) {
                return false; // do not return true as blocks that yet have to be discovered should still be included
            }

            return super.visitExpressionStatement(node);
        }

        @Override
        public boolean visitStringLiteral(StringLiteral node) {
            if (!collectionAllowed) {
                log("    VariableValueVisitor.visitStringLiteral (%s): collection not allowed", node);
                return true;
            }

            log("    VariableValueVisitor.visitStringLiteral: %s", node);
            addToResult(node);
            return true;
        }

        @Override
        public boolean visitVariableReference(VariableReference node) {
            if (!collectionAllowed) {
                log("    VariableValueVisitor.visitVariableReference (%s): collection not allowed", node.astIdentifier());
                return true;
            }

            if (node.astIdentifier().astValue().equals(subject.astIdentifier().astValue())) {
                return true; // this a assignment to the subject variable
            }

            log("    VariableValueVisitor.visitVariableReference: %s", node);
            addToResult(node);
            return super.visitVariableReference(node);
        }

        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            if (!collectionAllowed) {
                log("    VariableValueVisitor.visitMethodInvocation (%s): collection not allowed", node.astName());
                return true;
            }

            log("    VariableValueVisitor.visitMethodInvocation: %s", node);
            addToResult(node);
            return super.visitMethodInvocation(node);
        }

        @Override
        public boolean visitSelect(Select node) {
            if (!collectionAllowed) {
                log("    VariableValueVisitor.visitSelect (%s): collection not allowed", node);
                return true;
            }

            log("    VariableValueVisitor.visitSelect: %s", node);
            addToResult(node);

            // DO NOT CHANGE as LocationManager.GPS_PROVIDER would add a false VariableReference
            // to LocationManager otherwise instead
            return true;
        }
    }
}
