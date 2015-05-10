package io.github.nohum.androidlint.detectors;

import static com.android.SdkConstants.*;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This detector checks if activities get started with the right flags when being started from broadcast receivers.
 *
 * When starting an Activity from a BroadcastReceiver, this fails unless the Activity is started with the
 * FLAG_ACTIVITY_NEW_TASK flag.
 */
public class ReceiverStartActivityFlagsDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
            "ReceiverStartActivityFlagsMissing",
            "Activity is started from withing receiver with wrong flags",
            "When starting Activities from within broadcast receivers, they must be started with the " +
            "Intent flag `FLAG_ACTIVITY_NEW_TASK` as otherwise an exception is thrown.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(ReceiverStartActivityFlagsDetector.class, Scope.JAVA_FILE_SCOPE));

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_BROADCASTRECEIVER);
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @Nullable ClassDeclaration declaration, @NonNull Node node,
                           @NonNull JavaParser.ResolvedClass resolvedClass) {
        // look for startActivity calls ... if there are none, we immediately done here
        StartActivityInvocationFinder startActivityVisitor = new StartActivityInvocationFinder();
        node.accept(startActivityVisitor);

        List<MethodInvocation> startInvocations = startActivityVisitor.getStartInvocs();
        for (MethodInvocation startInvocation : startInvocations) {
            // first check every intent for missing flags
            String calledMethod = startInvocation.astName().astValue();
            Expression intentVariable = startInvocation.astArguments().first();

            // matches e.g. startActivity(new Intent(context, MainActivity.class))
            if (intentVariable instanceof ConstructorInvocation) {
                // check if Intent is used: if, this case is wrong and must yield an report!
                TypeReference ref = ((ConstructorInvocation) intentVariable).astTypeReference();

                if ("Intent".equals(ref.getTypeName())) {
                    context.report(ISSUE, context.getLocation(startInvocation),
                            String.format("Activity started with `%s` will probably crash because of missing flag", calledMethod));
                }

                continue;
            }

            if (intentVariable instanceof VariableReference) {
                IntentWriteFinder writeFinder =  new IntentWriteFinder((VariableReference) intentVariable);
                node.accept(writeFinder);

                if (writeFinder.hasFoundNewTaskFlag()) {
                    continue;
                }

                context.report(ISSUE, context.getLocation(startInvocation),
                        String.format("Activity started with `%s` will probably crash because of missing flag", calledMethod));
            }
        }
    }

    private class StartActivityInvocationFinder extends ForwardingAstVisitor {
        private List<MethodInvocation> invocs = new ArrayList<MethodInvocation>();

        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            if (node.astName().astValue().startsWith("startActivity")) { // there are several implementations
                invocs.add(node);
            }

            return false;
        }

        public List<MethodInvocation> getStartInvocs() {
            return invocs;
        }
    }

    private class IntentWriteFinder extends ForwardingAstVisitor {
        private VariableReference callee;

        private boolean foundNewTaskFlag = false;

        public IntentWriteFinder(VariableReference callee) {
            this.callee = callee;
        }

        public boolean hasFoundNewTaskFlag() {
            return foundNewTaskFlag;
        }

        @Override
        public void afterVisitMethodInvocation(MethodInvocation node) {
            if (node.astName().astValue().equals("setFlags")) {
                if (!(node.astOperand() instanceof VariableReference)) {
                    return;
                }

                VariableReference methodCaller = (VariableReference) node.astOperand();
                // check that call is being made on same Intent variable
                if (!methodCaller.astIdentifier().astValue().equals(callee.astIdentifier().astValue())) {
                    return;
                }

                Iterator<Expression> iter = node.astArguments().iterator();
                boolean found = false;
                while (iter.hasNext()) {
                    Expression expression = iter.next();
                    if (expression instanceof Select) {
                        String expressionValue = ((Select) expression).astIdentifier().astValue();
                        if ("FLAG_ACTIVITY_NEW_TASK".equals(expressionValue)) {
                            found = true;
                            break;
                        }
                    }
                }

                // reset the variable to that value as several consecutive setFlags calls may overwrite each other
                // and we want to respect that...
                foundNewTaskFlag = found;
            }
        }
    }
}
