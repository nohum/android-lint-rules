package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

import static com.android.SdkConstants.*;

/**
 * Detector that checks for usage of an internet socket or other common libraries that connect
 * to the internet. If the app does not possess the right to access the internet, an issue will
 * be reported.
 */
public class SocketUsageDetectorAst extends Detector implements Detector.XmlScanner, Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
            "SocketUsageWithoutPermissionAst",
            "Internet is accessed without proper permission",
            "When accessing the internet using a socket or some other available methods, " +
            "the `android.permission.INTERNET` permission must be acquired in the manifest.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(SocketUsageDetectorAst.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    /** Permission name of INTERNET permission */
    private static final String INTERNET_PERMISSION = "android.permission.INTERNET";

    private static final String PACKAGESTAR_SOCKET_AND_URL = "java.net.*";
    private static final String CLASS_SOCKET = "java.net.Socket";
    private static final String METHOD_SOCKET_CONNECT = "connect";

    private static final String PACKAGESTAR_SOCKET_FACTORY = "javax.net.*";
    private static final String CLASS_SOCKET_FACTORY = "javax.net.SocketFactory";
    private static final String METHOD_SOCKET_FACTORY_CREATE = "createSocket";
    private static final String CLASS_SSL_SOCKET_FACTORY = "javax.net.SSLSocketFactory";
    // method for SSLSocketFactor is the same as it extends SocketFactory

    private static final String PACKAGESTAR_HTTP_CLIENT = "org.apache.http.client.*";
    private static final String CLASS_HTTP_CLIENT = "org.apache.http.client.HttpClient";
    private static final String PACKAGESTAR_DEFAULT_HTTP_CLIENT = "org.apache.http.impl.client.*";
    private static final String CLASS_DEFAULT_HTTP_CLIENT = "org.apache.http.impl.client.DefaultHttpClient";
    private static final String METHOD_HTTP_CLIENT_EXECUTE = "execute";

    // covered by PACKAGESTAR_SOCKET_AND_URL !!
    private static final String CLASS_URL = "java.net.URL";
    private static final String METHOD_URL_OPEN_CONNECTION = "openConnection";

    private boolean hasInternetPermission = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (name != null && name.getValue().equals(INTERNET_PERMISSION)) {
            hasInternetPermission = true;
        }
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        if (hasInternetPermission) {
            return null; // no need to do further checks
        }

        return new DeclarationVisitor(context);
    }

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        // our visitor looks for these nodes in the AST
        List<Class<? extends Node>> types = new ArrayList<Class<? extends Node>>();
        types.add(ImportDeclaration.class);
        types.add(MethodDeclaration.class);
        types.add(ConstructorDeclaration.class);
        types.add(VariableDefinitionEntry.class);
        types.add(MethodInvocation.class);
        types.add(VariableReference.class);

        return types;
    }

    private class DeclarationVisitor extends ForwardingAstVisitor {
        private static final boolean DEBUG = false;
        private JavaContext context;
        private Map<String, String> currentSuspectedVars = new HashMap<>();
        private Set<String> imports = new HashSet<String>();
        private MethodInvocation currentInvocatedMethod;

        private Set<String> criticalStarImports = new HashSet<String>() {{
            add(PACKAGESTAR_SOCKET_AND_URL);
            add(PACKAGESTAR_SOCKET_FACTORY);
            add(PACKAGESTAR_HTTP_CLIENT);
            add(PACKAGESTAR_DEFAULT_HTTP_CLIENT);
        }};

        private Set<String> criticalImports = new HashSet<String>() {{
            add(CLASS_SOCKET);
            add(CLASS_SOCKET_FACTORY);
            add(CLASS_SSL_SOCKET_FACTORY);
            add(CLASS_HTTP_CLIENT);
            add(CLASS_DEFAULT_HTTP_CLIENT);
            add(CLASS_URL);
        }};

        public DeclarationVisitor(JavaContext context) {
            this.context = context;
        }

        private void log(String format, Object... args) {
            if (DEBUG) {
                System.out.println(String.format(format, args));
            }
        }

        private String classNameFromFcqn(String fqcn) {
            String[] parts = fqcn.split("\\.");
            return parts.length == 0 ? "" : parts[parts.length - 1];
        }


        private boolean noConnectionClassesUsed() {
            return imports.isEmpty();
        }

        private boolean noSuspectedVariables() {
            return currentSuspectedVars.isEmpty();
        }

        /**
         * Called first
         */
        @Override
        public boolean visitImportDeclaration(ImportDeclaration node) {
            String fqcn = node.asFullyQualifiedName();

            // if one of the star imports is listed, one of our classes is used
            if (node.astStarImport() && criticalStarImports.contains(fqcn)) {
                if (PACKAGESTAR_SOCKET_AND_URL.equals(fqcn)) {
                    imports.add(CLASS_SOCKET);
                    imports.add(CLASS_URL);
                } else if (PACKAGESTAR_SOCKET_FACTORY.equals(fqcn)) {
                    imports.add(CLASS_SOCKET_FACTORY);
                    imports.add(CLASS_SSL_SOCKET_FACTORY);
                } else if (PACKAGESTAR_HTTP_CLIENT.equals(fqcn)) {
                    imports.add(CLASS_HTTP_CLIENT);
                } else if (PACKAGESTAR_DEFAULT_HTTP_CLIENT.equals(fqcn)) {
                    imports.add(CLASS_DEFAULT_HTTP_CLIENT);
                }
            } else if (criticalImports.contains(fqcn)) {
                imports.add(fqcn);
            }

            return true;
        }

        /**
         * Called secondly
         */
        @Override
        public boolean visitMethodDeclaration(MethodDeclaration node) {
            if (noConnectionClassesUsed()) {
                return true; // no internet connection classes used, we are finished here ...
            }

            currentSuspectedVars.clear();

            log("in method: %s", node.astMethodName().astValue());
            return super.visitMethodDeclaration(node);
        }

        /**
         * Called secondly
         */
        @Override
        public boolean visitConstructorDeclaration(ConstructorDeclaration node) {
            if (noConnectionClassesUsed()) {
                return true; // no internet connection classes used, we are finished here ...
            }

            currentSuspectedVars.clear();
            return super.visitConstructorDeclaration(node);
        }

        /**
         * Called third, for method/constructor signatures or variable definitions within method bodies
         */
        @Override
        public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
            if (noConnectionClassesUsed()) {
                return true; // no internet connection classes used, we are finished here ...
            }

            TypeReference type = node.upToVariableDefinition().astTypeReference();

            for (String importedClass : imports) {
                // this type may either be a classname or a FQCN
                if (importedClass.equals(type.getTypeName()) || classNameFromFcqn(importedClass).equals(type.getTypeName())) {
                    // the same variable identifier may be saved several times (e.g. same identifier used in different scopes)
                    currentSuspectedVars.put(node.astName().astValue(), importedClass);

                    log("currentSuspectedVars: %s -> %s", node.astName().astValue(), importedClass);
                }
            }

            return false;
        }

        /**
         * Called as a fourth step. Most importantly, any used variable that is suspectible is already contained
         * in {@link #currentSuspectedVars}
         */
        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            if (noSuspectedVariables()) {
                return true; // nothing to do ...
            }

            /* node.astOperand() will be something like:
                  (socket != null ? socket : null).connect(null);
               --- or ---
                  ((Socket) socket).connect(null);
               --- or simply ---
                  socket.connect(...);

               respectively, in each case the interesting inner part is a VariableReference
               thus visitVariableReference IS GOING TO get called shortly after this visit
            */

            String calledMethodName = node.astName().astValue();

            if (node.astOperand() == null) { // if null, this looks like this: methodName(var, var2);
                return true;
            }

            log("invocation: %s (%s).%s", node.astOperand(), node.astOperand().getClass(), calledMethodName);

            currentInvocatedMethod = node;
            return false;
        }

        /**
         * Fifth step (method invocations usually contain a variable reference)
         */
        @Override
        public boolean visitVariableReference(VariableReference node) {
            if (currentInvocatedMethod == null) {
                return true;
            }

            String varName = node.astIdentifier().astValue();
            String calledMethodName = currentInvocatedMethod.astName().astValue();
            boolean hasParameters = !currentInvocatedMethod.astArguments().isEmpty();
            log("var-ref: %s (call should be to: %s)", varName, calledMethodName);

            if (callWillFail(varName, calledMethodName, hasParameters)) {
                log("found failing: %s.%s", varName, calledMethodName);

                context.report(ISSUE, currentInvocatedMethod, context.getLocation(currentInvocatedMethod),
                        String.format("Call to `%s` requires INTERNET permission", currentInvocatedMethod));
            }

            // we are making a trade-off here: by setting the invocation to null, e.g. inline-expressions with multiple
            // variable references are only detected once. but this is ok as we are finally interested in the method
            // call, not the variables
            currentInvocatedMethod = null;
            return super.visitVariableReference(node);
        }

        /**
         * Map a variable name to its type and lookup if the method name is likely to fail
         */
        private boolean callWillFail(String varName, String methodName, boolean hasParameters) {
            if (!currentSuspectedVars.containsKey(varName)) {
                return false;
            }

            String fqcn = currentSuspectedVars.get(varName);
            switch (fqcn) {
                case CLASS_SOCKET:
                    return METHOD_SOCKET_CONNECT.equals(methodName);

                case CLASS_SOCKET_FACTORY:
                    // the createSocket method without parameter is a false positive
                    return METHOD_SOCKET_FACTORY_CREATE.equals(methodName) && hasParameters;

                case CLASS_SSL_SOCKET_FACTORY:
                    // the createSocket method without parameter is a false positive
                    return METHOD_SOCKET_FACTORY_CREATE.equals(methodName) && hasParameters;

                case CLASS_HTTP_CLIENT:
                    return METHOD_HTTP_CLIENT_EXECUTE.equals(methodName);

                case CLASS_DEFAULT_HTTP_CLIENT:
                    return METHOD_HTTP_CLIENT_EXECUTE.equals(methodName);

                case CLASS_URL:
                    return METHOD_URL_OPEN_CONNECTION.equals(methodName);

                default:
                    return false;
            }
        }
    }
}
