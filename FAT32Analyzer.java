/**
 * Analyzer and repair tool for FAT32 file system partitions
 *
 * Tasks:
 *		1) Read FAT32 partition into byte array (DONE)
 *		2) Begin at BPB and ensure it's not missing. Locate it via
 *         the jmpBoot signature:
 *		   	jmpBoot[0] = 0xEB
 *		   	jmpBoot[1] = ??
 *		   	jmpBoot[2] = 0x90
 **              OR
 *         	jmpBoot[0] = 0xE9
 *		   	jmpBoot[1] = ??
 *		   	jmpBoot[2] = ??
 *		3) If BPB is missing, locate the backup at sector 6. 
 *         Can ensure we found the backups jmpBoot signature specifically
 *		   by calculating the offset and ensuring it is at the start of
 *		   a sector (regardless of bytes per sector, since all options
 *         for bytes per sector are divisible by 512)
 *		4) Compare BPB to backup and repair BPB using backup if they don't
 *         match.
 *		5) Parse through intact BPB for information: 
 *			 a) bytes per sector
 *         	 b) sectors per cluster 
 *			 c) number of reserved sectors 
 *			 d) location of root cluster
 *		6) Locate root directory and search for the following illegal
 *         characters:
 			 a) 0x00 in first byte
 			 b) 0xE5 in first byte
 			 c) any characters less than 0x20 (except 0x05)
 			 d) 0x22, 0x2A, 0x2B, 0x2C, 0x2E, 0x2F, 0x3A, 0x3B, 0x3C,
 			    0x3D, 0x3E, 0x3F, 0x5B, 0x5C, 0x5D, 0x7C
 *		7) Replace illegal characters with legal ones
 *		8) (Optional) Ensure other fields with limited legal character
 *         choices (i.e. directory entry's file attribute type) adhere
 *         to their requirements
 *		9) (Optional) Ensure reserved bytes aren't overwritten
 *	   10) (Probably not) GUI for the tool via Java Swing
 * Usage:
 *     	java FAT32Analyzer path/to/fat32.dd
 *
 * @author Hannah Juraszek
 * @author Jordan Gillespie
 * @version 14 November 2019
 *
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FAT32Analyzer {

	public static void main(String[] args) throws IOException {
		
		File file;
		String fileNamePath;

		//Ensure proper usage
		if(args.length != 1) {
			System.out.println("Usage: java FAT32Analyzer path/to/file.dd");
			System.exit(1);
		}

		fileNamePath = args[0];
		file = new File(fileNamePath);

		//try{															//kept getting errors with the exeception handling
			byte[] fileContent = FAT32Analyzer.getFileBytes(file);
	//	} catch ( IOException ioe){
	//		ioe.printStackTrace();
	//	}	
			
		bpbEntry(fileContent);
	}

	/**
	 * Method to quickly turn a file into a byte array
	 * (Task 1)
	 * 
	 * @param file - the file to be converted to byte array
	 * @return the byte array of the file's contents
	 */
	public static byte[] getFileBytes(File file) throws IOException {
		
		return Files.readAllBytes(file.toPath());

	}

	/**
	*Method to see if BPB is Present or missing.
	* (Task 2)
	*PLEASE READ TO UNDERSTAND HOW IM GETTING LINE 102, i printed index 0-10 and i saw that the index 0 = -21 signed 2's complement which according to this website is converted to eb in hex, https://www.rapidtables.com/convert/number/hex-to-decimal.html
	*also I used the corrected version of fat32.dd file.  
	*@param fileContent - the byte array
	*@return bpbPresent - true will equal BPB is present otherwise, not present , if !bpbPresent can be difference between task 3 occuring or not 
	*/
	public static boolean bpbEntry(byte[] fileContent) {
		boolean bpbPresent = false; // if BPB present then set to true 
		int arrayLength = fileContent.length - 1, i = 0; // length and iterator
		
		while (i < arrayLength) {

			 byte entry = fileContent[i];
			 System.out.println(entry); //test, delete
			if( entry == -21){
				bpbPresent = true;
				break;  				// break out while cause we found what we need
			}
			i++;
		}


		System.out.println(arrayLength);// test, delete
		System.out.println(bpbPresent); //test , delete  
		return bpbPresent;

	}
}
