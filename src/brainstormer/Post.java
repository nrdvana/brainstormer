package brainstormer;

import java.sql.*;
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
public class Post {
	int id;
	String title;
	String[] keywords;
	User author;
	java.sql.Timestamp postTime, editTime;
	PostContent content;

	HashMap<String,Set<Link>>
		inEdges= new HashMap<String,Set<Link>>(),
		outEdges= new HashMap<String,Set<Link>>();

	void updateId(int newVal) {
		for (Set<Link> links: outEdges.values())
			for (Link l: links)
				l.node= newVal;
		for (Set<Link> links: inEdges.values())
			for (Link l: links)
				l.peer= newVal;
		id= newVal;
	}

	void addLink(Link l) {
		HashMap<String,Set<Link>> dest= (l.node == id)? outEdges : inEdges;
		Set<Link> set= dest.get(l.relation);
		if (set == null) {
			set= new HashSet<Link>();
			dest.put(l.relation, set);
		}
		set.add(l);
	}
	void wipeLinks() {
		inEdges.clear();
		outEdges.clear();
	}

	int[] getParents() {
		return getPostSet(true, "reply");
	}

	int[] getChildren() {
		return getPostSet(false, "reply");
	}

	int[] getTopics() {
		return getPostSet(true, "topic");
	}

	int[] getTopicRelatives() {
		return getPostSet(false, "topic");
	}

	private int[] getPostSet(boolean outgoing, String relation) {
		Set<Link> parentSet= (outgoing? outEdges : inEdges).get(relation);
		if (parentSet == null)
			return new int[0];
		else {
			int[] result= new int[parentSet.size()];
			int i= 0;
			for (Link l: parentSet)
				result[i++]= outgoing? l.peer : l.node;
			return result;
		}
	}
}

class Link {
	int node, peer;
	String relation;

	public Link() {}
	public Link(int node, int peer, String relation) {
		this.node= node;
		this.peer= peer;
		this.relation= relation.intern();
	}
	public int hashCode() {
		return node ^ (peer<<12) ^ (peer>>20) & relation.hashCode();
	}
}

interface PostContent {
	public String getText();
	public String getDBContentType();
	public void render(HtmlGL hgl);
}

class PostTextContent implements PostContent {
	String text;
	public PostTextContent(String text) {
		this.text= text;
	}
	public String getDBContentType() {
		return "plain";
	}
	public String getText() {
		return text;
	}
	public void render(HtmlGL hgl) {
		hgl.pTextMultiline(text);
	}
}

class PostWikiContent implements PostContent {
	String text;
	public PostWikiContent(String text) {
		this.text= text;
	}
	public String getDBContentType() {
		return "wiki";
	}
	public String getText() {
		return text;
	}
	public void render(HtmlGL hgl) {
		hgl.pText(text);
	}
}

class PostBBBCodeContent implements PostContent {
	String text;
	public PostBBBCodeContent(String text) {
		this.text= text;
	}
	public String getDBContentType() {
		return "bbbcode";
	}
	public String getText() {
		return text;
	}
	public void render(HtmlGL hgl) {
		BastardizedBulletinBoardCode.render(text, hgl);
	}
}

class PostFileContent implements PostContent {
	String fileId;
	public String getDBContentType() {
		return "file";
	}
	public String getText() {
		return null;
	}
	public void render(HtmlGL hgl) {
		hgl.p("<h3>Unimplemented</h3>\n(file ").pText(fileId).p(")<br/>");
	}
}

class NoContent implements PostContent {
	public String getText() {
		return null;
	}
	public String getDBContentType() {
		return "none";
	}
	public void render(HtmlGL hgl) {
	}
	static final NoContent INSTANCE= new NoContent();
}
