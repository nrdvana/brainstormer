package brainstormer;

import java.io.IOException;
import javax.servlet.http.*;
import javax.servlet.*;
import java.sql.*;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class SetupPage extends HttpServlet {
	static final String[] styles= new String[] { "Page.css", "Widgets.css", "AdminUI.css" };

	static class DBStatus {
		int driverStat= 0, connStat= 0, schemaStat= 0;
		int schemaVersionFound= -1;
		Exception driverFailure= null, connFailure= null, schemaFailure= null;
	}

	public void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
		HtmlGL hgl= new HtmlGL(req, response);
		try {
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
			hgl.beginPage("Setup", styles, db);
			renderPage(db, dbStat, hgl);
			hgl.endPage();
		}
		catch (Exception ex) {
			hgl.pException(ex);
		}
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response) throws IOException {
		HtmlGL hgl= new HtmlGL(req, response);
		try {
			if (req.getParameter("InitDB") != null)
				initDatabase(req.getParameter("dbAdminUname"), req.getParameter("dbAdminPass"));
			else if (req.getParameter("InitSchema") != null) {
				String adminUname= Util.trimPossibleNull(req.getParameter("forumAdminUname"));
				String adminPass= Util.trimPossibleNull(req.getParameter("forumAdminPass"));
				initSchema(
					Util.trimPossibleNull(req.getParameter("dbAdminUname")),
					Util.trimPossibleNull(req.getParameter("dbAdminPass")),
					adminUname, adminPass, hgl);
			}
			response.sendRedirect(response.encodeRedirectURL("setup"));
		}
		catch (Exception ex) {
			hgl.pException(ex);
		}
	}

	void renderPage(DB db, DBStatus dbStat, HtmlGL hgl) throws Exception {
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
		if (db != null) {
			hgl.p("<br/>Transaction Isolation: ");
			int iso= db.conn.getTransactionIsolation();
			switch (iso) {
			case Connection.TRANSACTION_READ_UNCOMMITTED: hgl.p("READ UNCOMITTED"); break;
			case Connection.TRANSACTION_READ_COMMITTED:   hgl.p("READ COMMITTED"); break;
			case Connection.TRANSACTION_REPEATABLE_READ:  hgl.p("REPEATABLE READ"); break;
			case Connection.TRANSACTION_SERIALIZABLE:     hgl.p("SERIALIZABLE"); break;
			case Connection.TRANSACTION_NONE: hgl.p("Not supported"); break;
			default:
				hgl.p("Unknown");
			}
			hgl.p("<br/>Autocommit: "+db.conn.getAutoCommit());
		}
		if (dbStat.connStat == -1)
			hgl.p("<hr/><br/>\n<form action='setup' method='post'>\n"
				+"You can have the servlet automatically create your database.<br/>\n"
				+"Enter a username and password with sufficient rights to create a database and add a user.<br/>\n"
				+"Username: <input type='text' name='dbAdminUname'/><br/>\n"
				+"Password: <input type='password' name='dbAdminPass'/><br/>\n"
				+"<input type='submit' name='InitDB' value='Init DB'/>\n"
				+"</form>");
		if (dbStat.schemaStat == -1) {
			hgl.p("<hr/><br/>\n<form action='setup' method='post'>\n"
				+"You can have the servlet automatically configure your tables.<br/>\n"
				+"Enter a username and password with sufficient rights to alter tables in this database.<br/>\n"
				+"Username: <input type='text' name='dbAdminUname'/><br/>\n"
				+"Password: <input type='password' name='dbAdminPass'/><br/>\n");
			if (dbStat.schemaVersionFound < 1)
				hgl.p("Since you do not yet have any users, enter a username and password "
					+"who will be created and given administrative prieveleges:<br/>\n"
					+"AdminUser: <input type='text' name='forumAdminUname'/> Pass:<input type='password' name='forumAdminPass'/><br/>");
			hgl.p("<input type='submit' name='InitSchema' value='Init Schema'/>\n"
				+"</form>");
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

	void initSchema(String dbUname, String dbPass, String forumAdminName, String forumAdminPass, HtmlGL hgl) throws SQLException {
		ServletContext cx= getServletContext();
		String dbURI= cx.getInitParameter("Database");

		DB db= new DB();
		db.connect(dbURI, dbUname, dbPass);
		int startVersion= db.getSchemaVersion();
		for (int i=startVersion+1; i<=Schema.getMaxVersion(); i++)
			try {
				Schema.SCHEMAS[i].deploy(db);
			}
			catch (SQLException ex) {
				throw new RuntimeException("Error while deploying schema version "+i+":", ex);
			}
		if (startVersion < 1 && forumAdminName.length() != 0) { // need to add admin user, if given
			db.init(); // is now safe to prepare queries, etc
			try {
				User u= db.userLoader.newUser(forumAdminName, forumAdminPass);
				User root= db.userLoader.loadById(0);
				if (root == null)
					throw new RuntimeException("Root user does not exist");
				u.groups= new User[] {root};
				db.userLoader.storeUser(u);
			}
			catch (Exception ex) {
				throw new RuntimeException("Error while adding the default admin user "+forumAdminName+":", ex);
			}
		}
		db.disconnect();
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
					dbStat.schemaVersionFound= db.getSchemaVersion();
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
