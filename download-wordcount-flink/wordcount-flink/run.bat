@echo off
set HADOOP_HOME=%~dp0hadoop.win
set HADOOP_BIN_PATH=%HADOOP_HOME%\bin
set PATH=%PATH%;%HADOOP_BIN_PATH%;
call .\gradlew.bat run -Dhadoop.home.dir=%HADOOP_HOME% -Djava.library.path=%HADOOP_BIN_PATH% --args="%*"
