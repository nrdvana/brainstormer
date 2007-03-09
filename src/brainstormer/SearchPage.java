package brainstormer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class SearchPage extends RADServlet {
	static String[] styles= new String[] { "Page.css", "Widgets.css", "SearchUI.css" };

	enum SearchAction {
		None(null, false),
		Standard("Search", false),
		All("All Posts", true),
		Orphans("Orphaned Posts", true);

		public final String displayName;
		public final boolean isSpecial;

		private SearchAction(String displayName, boolean isSpecial) {
			this.displayName= displayName;
			this.isSpecial= isSpecial;
		}
	}

	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		String phrase= Util.trimPossibleNull(req.getParameter("q"));
		String actionName= Util.trimPossibleNull(req.getParameter("action"));
		SearchAction action= SearchAction.None;
		for (SearchAction ss: SearchAction.values())
			if (actionName.equals(ss.displayName)) {
				action= ss;
				break;
			}
		hgl.beginPage("Search", styles, db);
		hgl.p("<div class='content'>\n");
		renderSearchOptions(phrase, hgl);
		if (action != SearchAction.None) {
			Collection results;
			switch (action) {
			case Standard:
				results= db.postLoader.search(phrase);
				break;
			case All:
				int page= Util.parseOrDefault(req.getParameter("page"), 0);
				results= db.postLoader.searchAll(page*50, 50);
				break;
			case Orphans:
				results= db.postLoader.searchOrphans();
				break;
			default:
				throw new RuntimeException("Unhandled input");
			}
			renderSearchResults(results, hgl);
		}
		hgl.p("</div>\n");
		hgl.endPage();
	}

	static void renderSearchOptions(String prevQuery, HtmlGL hgl) {
		hgl.beginGroupBox("Search");
		hgl.p("  <form action='search' method='get'><div>\n"
			+"    ").pPrompt("Text search: ").pTextBlank("q", prevQuery, "mainsearch").p("<input type='submit' name='action' value='Search'/><br/><br/>\n"
			+"    ").pPrompt("Special Searches:").p("\n"
			+"    <div>\n");
		for (SearchAction option: SearchAction.values())
			if (option.isSpecial)
				hgl.p("      <input type='submit' name='action' value='").pText(option.displayName).p("'/>&nbsp;&nbsp;\n");
		hgl.p("    </div\n"
			+"  ></div></form\n>");
		hgl.endGroupBox();
	}

	static void renderSearchResults(Collection results, HtmlGL hgl) {
		boolean scores= results.size() > 0 && results.iterator().next() instanceof PostLoader.ScoredPost;
		hgl.beginGroupBox();
		hgl.p("  <table class='searchresult' width='100%'>\n"
			+"    <col width='1em' class='link'/>\n");
		if (scores)
			hgl.p("    <col width='2em' class='relevance' align='right'/>\n");
		hgl.p("    <col width='15%' class='author' align='center'/>\n"
			+"    <col width='70%' class='title'/>\n"
			+"    <col width='0*' class='date'/>\n"
			+"    <thead><tr><th></th>");
		if (scores)
			hgl.p("<th>Relevance</th>");
		hgl.p("<th>Author</th><th class='title'>Title</th><th>Date</th></tr></thead>\n"
			+"    <tbody>\n");
		for (Object obj: results) {
			PostLoader.ScoredPost sp= scores? (PostLoader.ScoredPost) obj : null;
			Post p= !scores? (Post) obj : sp.post;
			hgl.p("      <tr><td><a class='btn' href='").pURL("view?id="+p.id).p("'><img src='").pURL("skin/img/Retree.gif").p("' alt='retree'/></a></td>\n");
			if (scores)
				hgl.p("        <td>").pText(""+sp.score).p("</td>\n");
			hgl.p("        <td><a href='").pURL("members?id="+p.author.id).p("'>").pText(p.author.name).p("</a></td>\n"
				+"        <td><a href='").pURL("view?id="+p.id).p("'>").pText(p.title).p("</a></td>\n"
				+"        <td class='date'>").pTimestamp(p.editTime).p("</td></tr>\n");
		}
		if (results.size() == 0)
			hgl.p("        <tr><td colspan='4'>No Matching Posts</td></tr>\n");
		hgl.p("    </tbody>\n  </table>\n");
		hgl.endGroupBox();
	}
}
