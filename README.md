Android lint rules
=======================================

Adds a few rules 

After checkout, copy `local.properties.dist` to `local.properties`.
The path supplied in that file should be correct for lint to find the custom rules.

Then run:

```
gradle assemble uploadArchives
```

Upon next run of lint in any Android project with `gradle lint` the supplied rules should be considered too.

Some detectors and classes provide a flag (`private static final boolean DEBUG = false;` somewhere at the beginning) to
switch on debug logging. After switching on this functionality (and rebuilding of course), the resulting project must be
run in non-daemon mode as otherwise the logs are not shown:

```
gradle --no-daemon lint
```
