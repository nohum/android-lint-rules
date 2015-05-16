package io.github.nohum.androidlint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import io.github.nohum.androidlint.detectors.LocationUsageDetector;
import io.github.nohum.androidlint.detectors.ReceiverStartActivityFlagsDetector;
import io.github.nohum.androidlint.detectors.ResourceIdNamingConventionDetector;
import io.github.nohum.androidlint.detectors.SocketUsageDetector;

/**
 * Entry point for this Android lint extension.
 */
public class ExtensionIssueRegistry extends IssueRegistry {

    public ExtensionIssueRegistry() {
    }

    @Override
    public List<Issue> getIssues() {
        List<Issue> issues = new ArrayList<Issue>();
        issues.add(ResourceIdNamingConventionDetector.ISSUE);
        issues.add(ReceiverStartActivityFlagsDetector.ISSUE);
        issues.add(LocationUsageDetector.ISSUE);
        issues.add(SocketUsageDetector.ISSUE);

        return Collections.unmodifiableList(issues);
    }
}
