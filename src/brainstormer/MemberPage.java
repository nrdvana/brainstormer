package brainstormer;

import java.sql.*;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
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
public class MemberPage extends RADServlet {
	public MemberPage() {
	}

	static final String[] styles= new String[] { "Page.css", "Widgets.css", "MemberUI.css" };

	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		if (req.getPathInfo() == null)
			doRedirect(req, response, db, hgl);
		else {
			String userName= req.getPathInfo().substring(1);
			User user;
			if (userName.equals(""))
				renderMemberList(db, hgl);
			else if ((user=db.userLoader.loadByName(userName)) != null)
				renderUserPage(hgl, db, user);
			else
				response.sendError(404, "User Not Found");
		}
	}

	void doRedirect(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		String userIDParam, userNameParam;
		if ((userIDParam= req.getParameter("id")) != null) {
			User user= db.userLoader.loadById(Integer.parseInt(userIDParam));
			if (user != null)
				response.sendRedirect(response.encodeRedirectURL("members/"+user.name));
			else
				renderUserNotExistError(hgl, userIDParam);
		}
		if ((userNameParam= req.getParameter("u")) != null)
			response.sendRedirect(response.encodeRedirectURL("members/"+userNameParam));
		else
			response.sendRedirect(response.encodeRedirectURL("members/"));
	}

	public static void renderMemberList(DB db, HtmlGL hgl) throws IOException {
		hgl.beginPage("List of members", styles, db);
		hgl.p("<h3>Listing unavailable</h4>");
		hgl.endPage();
	}

	public static void renderUserNotExistError(HtmlGL hgl, String userName) throws IOException {
		hgl.beginPage("User does not exist", styles, null);
		hgl.p("<h3>").pText(userName).p(" is not a valid user name</h4>");
		hgl.endPage();
	}

	public static void renderUserPage(HtmlGL hgl, DB db, User user) throws IOException {
		hgl.beginPage("Member "+user.name, styles, db);
		renderUser(hgl, user);
		hgl.endPage();
	}

	public static void renderUser(HtmlGL hgl, User user) throws IOException {
		hgl.p("<h4>").pText(user.name).p("</h4>");
		hgl.p("<pre>"
			+"\nID: "+user.id);
		hgl.p("\nAvatarURL: ").pText(user.avatar != null? user.avatar : "null");
		hgl.p("\nMember since: ").pText(user.dateJoined.toString());
		hgl.p("\nGroups:</pre>\n<div style='padding-left:2em;'>");
		for (User group: user.groups)
			renderUser(hgl, group);
		hgl.p("</div>");
	}
}
