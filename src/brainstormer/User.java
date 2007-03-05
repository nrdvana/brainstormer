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
public class User {
	int id= -1;
	String name;
	String passHash;
	boolean isGroup;
	java.sql.Timestamp dateJoined;
	String avatar;

	User[] groups= EMPTY_LIST;
	private static final User[] EMPTY_LIST= new User[0];
}
