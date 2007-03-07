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
		renderPage(db, hgl);
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		if (db.activeUser == null || !db.activeUser.isMemberOf(db.userLoader.loadByName("Admin")))
			renderDenied(hgl);
		else {
			if (req.getParameter("newUser") != null)
				addUser(req.getParameter("newUserName"), req.getParameter("newUserPass"), false, hgl);
			else if (req.getParameter("newGroup") != null)
				addUser(req.getParameter("newGroupName"), "", true, hgl);
			response.sendRedirect(response.encodeRedirectURL("admin"));
		}
	}

	void renderPage(DB db, HtmlGL hgl) throws IOException {
		hgl.beginPage("Admin", styles);
		hgl.pNavBar(db.activeUser);
		hgl.p("<div class='content'>\n");
		hgl.p("<form action='admin' method='post'>\n"
			+"Add a user: <input type='text' name='newUserName'/> Pass:<input type='password' name='newUserPass'/><input type='submit' name='newUser' value='Add'/><br/>\n"
			+"Add a group: <input type='text' name='newGroupName'/><input type='submit' name='newGroup' value='Add'/><br/>\n"
			+"</form>");
		hgl.p("</div>\n");
		hgl.endPage();
	}

	void renderDenied(HtmlGL hgl) {
		hgl.beginPage("Admin", styles);
		hgl.p("<div class='content'>\n");
		hgl.beginErrorMsg();
		hgl.p("<h4>You are not an administrator</h4>\n");
		hgl.endErrorMsg();
		hgl.p("</div>\n");
		hgl.endPage();
	}

	void addUser(String name, String password, boolean isGroup, HtmlGL hgl) throws Exception {
		DB db= DB.getInstance(getServletContext());
		if (db.userLoader.loadByName(name) != null)
			throw new UserException("Error: User name "+name+" already exists.");
		if (isGroup)
			db.userLoader.newGroup(name);
		else
			db.userLoader.newUser(name, password);
	}
}
