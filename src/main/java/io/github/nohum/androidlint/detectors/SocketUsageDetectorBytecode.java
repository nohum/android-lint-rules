package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

/**
 * Detector that checks for usage of an internet socket or other common libraries that connect
 * to the internet. If the app does not possess the right to access the internet, an issue will
 * be reported.
 */
public class SocketUsageDetectorBytecode extends Detector implements Detector.XmlScanner, Detector.ClassScanner  {

    public static final Issue ISSUE = Issue.create(
            "SocketUsageWithoutPermissionBytecode",
            "Internet is accessed without proper permission",
            "When accessing the internet using a socket or some other available methods, " +
            "the `android.permission.INTERNET` permission must be acquired in the manifest.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(SocketUsageDetectorBytecode.class, EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE,
                    Scope.JAVA_LIBRARIES)));

    /** Permission name of INTERNET permission */
    private static final String INTERNET_PERMISSION = "android.permission.INTERNET";

    private static final String CLASS_SOCKET = "java/net/Socket";
    private static final String METHOD_SOCKET_CONNECT = "connect";

    private static final String CLASS_SOCKET_FACTORY = "javax/net/SocketFactory";
    private static final String METHOD_SOCKET_FACTORY_CREATE = "createSocket";
    private static final String CLASS_SSL_SOCKET_FACTORY = "javax/net/SSLSocketFactory";
    // method for SSLSocketFactor is the same as it extends SocketFactory

    private static final String CLASS_HTTP_CLIENT = "org/apache/http/client/HttpClient";
    private static final String CLASS_DEFAULT_HTTP_CLIENT = "org/apache/http/impl/client/DefaultHttpClient";
    private static final String METHOD_HTTP_CLIENT_EXECUTE = "execute";

    private static final String CLASS_URL = "java/net/URL";
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
    public List<String> getApplicableCallNames() {
        return Arrays.asList(
                METHOD_SOCKET_CONNECT,
                METHOD_SOCKET_FACTORY_CREATE,
                METHOD_HTTP_CLIENT_EXECUTE,
                METHOD_URL_OPEN_CONNECTION
        );
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode, @NonNull MethodNode method,
                          @NonNull MethodInsnNode call) {
        // we would only generate false positives if the app possesses the INTERNET permission
        if (hasInternetPermission) {
            return;
        }

        // we only accept instance calls (also HttpClient is a interface)
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL && call.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return;
        }

        if (matches(call, call.getOpcode() == Opcodes.INVOKEVIRTUAL, context.getDriver())) {
            context.report(ISSUE, method, call, context.getLocation(call),
                    String.format("Call to `%s` requires INTERNET permission", call.name));
        }
    }

    private boolean matches(MethodInsnNode call, boolean checkInheritance, LintDriver driver) {
        String owner = call.owner;
        if (checkInheritance) {
            owner = getApplicableInheritance(owner, driver);
        }

        if (METHOD_SOCKET_CONNECT.equals(call.name) && CLASS_SOCKET.equals(owner)) {
            return true;
        }

        if (METHOD_SOCKET_FACTORY_CREATE.equals(call.name)
                && (CLASS_SOCKET_FACTORY.equals(owner) || CLASS_SSL_SOCKET_FACTORY.equals(owner))) {
            // the createSocket-method has many overloaded variants - we are only interested in the ones that
            // take parameters as these are creating an already connected socket - (the simple one would be
            // a false positive)
            Type[] methodArgumentTypes = Type.getArgumentTypes(call.desc);
            return methodArgumentTypes.length > 0;
        }

        if (METHOD_HTTP_CLIENT_EXECUTE.equals(call.name)
                && (CLASS_HTTP_CLIENT.equals(owner) || CLASS_DEFAULT_HTTP_CLIENT.equals(owner))) {
            return true;
        }

        if (METHOD_URL_OPEN_CONNECTION.equals(call.name) && CLASS_URL.equals(owner)) {
            return true;
        }

        return false;
    }

    private String getApplicableInheritance(String originalOwner, LintDriver driver) {
        String superClass = originalOwner;
        do {
            superClass = driver.getSuperClass(superClass);

            if (CLASS_SOCKET.equals(superClass)) {
                return CLASS_SOCKET;
            } else if (CLASS_SOCKET_FACTORY.equals(superClass)) { // SSL version extends this
                return CLASS_SOCKET_FACTORY;
            } else if (CLASS_DEFAULT_HTTP_CLIENT.equals(superClass)) {
                return CLASS_DEFAULT_HTTP_CLIENT;
            }
        } while (superClass != null);

        // no need to check URL as it is declared final
        // also no need for HttpClient as not invoked in case of INVOKEVIRTUAL (would use INVOKEINTERFACE instead)
        return originalOwner;
    }
}
