public class VMParserException extends RuntimeException {
	private String message;
	
	public VMParserException(String message) {
		this.message = message;
	}
	
	public String getMessage () {
		return message;
	}

	private static final long serialVersionUID = 1521592301162885731L;
}
