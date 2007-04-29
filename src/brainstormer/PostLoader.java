package brainstormer;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class PostLoader {
	DB db;

	static final String
		postFields= "p.ID, p.AuthorID, p.Title, p.PostTime, p.EditTime, p.ContentType, p.Text ",
		postTable= "Post p ",
		keywordFields= "k.Keyword ",
		keywordTable= "Keyword k ",
		linkFields= "l.Node, l.Peer, l.Relation ",
		linkTable= "Link l ";

	final PreparedStatement
		s_SelectPostFields, s_SelectPostKeywords, s_SelectPostLinks,
		s_SearchForPosts, s_SelectAllPosts, s_SelectLonePosts, s_SelectRootPosts,
		s_InsertPost, s_UpdatePost, s_WipeKeywords, s_InsertKeyword, s_WipeLinks, s_InsertLink;

	public PostLoader(DB db) throws SQLException {
		this.db= db;
		s_SelectPostFields= db.prep("SELECT "+postFields+"FROM "+postTable+"WHERE p.ID = ?");
		s_SelectPostKeywords= db.prep("SELECT "+keywordFields+"FROM "+keywordTable+"WHERE k.PostID = ?");
		s_SelectPostLinks= db.prep("SELECT "+linkFields+"FROM "+linkTable+"WHERE l.Node = ? or l.Peer = ?");
		s_SearchForPosts= db.prep(
			"SELECT "+postFields+", count(k.PostID) as KwMatches FROM "+postTable+"LEFT JOIN Keyword k on p.ID = k.PostID AND k.Keyword LIKE ? "
			+"WHERE Title LIKE ? OR Text LIKE ? GROUP BY p.ID");
		s_SelectAllPosts= db.prep("SELECT "+postFields+"FROM "+postTable+"LIMIT ? OFFSET ?");
		s_SelectLonePosts= db.prep(
			"SELECT "+postFields+", l_in.Node, l_out.Node FROM "+postTable
			+"LEFT JOIN Link l_in ON l_in.Peer = p.ID "
			+"LEFT JOIN Link l_out ON l_out.Node = p.ID "
			+"WHERE l_in.Node IS NULL AND l_out.Node IS NULL");
		s_SelectRootPosts= db.prep(
			"SELECT "+postFields+", l_out.Node FROM "+postTable
			+"LEFT JOIN Link l_out ON l_out.Node = p.ID AND l_out.Relation = 'reply' "
			+"WHERE l_out.Node IS NULL");
		s_InsertPost= db.prep("INSERT INTO Post (Title, ContentType, Text, AuthorID, PostTime, EditTime) VALUES (?, ?, ?, ?, NOW(), NOW())");
		s_UpdatePost= db.prep("UPDATE Post SET Title = ?, ContentType = ?, Text = ?, EditTime = NOW() WHERE ID = ?");
		s_WipeKeywords= db.prep("DELETE FROM Keyword WHERE PostID = ?");
		s_InsertKeyword= db.prep("INSERT INTO Keyword (PostID, Keyword) VALUES (?, ?)");
		s_WipeLinks= db.prep("DELETE FROM Link WHERE Node = ?");
		s_InsertLink= db.prep("INSERT INTO Link (Node, Peer, Relation) VALUES (?, ?, ?)");
	}

	public Post loadById(int searchId) throws SQLException {
		Post ret= null;
		s_SelectPostFields.setInt(1, searchId);
		ResultSet rs= s_SelectPostFields.executeQuery();
		if (rs.next()) {
			ret= postFromResultSet(rs);
			if (ret.keywords == null)
				loadKeywordsFor(ret);
			if (ret.inEdges.size() == 0 && ret.outEdges.size() == 0)
				loadLinksFor(ret);
		}
		rs.close();
		return ret;
	}

	public static class ScoredPost implements Comparable {
		public Post post;
		public int score;
		ScoredPost(Post p, int score) {
			this.post= p;
			this.score= score;
		}
		public int compareTo(Object other) {
			int otherScore= ((ScoredPost) other).score;
			if (score == otherScore)
				return 0;
			else if (score < otherScore)
				return 1;
			else
				return -1;
		}

		static int calcScore(Post p, int kwMatches, Pattern wordPattern) {
			int titleMatch= countInstances(p.title, wordPattern);
			int bodyMatch= countInstances(p.content.getText(), wordPattern);
			return kwMatches*3+titleMatch*2+bodyMatch;
		}

		static int countInstances(String text, Pattern pattern) {
			int result= 0;
			Matcher m= pattern.matcher(text);
			for (int pos= 0; m.find(pos); pos=m.end())
				result++;
			return result;
		}
	}

	static Pattern wordSanitizer= Pattern.compile("([^\\w])");
	static String toSQL(String word) {
		return wordSanitizer.matcher(word).replaceAll("?");
	}
	static String toRegexStr(String word) {
		return wordSanitizer.matcher(word).replaceAll("\\\\1");
	}

	public Collection<ScoredPost> search(String phrase) throws SQLException {
		HashMap<Integer,ScoredPost> postScores= new HashMap<Integer,ScoredPost>();
		for (String word: phrase.split("[ \u0001\t\n\r]+")) {
			String param= '%'+toSQL(word)+'%';
			s_SearchForPosts.setString(1, param);
			s_SearchForPosts.setString(2, param);
			s_SearchForPosts.setString(3, param);
			ResultSet rs= s_SearchForPosts.executeQuery();
			if (!rs.isAfterLast()) {
				Pattern wordPattern= Pattern.compile(toRegexStr(word), Pattern.CASE_INSENSITIVE);
				while (rs.next()) {
					int id= rs.getInt(1);
					int kwMatches= rs.getInt(rs.findColumn("KwMatches"));
					ScoredPost sp= postScores.get(id);
					if (sp == null) {
						Post p= postFromResultSet(rs);
						sp= new ScoredPost(p, ScoredPost.calcScore(p, kwMatches, wordPattern));
						postScores.put(id, sp);
					}
					else
						sp.score+= ScoredPost.calcScore(sp.post, kwMatches, wordPattern);
				}
			}
			rs.close();
		}
		ArrayList<ScoredPost> list= new ArrayList<ScoredPost>(postScores.size());
		list.addAll(postScores.values());
		Collections.sort(list);
		return list;
	}

	public Collection<Post> searchAll(int offset, int count) throws SQLException {
		ArrayList<Post> result= new ArrayList<Post>(count);
		s_SelectAllPosts.setInt(1, count);
		s_SelectAllPosts.setInt(2, offset);
		ResultSet rs= s_SelectAllPosts.executeQuery();
		while (rs.next())
			result.add(postFromResultSet(rs));
		return result;
	}

	public Collection<Post> searchOrphans() throws SQLException {
		LinkedList<Post> result= new LinkedList<Post>();
		ResultSet rs= s_SelectLonePosts.executeQuery();
		while (rs.next())
			result.add(postFromResultSet(rs));
		return result;
	}

	public Collection<Post> searchRootPosts() throws SQLException {
		LinkedList<Post> result= new LinkedList<Post>();
		ResultSet rs= s_SelectRootPosts.executeQuery();
		while (rs.next())
			result.add(postFromResultSet(rs));
		return result;
	}

	public int createPost(Post p) throws SQLException {
		if (p.id != -1)
			throw new RuntimeException("Post already exists (id "+p.id+")");
		return createPost(p.author, p.title, p.content, p.keywords, p.getOutgoingEdges());
	}

	public int createPost(User author, String title, PostContent content, String[] keywords, Collection<Link> links) throws SQLException {
		if (db.activeUser == null)
			throw new UserException("You must be logged in before you can create/edit posts");
		if (!db.activeUser.isMemberOf(author))
			throw new UserException("You don't have permission to post as "+author.name);
		PreparedStatement stmt= s_InsertPost;
		stmt.setString(1, title);
		stmt.setString(2, content.getDBContentType());
		stmt.setString(3, content.getText());
		stmt.setInt(4, author.id);
		stmt.executeUpdate();
		ResultSet rs= stmt.getGeneratedKeys();
		rs.next();
		int postId= rs.getInt(1);
		rs.close();
		storeKeywords(postId, keywords);
		storeLinks(postId, links);
		return postId;
	}

	public void store(Post p) throws SQLException {
		if (db.activeUser == null)
			throw new UserException("You must be logged in before you can create/edit posts");
		if (p.id == -1)
			createPost(p);
		else {
			if (!db.activeUser.isMemberOf(p.author))
				throw new UserException("You don't have permission to edit posts made by "+p.author.name);
			PreparedStatement stmt= s_UpdatePost;
			stmt.setString(1, p.title);
			stmt.setString(2, p.content.getDBContentType());
			stmt.setString(3, p.content.getText());
			stmt.setInt(4, p.id);
			int changed= stmt.executeUpdate();
			if (changed != 1)
				throw new UserException("Failed to update post  (maybe it doesn't exist anymore?)");
			storeKeywords(p.id, p.keywords);
			storeLinks(p.id, p.getOutgoingEdges());
		}
	}

	private Post postFromResultSet(ResultSet rs) throws SQLException {
		int id= rs.getInt(1);
		Post ret= db.postCache.get(id);
		if (ret == null) {
			User author= db.userLoader.loadById(rs.getInt(2));
			ret= new Post(id, author, rs.getTimestamp(4));
			db.postCache.put(id, ret);
			ret.title= rs.getString(3);
			ret.editTime= rs.getTimestamp(5);
			String contentType= rs.getString(6);
			ret.setContent(contentType, rs.getString(7));
		}
		return ret;
	}

	private void loadKeywordsFor(Post p) throws SQLException {
		s_SelectPostKeywords.setInt(1, p.id);
		ResultSet rs= s_SelectPostKeywords.executeQuery();
		ArrayList<String> keywords= new ArrayList<String>();
		while (rs.next())
			keywords.add(rs.getString(1));
		rs.close();
		p.keywords= keywords.toArray(new String[keywords.size()]);
	}

	private void storeKeywords(int id, String[] keywords) throws SQLException {
		s_WipeKeywords.setInt(1, id);
		s_WipeKeywords.executeUpdate();
		for (String keyword: keywords) {
			s_InsertKeyword.setInt(1, id);
			s_InsertKeyword.setString(2, keyword);
			s_InsertKeyword.addBatch();
		}
		int[] result= s_InsertKeyword.executeBatch();
		DB.assertBatchSucceed(result, "inserting keywords");
	}

	private void loadLinksFor(Post p) throws SQLException {
		s_SelectPostLinks.setInt(1, p.id);
		s_SelectPostLinks.setInt(2, p.id);
		ResultSet rs= s_SelectPostLinks.executeQuery();
		while (rs.next())
			p.addLink(new Link(rs.getInt(1), rs.getInt(2), rs.getString(3)));
		rs.close();
	}

	private void storeLinks(int id, Collection<Link> links) throws SQLException {
		s_WipeLinks.setInt(1, id);
		s_WipeLinks.executeUpdate();
		for (Link l: links) {
			s_InsertLink.setInt(1, id);
			s_InsertLink.setInt(2, l.peer);
			s_InsertLink.setString(3, l.relation);
			s_InsertLink.addBatch();
		}
		int[] result= s_InsertLink.executeBatch();
		DB.assertBatchSucceed(result, "inserting keywords");
	}

	static final String PostSelect= null;
}
