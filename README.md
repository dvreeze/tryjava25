
# Trying out Java 25

Trying out Java 25 JDK APIs (not necessarily introduced in Java 25), such as Stream Gatherers.

## FFM

Some programs experimented with the FFM API (Java 22+). For some of these programs the "jextract" tool was used.
For regular Linux platforms: download "jextract", and extract (with "tar xvfz") the "gz" file under the jextract folder.

To generate native bindings in Java for stdio.h, use the following command or something similar (for Linux):

```bash
# From the root of the project, assuming jextract has been installed as described above
./jextract/jextract-25/bin/jextract \
  --include-dir /usr/include \
  --output src/main/java \
  --target-package eu.cdevreeze.tryjava25.generated \
  "<stdio.h>"
```

For more information on how to use "jextract", see [jextract](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md). We can also find many examples there, to
get a better feel for the "jextract" tool.

Other interesting links on "jextract" are [dev.java on jextract](https://dev.java/learn/jvm/tools/complementary/jextract/)
and [jdk.java.net/jextract](https://jdk.java.net/jextract/).
