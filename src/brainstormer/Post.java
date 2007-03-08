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
	final int id;
	final User author;
	final java.sql.Timestamp postTime;
	java.sql.Timestamp editTime;
	String title;
	String[] keywords= EMPTY_KEYWORDS;
	PostContent content;

	Post(User author) {
		this.id= -1;
		this.author= author;
		this.postTime= new java.sql.Timestamp(System.currentTimeMillis());
	}

	Post(int id, User author, java.sql.Timestamp postTime) {
		this.id= id;
		this.author= author;
		this.postTime= postTime;
	}

	static final String[] EMPTY_KEYWORDS= new String[0];

	HashMap<String,Set<Link>>
		inEdges= new HashMap<String,Set<Link>>(),
		outEdges= new HashMap<String,Set<Link>>();

	Collection<Link> getOutgoingEdges() {
		LinkedList<Link> result= new LinkedList<Link>();
		for (Set<Link> s: outEdges.values())
			result.addAll(s);
		return result;
	}

	void addLink(Link l) {
		HashMap<String,Set<Link>> dest= (l.node == id)? outEdges : (l.peer == id)? inEdges : null;
		if (dest == null)
			throw new RuntimeException("Link "+l+" is not connected to Post #"+id);
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

	void setContent(String contentType, String Text) {
		if (contentType.equals("plain"))
			content= new PostTextContent(Text);
		else if (contentType.equals("bbbcode"))
			content= new PostBBBCodeContent(Text);
		else if (contentType.equals("wiki"))
			content= new PostBBBCodeContent(Text);
		else
			throw new RuntimeException("Unsupported content type: "+contentType);
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
	public boolean equals(Object other) {
		return (other instanceof Link)
			&& node == ((Link)other).node
			&& peer == ((Link)other).peer
			&& relation.equals(((Link)other).relation);
	}
	public int hashCode() {
		return node ^ (peer<<12) ^ (peer>>20) & relation.hashCode();
	}
	public String toString() {
		return "["+node+","+peer+","+relation+"]";
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
