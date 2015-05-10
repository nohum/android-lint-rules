package io.github.nohum.androidlint.detectors;

import static com.android.SdkConstants.*;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enforce a single naming convention of all resource ids. This detector expects
 * all ids having a name like "another_id". Other forms like camel-case style ids
 * do emit a warning.
 */
public class ResourceIdNamingConventionDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ResourceIdNaming",
            "Naming of resource id is not following conventions",
            "Resource ids should typically follow the convention of being named in " +
                    "underscore_casing instead of e.g. camelCasing. This is to maintain " +
                    "consistency with the rest of the identifiers used by Android.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ResourceIdNamingConventionDetector.class, Scope.ALL_RESOURCES_SCOPE));

    // besides the listed below, there are further ignored elements (e.g. "style", "item"). we ignore them as
    // they use a completely different naming scheme
    private static final List<String> ALLOWED_NAMED_ELEMENTS = Arrays.asList(TAG_STRING_ARRAY,
            TAG_ARRAY, TAG_PLURALS, TAG_INTEGER_ARRAY, TAG_STRING, TAG_COLOR, TAG_DIMEN, "integer", "bool");

    private static final Pattern ALLOWED_NAMING_PATTERN = Pattern.compile("[a-z0-9_]*");

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.COLOR || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.MENU || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!context.isEnabled(ISSUE)) {
            return;
        }

        // true for layout elements, menu items, ...
        if (element.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID); // returns something like: @+id/string
            if (id != null && !id.isEmpty()) {
                if (id.startsWith(ANDROID_ID_PREFIX)) { // ignore system special ids
                    return;
                }

                String strippedId = LintUtils.stripIdPrefix(id); // stripIdPrefix strips the "@+id/" part
                if (!isFollowingNamingConvention(strippedId)) {
                    report(context, element, correctIdentifier(strippedId));
                }
            }
        }
        // ... however, value files use "name" instead
        else if (isValidValueElement(element) && element.hasAttribute(ATTR_NAME)) {
            String name = element.getAttribute(ATTR_NAME);

            if (name != null && !name.isEmpty() && !isFollowingNamingConvention(name)) {
                report(context, element, correctIdentifier(name));
            }
        }
    }

    private String correctIdentifier(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    private void report(XmlContext context, Element element, String correctlyNamed) {
        context.report(ISSUE, element, context.getLocation(element),
                String.format("Use underscore_casing for the identifier to maintain consistency (e.g. `%s`)",
                        correctlyNamed));
    }

    private boolean isValidValueElement(Element element) {
        return ALLOWED_NAMED_ELEMENTS.contains(element.getTagName());
    }

    private boolean isFollowingNamingConvention(String identifierName) {
        return ALLOWED_NAMING_PATTERN.matcher(identifierName).matches();
    }

}
