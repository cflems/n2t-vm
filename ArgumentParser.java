import java.util.HashMap;
import java.util.LinkedList;

public class ArgumentParser {
	private LinkedList<String> nonflags;
	private HashMap<String, String> flags;
	
	public ArgumentParser (String[] args) {
		nonflags = new LinkedList<String>();
		flags = new HashMap<String, String>();
		
		boolean flagMode = false;
		String flagKey = null;
		for (int i = 0; i < args.length; i++) {
			if (flagMode) {
				flags.put(flagKey, args[i]);
			} else if (args[i].startsWith("-") && args[i].length() > 1) {
				flagMode = true;
				flagKey = args[i].substring(1);
			} else {
				nonflags.add(args[i]);
			}
		}
	}
	
	public String popNonFlag () {
		return nonflags.poll();
	}
	
	public String peekNonFlag () {
		return nonflags.peek();
	}
	
	public boolean flagSet (String flag) {
		return flags.containsKey(flag);
	}
	
	public String getFlag (String flag) {
		return flags.get(flag);
	}
	
}
