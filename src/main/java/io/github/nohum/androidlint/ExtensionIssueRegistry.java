package io.github.nohum.androidlint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import io.github.nohum.androidlint.detectors.ReceiverStartActivityFlagsDetector;
import io.github.nohum.androidlint.detectors.ResourceIdNamingConventionDetector;

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

        return Collections.unmodifiableList(issues);
    }
}
