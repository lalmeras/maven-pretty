package likide.pretty;

import java.util.regex.Pattern;

public class Constants {

	public static final Pattern ESCAPE_PATTERN = Pattern.compile("\033\\[[^m]*m");
	public static final String TERM_LINE_UP = "%dA";
	public static final String TERM_LINE_BACK = "2K";
	public static final String TERM_ESCAPE = "\033[";
	public static final String TERM_RESET = "\033[0m";
	public static final String TERM_BOLD = "1m";
	public static final String TERM_BOLD_GREEN = "1;32m";
	public static final String TERM_BOLD_YELLOW = "1;33m";
	public static final String TERM_BOLD_RED = "1;31m";
	public static final String TERM_BOLD_BLUE = "1;34m";
	public static final String TERM_GREY = "38;5;8m";

}
