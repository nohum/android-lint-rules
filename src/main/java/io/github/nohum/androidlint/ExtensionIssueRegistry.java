package io.github.nohum.androidlint;

import java.util.Collections;
import java.util.List;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import io.github.nohum.androidlint.detectors.ResourceIdNamingConventionDetector;

public class ExtensionIssueRegistry extends IssueRegistry {

    public ExtensionIssueRegistry() {
    }

    @Override
    public List<Issue> getIssues() {
        return Collections.singletonList(
                ResourceIdNamingConventionDetector.ISSUE
        );
    }
}
