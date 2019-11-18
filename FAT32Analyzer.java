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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;


public class FAT32Analyzer {

	private static int bytesPerSector;
	private static int sectorsPerCluster;
	private static int reservedSectorCount;
	private static int numFATs;
	private static int sizeOfFAT;
	private static int rootCluster;

	public static void main(String[] args) throws IOException {
		
		File file;
		String fileNamePath;
		boolean bpbPresent;
		boolean bpbBackupPresent;

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
			
		  bpbPresent = bpbEntry(fileContent);
		  if(!bpbPresent)
		  bpbBackupPresent = bpbBackup(fileContent);
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
	 * @param fileContent - the byte array
	 * @return false if the BPB contains any errors/modifications that will cause corruption, true otherwise 
	 */
	public static boolean bpbEntry(byte[] fileContent) {
		
		byte jmpBootByteOne = fileContent[0];
		byte jmpBootByteThree = fileContent[2];
		byte bytesPerSectorByteOne = fileContent[11];
		byte bytesPerSectorByteTwo = fileContent[12];
		byte sectorsPerCluster = fileContent[13];
		byte rsvdSecCntByteOne = fileContent[14];
		byte rsvdSecCntByteTwo = fileContent[15];
		byte numFATs = fileContent[16];
		byte rootEntCntByteOne = fileContent[17];
		byte rootEntCntByteTwo = fileContent[18];
		byte totSec16ByteOne = fileContent[19];
		byte totSec16ByteTwo = fileContent[20];
		byte media = fileContent[21];
		byte fatSz16ByteOne = fileContent[22];
		byte fatSz16ByteTwo = fileContent[23];
		byte totSec32ByteOne = fileContent[34];
		byte totSec32ByteTwo = fileContent[35];
		byte fsVerByteOne = fileContent[42];
		byte fsVerByteTwo = fileContent[43];
		byte reserved = fileContent[52];
		byte drvNum = fileContent[64];
		byte reservedOne = fileContent[65];
		byte endSignatureByteOne = fileContent[510];
		byte endSignatureByteTwo = fileContent[511];
			
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
		if(sectorsPerCluster != 1 && sectorsPerCluster != 2 && sectorsPerCluster != 4 &&
			sectorsPerCluster != 8 && sectorsPerCluster != 16 && sectorsPerCluster != 32 &&
			sectorsPerCluster != 64 && sectorsPerCluster != -128) {
			return false;
		}

		//If reserved sector count is 0, return false
		if(rsvdSecCntByteOne == 0) {
			if(rsvdSecCntByteTwo == 0) {
				return false;
			}
		}

		//If NumFATs isn't 2 or 1, return false
		if(numFATs != 2 && numFATs != 1) {
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
			this.bytesPerSector = 512;
		else if(bytesPerSectorByteTwo == 4)
			this.bytesPerSector = 1024;
		else if(bytesPerSectorByteTwo == 8)
			this.bytesPerSector = 2048;
		else if(bytesPerSectorByteTwo == 16)
			this.bytesPerSector = 4096;

		/*Determine the sectors per cluster*/
		if(sectorsPerCluster == -128)
			this.sectorsPerCluster = 128;
		else
			this.sectorsPerCluster = sectorsPerCluster;

		/*Determine the number of reserved sectors*/
		if(rsvdSecCntByteTwo == 0)
			this.reservedSectorCount = rsvdSecCntByteOne;
		else {
			this.reservedSectorCount = (rsvdSecCntByteTwo << 8) + rsvdSecCntByteOne;
		}

		/*Determine the number of FATs*/
		this.numFATs = numFATs;

		/*Determine the number of sectors per FAT*/
		//Create a new byte array to hold the 4 bytes of the sizeOfFAT
		byte[] temp = new byte[4];
		//Add the 4 bytes to the array
		temp[0] = fileContent[36];
		temp[1] = fileContent[37];
		temp[2] = fileContent[38];
		temp[3] = fileContent[39];
		//Create a ByteBuffer wrapped around the array
		ByteBuffer bb = ByteBuffer.wrap(temp);
		//Change the ByteBuffer to little endian
		bb.order(ByteOrder.LITTLE_ENDIAN);
		//Convert it to an int and store
		this.sizeOfFAT = bb.getInt();

		/*Determine location of root cluster*/
		//If the 3 bytes following the least significant byte are 0
		if(fileContent[45] == 0 && fileContent[46] == 0 && fileContent[47] == 0)
			//Then the rootCluster field is just in the first byte
			this.rootCluster = fileContent[44];
		//Otherwise create a new byte array to hold the 4 bytes and convert using ByteBuffer
		else {
			byte[] arr = new byte[4];
			//Add the 4 bytes to the array
			arr[0] = fileContent[44];
			arr[1] = fileContent[45];
			arr[2] = fileContent[46];
			arr[3] = fileContent[47];
			//Override our existing byte buffer to be wrapped around this array
			bb = ByteBuffer.wrap(arr);
			//Just to make sure it's using little endian ordering
			bb.order(ByteOrder.LITTLE_ENDIAN);
			//Convert it to an int and store
			this.rootCluster = bb.getInt();
		}


		//Finally, return true
		return true;
	}


	/**
	*Located BPB backup
	* (Task 3)
	*@param fileContent - the byte array
	*@return bpbBackupPresent - true will equal BPB backup is present 
	*
	*/
	public static boolean bpbBackup(byte[] fileContent){
		boolean bpbBackupPresent = false;
		int arrayLength = fileContent.length-1, i=0;

		while( i < arrayLength){

				byte entry = fileContent[i];
				int j = i+2;
				int k = (i+1 % 512);
				byte entryPlusTwo = fileContent[j];
			
			// Oxeb = -21, 0x90 = -112, 0xe9 = -23
			if( ((entry == -21) && (entryPlusTwo == -112)) || ((entry ==-23 ) && (k == 0)) ){
				
				bpbBackupPresent = true;
				System.out.println(i);  // test, delete 
				System.out.println((i+1) %512); // test, delete
				break;  				// break out while cause we found what we need
			}
			i++;
		}
		System.out.println(arrayLength + " task 3");// test, delete
		System.out.println(bpbBackupPresent); //test , delete  
		return bpbBackupPresent;

	}



}

	



