package brainstormer;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class Util {
	static final String trimPossibleNull(String val) {
		return (val == null)? "" : val.trim();
	}

	static final int parseOrDefault(String number, int defaultVal) {
		try {
			if (number != null)
				return Integer.parseInt(number);
		}
		catch (NumberFormatException ex) {}
		return defaultVal;
	}
}
