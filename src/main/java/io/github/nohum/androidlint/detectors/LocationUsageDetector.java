package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.detector.api.*;

/**
 * Detector that checks for Android Location API usages which are likely to fail without
 * the proper permissions. The Android manifest is scanned for the permissions. Hence, usages
 * of the APIs with correct permissions will not trigger any warnings.
 */
public class LocationUsageDetector extends Detector implements Detector.XmlScanner, Detector.ClassScanner {

    public static final Issue ISSUE = Issue.create(
            "LocationUsageWithoutPermission",
            "Location data is gathered without proper permission",
            "When requesting location data, the proper permission (`ACCESS_COARSE_LOCATION`, " +
            "`ACCESS_FINE_LOCATION ` or both) must be requested in the manifest. Otherwise the " +
            "application will crash at the location data request.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(LocationUsageDetector.class, Scope.CLASS_AND_ALL_RESOURCE_FILES));

}
