# digital-forensics-project
update:

Fixed some of the errors i was getting after running through all the changes 
that were being made in debug mode, one of the additional calls to bpbEntry
was deleted that was crucial. If the bpb isnt present some of variables do not 
get populated, so an additional call to bpbEntry after the repair is made is 
needed or when trying to analyzeRoot the offset will be wrong and multiple 
things will be changed.
Also I made a batch file that asks for the image file path and is stored in
%Name% , could possibly be edited to look better.


UH:

	Yo can you run a few test runs and make sure this commit version works?
	I have it outputting the changes to a new file instead of the input
	file now, so that way it's not overwriting our input files (for the
	presentation). Let's make sure we have input files that reflect what
	we want to show (partially wiped BPB / the lab one as is / one with 
	an invalid attribute type). It outputs every time to output.dd, so 
	that'll be our output file to look at and mount and make sure it's
	good.

	I put some printlns in to print what it's doing at the time. Feel free
	to add to or modify them to make them look better or whatever. I also
	realized it was incorrectly changing out the hex character "0x2d" in
	the filename replacing section, which is actually a valid character.
	I fixed it (I think) in the if-statement section, but double check me
	and make sure I did it right to include the other values.

	I also tidied up some of the variable names and simplified some code 
	where I could. I'm almost certain (?) I didn't break anything, but 
	like I said, a few test runs could be good lol. 

	If it works and we've got our input files ready to rock, I think we're
	good for tomorrow. 

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

	Regarding the directory entries, the longname entries come before the
	short name entry for that file. Each long name entry has that 0f at
	offset 11, so we can differentiate between the long name entries and
	the short name entries that way. We only need to check the short name
	entries for illegal characters, the long name entries don't matter.
	So if we locate the end of the short name entry (the 00 00) and iterate
	backwards to the beginning of the line, we can parse through the first
	11 bytes of the name that way.

	Pulled this quote from the resource I added to the bottom that explains 
	it pretty well:
	Each long file name can hold 13 characters. If a filename needs more
	than 13 characters, then more than one LFN will precede the directory
	entry. They come in reverse order, last first. The last's sequence 
	number is ORed with the value 0x40. For example, if there was a file
	with the name 'File with very long filename.ext', which needs 3 LFN
	entries, the sequence numbers and LFN directory entries would be:
		0x43 "me.ext"
		0x02 "y long filena"
		0x01 "File with ver"

	So to implement:
	currentOffset = first byte of this line
	if(offset 11 of this line is not 0f)
		for(every byte from the start of the line to byte 11 inclusive)
			if(that byte equals any of the illegal characters)
				replace
	else
		just increment our offset by 32 and move back to the top of the loop
		to check the next line

USEFUL RESOURCES:

	To help locate the root directory: http://www.tavi.co.uk/phobos/fat.html

	Same: http://www.cs.uni.edu/~diesburg/courses/cop4610_fall10/week11/week11.pdf

	

	To help differentiate between short and long file names:

	https://people.cs.umass.edu/~liberato/courses/2017-spring-compsci365/lecture-notes/11-fats-and-directory-entries/
