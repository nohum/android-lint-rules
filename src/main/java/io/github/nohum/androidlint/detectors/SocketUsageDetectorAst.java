package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import com.google.common.collect.Sets;
import lombok.ast.*;
import lombok.ast.printer.SourceFormatter;
import lombok.ast.printer.SourcePrinter;
import lombok.ast.printer.StructureFormatter;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.PrintWriter;
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
            "Internet is accesses without proper permission",
            "When accessing the internet using a socket or some other available methods, " +
            "the `android.permission.INTERNET` permission must be acquired in the manifest.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(SocketUsageDetectorAst.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    /** Permission name of INTERNET permission */
    private static final String INTERNET_PERMISSION = "android.permission.INTERNET";

    private static final String CLASS_SOCKET = "java/net/Socket";
    private static final String METHOD_SOCKET_CONNECT = "connect";

    private static final String CLASS_SOCKET_FACTORY = "javax.net.SocketFactory";
    private static final String METHOD_SOCKET_FACTORY_CREATE = "createSocket";
    private static final String CLASS_SSL_SOCKET_FACTORY = "javax.net.SSLSocketFactory";
    // method for SSLSocketFactor is the same as it extends SocketFactory

    private static final String CLASS_HTTP_CLIENT = "org.apache.http.client.HttpClient";
    private static final String CLASS_DEFAULT_HTTP_CLIENT = "org.apache.http.impl.client.DefaultHttpClient";
    private static final String METHOD_HTTP_CLIENT_EXECUTE = "execute";

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
        List<Class<? extends lombok.ast.Node>> types = new ArrayList<Class<? extends lombok.ast.Node>>();
        types.add(CompilationUnit.class);
        types.add(ImportDeclaration.class);
        types.add(MethodDeclaration.class);
        types.add(ConstructorDeclaration.class);
        types.add(VariableDefinitionEntry.class);

        return types;
    }

    private String classNameFromFcqn(String fqcn) {
        String[] parts = fqcn.split("\\.");
        return parts.length == 0 ? "-" : parts[parts.length - 1];
    }

    private final class DeclarationVisitor extends ForwardingAstVisitor {
        private JavaContext context;
        private Set<String> localVars;
        private Node currentMethod;
        private Set<String> imports = new HashSet<String>();
        private Set<String> starImports = new HashSet<String>();

        private DeclarationVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitCompilationUnit(CompilationUnit node) {
            boolean shouldContinue = false;


            for (TypeDeclaration declaration : node.astTypeDeclarations()) {
                if (declaration.astName().astValue().contains("AsyncHttpClientTask")) { // detect own classes preliminary
                    shouldContinue = true;
                    break;
                }
            }

            if (!shouldContinue) {
                return true;
            }

            SourceFormatter formatter = StructureFormatter.formatterWithPositions();
            SourcePrinter printer = new SourcePrinter(formatter);

            node.accept(printer);
            new PrintWriter(System.out).write(formatter.finish());

            return true;
        }

        @Override
        public boolean visitImportDeclaration(ImportDeclaration node) {
//            System.out.println("import: " + node.asFullyQualifiedName());

            if (node.astStarImport()) {

            }

            return false;
        }

        @Override
        public boolean visitVariableDefinitionEntry(VariableDefinitionEntry node) {
            if (currentMethod != null) {
                if (localVars == null) {
                    localVars = Sets.newHashSet();
                }
                localVars.add(node.astName().astValue());
            } else {
                if (imports == null) {
                    imports = Sets.newHashSet();
                }
                imports.add(node.astName().astValue());
            }
            return super.visitVariableDefinitionEntry(node);
        }

        @Override
        public boolean visitMethodDeclaration(MethodDeclaration node) {
            localVars = null;
            currentMethod = node;
//            System.out.println(node.toString() + ": ");

            StrictListAccessor<VariableDefinition, lombok.ast.MethodDeclaration> parameters = node.astParameters();
            for (VariableDefinition def : parameters) {

//                def.astTypeReference().getTypeName();
            }

            return super.visitMethodDeclaration(node);
        }

        @Override
        public boolean visitConstructorDeclaration(ConstructorDeclaration node) {
            localVars = null;
            currentMethod = node;
//            System.out.println(node.toString() + " (ctr): " + node.astTypeName().astValue());

            return super.visitConstructorDeclaration(node);
        }

        @Override
        public void endVisit(lombok.ast.Node node) {
            if (node == currentMethod) {
                currentMethod = null;
            }

            super.endVisit(node);
        }
    }
}
