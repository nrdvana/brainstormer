package brainstormer;

import java.sql.*;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class AdminPage extends RADServlet {
	static final String[] styles= new String[] { "Page.css", "Widgets.css", "AdminUI.css" };

	public AdminPage() {
//		try {
//			db= DB.getInstance();
//			db.connect(dbURI, userName, userPass);
//			db.init();
//		}
//		catch (SQLException ex) {
	}
	static class DBStatus {
		int driverStat= 0, connStat= 0, schemaStat= 0;
		Exception driverFailure= null, connFailure= null, schemaFailure= null;
	}

	public void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
		HtmlGL hgl= new HtmlGL(req, response);
		ServletContext cx= getServletContext();
		DBStatus dbStat= new DBStatus();
		DB db= null;
		try {
			db= DB.getInstance(cx);
			dbStat.driverStat= dbStat.connStat= dbStat.schemaStat= 1;
		}
		catch (DBInitException ex) {
			diagnoseDBFailure(dbStat);
		}
		hgl.beginPage("Admin", styles);
		renderPage(db, dbStat, hgl);
	}

	void renderPage(DB db, DBStatus dbStat, HtmlGL hgl) throws IOException {
		ServletContext cx= getServletContext();
		hgl.p("<h3>Settings:</h3>\n"
			+"(from WEB-INF/web.xml)<br/>\n"
			+"Database: "+cx.getInitParameter("Database")+"<br/>\n"
			+"UserName: "+cx.getInitParameter("UserName")+"<br/>\n"
			+"Password: (hidden)<br/>\n");
		String[] status= new String[] { "Failed", "Unknown", "success" };
		hgl.p("MySQL driver: ").p(status[dbStat.driverStat+1]);
		if (dbStat.driverFailure != null)
			hgl.p(": "+dbStat.driverFailure);
		hgl.p("<br/>\nDB Connection: ").p(status[dbStat.connStat+1]);
		if (dbStat.connFailure != null)
			hgl.p(": "+dbStat.connFailure);
		hgl.p("<br/>\nSchema: ").p(status[dbStat.schemaStat+1]);
		if (dbStat.schemaFailure != null)
			hgl.p(": "+dbStat.schemaFailure);
		if (dbStat.connStat == -1)
			hgl.p("<hr/><br/>\n<form action=\"admin\" method=\"post\">\n"
				+"You can have the servlet automatically create your database.<br/>\n"
				+"Enter a username and password with sufficient rights to create a database and add a user.<br/>\n"
				+"Username: <input type=\"text\" name=\"dbAdminUname\"/><br/>\n"
				+"Password: <input type=\"password\" name=\"dbAdminPass\"/><br/>\n"
				+"<input type=\"submit\" name=\"InitDB\" value=\"Init DB\"/>\n"
				+"</form>");
		if (dbStat.schemaStat == -1)
			hgl.p("<hr/><br/>\n<form action=\"admin\" method=\"post\">\n"
				+"You can have the servlet automatically configure your tables.<br/>\n"
				+"Enter a username and password with sufficient rights to alter tables in this database.<br/>\n"
				+"Username: <input type=\"text\" name=\"dbAdminUname\"/><br/>\n"
				+"Password: <input type=\"password\" name=\"dbAdminPass\"/><br/>\n"
				+"<input type=\"submit\" name=\"InitSchema\" value=\"Init Schema\"/>\n"
				+"</form>");

		hgl.p("<hr/><br/><br/><form action='admin' method='post'>\n"
			+"Add a user: <input type='text' name='newUserName'/> Pass:<input type='password' name='newUserPass'/><input type='submit' name='newUser' value='Add'/><br/>\n"
			+"Add a group: <input type='text' name='newGroupName'/><input type='submit' name='newGroup' value='Add'/><br/>\n"
			+"</form>");
		hgl.endPage();
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response) throws IOException {
		HtmlGL hgl= new HtmlGL(req, response);
		try {
			if (req.getParameter("InitDB") != null)
				initDatabase(req.getParameter("dbAdminUname"), req.getParameter("dbAdminPass"));
			else if (req.getParameter("InitSchema") != null)
				initSchema(req.getParameter("dbAdminUname"), req.getParameter("dbAdminPass"));
			else if (req.getParameter("newUser") != null)
				addUser(req.getParameter("newUserName"), req.getParameter("newUserPass"), false, hgl);
			else if (req.getParameter("newGroup") != null)
				addUser(req.getParameter("newGroupName"), null, true, hgl);
			response.sendRedirect(response.encodeRedirectURL("admin"));
		}
		catch (Exception ex) {
			hgl.pException(ex);
		}
	}

	void initDatabase(String adminUname, String adminPass) throws SQLException {
		ServletContext cx= getServletContext();
		String dbURI= cx.getInitParameter("Database");
		int divPos= dbURI.lastIndexOf("/");
		String dbName= dbURI.substring(divPos+1);
		String baseURI= dbURI.substring(0, divPos);
		String servletUser= cx.getInitParameter("UserName");
		String servletPass= cx.getInitParameter("Password");
		Connection conn= DriverManager.getConnection("jdbc:"+baseURI+"/mysql", adminUname, adminPass);
		Statement stmt= conn.createStatement();

		stmt.execute("CREATE DATABASE "+dbName);
		stmt.execute("GRANT select, insert, update, delete ON "+dbName+".* TO `"+servletUser+"`@`localhost` IDENTIFIED BY '"+servletPass+"'");
		stmt.close();
		conn.close();
	}

	void initSchema(String adminUname, String adminPass) throws SQLException {
		ServletContext cx= getServletContext();
		String dbURI= cx.getInitParameter("Database");

		DB db= new DB();
		db.connect(dbURI, adminUname, adminPass);
		Schema.SCHEMAS[Schema.getMaxVersion()].deploy(db);
		db.disconnect();
	}

	void addUser(String name, String password, boolean isGroup, HtmlGL hgl) throws Exception {
		DB db= DB.getInstance(getServletContext());
		if (db.userLoader.loadByName(name) != null)
			throw new UserException("Error: User name "+name+" already exists.");
		User newUser= new User();
		newUser.name= name;
		newUser.passHash= Auth.hashPassword(name, password);
		newUser.avatar= null;
		db.userLoader.storeUser(newUser);
	}

	void diagnoseDBFailure(DBStatus dbStat) {
		try {
			DB db= new DB();
			dbStat.driverStat= 1;
			try {
				db.connect(getServletContext());
				dbStat.connStat= 1;
				try {
					db.init();
					db.assertSchemaVersion(Schema.getMaxVersion());
					dbStat.schemaStat= 1;
				}
				catch (Exception ex3) {
					dbStat.schemaStat= -1;
					dbStat.schemaFailure= ex3;
				}
			}
			catch (Exception ex2) {
				dbStat.connStat= -1;
				dbStat.connFailure= ex2;
			}
		}
		catch (Exception ex1) {
			dbStat.driverStat= -1;
			dbStat.driverFailure= ex1;
		}
	}
}
