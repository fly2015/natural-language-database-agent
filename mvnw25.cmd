@echo off
set "JAVA_HOME=D:\Software Engineer\DevTools\jdk-25.0.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0mvnw.cmd" %*
