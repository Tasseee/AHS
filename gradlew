#!/bin/sh
# HugoSniper - schlankes Gradle-Wrapper-Startskript.
# Laedt beim ersten Start das offizielle gradle-wrapper.jar (Internet noetig), danach arbeitet es wie der Standard-Wrapper.
# Die Gradle-Version steht in gradle/wrapper/gradle-wrapper.properties.
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v9.4.1/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "FEHLER: Java wurde nicht gefunden. Bitte JDK 21 installieren und JAVA_HOME setzen." >&2
    exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar fehlt - lade es von $WRAPPER_URL"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
    else
        echo "FEHLER: weder curl noch wget gefunden. Lade die Datei manuell nach gradle/wrapper/gradle-wrapper.jar" >&2
        echo "        oder fuehre mit installiertem Gradle aus:  gradle wrapper --gradle-version 9.4.1" >&2
        exit 1
    fi
fi

exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
    -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
