package brainstormer;

import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.io.*;

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
	static class ServletContextLogger extends PrintStream {
		javax.servlet.ServletContext cx;
		ServletContextLogger(javax.servlet.ServletContext cx) {
			super(System.out);
			this.cx= cx;
		}
		public void print(String s) {
			cx.log(s);
		}
	}
	static final Object lock= new Object();
	static PrintStream logger= null;

	public RADServlet() {
	}

	public void init() {
		synchronized (lock) {
			if (logger == null) {
				logger= new ServletContextLogger(getServletContext());
				System.setOut(logger);
				System.setErr(logger);
				DriverManager.setLogWriter(new PrintWriter(logger));
				logger.println("lalala unique string  (init of "+getClass().getName()+")");
			}
		}
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
		// In case of DB communication problems, we might need multiple attempts
		boolean retry= false;
		do {
			try {
				db= DB.getInstance(getServletContext());
				db.activeUser= db.auth.authenticateCurrentUser(req);
				if (db.activeUser == null && db.getRecycleCount() > 0)
					// if noone is logged in, and this is a reused connection
					// we haven't run a query yet, and could have communication
					// problems.  So run a query to test it.
					// We probably need to load user 0 anyway
					db.userLoader.loadById(0);
				try {
					switch (acn) {
					case Get:  doGet(req, resp, db, hgl);  break;
					case Post: doPost(req, resp, db, hgl); break;
					}
				}
				catch (Exception ex) {
					hgl.pException(ex);
				}
				DB.recycleInstance(db);
			}
			catch (Exception ex) {
				if (db != null) {
					try {
						db.disconnect();
					}
					catch (Exception e) {}
					// if we have communication problems, try again, unless we had a fresh connection
					retry= db.getRecycleCount() > 0;
					db= null;
				}
				// If we're giving up, show the exception
				if (!retry)
					hgl.pException(ex);
			}
		} while (retry);
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		super.doGet(req, resp);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		super.doPost(req, resp);
	}
}
