@echo off
set /p Name=Enter your path to your FAT32 image:
set /p End=Enter your path for repaired file image include .dd extention at the end:
echo Your file image path is %Name% 
echo Your repaired file image path is %End% 
echo Press any key to begin. & pause
javac FAT32Analyzer.java
java FAT32Analyzer %Name% %End%
pause