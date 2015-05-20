package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

import static com.android.SdkConstants.*;

/**
 * Detector that checks for Android Location API usages which are likely to fail without
 * the proper permissions. The Android manifest is scanned for the permissions. Hence, usages
 * of the APIs with correct permissions will not trigger any warnings.
 */
public class LocationUsageDetectorBytecode extends Detector implements Detector.XmlScanner, Detector.ClassScanner {

    public static final Issue ISSUE = Issue.create(
            "LocationUsageWithoutPermissionBytecode",
            "Location data is gathered without declared manifest permission",
            "When requesting location data, the proper permission (`ACCESS_COARSE_LOCATION` or " +
            "`ACCESS_FINE_LOCATION`) must be requested in the manifest. Otherwise the " +
            "Android framework will throw a `SecurityException` when requesting location data." +
            "\n" +
            "This detector is not checking for permissions or calls to the mock location methods!",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(LocationUsageDetectorBytecode.class, EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)));

    /** Permission name of coarse location permission */
    public static final String COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION";

    /** Permission name of fine location permission */
    public static final String FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION";

    private static final String CLASS_LOCATION_MANAGER = "android/location/LocationManager";

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
    public List<String> getApplicableCallNames() {
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
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode, @NonNull MethodNode method,
                          @NonNull MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return;
        }

        if (!call.owner.equals(CLASS_LOCATION_MANAGER)) {
            return;
        }

        // used to print class byte code representations
        // easiest way to get that to work: include in project build.gradle classpath with: classpath 'org.ow2.asm:asm-debug-all:5.0.3'
        // classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));

        String calledMethod = call.name;

        // some calls always require fine permission
        if (!hasFinePermission && METHOD_ADD_GPS_LISTENER.equals(calledMethod) || METHOD_ADD_NMEA_LISTENER.equals(calledMethod)) {
            reportDefault(context, method, call, FINE_LOCATION_PERMISSION);
        }
        // the semantics of what these methods accept and when they throw an exception has been changed at some point
        else if (METHOD_ADD_PROXIMITRY_ALERT.equals(calledMethod) || METHOD_REMOVE_PROXIMITRY_ALERT.equals(calledMethod)) {
            handleProximityMethods(context, method, call);
        }
        // ... also semantic change of when a exception is thrown
        else if (METHOD_IS_PROVIDER_ENABLED.equals(calledMethod) && getTargetSdk(context) < API_LEVEL_LOLLIPOP) {
            handleProviderEnabled(context, method, call);
        }
        // these calls depend on the used location provider
        else if (METHOD_REQUEST_LOCATION_UPDATES.equals(calledMethod) || METHOD_REQUEST_SINGLE_UPDATE.equals(calledMethod)) {
            handleRequestMethods(context, method, call);
        }
    }

    private void reportDefault(ClassContext context, MethodNode method, MethodInsnNode call, String requiredPermission) {
        context.report(ISSUE, method, call, context.getLocation(call),
                String.format("Call to `%s` requires `%s`", call.name, requiredPermission));
    }

    private void handleRequestMethods(ClassContext context, MethodNode method, MethodInsnNode call) {
        // to make matters worse, there are many overloaded versions of the methods at hand
        // we only look at the string versions here.

        // do a heuristic search for the expected location provider strings
        AbstractInsnNode prev = call;
        String usedLocationProvider = null;
        while ((prev = LintUtils.getPrevInstruction(prev)) != null) {
            if (prev instanceof LdcInsnNode && ((LdcInsnNode) prev).cst instanceof String) {
                String tempData = (String) ((LdcInsnNode) prev).cst;

                if (LOCATION_METHOD_FINE.equals(tempData) || LOCATION_METHOD_COARSE.equals(tempData)
                        || LOCATION_METHOD_PASSIVE.equals(tempData)) {
                    usedLocationProvider = tempData;
                    break;
                }
            }
        }

        if (!hasFinePermission && LOCATION_METHOD_FINE.equals(usedLocationProvider)) {
            reportDefault(context, method, call, FINE_LOCATION_PERMISSION);
        } else if (!hasCoarsePermission && (LOCATION_METHOD_COARSE.equals(usedLocationProvider)
                || LOCATION_METHOD_PASSIVE.equals(usedLocationProvider))) {
            reportDefault(context, method, call, COARSE_LOCATION_PERMISSION);
        }
    }

    private void handleProviderEnabled(ClassContext context, MethodNode method, MethodInsnNode call) {
        // previous to the INVOKEVIRTUAL call, there is an LDC call putting the provider on the stack, e.g.:
        // HOWEVER, only if that value is not resolved using e.g. a separate method!!
        //  ALOAD 2
        //  LDC "gps"
        //  INVOKEVIRTUAL android/location/LocationManager.isProviderEnabled (Ljava/lang/String;)Z
        //  POP

        AbstractInsnNode prev = LintUtils.getPrevInstruction(call);
        if (!(prev instanceof LdcInsnNode)) {
            return; // if not, there is something wrong here...
        }

        // the provider should actually be a string
        if (!(((LdcInsnNode) prev).cst instanceof String)) {
            return;
        }

        String provider = (String) ((LdcInsnNode) prev).cst;
        if (LOCATION_METHOD_FINE.equals(provider) && !hasFinePermission) {
            reportDefault(context, method, call, FINE_LOCATION_PERMISSION);
        }

        if (LOCATION_METHOD_COARSE.equals(provider) && !hasCoarsePermission) {
            reportDefault(context, method, call, COARSE_LOCATION_PERMISSION);
        }

        if (LOCATION_METHOD_PASSIVE.equals(provider) && !hasCoarsePermission) {
            reportDefault(context, method, call, COARSE_LOCATION_PERMISSION);
        }
    }

    private void handleProximityMethods(ClassContext context, MethodNode method, MethodInsnNode call) {
        // starting with level 17, fine location is required
        if (getTargetSdk(context) >= API_LEVEL_JELLY_BEAN_MR1 && !hasFinePermission) {
            context.report(ISSUE, method, call, context.getLocation(call),
                    String.format("Call to `%s` requires `%s` (starting with api %d)", call.name,
                            FINE_LOCATION_PERMISSION, API_LEVEL_JELLY_BEAN_MR1));
        }

        if (getTargetSdk(context) < API_LEVEL_JELLY_BEAN_MR1 && !hasFinePermission && !hasCoarsePermission) {
            reportDefault(context, method, call, COARSE_LOCATION_PERMISSION);
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
