package brainstormer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class MainPage extends RADServlet {
	static final String[] styles= new String[] { "Page.css", "ViewUI.css" };

	protected void doGet(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		if (req.getPathInfo() != null)
			resp.sendError(404);
		else {
			hgl.beginPage("Brainstormer", styles);
			hgl.pNavBar(db.activeUser);
			hgl.p("<div class='main'>");
			Post root= db.postLoader.loadById(0);
			root.content.render(hgl);
			hgl.p("</div>");
			hgl.endPage();
		}
	}
}
