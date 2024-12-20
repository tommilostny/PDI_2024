#!/bin/sh

# url for okio-jvm-*.jar in module com.squareup.okio:okio:* goes to different filename, i.e., okio-*.jar
# that is defined in the module file; see https://repo1.maven.org/maven2/com/squareup/okio/okio/*/okio-*.module
# the verification-metadata parser does not implement such renaming; see https://github.com/raphiz/buildGradleApplication/blob/main/buildGradleApplication/parse.py
# the parser will be executed on the patched verification-metadata while the original one will be used by gradle for the build

exec sed 's|artifact name="okio-jvm-\([0-9.]\+\)\.jar"|artifact name="okio-\1.jar"|' ./verification-metadata.xml >./patched-verification-metadata.xml
