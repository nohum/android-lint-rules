package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.JavaContext;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.StringLiteral;

import java.util.ArrayList;
import java.util.List;

public class StringDataFlowAstVisitor extends ForwardingAstVisitor {

    private static final boolean DEBUG = true;

    private JavaContext context;

    private Expression startingExpression;

    private List<String> results;

    public StringDataFlowAstVisitor(JavaContext context) {
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
        this.startingExpression = start;

        if (handleSimpleFieldDereferences()) {
            return;
        }
    }

    /**
     * The context provides a Java parser that is able to do simple field and variable dereferences.
     * In most cases we will find something like that, so use that to shorten the analysis time.
     *
     * @return boolean true if fields have been dereferenced
     */
    private boolean handleSimpleFieldDereferences() {
        if (startingExpression instanceof StringLiteral) {
            StringLiteral argument = (StringLiteral) startingExpression;
            String parameter = argument.astValue();

            log("handleSimpleFieldDereferences: string literal found: %s", parameter);
            addResult(parameter);

            return true;
        }

        JavaParser.ResolvedNode resolveNode = context.resolve(startingExpression);
        // resolving nodes may also fail completely, e.g. for inline if expressions
        log("handleSimpleFieldDereferences: resolved expression to: %s", resolveNode);

        if (resolveNode instanceof JavaParser.ResolvedField) {
            JavaParser.ResolvedField field = (JavaParser.ResolvedField) resolveNode;
            Object value = field.getValue();
            log("handleSimpleFieldDereferences: resolved to value: %s", value);

            if (value instanceof String) {
                addResult((String) value);
                return true;
            }
        }

        return false;
    }

    public List<String> getResults() {
        return results;
    }
}
