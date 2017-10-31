import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class VMParser {
	private File infile;
	private String classname;
	private HashMap<String, String> segmap;

	public VMParser (File infile, String classname) {
		this.infile = infile;
		this.classname = classname;
		this.segmap = new HashMap<String, String>();
		segmap.put("local", "LCL");
		segmap.put("argument", "ARG");
		segmap.put("this", "THIS");
		segmap.put("that", "THAT");
	}
	
	// still needs functions, labels, ifs, gotos
	// you can do vmp.run(a); vmp.run(b); to direct to multiple outputs
	public void run (File outfile) throws IOException {
		BufferedReader instream = new BufferedReader(new FileReader(infile));
		PrintWriter outstream = new PrintWriter(new FileWriter(outfile));
		
		// runtime variables
		int vm_internal = 0;
		
		// load @LCL and @ARG into memory
		
		for (String line = instream.readLine(); line != null; line = instream.readLine()) {
			line = line.trim();
			if (line.length() < 1) continue;
			String cmd = line.substring(0, line.indexOf(' ') != -1 ? line.indexOf(' ') : 0).trim().toLowerCase();
			switch (cmd) {
			case "push":
				if (line.lastIndexOf(' ') < 0) throw new VMParserException("Malformatted push statement.");
				pushsegmt(line.substring(line.indexOf(' ')+1, line.lastIndexOf(' ')).trim().toLowerCase(), line.substring(line.lastIndexOf(' ')+1), outstream);
				break;
			case "pop":
				popsegmt(line.substring(line.indexOf(' ')+1, line.lastIndexOf(' ')).trim().toLowerCase(), line.substring(line.lastIndexOf(' ')+1), outstream);
				break;
			case "add":
				popsegmt("constant", "13", outstream);
				popD(outstream);
				outstream.println("@13");
				outstream.println("D=D+M");
				pushD(outstream);
				break;
			case "sub":
				popsegmt("constant", "13", outstream);
				popD(outstream);
				outstream.println("@13");
				outstream.println("D=D-M");
				pushD(outstream);
				break;
			case "and":
				popsegmt("constant", "13", outstream);
				popD(outstream);
				outstream.println("@13");
				outstream.println("D=D&M");
				pushD(outstream);
				break;
			case "or":
				popsegmt("constant", "13", outstream);
				popD(outstream);
				outstream.println("@13");
				outstream.println("D=D|M");
				pushD(outstream);
				break;
			case "eq":
			case "gt":
			case "lt":
				comparison(cmd, vm_internal++, vm_internal++, outstream); // pass two names that will never overlap in order to perform jumps
				break;
			case "neg":
				popD(outstream);
				outstream.println("D=-D");
				pushD(outstream);
				break;
			case "not":
				popD(outstream);
				outstream.println("D=!D");
				break;
			default:
				throw new VMParserException("Unrecognized instruction: "+cmd);
			}
		}
		instream.close();
		outstream.close();
	}
	
	private void comparison (String type, int spot1, int spot2, PrintWriter outstream) {
		popsegmt("constant", "13", outstream);
		popD(outstream);
		outstream.println("@13");
		outstream.println("D=D-M");
		outstream.println("@VM_INTERNAL_"+spot1); // jump if true
		outstream.println("D;J"+type.toUpperCase()); // it's either EQ, LT, or GT anyway
		outstream.println("@0");
		outstream.println("D=A");
		outstream.println("@VM_INTERNAL_"+spot2);
		outstream.println("0;JMP");
		outstream.println("(VM_INTERNAL_"+spot1+")");
		outstream.println("@32767"); // 15 1's in binary
		outstream.println("D=A");
		outstream.println("(VM_INTERNAL_"+spot2+")");
		pushD(outstream);

	}
	
	private void pushsegmt (String segmt, String offset, PrintWriter outstream) {
		int off = Integer.parseInt(offset);
		
		switch (segmt) {
		case "constant":
			outstream.println("@"+off);
			outstream.println("D=A");
			break;
		case "temp":
			// @5-@12
			if (off > 7) throw new VMParserException("temp segment is limited to offsets 0-7.");
			outstream.println("@"+(off+5));
			outstream.println("D=M");
			break;
		case "pointer":
			// pointer[0] is a @this and pointer[1] is at @that
			if (off == 0) outstream.println("@THIS");	
			else if (off == 1) outstream.println("@THAT");
			else throw new VMParserException("pointer segment is limited to offsets 0-1.");
			outstream.println("D=M");
			break;
		case "static":
			// use a variable for its data...@class.offset
			outstream.println("@"+classname+"."+off);
			outstream.println("D=M");
			break;
		default:
			// look up the appropriate code word in the table
			if (segmap.containsKey(segmt)) {
				outstream.println("@"+off);
				outstream.println("D=A");
				outstream.println("@"+segmap.get(segmt));
				outstream.println("A=M+D");
				outstream.println("D=M");
			} else throw new VMParserException("Nonexistent segment "+segmt+"!");
		}
		pushD(outstream);
	}
	
	private void pushD (PrintWriter outstream) {
		outstream.println("@SP");
		outstream.println("AM=M+1");
		outstream.println("M=D");
	}
	
	private void popsegmt (String segmt, String offset, PrintWriter outstream) {
		int off = Integer.parseInt(offset);
		
		switch (segmt) {
		case "constant":
			popD(outstream);
			outstream.println("@"+off);
			outstream.println("M=D");
			break;
		case "temp":
			// @5-@12
			if (off > 7) throw new VMParserException("temp segment is limited to offsets 0-7.");
			popD(outstream);
			outstream.println("@"+(off+5));
			outstream.println("M=D");
			break;
		case "pointer":
			// pointer[0] is a @this and pointer[1] is at @that
			popD(outstream);
			if (off == 0) outstream.println("@THIS");	
			else if (off == 1) outstream.println("@THAT");
			else throw new VMParserException("pointer segment is limited to offsets 0-1.");
			outstream.println("M=D");
			break;
		case "static":
			// use a variable for its data...@class.offset
			popD(outstream);
			outstream.println("@"+classname+"."+off);
			outstream.println("M=D");
			break;
		default:
			if (segmap.containsKey(segmt)) {
				outstream.println("@"+off);
				outstream.println("D=A");
				outstream.println("@"+segmap.get(segmt));
				outstream.println("D=A+D");
				outstream.println("@13");
				outstream.println("M=D");
				popD(outstream);
				outstream.println("@13");
				outstream.println("A=M");
				outstream.println("M=D");
			} else throw new VMParserException("Nonexistent segment "+segmt+"!");
		}
	}
	
	private void popD (PrintWriter outstream) {
		outstream.println("@SP");
		outstream.println("D=M");
		outstream.println("M=M-1");
		outstream.println("A=D");
		outstream.println("D=M");
	}
	
}
