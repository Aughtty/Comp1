@echo off
REM Run the application. Ensure JDBC driver is on classpath (you said it's configured)
set CLASSPATH=bin;.;lib\*;.
java -cp %CLASSPATH% com.bigcomp.accesscontrol.Main
pause