@echo off
cd /d "%~dp0"
echo ===== CampusConnect =====
echo Updating suggestions...
python database\similarity.py
echo Compiling Java...
javac -d bin -cp "lib/*" src\campus\*.java
if errorlevel 1 (
  echo.
  echo Compile error. Fix it and run start.bat again.
  pause
  exit /b 1
)
echo Opening browser in 3 seconds...
start "" cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:8080"
echo Starting server. Keep this window open. Close it to stop the server.
java -cp "bin;lib/*" campus.Server
pause
