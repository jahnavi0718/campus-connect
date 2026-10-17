@echo off
REM run.bat : compiles the Java code and runs it
cd /d "%~dp0"
if not exist bin mkdir bin
javac -cp "lib/*" -d bin src\campus\*.java
if errorlevel 1 (
    echo Compile failed. Read the error above.
    pause
    exit /b 1
)
java --enable-native-access=ALL-UNNAMED -cp "bin;lib/*" campus.Main
