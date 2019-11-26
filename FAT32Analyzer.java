/**
 * Analyzer and repair tool for FAT32 file system partitions
 *
 * Tasks:
 *		1) Read FAT32 partition into byte array 							(DONE)
 *		2) Begin at BPB and ensure it's not missing. Locate it via 			(DONE)
 *         the jmpBoot signature:
 *		   	jmpBoot[0] = 0xEB
 *		   	jmpBoot[1] = ??
 *		   	jmpBoot[2] = 0x90
 *              OR
 *         	jmpBoot[0] = 0xE9
 *		   	jmpBoot[1] = ??
 *		   	jmpBoot[2] = ??
 *		3) If BPB is missing, locate the backup at sector 6. 				(DONE)
 *         Can ensure we found the backups jmpBoot signature specifically
 *		   by calculating the offset and ensuring it is at the start of
 *		   a sector (regardless of bytes per sector, since all options
 *         for bytes per sector are divisible by 512)
 *		4) Compare BPB to backup and repair BPB using backup if they don't 	(DONE)
 *         match.
 *		5) Parse through intact BPB for information: 						(DONE)
 *			 a) bytes per sector
 *         	 b) sectors per cluster 
 *			 c) number of reserved sectors 
 *			 d) location of root cluster
 *		6) Locate root directory and search for the following illegal 		(DONE)
 *         characters:
 *			 a) 0x00 in first byte
 *			 b) 0xE5 in first byte
 *			 c) any characters less than 0x20 (except 0x05)
 *			 d) 0x22, 0x2A, 0x2B, 0x2C, 0x2E, 0x2F, 0x3A, 0x3B, 0x3C,
 *			    0x3D, 0x3E, 0x3F, 0x5B, 0x5C, 0x5D, 0x7C
 *		7) Replace illegal characters with legal ones 						(DONE)
 *		8) Ensure other fields with limited legal character 				(DONE)
 *         choices (i.e. directory entry's file attribute type) adhere
 *         to their requirements. Report to the user if not.
 *		9) Ensure reserved bytes aren't overwritten. Repair if so. 			(DONE)
 * 
 * Usage:
 *     	java FAT32Analyzer path/to/fat32.dd
 *
 * @author Hannah Juraszek
 * @author Jordan Gillespie
 * @version 26 November 2019
 *
 */

import java.io.File;
import java.io.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.*;


public class FAT32Analyzer {

	private static int bytesPerSector;
	private static int sectorsPerCluster;
	private static int reservedSectorCount;
	private static int numFATs;
	private static int sizeOfFAT;
	private static int rootCluster;
	private static byte[] fileContent;

	public static void main(String[] args) throws IOException {
		
		File file;
		String inputFileNamePath;
		String outputFileNamePath;
		boolean bpbPresent;
		boolean bpbBackupPresent;

		//Ensure proper usage
		if(args.length != 2) {
			System.out.println("Usage: java FAT32Analyzer path/to/input/file.dd path/to/output/file.dd");
			System.exit(1);
		}

		//Store the input file name path
		inputFileNamePath = args[0];

		//Store the output file name path
		outputFileNamePath = args[1];

		//Instantiate the file
		file = new File(inputFileNamePath);

		try{															
			fileContent = FAT32Analyzer.getFileBytes(file);
			System.out.println("Analyzing boot sector...");
			bpbPresent = FAT32Analyzer.bpbEntry();
			if(!bpbPresent) {
				System.out.println("Boot sector is missing and/or modified. Checking backup...");
				bpbBackupPresent = FAT32Analyzer.bpbBackup();

				if(!bpbBackupPresent) {
					System.out.println("Boot sector and backup boot sector are missing and/or corrupted beyond repair.");
					System.exit(1);
				}
				else {
					System.out.println("Backup boot sector located.");
					System.out.println("Boot sector repaired using the backup.");
				}
			}
			else {
				System.out.println("Boot sector located.");
			}
			System.out.println("------------------------------------");
			System.out.println("Bytes per sector: " + bytesPerSector);
			System.out.println("Sectors per cluster: " + sectorsPerCluster);
			System.out.println("Number of reserved sectors: " + reservedSectorCount);
			System.out.println("Number of FATs: " + numFATs);
			System.out.println("Size of FATs (in sectors): " + sizeOfFAT);
			System.out.println("------------------------------------");
		  	FAT32Analyzer.analyzeRoot();
		  	System.out.println("All done.");
		  	FileOutputStream fos = new FileOutputStream(outputFileNamePath);
		  	fos.write(fileContent);	
		
		} catch ( IOException ioe){
			ioe.printStackTrace();
		}
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
	 * Method to see if BPB is present and intact or missing/corrupted/modified.
	 * (Task 2)
	 *
	 * @return false if the BPB contains any errors/modifications that will cause corruption, true otherwise 
	 */
	public static boolean bpbEntry() {
		return FAT32Analyzer.parseBPB(0);
	}


	/**
	 * Method to locate backup BPB, ensure backup isn't corrupted/modified,
	 * and repair the sector 0 BPB with the backup.
	 * (Task 3 & 4)
	 *
	 * @return false if the backup is missing, corrupted, or modified, true otherwise
	 */
	public static boolean bpbBackup(){
		/*Locate the backup*/
		//Whether we were successful locating the backup or not, initialized to false
		boolean foundBackup = false;
		//int to store the offset of the backup BPB
		int backupOffset = 0;
		//For every byte in the file,
		for(int i = 0; i < fileContent.length - 1; i++) {
			//Store that byte
			byte thisByte = fileContent[i];
			//If the byte is 0xEB (-21)
			if(thisByte == -21) {
				//Check to see if the byte at index + 2 is 0x90 (-112) and check if
				//the index we found this byte at is the start of a sector
				//Also checks for the end signature word (0x55 at offset 510 and 
				//0xAA at offset 511)
				if(fileContent[i + 2] == -112 && fileContent[i + 510] == 85 && fileContent[i + 511] == -86 && i % 512 == 0) {
					//If so, we found the backup, so store the offset
					backupOffset = i;
					//And set foundBackup to true to break us out of the while loop
					foundBackup = true;
					//Break out of the for loop
					break;
				}
			}
			//Otherwise if the byte is 0xE9 (-23)
			else if(thisByte == -23) {
				//Check to see if the index we found this byte at is the start of a sector
				//and contains the end signature word
				if(fileContent[i + 510] == 85 && fileContent[i + 511] == -86 && i % 512 == 0) {
					//If so, we found the backup, so store the offset and break
					backupOffset = i;
					foundBackup = true;
					break;
				}
			}
		}

		//If we iterated through the file and never found the backup, return false
		if(foundBackup = false)
			return false;

		//Parse through the backup by passing in the backupOffset to parseBPB method
		boolean backupIntact = FAT32Analyzer.parseBPB(backupOffset);

		//If the backup is not intact (parseBPB returned false), return false
		if(backupIntact == false)
			return false;

		/*Repair the BPB using this backup*/
		//MAY BE INCORRECT, THIS IS A DRAFT
		//NEED TO VERIFY CORRECTNESS
		//Create a new byte array of size 512 (number of bytes in BPB)
		byte[] backup = new byte[512];
		//Store the backup BPB from fileContent in the backup array
		int j = backupOffset;
		for(int i = 0; i < 512;i++) {
			//j is the index for fileContent
			
			backup[i] = fileContent[j];
			
			j++;
			
		}
		//Index for fileContent
		int index = 0;
		//For every byte in the backup
		for(byte backupByte : backup) {
			//Write that byte to its respective location in the BPB of the fileContent
			fileContent[index] = backupByte;

			
			//Increment fileContent index
			index++;
		}
		

	
		//Finally, return true to indicate success
		return true;
	}

	/**
	 * Helper method to parse BPB structure, both sector 0 and backup. Also does
	 * the storing of important information parsed from BPB.
	 *
	 * @param startingOffset the offset the BPB block starts at
	 * @return false if the BPB is modified/corrupted/missing, true otherwise
	 */
	public static boolean parseBPB(int startingOffset) {
		byte jmpBootByteOne = fileContent[startingOffset];
		byte jmpBootByteThree = fileContent[startingOffset + 2];
		byte bytesPerSectorByteOne = fileContent[startingOffset + 11];
		byte bytesPerSectorByteTwo = fileContent[startingOffset + 12];
		byte secPerClus = fileContent[startingOffset + 13];
		byte rsvdSecCntByteOne = fileContent[startingOffset + 14];
		byte rsvdSecCntByteTwo = fileContent[startingOffset + 15];
		byte numFAT = fileContent[startingOffset + 16];
		byte rootEntCntByteOne = fileContent[startingOffset + 17];
		byte rootEntCntByteTwo = fileContent[startingOffset + 18];
		byte totSec16ByteOne = fileContent[startingOffset + 19];
		byte totSec16ByteTwo = fileContent[startingOffset + 20];
		byte media = fileContent[startingOffset + 21];
		byte fatSz16ByteOne = fileContent[startingOffset + 22];
		byte fatSz16ByteTwo = fileContent[startingOffset + 23];
		byte totSec32ByteOne = fileContent[startingOffset + 34];
		byte totSec32ByteTwo = fileContent[startingOffset + 35];
		byte fsVerByteOne = fileContent[startingOffset + 42];
		byte fsVerByteTwo = fileContent[startingOffset + 43];
		byte reserved = fileContent[startingOffset + 52];
		byte drvNum = fileContent[startingOffset + 64];
		byte reservedOne = fileContent[startingOffset + 65];
		byte endSignatureByteOne = fileContent[startingOffset + 510];
		byte endSignatureByteTwo = fileContent[startingOffset + 511];
			
		// Oxeb = -21, 0x90 = -112, 0xe9 = -23
		//If the first byte is 0xeb 
		if(jmpBootByteOne == -21){
			//Make sure the third byte is 0x90
			if(jmpBootByteThree != -112)
			{
				//If it's not, BPB is corrupted, so return false
				return false;
			}
		}
		//Otherwise if the first byte isn't 0xeb or 0xe9
		else if(jmpBootByteOne != -23) {
			//BPB is corrupted, so return false
			return false;
		}
		//If bytes per sector isn't one of the allowed values, return false
		//512, 1024, 2048, 4096
		//512 = 200, 1024 = 400, 2048 = 800, 4096 = 1000
		if(bytesPerSectorByteOne != 00)
		{
			if(bytesPerSectorByteTwo != 2 && bytesPerSectorByteTwo != 4 && 
				bytesPerSectorByteTwo != 8 && bytesPerSectorByteTwo != 16) {
				return false;
			}
		}

		//If sectors per cluster isn't one of the allowed values, return false
		//Allowed values: 1, 2, 4, 8, 16, 32, 64, 128
		//128 = -128
		if(secPerClus != 1 && secPerClus != 2 && secPerClus != 4 &&
			secPerClus != 8 && secPerClus != 16 && secPerClus != 32 &&
			secPerClus != 64 && secPerClus != -128) {
			return false;
		}

		//If reserved sector count is 0, return false
		if(rsvdSecCntByteOne == 0) {
			if(rsvdSecCntByteTwo == 0) {
				return false;
			}
		}

		//If NumFATs isn't 2 or 1, return false
		if(numFAT != 2 && numFAT != 1) {
			return false;
		}

		//If rootEntCnt isn't 0, return false
		if(rootEntCntByteOne != 0) {
			return false;
		}
		if(rootEntCntByteTwo != 0) {
			return false;
		}

		//If totSec16 isn't 0, return false
		if(totSec16ByteOne != 0) {
			return false;
		}
		if(totSec32ByteTwo != 0) {
			return false;
		}

		//If media isn't one of the allowed values, return false
		//Allowed values: 0xF0, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
		//F0 = -16, F8 = -8, F9 = -7, FA = -6, FB = -5, FC = -4, FD = -3, FE = -2, FF = -1
		if(media != -16 && media != -8 && media != -7 && media != -6 && media != -5 &&
			media != -4 && media != -3 && media != -2 && media != -1) {
			return false;
		}

		//If fatSz16 isn't 0, return false
		if(fatSz16ByteOne != 0)
			return false;
		if(fatSz16ByteTwo != 0)
			return false;

		//If totSec32 is 0, return false
		if(totSec32ByteOne == 0)
		{
			if(totSec32ByteTwo == 0) {
				return false;
			}
		}

		//If fsVer isn't 0, return false
		if(fsVerByteOne != 0)
			return false;
		if(fsVerByteTwo != 0)
			return false;

		//If reserved isn't 0, return false
		if(reserved != 0)
			return false;

		//If drvNum isn't 0x80 or 0, return false
		if(drvNum != -128 && drvNum != 0)
			return false;

		//If reservedOne isn't 0, return false
		if(reservedOne != 0)
			return false;

		//If end signature isn't 0x55 0xAA, return false
		//0x55 = 85, 0xAA = -86
		if(endSignatureByteOne != 85)
			return false;
		if(endSignatureByteTwo != -86)
			return false;

		//If we made it here, it passed all the previous tests.
		/*Determine the bytes per sector*/
		if(bytesPerSectorByteTwo == 2)
			bytesPerSector = 512;
		else if(bytesPerSectorByteTwo == 4)
			bytesPerSector = 1024;
		else if(bytesPerSectorByteTwo == 8)
			bytesPerSector = 2048;
		else if(bytesPerSectorByteTwo == 16)
			bytesPerSector = 4096;

		/*Determine the sectors per cluster*/
		if(secPerClus == -128)
			sectorsPerCluster = 128;
		else
			sectorsPerCluster = secPerClus;

		/*Determine the number of reserved sectors*/
		if(rsvdSecCntByteTwo == 0)
			reservedSectorCount = rsvdSecCntByteOne;
		else {
			reservedSectorCount = (rsvdSecCntByteTwo << 8) + rsvdSecCntByteOne;
		}

		/*Determine the number of FATs*/
		numFATs = numFAT;

		/*Determine the number of sectors per FAT*/
		//Create a new byte array to hold the 4 bytes of the sizeOfFAT
		byte[] temp = new byte[4];
		//Add the 4 bytes to the array
		temp[0] = fileContent[startingOffset + 36];
		temp[1] = fileContent[startingOffset + 37];
		temp[2] = fileContent[startingOffset + 38];
		temp[3] = fileContent[startingOffset + 39];
		//Create a ByteBuffer wrapped around the array
		ByteBuffer bb = ByteBuffer.wrap(temp);
		//Change the ByteBuffer to little endian
		bb.order(ByteOrder.LITTLE_ENDIAN);
		//Convert it to an int and store
		sizeOfFAT = bb.getInt();

		/*Determine location of root cluster*/
		//If the 3 bytes following the least significant byte are 0
		if(fileContent[startingOffset + 45] == 0 && fileContent[startingOffset + 46] == 0 && fileContent[startingOffset + 47] == 0)
			//Then the rootCluster field is just in the first byte
			rootCluster = fileContent[startingOffset + 44];
		//Otherwise create a new byte array to hold the 4 bytes and convert using ByteBuffer
		else {
			byte[] arr = new byte[4];
			//Add the 4 bytes to the array
			arr[0] = fileContent[startingOffset + 44];
			arr[1] = fileContent[startingOffset + 45];
			arr[2] = fileContent[startingOffset + 46];
			arr[3] = fileContent[startingOffset + 47];
			//Override our existing byte buffer to be wrapped around this array
			bb = ByteBuffer.wrap(arr);
			//Just to make sure it's using little endian ordering
			bb.order(ByteOrder.LITTLE_ENDIAN);
			//Convert it to an int and store
			rootCluster = bb.getInt();
		}

		//Finally, return true
		return true;
	}

	/**
	 * Method to locate and analyze the root directory, searching for and repairing
	 * illegal characters.
	 * (Task 6, 7, 8, & 9)
	 *
	 * Illegal characters in directory entry file name:
 	 *		 a) 0x00 in first byte
 	 *		 b) 0xE5 in first byte
 	 *       c) 0x20 in first byte
 	 *		 d) any characters less than 0x20 (except 0x05)
 	 *		 e) 0x22, 0x2A, 0x2B, 0x2C, 0x2E, 0x2F, 0x3A, 0x3B, 0x3C,
 	 *		    0x3D, 0x3E, 0x3F, 0x5B, 0x5C, 0x5D, 0x7C
	 */
	public static void analyzeRoot() {
		/*Locate the root directory*/
		//Determine the number of sectors before the root by calculating the number
		//of sectors taken up by the FATs and adding the reserved sectors
		int sectorsBeforeRoot = numFATs * sizeOfFAT + reservedSectorCount;
		
		//Calculate the offset of the root directory start
		int offsetOfRootStart = sectorsBeforeRoot * bytesPerSector;
		int currentOffset = offsetOfRootStart;
		
		//The number of bytes in the file name field of the directory entry
		int numBytes = 11;
		//Store a legal byte to replace illegal characters with
		byte legalByte = 48;
		//Boolean loop control variable
		boolean done = false;

		//While we haven't reached the end of the root directory (indicated by entries
		//of 0's)
		while(done == false) {
			byte firstByte = fileContent[currentOffset];
			byte entryAttribute = fileContent[currentOffset + numBytes];
			byte entryReserved = fileContent[currentOffset+ 12];
			byte[] shortName = new byte[11];
			char[] shortNameString = new char[11];

			//If the attribute field doesn't equal one of the allowed forms
			if( (entryAttribute != 1) && 
				(entryAttribute != 2) && 
				(entryAttribute != 4) && 
				(entryAttribute != 8) && 
				(entryAttribute != 16) && 
				(entryAttribute != 32) && 
				(entryAttribute != 15) ) { 
				//Iterate through the shortname and store the name in a byte array
				int ji = 0;
				for(int j = currentOffset;j< (currentOffset+ numBytes); j++){
					shortName[ji] = fileContent[j];
					int name = shortName[ji];
					char Character = (char) name;
					shortNameString[ji]= Character;
					ji++;
				}
				String str = new String(shortNameString);
				System.out.println(str + " located at offset " + currentOffset + " has an invalid file attribute type.");
				System.out.println("This repair cannot be done automatically.");
			}

			//Check to see if rootDirectory reserved bits are what they are supposed to be, if not change and annouce.
			if(entryReserved != 0){
				fileContent[currentOffset + 12] =0;
				System.out.println("Repaired the directory entry's reserved byte at offset " + (currentOffset + 12) + ".");
			}

			
			//Check to see if first byte contains 0xE5 (-27) or 0x20 (32)
			if(firstByte == -27 || firstByte == 32) {
				//If so, replace it with a legal character
				fileContent[currentOffset] = legalByte;
				System.out.println("Replaced an illegal character in a directory entry's file name at offset " + currentOffset + ".");
			}
			//Otherwise check to see if the first byte contains 0
			else if(firstByte == 0) {
				//If so, check the next byte and the byte after that
				if(fileContent[currentOffset + 1] != 0 && fileContent[currentOffset + 2] != 0) {
					//If they aren't 0 also, change firstByte to non-zero
					fileContent[currentOffset] = legalByte;
					System.out.println("Replaced an illegal character in a directory entry's file name at offset " + currentOffset + ".");
				}
				//If they are 0,
				else {
					//Check the next entry for 0's also
					if(fileContent[currentOffset + 32] == 0 && fileContent[currentOffset + 33] == 0) {
						//If they are 0's also, we've reached the end of the directory
						done = true;
					}
				}
			}

			//Iterate through the file name and check for any illegal characters
			for(int i = currentOffset; i < currentOffset + numBytes; i++) {
				byte thisByte = fileContent[i];
				byte longNameReservedBitOne = fileContent[currentOffset+26];
				byte longNameReservedBitTwo = fileContent[currentOffset+27];

				//Check to see if we are in a long name entry, if so, break and increment 32 so we iterate through next line for the same.
				if(entryAttribute == 15){
					//Check to see if long name entry specific reserved bits are what they are supposed to be, if not change and announce.
					if((longNameReservedBitOne != 0) || (longNameReservedBitTwo != 0)){
						fileContent[currentOffset +26] = 0;
						fileContent[currentOffset +27] = 0;
						System.out.println("LDIR_FstClusLO reserved slot is invalid, changed to 00 at offset" +(currentOffset+26) +" and "+(currentOffset+27));
					}

					break;
				}

				//If this byte is equal to any of the illegal characters, replace it
				// Check to see if file name contains any character less than(0-4 U 6-25) 0x20 (32) except 0x05
				// lower case characters 0x61-0x7A = 97-122
				//0x22 = 34, 0x2A=42-0x2F=47, 0x58=3A-0x3f=63, 0x5B=91-0x5D=93, 0x7c =124
				if( ((thisByte >= 0) && (thisByte <= 4)) || 
					((thisByte >=6 ) && (thisByte <= 25)) || 
					(thisByte == 32) || 
					(thisByte == 34) ||  
					((thisByte >= 42) && (thisByte <= 44)) ||
					(thisByte == 46) ||
					(thisByte == 47) ||
					((thisByte >= 58) && (thisByte <= 63)) || 
					((thisByte >= 91) && (thisByte <= 93)) || 
					((thisByte >= 97) && (thisByte <= 122)) ||
					(thisByte == 124) ) {
					//If so, replace it with a legal character
					fileContent[i] = legalByte;
					System.out.println("Replaced an illegal character in a directory entry's file name at offset "+ i + ".");
				}
				else {
					if(fileContent[currentOffset + 32] == 0 && fileContent[currentOffset + 33] == 0) {
						done = true;
					}
				}

			}
		
			//Increment currentOffset to the offset of the next directory entry
			currentOffset += 32;
		}
	}
}