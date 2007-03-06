package brainstormer;

import java.sql.*;
import java.util.*;
import javax.servlet.*;
import java.io.PrintWriter;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class DB {
	Connection conn;
	int foundSchemaVersion= -1;

	User activeUser;
	PostLoader postLoader;
	UserLoader userLoader;
	Auth auth;
	Map<Integer,Post> postCache= new HashMap<Integer,Post>();
	Map<Integer,User> userCache= new HashMap<Integer,User>();

	private static final LinkedList pool= new LinkedList();
	private static boolean driverFound= false;

	public static DB getInstance(ServletContext context) throws DBInitException {
		synchronized (pool) {
			if (pool.size() > 0)
				return (DB) pool.removeFirst();
		}
		try {
			DB result= new DB();
			result.connect(context);
			result.init();
			result.assertSchemaVersion(Schema.getMaxVersion());
			return result;
		}
		catch (SQLException ex) {
			throw new DBInitException(ex);
		}
	}

	public static void recycleInstance(DB db) {
		db.postCache.clear();
		db.userCache.clear();
		db.activeUser= null;
		synchronized (pool) {
			if (pool.size() < 10) {
				pool.addLast(db);
				db= null;
			}
		}
		if (db != null)
			try {
				db.disconnect();
			}
			catch (Exception ex) {}
	}

	public static final void assertBatchSucceed(int[] resultCodes, String context) throws SQLException {
		for (int result: resultCodes)
			if (result <= 0)
				throw new SQLException("While "+context+": insert or update failed with code "+result);
	}

	public DB() throws SQLException {
		initDriver();
	}

	public void connect(ServletContext cx) throws SQLException {
		String dbURI= cx.getInitParameter("Database");
		String uname= cx.getInitParameter("UserName");
		String pass= cx.getInitParameter("Password");
		connect(dbURI, uname, pass);
	}

	public void connect(String dbURI, String userName, String userPass) throws SQLException {
		conn= DriverManager.getConnection("jdbc:"+dbURI, userName, userPass);
	}

	public void disconnect() throws SQLException {
		conn.close();
	}

	public boolean connected() {
		return conn != null;
	}

	public void init() throws SQLException {
		foundSchemaVersion= loadSchemaVersion();
		postLoader= new PostLoader(this);
		userLoader= new UserLoader(this);
		auth= new Auth(this);
	}

	public int getSchemaVersion() {
		return foundSchemaVersion;
	}

	public boolean hasCorrectSchemaVersion() {
		return foundSchemaVersion == Schema.getMaxVersion();
	}

	public void assertSchemaVersion(int expected) {
		int curVer= getSchemaVersion();
		if (curVer != expected)
			throw new DBInitException("Wrong schema version ("+curVer+" instead of "+expected+")");
	}

	private void initDriver() {
		// this is thread-safe because there is no harm in running it multiple times
		if (driverFound) return;
		try {
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ex) {
			throw new DBInitException("MySQL driver class not found", ex);
		}
		driverFound= true;
	}

	private int loadSchemaVersion() {
		try {
			Statement stmt= conn.createStatement();
			ResultSet rs= stmt.executeQuery("SELECT Version FROM SchemaVersion");
			rs.next();
			int result= rs.getInt(1);
			rs.close();
			stmt.close();
			return result;
		}
		catch (SQLException ex) {
			// no schema.. call it version 0
			return 0;
		}
	}

	PreparedStatement prep(String sql) throws SQLException {
		return conn.prepareStatement(sql);
	}

	void createTable(Schema.Table t) throws SQLException {
		StringBuffer query= new StringBuffer("CREATE TABLE ").append(t.name).append(" ( ");
		for (int i=0; i<t.columns.length; i++)
			query.append(t.columns[i]).append(", ");
		for (int i=0; i<t.keys.length; i++)
			query.append(t.keys[i]).append(", ");
		query.delete(query.length()-2, query.length());
		query.append(")");
		conn.createStatement().execute(query.toString());
	}

	void storeSchemaVersion(int version) throws SQLException {
		Statement stmt= conn.createStatement();
		stmt.execute("DELETE FROM SchemaVersion");
		stmt.executeUpdate("INSERT INTO SchemaVersion VALUES ("+version+")");
		stmt.close();
	}
}
