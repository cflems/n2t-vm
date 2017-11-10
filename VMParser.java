import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class VMParser {
	private PrintWriter outstream;
	// map segment names to their assembly variables
	private HashMap<String, String> segmap;
	// unique IDs for making internal jumps
	private int vm_internal;
	// the function we're currently processing
	private String fnname;

	public VMParser (File outfile) throws IOException {
		this.segmap = new HashMap<String, String>();
		segmap.put("local", "LCL");
		segmap.put("argument", "ARG");
		segmap.put("this", "THIS");
		segmap.put("that", "THAT");
		this.vm_internal = 0;
		this.fnname = "";
		this.outstream = new PrintWriter(new FileWriter(outfile));
		bootstrap();
	}

	private String[] processLine (String ln) {
		ln = ln.replaceAll("\\/\\/.*", "");
		String[] potentials = ln.split("\\s");
		ArrayList<String> applicable = new ArrayList<String>(potentials.length);
		for (int i = 0; i < potentials.length; i++) {
			if (potentials[i].trim().length() > 0) applicable.add(potentials[i].trim());
		}
		return applicable.toArray(new String[0]);
	}

	private void bootstrap () {
		// SP=256
		outstream.println("@256");
		outstream.println("D=A");
		outstream.println("@SP");
		outstream.println("M=D");
		// call Sys.init
		callFunction("Sys.init", "0");
	}

	public void run (File infile, String classname) throws IOException {
		BufferedReader instream = new BufferedReader(new FileReader(infile));

		for (String line = instream.readLine(); line != null; line = instream.readLine()) {
			line = line.trim();
			String[] processed = processLine(line);
			if (processed.length < 1) continue;

			switch (processed[0].toLowerCase()) {
			case "push":
				if (processed.length < 3) throw new VMParserException("Malformatted push statement.");
				pushsegmt(processed[1], processed[2], classname);
				break;
			case "pop":
				if (processed.length < 3) throw new VMParserException("Malformatted pop statement.");
				popsegmt(processed[1], processed[2], classname);
				break;
			case "add":
				popvar("R13");
				popD();
				outstream.println("@R13");
				outstream.println("D=D+M");
				pushD();
				break;
			case "sub":
				popvar("R13");
				popD();
				outstream.println("@R13");
				outstream.println("D=D-M");
				pushD();
				break;
			case "and":
				popvar("R13");
				popD();
				outstream.println("@R13");
				outstream.println("D=D&M");
				pushD();
				break;
			case "or":
				popvar("R13");
				popD();
				outstream.println("@R13");
				outstream.println("D=D|M");
				pushD();
				break;
			case "eq":
			case "gt":
			case "lt":
				comparison(processed[0].toLowerCase());
				break;
			case "neg":
				popD();
				outstream.println("D=-D");
				pushD();
				break;
			case "not":
				popD();
				outstream.println("D=!D");
				pushD();
				break;
			case "goto":
				if (processed.length < 2) throw new VMParserException("Too few arguments to 'goto'.");
				outstream.println("@"+fnname+"$"+processed[1]);
				outstream.println("0;JMP");
				break;
			case "if-goto":
				if (processed.length < 2) throw new VMParserException("Too few arguments to 'if-goto'.");
				popD();
				outstream.println("@"+fnname+"$"+processed[1]);
				outstream.println("D;JNE");
				break;
			case "label":
				if (processed.length < 2) throw new VMParserException("Too few arguments to 'label'.");
				outstream.println("("+fnname+"$"+processed[1]+")");
				break;
			case "function":
				if (processed.length < 3) throw new VMParserException("Too few arguments to 'function'.");
				int locals = Integer.parseInt(processed[2]);
				fnname = processed[1];
				outstream.println("("+processed[1]+")");
				for (int i = 0; i < locals; i++) {
					pushsegmt("constant", "0", classname);
				}
				break;
			case "call": // syntax: call function nargs
				if (processed.length < 3) throw new VMParserException("Too few arguments to 'call'.");
				callFunction(processed[1], processed[2]);
				break;
			case "return":
				outstream.println("@LCL");
				outstream.println("D=M");
				outstream.println("@R14"); // R14 is FRAME
				outstream.println("M=D");
				outstream.println("@5");
				outstream.println("A=D-A");
				outstream.println("D=M");
				outstream.println("@R15"); // R15 is RET
				outstream.println("M=D");
				popsegmt("argument", "0", classname); // *ARG = pop() => pop argument 0
				// SP = ARG+1
				outstream.println("@ARG");
				outstream.println("D=M+1");
				outstream.println("@SP");
				outstream.println("M=D");
				// THAT = *(FRAME-1)
				outstream.println("@R14");
				outstream.println("A=M-1");
				outstream.println("D=M");
				outstream.println("@THAT");
				outstream.println("M=D");
				// THIS = *(FRAME-2)
				outstream.println("@2");
				outstream.println("D=A");
				outstream.println("@R14");
				outstream.println("A=M-D");
				outstream.println("D=M");
				outstream.println("@THIS");
				outstream.println("M=D");
				// ARG = *(FRAME-3)
				outstream.println("@3");
				outstream.println("D=A");
				outstream.println("@R14");
				outstream.println("A=M-D");
				outstream.println("D=M");
				outstream.println("@ARG");
				outstream.println("M=D");
				// LCL = *(FRAME-4)
				outstream.println("@4");
				outstream.println("D=A");
				outstream.println("@R14");
				outstream.println("A=M-D");
				outstream.println("D=M");
				outstream.println("@LCL");
				outstream.println("M=D");
				// goto RET (R15)
				outstream.println("@R15");
				outstream.println("A=M");
				outstream.println("0;JMP");
				break;
			default:
				throw new VMParserException("Unrecognized instruction: "+processed[0]);
			}
		}
		instream.close();
	}

	private void callFunction (String function, String strnargs) {
		String returnlabel = "VM_INTERNAL_"+(vm_internal++);
		pushptr(returnlabel);
		pushvar("LCL");
		pushvar("ARG");
		pushvar("THIS");
		pushvar("THAT");
		//ARG = SP-n-5
		int nargs = Integer.parseInt(strnargs);
		outstream.println("@"+(nargs+5));
		outstream.println("D=A");
		outstream.println("@SP");
		outstream.println("D=M-D");
		outstream.println("@ARG");
		outstream.println("M=D");
		outstream.println("@SP");
		outstream.println("D=M");
		outstream.println("@LCL");
		outstream.println("M=D");
		outstream.println("@"+function);
		outstream.println("0;JMP");
		outstream.println("("+returnlabel+")");
	}

	private void comparison (String type) {
		int spot1 = vm_internal++, spot2 = vm_internal++;
		popvar("R13");
		popD();
		outstream.println("@R13");
		outstream.println("D=D-M");
		outstream.println("@VM_INTERNAL_"+spot1); // jump if true
		outstream.println("D;J"+type.toUpperCase()); // it's either EQ, LT, or GT anyway
		outstream.println("@0");
		outstream.println("D=A");
		outstream.println("@VM_INTERNAL_"+spot2);
		outstream.println("0;JMP");
		outstream.println("(VM_INTERNAL_"+spot1+")");
		outstream.println("D=-1");
		outstream.println("(VM_INTERNAL_"+spot2+")");
		pushD();

	}

	private void pushptr (String ptr) {
		outstream.println("@"+ptr);
		outstream.println("D=A");
		pushD();
	}

	private void pushvar (String var) {
		outstream.println("@"+var);
		outstream.println("D=M");
		pushD();
	}

	private void pushsegmt (String segmt, String offset, String classname) {
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
		pushD();
	}

	private void pushD () {
		outstream.println("@SP");
		outstream.println("M=M+1");
		outstream.println("A=M-1");
		outstream.println("M=D");
	}

	private void popvar (String var) {
		popD();
		outstream.println("@"+var);
		outstream.println("M=D");
	}

	private void popsegmt (String segmt, String offset, String classname) {
		int off = Integer.parseInt(offset);

		switch (segmt) {
		case "constant":
			popD();
			outstream.println("@"+off);
			outstream.println("M=D");
			break;
		case "temp":
			// @5-@12
			if (off > 7) throw new VMParserException("temp segment is limited to offsets 0-7.");
			popD();
			outstream.println("@"+(off+5));
			outstream.println("M=D");
			break;
		case "pointer":
			// pointer[0] is a @this and pointer[1] is at @that
			popD();
			if (off == 0) outstream.println("@THIS");
			else if (off == 1) outstream.println("@THAT");
			else throw new VMParserException("pointer segment is limited to offsets 0-1.");
			outstream.println("M=D");
			break;
		case "static":
			// use a variable for its data...@class.offset
			popD();
			outstream.println("@"+classname+"."+off);
			outstream.println("M=D");
			break;
		default:
			if (segmap.containsKey(segmt)) {
				outstream.println("@"+off);
				outstream.println("D=A");
				outstream.println("@"+segmap.get(segmt));
				outstream.println("D=M+D");
				outstream.println("@R13");
				outstream.println("M=D");
				popD();
				outstream.println("@R13");
				outstream.println("A=M");
				outstream.println("M=D");
			} else throw new VMParserException("Nonexistent segment "+segmt+"!");
		}
	}

	private void popD () {
		outstream.println("@SP");
		outstream.println("AM=M-1");
		outstream.println("D=M");
		// outstream.println("M=0"); // do we wipe the memory when we pop?
	}

	public void close () {
		outstream.close();
	}

}
