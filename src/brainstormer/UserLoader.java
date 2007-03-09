package brainstormer;

import java.sql.*;
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
public class UserLoader {
	DB db;

	PreparedStatement
		s_SelectUserByID, s_SelectUserByName, s_SelectUserGroups,
		s_InsertUser, s_UpdateUser,
		s_WipeGroupMemberships, s_AddGroupMembership;

	static final String
		userFields= "u.ID, u.Name, u.PassHash, u.IsGroup, u.DateJoined, u.AvatarURL, count(*) as GroupCount ",
		userTable= "User u ",
		userJoin= "LEFT JOIN GroupMembership gcount ON u.ID = gcount.UserID ",
		userGroupBy= "GROUP BY u.ID ",
		groupFields= "g.UserID, g.GroupID ",
		groupTable= "GroupMembership g ";

	public UserLoader(DB db) throws SQLException {
		this.db= db;
		s_SelectUserByID= db.prep("SELECT "+userFields+"FROM "+userTable+userJoin+"WHERE u.ID = ? "+userGroupBy);
		s_SelectUserByName= db.prep("SELECT "+userFields+"FROM "+userTable+userJoin+"WHERE u.Name = ? "+userGroupBy);
		s_SelectUserGroups= db.prep(
			"SELECT "+userFields
			+"FROM "+groupTable+"INNER JOIN "+userTable+"ON u.ID = g.GroupID "+userJoin
			+"WHERE g.UserID = ? "
			+userGroupBy);
		s_InsertUser= db.prep("INSERT INTO User (Name, PassHash, IsGroup, AvatarURL) VALUES (?, ?, ?, ?)");
		s_UpdateUser= db.prep("UPDATE User SET Name = ?, PassHash = ?, IsGroup = ?, AvatarURL = ? WHERE ID = ?");
		s_WipeGroupMemberships= db.prep("DELETE FROM GroupMembership WHERE UserID = ?");
		s_AddGroupMembership= db.prep("INSERT INTO GroupMembership (UserID, GroupID) VALUES (?, ?)");
	}

	public User loadById(int searchId) throws SQLException {
		User result= db.userCache.get(searchId);
		if (result == null) {
			s_SelectUserByID.setInt(1, searchId);
			result= loadFromStmt(s_SelectUserByID);
		}
		return result;
	}

	public User loadByName(String searchName) throws SQLException {
		s_SelectUserByName.setString(1, searchName);
		return loadFromStmt(s_SelectUserByName);
	}

	private User loadFromStmt(PreparedStatement stmt) throws SQLException {
		User ret= null;
		ResultSet rs= stmt.executeQuery();
		if (rs.next()) {
			ret= userFromResultSet(rs);
			if (ret.groups.length > 0 && ret.groups[0] == null)
				loadUserGroups(ret);
		}
		if (rs.next())
			throw new RuntimeException("Multiple rows returned??");
		rs.close();
		return ret;
	}

	User userFromResultSet(ResultSet rs) throws SQLException {
		int id= rs.getInt(rs.findColumn("ID"));
		User u= db.userCache.get(id);
		if (u == null) {
			String name= rs.getString(rs.findColumn("Name"));
			u= new User(id, name);
			db.userCache.put(id, u);
			u.passHash= rs.getString(rs.findColumn("PassHash"));
			u.isGroup= rs.getBoolean(rs.findColumn("IsGroup"));
			u.dateJoined= rs.getTimestamp(rs.findColumn("DateJoined"));
			u.avatar= rs.getString(rs.findColumn("AvatarURL"));
			int groupCount= rs.getInt(rs.findColumn("GroupCount"));
			if (groupCount > 0)
				u.groups= new User[groupCount];
		}
		return u;
	}

	void loadUserGroups(User u) throws SQLException {
		s_SelectUserGroups.setInt(1, u.id);
		ResultSet rs= s_SelectUserGroups.executeQuery();
		LinkedList<User> list= new LinkedList<User>();
		while (rs.next())
			list.add(userFromResultSet(rs));
		rs.close();
		u.groups= list.toArray(list.size() == u.groups.length? u.groups : new User[list.size()]);
		// now check for missing group membership
		for (User g: u.groups)
			if (g.groups.length > 0 && g.groups[0] == null)
				loadUserGroups(g);
	}

	User newUser(String uname, String pass) throws SQLException {
		User u= new User(uname);
		u.isGroup= false;
		u.passHash= Auth.hashPassword(uname, pass);
		u.avatar= null;
		storeUser(u);
		return u;
	}

	User newGroup(String uname) throws SQLException {
		User u= new User(uname);
		u.isGroup= true;
		u.passHash= "-";
		u.avatar= null;
		storeUser(u);
		return u;
	}

	void storeUser(User u) throws SQLException {
		if (db.activeUser == null)
			throw new UserException("You must be logged in before you can alter user information");
		if (u.id == -1)
			createUser(u);
		else {
			if (db.activeUser != u && !db.activeUser.isMemberOf(loadById(0)))
				throw new UserException("You must be a member of the Admin group to alter other users");
			PreparedStatement stmt= s_UpdateUser;
			stmt.setString(1, u.name);
			stmt.setString(2, u.passHash);
			stmt.setBoolean(3, u.isGroup);
			stmt.setString(4, u.avatar);
			stmt.setInt(5, u.id);
			int changed= stmt.executeUpdate();
			if (changed != 1)
				throw new UserException("Unable to alter user "+u.name+".  Perhaps this user was deleted?");
		}
	}

	int createUser(User u) throws SQLException {
		if (db.activeUser == null)
			throw new UserException("You must be logged in before you can alter user information");
		if (!db.activeUser.isMemberOf(loadById(0)))
			throw new UserException("You must be a member of the Admin group to create users");
		PreparedStatement stmt= s_InsertUser;
		stmt.setString(1, u.name);
		stmt.setString(2, u.passHash);
		stmt.setBoolean(3, u.isGroup);
		stmt.setString(4, u.avatar);
		stmt.executeUpdate();
		ResultSet rs= stmt.getGeneratedKeys();
		if (!rs.next())
			throw new RuntimeException("Did not receive new user ID after insert");
		int result= rs.getInt(1);
		rs.close();
		return result;
	}

	void storeUserGroups(User u) throws SQLException {
		PreparedStatement stmt= s_WipeGroupMemberships;
		stmt.setInt(1, u.id);
		stmt.executeUpdate();
		stmt= s_AddGroupMembership;
		for (User g: u.groups) {
			stmt.setInt(1, g.id);
			stmt.addBatch();
		}
		DB.assertBatchSucceed(stmt.executeBatch(), "adding user's group membership");
	}
}
