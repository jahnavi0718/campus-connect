@echo off
cd /d "%~dp0"
echo ===== CampusConnect =====
python database\similarity.py
javac -d bin -cp "lib/*" src\campus\*.java
if errorlevel 1 (echo Compile error & pause & exit /b 1)
start "" cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:8080"
java -cp "bin;lib/*" campus.Server
pause