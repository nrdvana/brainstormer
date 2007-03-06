package brainstormer;

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
public class LoginPage extends RADServlet {
	static final String[] styles= new String[] { "Page.css", "Widgets.css", "LoginUI.css" };

	static class PageFields {
		String userName;
		int userId= -1;
		String password;
		String destination;
		boolean temporary;
		boolean displayFailure= false;

		public PageFields() {}
		public PageFields(HttpServletRequest req) {
			loadFrom(req);
		}
		public void loadFrom(HttpServletRequest req) {
			destination= req.getParameter("goto");
			if (destination == null)
				destination= req.getHeader("HTTP_REFERER");
			if (destination == null)
				destination= "index.html";

			userName= req.getParameter("user");
			userId= Auth.getPrefUserID(req); // get value from cookie
			if (userId == -1)
				userId= Util.parseOrDefault("id", -1); // or from request
			password= req.getParameter("pass");
			temporary= req.getParameter("stayLoggenIn") == null;
		}
	}


	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		PageFields fields= new PageFields(req);
		if (req.getParameter("checkcookie") != null) {
			if (db.activeUser != null && db.activeUser.id == Util.parseOrDefault(req.getParameter("expectedId"), -2))
				response.sendRedirect(response.encodeRedirectURL(fields.destination));
			else
				renderMissingCookieError(hgl);
		}
		else if (req.getServletPath().equals("/logout")) {
			response.addCookie(Auth.getLogoutCookie());
			response.sendRedirect(response.encodeRedirectURL(fields.destination));
		}
		else
			renderLoginForm(fields, hgl);
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		PageFields fields= new PageFields(req);
		if (db.auth.tryLogin(fields.userName, fields.password)) {
			Cookie c= db.auth.getLoginCookie();
			c.setMaxAge(-1);
			c.setPath(req.getContextPath());
			response.addCookie(c);
			response.sendRedirect(response.encodeRedirectURL("login?checkcookie=y&expectedId="+db.activeUser.id+"&goto="+hgl.urlEsc(fields.destination)));
		}
		else {
			fields.displayFailure= true;
			renderLoginForm(fields, hgl);
		}
	}

	public static void renderLoginForm(PageFields fields, HtmlGL hgl) {
		hgl.beginPage("Login", styles);
		hgl.p("<div class='content'>\n");
		if (fields.displayFailure) {
			hgl.p("  ").beginErrorMsg("Login failed");
			hgl.p("Wrong username or password.\n  ");
			hgl.endErrorMsg();
		}
		hgl.p("  <form class='login' method='post' action='login'>\n    ");
		hgl.beginGroupBox("Log In");
		hgl.p("      <input type='hidden' name='goto' value='").pText(fields.destination).p("'/>\n"
			+"      <table class='prompts'>\n"
			+"        <tr><td>User&nbsp;Name:</td><td>").p("<input type='text' size='15' name='user' value='").pText(Util.trimPossibleNull(fields.userName)).p("'/></td></tr>\n"
			+"        <tr><td>Password:</td><td>").p("<input type='password' size='15' name='pass' value=''/></td></tr>\n"
			+"      </table>\n"
			+"      <div>\n"
			+"        <input type='submit' name='submit' value='Log In'/>\n"
			+"        <input type='reset' value='Reset'/><br/>\n"
			+"        <input type='checkbox' name='stayLoggedIn'").p(fields.temporary? "" : " checked='checked'").p("/>&nbsp;Stay&nbsp;logged&nbsp;in&nbsp;forever\n"
			+"      </div>\n    ");
		hgl.endGroupBox();
		hgl.p("  </form>\n"
			+"</div>");
		hgl.endPage();
	}

	public static void renderMissingCookieError(HtmlGL hgl) {
		hgl.beginPage("Enable Cookies", styles);
		hgl.beginErrorMsg();
		hgl.pText("Login succeeded, but the cookie didn't stick.").p("<br/>\n Please enable cookies.\n");
		hgl.endErrorMsg();
		hgl.endPage();
	}
}
