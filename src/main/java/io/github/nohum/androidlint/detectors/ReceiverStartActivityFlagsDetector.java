package io.github.nohum.androidlint.detectors;

import com.android.tools.lint.detector.api.*;

/**
 * This detector checks if activities get started with the right flags when being started from broadcast receivers.
 *
 * When starting an Activity from a BroadcastReceiver, this fails unless the Activity is started with the
 * FLAG_ACTIVITY_NEW_TASK flag.
 */
public class ReceiverStartActivityFlagsDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
            "ReceiverStartActivityFlagsMissing",
            "Naming of resource id is not following conventions",
            "Resource ids should typically follow the convention of being named in " +
                    "underscore_casing instead of e.g. camelCasing. This is to maintain " +
                    "consistency with the rest of the identifiers used by Android.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(ResourceIdNamingConventionDetector.class, Scope.JAVA_FILE_SCOPE));



}
