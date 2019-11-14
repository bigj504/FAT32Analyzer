import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FAT32Analyzer {

	public static void main(String[] args) {
		
		File file;
		String fileNamePath;

		//Ensure proper usage
		if(args.length != 1) {
			System.out.println("Usage: java FAT32Analyzer path/to/file.dd");
			System.exit(1);
		}

		fileNamePath = args[0];
		file = new File(fileNamePath);

		try {
			byte[] fileContent = Files.readAllBytes(file.toPath());
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}
}