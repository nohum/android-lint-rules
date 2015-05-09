package io.github.nohum.androidlint.detectors;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;

import java.util.ArrayList;
import java.util.Collections;
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
            "Intent flag FLAG_ACTIVITY_NEW_TASK as otherwise an exception is thrown.",
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
        return Collections.singletonList(SdkConstants.CLASS_BROADCASTRECEIVER);
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

            // secondly check if the startActivity gets called by onReceive
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
}
