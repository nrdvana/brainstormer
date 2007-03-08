package brainstormer;

import java.util.*;

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
	final int id;
	final String name;
	String passHash;
	boolean isGroup;
	java.sql.Timestamp dateJoined;
	String avatar;
	User[] groups= EMPTY_LIST;

	private static final User[] EMPTY_LIST= new User[0];

	public User(int id, String name) {
		this.id= id;
		this.name= name;
	}

	public User(String name) {
		this.id= -1;
		this.name= name;
	}

	public String debugInfo() {
		String result= "obj="+super.toString()+",id="+id+",name="+name+",groups=[";
		for (User u: groups)
			result+=u.debugInfo();
		return result+"]";
	}

	public boolean isMemberOf(User group) {
		Set<User> visitedSet= new HashSet<User>();
		return isMemberOf(group, visitedSet);
	}

	private boolean isMemberOf(User group, Set<User> visitedSet) {
		if (this == group)
			return true;
		for (User u: groups)
			if (u == group)
				return true;
		visitedSet.add(this);
		for (User u: groups)
			if (!visitedSet.contains(u))
				if (u.isMemberOf(group, visitedSet))
					return true;
		return false;
	}
}
