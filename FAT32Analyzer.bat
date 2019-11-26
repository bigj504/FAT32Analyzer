@echo off
echo What is the file image path?
set /p Name=Enter your file image path:;
echo Your file image path is %Name% & pause
javac FAT32Analyzer.java
java FAT32Analyzer %Name%
pause