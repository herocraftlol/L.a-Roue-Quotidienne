#!/bin/bash
# Maven Wrapper script

set -e

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Download Maven if not in PATH
MAVEN_PARENT="$HOME/.m2/wrapper/dists"
MAVEN_DIR="apache-maven-3.9.6"
if [ ! -d "$MAVEN_PARENT/$MAVEN_DIR" ]; then
    mkdir -p "$MAVEN_PARENT"
    echo "Downloading Maven..."
    curl -sL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip" -o /tmp/maven.zip
    python3 -c "import zipfile; zipfile.ZipFile('/tmp/maven.zip', 'r').extractall('$MAVEN_PARENT')"
    rm /tmp/maven.zip
fi
MAVEN_HOME="$MAVEN_PARENT/$MAVEN_DIR"

export MAVEN_HOME
export PATH="$MAVEN_HOME/bin:$PATH"

exec "$MAVEN_HOME/bin/mvn" "$@"
