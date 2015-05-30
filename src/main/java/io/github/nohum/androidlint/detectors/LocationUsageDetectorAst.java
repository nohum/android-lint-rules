package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

import static com.android.SdkConstants.*;

/**
 * Detector that checks for Android Location API usages which are likely to fail without
 * the proper permissions. The Android manifest is scanned for the permissions. Hence, usages
 * of the APIs with correct permissions will not trigger any warnings.
 */
public class LocationUsageDetectorAst extends Detector implements Detector.XmlScanner, Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
            "LocationUsageWithoutPermissionAst",
            "Location data is gathered without declared manifest permission",
            "When requesting location data, the proper permission (`ACCESS_COARSE_LOCATION` or " +
            "`ACCESS_FINE_LOCATION`) must be requested in the manifest. Otherwise the " +
            "Android framework will throw a `SecurityException` when requesting location data." +
            "\n" +
            "This detector is not checking for permissions or calls to the mock location methods!",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(LocationUsageDetectorAst.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    private static final boolean DEBUG = true;

    /** Permission name of coarse location permission */
    public static final String COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION";

    /** Permission name of fine location permission */
    public static final String FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION";

    private static final String CLASS_LOCATION_MANAGER = "android.location.LocationManager";

    private static final String LOCATION_METHOD_FINE = "gps";
    private static final String LOCATION_METHOD_COARSE = "network";
    private static final String LOCATION_METHOD_PASSIVE = "passive";

    private static final String METHOD_ADD_GPS_LISTENER = "addGpsStatusListener";
    private static final String METHOD_ADD_NMEA_LISTENER = "addNmeaListener";
    private static final String METHOD_ADD_PROXIMITRY_ALERT = "addProximityAlert";
    private static final String METHOD_GET_LAST_KNOWN_LOCATION = "getLastKnownLocation";
    private static final String METHOD_IS_PROVIDER_ENABLED = "isProviderEnabled";
    private static final String METHOD_REMOVE_PROXIMITRY_ALERT = "removeProximityAlert";
    private static final String METHOD_REQUEST_LOCATION_UPDATES = "requestLocationUpdates";
    private static final String METHOD_REQUEST_SINGLE_UPDATE = "requestSingleUpdate";

    private static final int API_LEVEL_JELLY_BEAN_MR1 = 17;
    private static final int API_LEVEL_LOLLIPOP = 21;

    private int targetApiLevel = -1;

    private boolean hasCoarsePermission = false;

    private boolean hasFinePermission = false;

    private void log(String format, Object... args) {
        if (DEBUG) {
            System.out.println(String.format(format, args));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (name == null) {
            return;
        }

        if (name.getValue().equals(COARSE_LOCATION_PERMISSION)) {
            hasCoarsePermission = true;
        }

        if (name.getValue().equals(FINE_LOCATION_PERMISSION)) {
            hasCoarsePermission = true; // fine location includes coarse
            hasFinePermission = true;
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        // relevant methods of android.location.LocationManager
        return Arrays.asList(
                METHOD_ADD_GPS_LISTENER,
                METHOD_ADD_NMEA_LISTENER,
                METHOD_ADD_PROXIMITRY_ALERT, // requires further api level check
                METHOD_GET_LAST_KNOWN_LOCATION,
                METHOD_IS_PROVIDER_ENABLED, // requires further api level check
                METHOD_REMOVE_PROXIMITRY_ALERT, // requires further api level check
                METHOD_REQUEST_LOCATION_UPDATES,
                METHOD_REQUEST_SINGLE_UPDATE
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, AstVisitor visitor, @NonNull MethodInvocation node) {
        // first, filter out calls that are not directed to the Android location manager

        JavaParser.ResolvedNode resolvedNode = context.resolve(node);
        if (!(resolvedNode instanceof JavaParser.ResolvedMethod)) {
            return;
        }

        JavaParser.ResolvedMethod resolvedMethod = (JavaParser.ResolvedMethod) resolvedNode;
        String calledMethod = node.astName().astValue();

        if (!CLASS_LOCATION_MANAGER.equals(resolvedMethod.getContainingClass().getName())) {
            log("visitMethod: discarding call to %s.%s", resolvedMethod.getContainingClass().getName(), calledMethod);
            return;
        }

        // some calls always require fine permission
        if (!hasFinePermission && METHOD_ADD_GPS_LISTENER.equals(calledMethod) || METHOD_ADD_NMEA_LISTENER.equals(calledMethod)) {
            reportDefaultIssue(context, node, FINE_LOCATION_PERMISSION);
        }
        // the semantics of what these methods accept and when they throw an exception has been changed at some point
        else if (METHOD_ADD_PROXIMITRY_ALERT.equals(calledMethod) || METHOD_REMOVE_PROXIMITRY_ALERT.equals(calledMethod)) {
            handleProximityMethods(context, node);
        }
        // ... also semantic change of when a exception is thrown
        else if (METHOD_IS_PROVIDER_ENABLED.equals(calledMethod) && getTargetSdk(context) < API_LEVEL_LOLLIPOP) {
            handleProviderEnabled(context, node);
        }
        // these calls depend on the used location provider
        else if (METHOD_REQUEST_LOCATION_UPDATES.equals(calledMethod) || METHOD_REQUEST_SINGLE_UPDATE.equals(calledMethod)) {
            handleRequestMethods(context, node, visitor);
        }
    }

    private void reportDefaultIssue(JavaContext context, MethodInvocation method, String requiredPermission) {
        context.report(ISSUE, method, context.getLocation(method),
                String.format("Call to `%s` requires `%s`", method.astName().astValue(), requiredPermission));
    }

    private void handleRequestMethods(JavaContext context, MethodInvocation method, AstVisitor visitor) {
        // to make matters worse, there are many overloaded versions of the methods at hand
        // we only look at the string versions here.
        log("handleRequestMethods ------------------------------");

        JavaParser.ResolvedMethod originalMethod = (JavaParser.ResolvedMethod) context.resolve(method);
        int argumentNumber = 0;
        boolean providerMode = false;

        for (int i = 0; i < originalMethod.getArgumentCount(); ++ i) {
            JavaParser.TypeDescriptor type = originalMethod.getArgumentType(i);
            argumentNumber = i;

            if (type.getName().equals(JavaParser.TYPE_STRING)) {
                providerMode = true;
                break;
            }
        }

        Expression actualArgumentData = null;
        int currCount = 0;
        for (Expression argument : method.astArguments()) {
            if (argumentNumber == currCount) {
                actualArgumentData = argument;
                break;
            }

            ++ currCount;
        }

        if (actualArgumentData == null) {
            throw new IllegalStateException("argument node was null, should not happen");
        }

        List<String> providers = new ArrayList<>();
        if (providerMode) {
            log("handleRequestMethods: in provider-mode, expression = %s", actualArgumentData);

            StringDataFlowDetector providerVisitor = new StringDataFlowDetector(context);
            providerVisitor.startInspectionOnExpression(actualArgumentData);
            providers = providerVisitor.getResults();
        }

        for (String provider : providers) {
            if (!hasFinePermission && LOCATION_METHOD_FINE.equals(provider)) {
                reportDefaultIssue(context, method, FINE_LOCATION_PERMISSION);
            } else if (!hasCoarsePermission && (LOCATION_METHOD_COARSE.equals(provider)
                    || LOCATION_METHOD_PASSIVE.equals(provider))) {
                reportDefaultIssue(context, method, COARSE_LOCATION_PERMISSION);
            }
        }
    }

    private void handleProviderEnabled(JavaContext context, MethodInvocation method) {
        log("handleProviderEnabled ------------------------------");
        StrictListAccessor<Expression, MethodInvocation> argumentList = method.astArguments();

        // method takes exactly one argument
        if (argumentList == null || argumentList.size() != 1) {
            log("handleProviderEnabled: method signature did not match");
            return;
        }

        StringDataFlowDetector visitor = new StringDataFlowDetector(context);
        visitor.startInspectionOnExpression(argumentList.first());
        List<String> providers = visitor.getResults();

        log("handleProviderEnabled: call %s\n  -> yielded result: %s", method, Arrays.toString(providers.toArray()));

        /*
           we may get multiple results back, e.g. for calls like:

               locationManager.isProviderEnabled(expression ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER);

           if one of these proves to be a issue we will stop further reporting. otherwise
           the same location will contain more than one warning which is possibly irritating
         */
        for (String provider : providers) {
            if (LOCATION_METHOD_FINE.equals(provider) && !hasFinePermission) {
                reportDefaultIssue(context, method, FINE_LOCATION_PERMISSION);
                break;
            }

            if (LOCATION_METHOD_COARSE.equals(provider) && !hasCoarsePermission) {
                reportDefaultIssue(context, method, COARSE_LOCATION_PERMISSION);
                break;
            }

            if (LOCATION_METHOD_PASSIVE.equals(provider) && !hasCoarsePermission) {
                reportDefaultIssue(context, method, COARSE_LOCATION_PERMISSION);
                break;
            }
        }
    }

    private void handleProximityMethods(JavaContext context, MethodInvocation method) {
        // starting with api level 17, fine location is required
        if (getTargetSdk(context) >= API_LEVEL_JELLY_BEAN_MR1 && !hasFinePermission) {
            context.report(ISSUE, method, context.getLocation(method),
                    String.format("Call to `%s` requires `%s` (starting with api %d)", method.astName().astValue(),
                            FINE_LOCATION_PERMISSION, API_LEVEL_JELLY_BEAN_MR1));
        }

        if (getTargetSdk(context) < API_LEVEL_JELLY_BEAN_MR1 && !hasFinePermission && !hasCoarsePermission) {
            reportDefaultIssue(context, method, COARSE_LOCATION_PERMISSION);
        }
    }

    protected int getTargetSdk(Context context) {
        if (targetApiLevel == -1) {
            AndroidVersion targetSdkVersion = context.getMainProject().getTargetSdkVersion();
            targetApiLevel = targetSdkVersion.getFeatureLevel();
        }

        return targetApiLevel;
    }
}
