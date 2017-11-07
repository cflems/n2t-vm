import java.io.File;
import java.io.IOException;

public class VMTranslator {
	// This class should only handle the command line options
	// and hand it off to another class
	
	private static void quit (String errormsg, boolean usage) {
		System.err.println("[VM Error] "+errormsg);
		if (usage) System.out.println("Usage: java -jar vmtranslator.jar <input file> [additional input files] [-o output file]");
		System.exit(1);
	}
	
	private static String classFromPath (String path) {
		String[] filePathParts = path.split("\\/|\\\\");
		String baseName = filePathParts[filePathParts.length-1];
		return baseName.contains(".") ? baseName.substring(0, baseName.indexOf('.')) : baseName;		
	}
	
	public static void main (String[] args) {
		ArgumentParser argparse = new ArgumentParser(args);
		if (argparse.peekNonFlag() == null) quit("Too few arguments.", true);
		String inpfile = argparse.peekNonFlag();
		String outfile = inpfile.substring(0, Math.max(Math.max(inpfile.lastIndexOf('/'), 0), inpfile.lastIndexOf('\\'))) + "/" + classFromPath(inpfile) +".asm";
		if (argparse.flagSet("o")) outfile = argparse.getFlag("o");
		
		try {
			VMParser parser = new VMParser(new File(outfile));
			String nextfile;
			while ((nextfile = argparse.popNonFlag()) != null) {
				System.out.println("Parsing: "+nextfile);
				parser.run(new File(nextfile), classFromPath(nextfile));
			}
			parser.close();
		} catch (IOException e) {
			e.printStackTrace();
			quit("Unable to work on input or output file.", true);
		}
		System.out.println("Translated to "+outfile);
	}
	
}
