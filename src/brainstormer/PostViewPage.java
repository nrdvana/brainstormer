package brainstormer;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.http.*;
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
public class PostViewPage extends RADServlet {
	static final String[] styles= new String[] { "Page.css", "Widgets.css", "ViewUI.css" };

	public void doGet(HttpServletRequest req, HttpServletResponse response, DB db, HtmlGL hgl) throws Exception {
		int rootId;
		try {
			rootId= Integer.parseInt(req.getParameter("id"));
		}
		catch (Exception ex) {
			response.sendRedirect(response.encodeRedirectURL("view?id=1"));
			return;
		}
		hgl.beginPage("Viewing tree rooted at post "+rootId, styles);
		hgl.pNavBar(db.activeUser);
		hgl.p("<div class='content'>");
		Post root= db.postLoader.loadById(rootId);
		renderTree(db, hgl, root);
		hgl.p("</div>");
		hgl.endPage();
	}

	public static void renderTree(DB db, HtmlGL hgl, Post root) throws IOException, SQLException {
		if (root == null) {
			hgl.p("<h4>Post does not exist</h4>");
			return;
		}
		int[] parents= root.getParents();
		if (parents.length > 0) {
			hgl.p("<div class='parentlist'>\n"
				+"  <span class='captiion'>In response to:</span>\n"
				+"  <ul>\n");
			for (int postID: parents) {
				Post p= db.postLoader.loadById(postID);
				hgl.p("    <li>");
				renderPostHeader(hgl, p, "", ICON_RETREE);
				hgl.p("</li>\n");
			}
			hgl.p("  </ul>\n</div>\n");
		}
		Set<Post> renderedSet= new HashSet<Post>();
		hgl.p("<div class='postlist'>\n");
		recursiveRenderNode(db, hgl, "r", root, 0, renderedSet);
		hgl.p("</div>\n");
	}

	static void recursiveRenderNode(DB db, HtmlGL hgl, String treePath, Post node, int depth, Set<Post> renderedSet) throws IOException, SQLException {
		if (renderedSet.contains(node)) {
			renderPostHeader(hgl, node, treePath, ICON_JUMPTO|ICON_RETREE);
		}
		else {
			renderedSet.add(node);
			hgl.p("<div class='post' id='p"+node.id).p("'>");
			boolean expanded= depth < 3;
			hgl.beginContentToggle(treePath, !expanded);
			renderPostHeader(hgl, node, treePath, ICON_EXPAND|ICON_RETREE);
			hgl.nextContentToggle(treePath, 1, expanded);
			renderPostHeader(hgl, node, treePath, ICON_COLLAPSE|ICON_RETREE);
			renderPostBody(hgl, node, treePath, db, renderedSet);
			hgl.endContentToggle();
			int[] children= node.getChildren();
			if (children.length > 0) {
				hgl.p("<div class='postchildren'>\n");
				int idx= 0;
				for (int childId: children) {
					Post child= db.postLoader.loadById(childId);
					recursiveRenderNode(db, hgl, treePath+"_"+(idx++), child, depth+1, renderedSet);
				}
				hgl.p("</div>\n");
			}
			hgl.p("</div>\n");
		}
	}

	static final int
		ICON_COLLAPSE= 1,
		ICON_EXPAND= 2,
		ICON_RETREE= 4,
		ICON_JUMPTO= 8;
	static void renderPostHeader(HtmlGL hgl, Post node, String treePath, int iconBitField) throws IOException {
		hgl.p("<div class='posttitle'>\n  ");
		hgl.p("  <span class='date'>").pTimestamp(node.postTime).p("</span>\n");
		if ((iconBitField&ICON_COLLAPSE) != 0) {
			hgl.beginContentSelectorButton(treePath, 0, true);
			hgl.p("<img src='").pURL("skin/img/Minus.gif").p("' alt='collapse'/></a>\n  ");
		}
		if ((iconBitField&ICON_EXPAND) != 0) {
			hgl.beginContentSelectorButton(treePath, 1, true);
			hgl.p("<img src='").pURL("skin/img/Plus.gif").p("' alt='expand'/></a>\n  ");
		}
		if ((iconBitField&ICON_RETREE) != 0) {
			hgl.p("<a class='btn' href='view?id=").p(node.id).p("'><img src='").pURL("skin/img/Retree.gif").p("' alt='retree'/></a>\n  ");
		}
		if ((iconBitField&ICON_JUMPTO) != 0) {
			hgl.p("<a class='btn' href='#p").p(node.id).p("'><img src='").pURL("skin/img/JumpTo.gif").p("' alt='jump to'/></a>\n  ");
		}
		hgl.p("<a class='author' href='members?id=").p(node.author.id).p("'>").pText(node.author.name).p("</a>\n");
		hgl.p("  <span class='title'>").pText(node.title).p("</span>\n");
		hgl.p("</div>\n");
	}

	static void renderPostBody(HtmlGL hgl, Post node, String treePath, DB db, Set<Post> renderedSet) throws SQLException {
		writeKeywords(hgl, node);
		if (node.author.avatar != null)
			hgl.p("<table class='posttable' cellspacing='0'><tr>\n"
				+"  <td width='96'><div class='avatar'>\n"
				+"    <img width='96' height='96' alt=\"user's avatar\" src='").pText(node.author.avatar).p("'/>"
				+"  </td><td>\n");
		hgl.p("<div class='postbody'><div class='").p(node.content.getDBContentType()).p("'>\n");
		node.content.render(hgl);
		hgl.p("\n</div></div>\n");
		writeActionBar(hgl, node);
		writeTopicRelations(hgl, node, db, renderedSet);
		if (node.author.avatar != null)
			hgl.p("  </td>\n</tr></table>\n");
	}

	static void writeKeywords(HtmlGL hgl, Post p) {
		if (p.keywords.length > 0) {
			hgl.p("<div class='keywords'>\n").beginMenu("Keywords:");
			for (String word: p.keywords)
				hgl.p("    <dd>").pText(word).p("</dd>\n");
			hgl.endMenu().p("</div>\n");
		}
	}

	static void writeTopicRelations(HtmlGL hgl, Post node, DB db, Set<Post> renderedSet) throws SQLException {
		int[] outTopicLinks= node.getTopics();
		int[] inTopicLinks= node.getTopicRelatives();
		if (outTopicLinks.length+inTopicLinks.length > 0) {
			hgl.p("<div class='topic-rel'>").beginMenu("topic-related posts:");
			HashSet<Integer>listed= new HashSet<Integer>();
			for (int peer: outTopicLinks) {
				renderRelationLink(hgl, db.postLoader.loadById(peer), renderedSet);
				listed.add(peer);
			}
			for (int peer: node.getTopicRelatives())
				if (!listed.contains(peer))
					renderRelationLink(hgl, db.postLoader.loadById(peer), renderedSet);
			hgl.endMenu().p("</div>\n");
		}
	}
	static void renderRelationLink(HtmlGL hgl, Post p, Set<Post> renderedSet) {
//		hgl.p("  <dd>").pText(p.author.name+" - "+p.title).p("</dd>\n");
		hgl.p("    <dd><a href='").p(renderedSet.contains(p)? "#p" : "view?id=").p(p.id).p("'>").pText(p.author.name+" - "+p.title).p("</a></dd>\n");
	}

	static void writeActionBar(HtmlGL hgl, Post node) {
		hgl.p("<div class='linkbar'>\n"
			+"  <span><a class='edit' href='edit?id=").p(node.id).p("'>Edit</a></span>\n"
			+"  <span><a class='reply' href='create?reply=").p(node.id).p("'>Reply</a></span>\n"
			+"</div>\n");
	}
}
