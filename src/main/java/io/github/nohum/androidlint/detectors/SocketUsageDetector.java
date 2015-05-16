package io.github.nohum.androidlint.detectors;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

/**
 * Detector that checks for usage of an internet socket or other common libraries that connect
 * to the internet. If the app does not possess the right to access the internet, an issue will
 * be reported.
 */
public class SocketUsageDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "SocketUsageWithoutPermission",
            "Internet is accesses without proper permission",
            "When accessing the internet using a socket or some other available methods, " +
            "the `android.permission.INTERNET` permission must be acquired in the manifest.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(SocketUsageDetector.class, EnumSet.of(Scope.MANIFEST)));

    /** Permission name of INTERNET permission */
    public static final String INTERNET_PERMISSION = "android.permission.INTERNET";

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

}
