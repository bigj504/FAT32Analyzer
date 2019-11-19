# digital-forensics-project

NOTES:

	Can easily apply the same logic of bpbEntry method to BPB backup
	just by changing the offsets to match the BPB backup location.
	(i.e. offset 0 in BPB entry would correspond to offset 3072 in BPB
	backup because 512 bytes per sector * 6 sectors (where the backup
	is located))

	But if we're trying to search for the backup in the first place,
	probably have to find the starting index by searching for the starting
	signature in the file and calculate the offset we found that signature
	at (so if we find the 0xEB 0x?? 0x90 series of bytes, calculate the
	offset we found that byte sequence at and see if that offset
	corresponds to the beginning of a sector (by being divisible by bytes
	per sector or just divisible by 512 since all values of bytes per
	sector are multiples of 512). Then save that offset and add that
	offset to all of the index values of fileContent we used in bpbEntry.

	May as well have the BPB backup search AND repair in the same method.
	Thoughts?
	Rationale being that if we end up having to search for the BPB backup,
	that means the BPB entry isn't there and needs to be repaired.
	If the BPB backup is modified or corrupted also, we just can't do
	anything with the file, and the program is done running. If the
	backup ISN'T modified or corrupted, we can just parse the backup into
	a byte array of size 512 and for every byte in that array, overwrite
	the bytes of the BPB (starting at 0) to that byte.

USEFUL RESOURCES:
	To help locate the root directory: http://www.tavi.co.uk/phobos/fat.html
	Same: http://www.cs.uni.edu/~diesburg/courses/cop4610_fall10/week11/week11.pdf
