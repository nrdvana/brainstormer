package brainstormer;

import javax.servlet.http.*;
import java.sql.SQLException;
import java.util.*;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class PostEditPage extends RADServlet {
	static final String[] styles= new String[] { "Page.css", "Widgets.css", "EditUI.css", "ViewUI.css" };

	static class PageFields {
		int PostID= -1;
		String Title= "";
		String[] Keywords= new String[0];
		String Text= "";
		String TextType= "plain";
		Set<Integer> ReplyToSet= new HashSet<Integer>();
		String ReplyToQuery= "";
		Set<Integer> TopicSet= new HashSet<Integer>();
		String TopicQuery= "";
		int ActiveTab= 0;
		SubmitAction userEvent;

		boolean doPreview= false;

		enum SubmitAction {
			MoreKeywords, TabChange, ReplyToSearch, TopicSearch, Preview, Post, None
		};

		public PageFields() {
		}

		public PageFields(HttpServletRequest req) {
			PostID= Util.parseOrDefault(req.getParameter("PostID"), -1);
			Title= Util.trimPossibleNull(req.getParameter("Title"));
			Keywords= removeEmpty(req.getParameterValues("Keywords"));
			TextType= Util.trimPossibleNull(req.getParameter("TextType"));
			if (!TextType.equals("plain") && !TextType.equals("bbbcode"))
				TextType= "plain";
			Text= Util.trimPossibleNull(req.getParameter("Text"));
			addStrArrayToSetOfInt(req.getParameterValues("ReplyToSet"), ReplyToSet);
			ReplyToQuery= Util.trimPossibleNull(req.getParameter("ReplyToQuery"));
			addStrArrayToSetOfInt(req.getParameterValues("TopicSet"), TopicSet);
			TopicQuery= Util.trimPossibleNull(req.getParameter("TopicQuery"));
			ActiveTab= Util.parseOrDefault(req.getParameter("CurTab"), 0);

			userEvent= SubmitAction.None;
			for (SubmitAction acn: SubmitAction.values())
				if (req.getParameter(acn.name()) != null) {
					userEvent= acn;
					break;
				}
		}

		public PageFields(Post p) {
			PostID= p.id;
			Title= Util.trimPossibleNull(p.title);
			Keywords= p.keywords == null? new String[0] : p.keywords;
			Text= Util.trimPossibleNull(p.content.getText());
			for (int id: p.getParents())
				ReplyToSet.add(id);
			for (int id: p.getTopics())
				TopicSet.add(id);
		}

		private static String[] removeEmpty(String[] vals) {
			int nonempty= 0;
			for (int i=0; i<vals.length; i++)
				if (vals[i] != null && (vals[i]=vals[i].trim()).length() > 0)
					nonempty++;
			if (nonempty != vals.length) {
				String[] newVals= new String[nonempty];
				int newPos= 0;
				for (String val: vals)
					if (val != null && val.length() > 0)
						newVals[newPos++]= val;
				vals= newVals;
			}
			return vals;
		}
		private static void addStrArrayToSetOfInt(String[] vals, Set<Integer> result) {
			if (vals != null)
				for (int i=0; i<vals.length; i++)
					result.add(Util.parseOrDefault(vals[i], -1));
			result.remove(-1);
		}
	}

	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		PageFields fields= null;
		if (db.activeUser == null)
			response.sendRedirect(response.encodeRedirectURL("login?goto="+hgl.urlEsc(req.getRequestURI()+"?"+Util.trimPossibleNull(req.getQueryString()))));
		String replyIdStr= req.getParameter("reply");
		String editIdStr= req.getParameter("id");
		if (editIdStr != null) {
			Post p= db.postLoader.loadById(Integer.parseInt(editIdStr));
			if (p == null)
				throw new RuntimeException("Post ID "+editIdStr+" does not exist");
			fields= new PageFields(p);
		}
		else {
			fields= new PageFields();
			if (replyIdStr != null) {
				int replyId= Integer.parseInt(replyIdStr);
				fields.ReplyToSet.add(replyId);
			}
		}
		renderPage(fields, db, hgl);
	}

	public void doPost(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		PageFields fields= new PageFields(req);
		switch (fields.userEvent) {
		case Post:
			Post p= postObjFromFields(fields, db);
			int redirectId= p.id;
			if (p.id == -1)
				redirectId= db.postLoader.createPost(p);
			else
				db.postLoader.store(p);
			response.sendRedirect(response.encodeRedirectURL("view?id="+redirectId));
			break;
		case TabChange:
			fields.ActiveTab= findClickedTabIndex(req.getParameter("TabChange"));
			renderPage(fields, db, hgl);
			break;
		case Preview:
			fields.doPreview= true;
		case MoreKeywords:
		case ReplyToSearch:
		case TopicSearch:
		default:
			renderPage(fields, db, hgl);
		}
	}

	static final String[] tabNames= new String[] { "Text", "Image", "Sketch" };
	int findClickedTabIndex(String submitValue) {
		for (int i=0; i<tabNames.length; i++)
			if (tabNames[i].equals(submitValue))
				return i;
		return 0;
	}

	static Post postObjFromFields(PageFields fields, DB db) throws SQLException {
		Post result= (fields.PostID == -1)? new Post(db.activeUser) : db.postLoader.loadById(fields.PostID);
		result.title= fields.Title;
		result.keywords= fields.Keywords;
		switch (fields.ActiveTab) {
		case 0:
			result.setContent(fields.TextType, fields.Text);
			break;
		default:
			result.setContent("plain", "");
		}
		for (Integer peer: fields.ReplyToSet)
			result.addLink(new Link(result.id, peer, "reply"));
		for (Integer peer: fields.TopicSet)
			result.addLink(new Link(result.id, peer, "topic"));
		return result;
	}

	void renderPage(PageFields fields, DB db, HtmlGL hgl) throws Exception {
		String postTarget= fields.PostID != -1? "edit" : "create";
		hgl.beginPage("Edit Post", styles);
		hgl.pNavBar(db.activeUser);
		hgl.p("<div class='content'>"
			+"<form action='").pURL(postTarget).p("' method='post'><div>\n"
			+"  <input type='hidden' name='CurTab' value='").p(fields.ActiveTab).p("'/>\n"
			+"  <input type='hidden' name='PostID' value='").p(fields.PostID).p("'/>\n");

		if (fields.doPreview) {
			hgl.beginGroupBox("Preview");
			Post post= postObjFromFields(fields, db);
			hgl.p("<div class='post'>\n");
			PostViewPage.renderPostHeader(hgl, post, "", 0);
			PostViewPage.renderPostBody(hgl, post, "", db, Collections.EMPTY_SET);
			hgl.p("</div>\n").endGroupBox();
		}

		hgl.pPrompt("Title").pTextBlank("Title", fields.Title, "title").p("<br/>\n");
		boolean kwExpand= fields.Keywords.length > 0;
		hgl.beginContentToggle("Tags", !kwExpand);
		hgl.p("    ").beginContentSelectorButton("Tags", 1, false).p("<img src='").pURL("skin/img/Plus.gif").p("' alt='expand'/></a>\n    ");
		hgl.pSpace("0.6em", null).pPrompt("Keywords: ...").p("\n");
		hgl.nextContentToggle("Tags", 1, kwExpand);
		hgl.p("    ").beginContentSelectorButton("Tags", 0, false).p("<img src='").pURL("skin/img/Minus.gif").p("' alt='collapse'/></a>\n    ");
		hgl.pSpace("0.6em", null).pPrompt("Keywords:").p("<br/>\n");
		renderKeywordUI(fields, hgl);
		hgl.endContentToggle();

		hgl.beginTabControl("TabChange", tabNames, fields.ActiveTab);
		switch (fields.ActiveTab) {
		case 0:
			hgl.p("Markup type: ").pDropdown("TextType", new String[] {"plain","bbbcode"}, new String[] {"Plain Text", "BBB Code"}, fields.TextType);
			hgl.p("<div class='text-edit'>\n"
				+"<textarea name='Text' rows='20' cols='80'>").pText(fields.Text).p("</textarea>\n"
				+"</div>\n");
			break;
		case 1:
			hgl.p("<div class='file-upload'>\n"
				+"<h2>Unimplemented</h2>\n"
				+"</div>\n");
			break;
		case 2:
			hgl.p("<div class='sketch'>\n"
				+"<h3>Sketch applet not yet implemented</h3>\n"
				+"</div>\n");
			break;
		}
		if (fields.ActiveTab != 0) {
			hgl.p("<input type='hidden' name='TextType' value='").pText(fields.TextType).p("'/>\n");
			hgl.p("<input type='hidden' name='Text' value='").pText(fields.Text).p("'/>\n");
		}
		hgl.endTabControl();

		hgl.p("<div class='container'><div class='post-buttons'><div>\n"
			+"  <input type='submit' name='Preview' value='Preview'/>\n"
			+"  <input type='submit' name='Post' value='Post'/>\n"
			+"</div></div></div>\n");
		hgl.p("<div class='below-post-buttons'>\n");
		renderLinkingUI("ReplyTo", "In reply to", fields.ReplyToSet, fields.ReplyToQuery, db, hgl);
		renderLinkingUI("Topic", "On the same topic as", fields.TopicSet, fields.TopicQuery, db, hgl);
		hgl.p("</div>\n");

		hgl.p("</div></form>\n"
			+"</div>");
		hgl.endPage();
	}

	void renderKeywordUI(PageFields fields, HtmlGL hgl) {
		hgl.p("    <div class='keyword-entry'>\n  ");
		for (String word: fields.Keywords)
			hgl.pTextBlank("Keywords", word).p("\n      ");
		for (int i=0; i<4; i++)
			hgl.pTextBlank("Keywords", "").p("\n      ");
		hgl.p("<br/><input class='btn' type='submit' name='MoreKeywords' value='More...'/>\n"
			+"    </div>\n");
	}

	void renderLinkingUI(String name, String label, Set<Integer> selectedPosts, String query, DB db, HtmlGL hgl) throws Exception {
		query= query == null? "" : query.trim();
		LinkedList<HtmlGL.CheckListItem> items = new LinkedList<HtmlGL.CheckListItem>();
		for (Integer postID : selectedPosts)
			items.add(new HtmlGL.CheckListItem(postID.toString(), renderSearchItem(db.postLoader.loadById(postID.intValue())), true));
		if (query.length() > 0)
			for (PostLoader.ScoredPost sp: db.postLoader.search(query))
				if (!selectedPosts.contains(sp.post.id))
					items.add(new HtmlGL.CheckListItem(Integer.toString(sp.post.id), renderSearchItem(sp.post, sp.score), false));

		boolean expand= query.length() > 0 || items.size() > 0;
		hgl.beginContentToggle(name, !expand);
		hgl.pContentExpandButton(name).pSpace("0.6em", null).pPrompt(label+": ...\n");
		hgl.nextContentToggle(name, 1, expand);
		hgl.pContentCollapseButton(name).pSpace("0.6em", null).pPrompt(label+":\n");
		hgl.p("<div class='container' style='padding-left:1em'>\n");
		if (items.size() > 0)
			hgl.pCheckList(name+"Set", items);
		else
			hgl.pSpace("1em", null).p("No posts selected<br/>\n");
		hgl.pPrompt("Search for more: ").pTextBlank(name+"Query", query);
		hgl.p("<input type='submit' name='").p(name).p("Search' value='Search'/>\n"
			+"</div>\n");
		hgl.endContentToggle();
	}
	String renderSearchItem(Post p) {
		return HtmlGL.esc(p.author.name)+": "+HtmlGL.esc(p.title);
	}
	String renderSearchItem(Post p, int score) {
		return HtmlGL.esc(p.author.name)+": "+HtmlGL.esc(p.title)+"   ("+score+" matches)";
	}
}
