AUTHORS:

	Hannah Juraszek
	Jordan Gillespie


VERSION: 

	26 November 2019


DESCRIPTION:

	A digital forensics tool that parses through an image of a FAT32 file system and can
	perform the following tasks: 
		1) restore a missing or modified boot sector using the backup
		2) parse through the BPB for information about the file system
		3) locate and repair illegal characters in the root directory
		4) report if the attribute type of a directory entry is invalid
		5) repair reserved bytes that have been overwritten


INSTRUCTIONS:

	 To start the FAT32Analyzer, double click the FAT32Analyzer.bat file.
	This will bring up the command line asking for the path of the fat 32 
	image to parse, if the fat32 image is not in the same file as the .bat
	file , an absolute path is neeeded. 
	  Next after the path is confirmed on screen hit enter again and the tool 
	will begin.The repaired image will be printed to output.dd , however if 
	you would like to specify a different file name for the repaired image, line
	115 can be changed to your intended location. 


FILES INCLUDED:

	FAT32Analyzer.java
	FAT32Analyzer.bat