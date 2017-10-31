import java.io.File;
import java.io.IOException;

public class VMTranslator {
	// This class should only handle the command line options
	// and hand it off to another class
	
	private static void quit (String errormsg, boolean usage) {
		System.err.println("[VM Error] "+errormsg);
		if (usage) System.out.println("Usage: java -jar vmtranslator.jar <input file> [optional output file]");
		System.exit(1);
	}
	
	public static void main (String[] args) {
		if (args.length < 1) quit("Too few arguments.", true);
		String inpfile = args[0];
		String outfile = (args.length > 1) ? args[1] : inpfile.split("\\.")[0]+".asm";
		try {
			new VMParser(new File(inpfile), inpfile.split("\\.")[0]).run(new File(outfile));
		} catch (IOException e) {
			e.printStackTrace();
			quit("Unable to work on input or output file.", true);
		}
	}
	
}
