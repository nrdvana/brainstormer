package brainstormer;

import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
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
public class RADServlet extends HttpServlet {
	static class ServletContextLogger extends PrintWriter {
		javax.servlet.ServletContext cx;
		ServletContextLogger(javax.servlet.ServletContext cx) {
			super(System.out);
			this.cx= cx;
		}
		public void print(String s) {
			cx.log(s);
		}
	}

	public RADServlet() {
	}

	public void init() {
		DriverManager.setLogWriter(new ServletContextLogger(getServletContext()));
	}

	protected void fail(String msg) {
		throw new UserException(msg);
	}

	enum HttpAction {
		Get, Post;
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		handleWithExtendedParams(HttpAction.Get, req, resp);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		handleWithExtendedParams(HttpAction.Post, req, resp);
	}

	protected void handleWithExtendedParams(HttpAction acn, HttpServletRequest req, HttpServletResponse resp) {
		HtmlGL hgl= new HtmlGL(req, resp);
		DB db= null;
		do {
			try {
				db= DB.getInstance(getServletContext());
				db.activeUser= db.auth.authenticateCurrentUser(req);
				switch (acn) {
				case Get:  doGet(req, resp, db, hgl);  break;
				case Post: doPost(req, resp, db, hgl); break;
				}
			}
			catch (SQLException ex) {
				// if we have communication problems, try again, unless we had a fresh connection
				if (ex instanceof com.mysql.jdbc.CommunicationsException && db.hasBeenRecycled())
					continue;

				if (db != null) {
					try {
						db.disconnect();
					}
					catch (Exception e) {}
					db= null;
				}
				hgl.pException(ex);
			}
			catch (Exception ex) {
				hgl.pException(ex);
			}
			if (db != null)
				DB.recycleInstance(db);
		} while (false); // yes, this is just a glorified wrapper for a goto
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		super.doGet(req, resp);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		super.doPost(req, resp);
	}
}
