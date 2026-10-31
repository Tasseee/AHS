@echo off
rem HugoSniper - schlankes Gradle-Wrapper-Startskript (Windows).
rem Laedt beim ersten Start das offizielle gradle-wrapper.jar (Internet noetig).
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.4.1/gradle/wrapper/gradle-wrapper.jar"

if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=java.exe"
)
"%JAVACMD%" -version >nul 2>&1
if errorlevel 1 (
    echo FEHLER: Java wurde nicht gefunden. Bitte JDK 21 installieren und JAVA_HOME setzen. 1>&2
    exit /b 1
)

if not exist "%WRAPPER_JAR%" (
    echo gradle-wrapper.jar fehlt - lade es von %WRAPPER_URL%
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
    if errorlevel 1 (
        echo FEHLER: Download fehlgeschlagen. Alternativ mit installiertem Gradle: gradle wrapper --gradle-version 9.4.1 1>&2
        exit /b 1
    )
)

"%JAVACMD%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
