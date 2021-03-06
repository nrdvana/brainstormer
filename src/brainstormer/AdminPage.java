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


	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		renderPage(hgl, db);
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		if (db.activeUser == null || !db.activeUser.isMemberOf(db.userLoader.loadByName("Admin")))
			renderDenied(hgl, db);
		else {
			if (req.getParameter("newUser") != null)
				db.userLoader.newUser(req.getParameter("newUserName"), req.getParameter("newUserPass"), new User[0]);
			else if (req.getParameter("newGroup") != null)
				db.userLoader.newGroup(req.getParameter("newGroupName"), new User[0]);
			response.sendRedirect(response.encodeRedirectURL("admin"));
		}
	}

	void renderPage(HtmlGL hgl, DB db) throws IOException {
		hgl.beginPage("Admin", styles, db);
		hgl.p("<div class='content'>\n");
		hgl.p("<form action='admin' method='post'>\n"
			+"Add a user: <input type='text' name='newUserName'/> Pass:<input type='password' name='newUserPass'/><input type='submit' name='newUser' value='Add'/><br/>\n"
			+"Add a group: <input type='text' name='newGroupName'/><input type='submit' name='newGroup' value='Add'/><br/>\n"
			+"</form>");
		hgl.p("</div>\n");
		hgl.endPage();
	}

	void renderDenied(HtmlGL hgl, DB db) {
		hgl.beginPage("Admin", styles, db);
		hgl.p("<div class='content'>\n");
		hgl.beginErrorMsg();
		hgl.p("<h4>You are not an administrator</h4>\n");
		hgl.endErrorMsg();
		hgl.p("</div>\n");
		hgl.endPage();
	}
}
