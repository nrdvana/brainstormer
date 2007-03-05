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
	public MainPage() {
	}

	static final String[] styles= new String[] { "Page.css" };

	protected void doGet(HttpServletRequest req, HttpServletResponse resp, DB db, HtmlGL hgl) throws Exception {
		if (req.getPathInfo() != null)
			resp.sendError(404);
		else {
			hgl.beginPage("Brainstormer", styles);
			hgl.pNavBar(db.activeUser);
			hgl.p("<h3>Brainstormer</h3>\n");
			hgl.endPage();
		}
	}
}
