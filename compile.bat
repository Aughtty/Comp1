@echo off
REM Compile all java files into the bin directory
if not exist bin mkdir bin
javac -d bin -sourcepath src src\com\bigcomp\accesscontrol\Main.java
if %ERRORLEVEL% neq 0 (
  echo Compilation failed.
  pause
) else (
  echo Compiled successfully.
)
pause