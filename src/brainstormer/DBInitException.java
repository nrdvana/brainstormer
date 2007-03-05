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
public class DBInitException extends RuntimeException {
	public DBInitException(String msg) {
		super(msg);
	}
	public DBInitException(String msg, Exception ex) {
		super(msg, ex);
	}
	public DBInitException(Exception ex) {
		super(ex);
	}
}
