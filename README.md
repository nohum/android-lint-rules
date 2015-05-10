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