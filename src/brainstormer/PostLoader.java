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
public class PostLoader {
	DB db;

	static final String
		postFields= "p.ID, p.AuthorID, p.Title, p.PostTime, p.EditTime, p.ContentType, p.Text ",
		postTable= "Post p ",
		keywordFields= "k.Keyword ",
		keywordTable= "Keyword k ",
		linkFields= "l.Node, l.Peer, l.Relation ",
		linkTable= "Link l ";

	PreparedStatement
		s_SelectPostFields, s_SelectPostKeywords, s_SelectPostLinks,
		s_SearchForPosts, s_SelectAllPosts, s_SelectLonePosts,
		s_InsertPost, s_UpdatePost, s_WipeKeywords, s_InsertKeyword, s_WipeLinks, s_InsertLink;

	public PostLoader(DB db) throws SQLException {
		this.db= db;
		s_SelectPostFields= db.prep("SELECT "+postFields+"FROM "+postTable+"WHERE p.ID = ?");
		s_SelectPostKeywords= db.prep("SELECT "+keywordFields+"FROM "+keywordTable+"WHERE k.PostID = ?");
		s_SelectPostLinks= db.prep("SELECT "+linkFields+"FROM "+linkTable+"WHERE l.Node = ? or l.Peer = ?");
		s_SearchForPosts= db.prep(
			"SELECT "+postFields+", count(k.Keyword) as Matches FROM "+postTable+"LEFT JOIN Keyword k on p.ID = k.PostID "
			+"WHERE k.Keyword LIKE ? GROUP BY p.ID "
			+"UNION ALL SELECT "+postFields+", 1 as Matches FROM "+postTable
			+"WHERE Title LIKE ? "
			+"UNION ALL SELECT "+postFields+", 1 as Matches FROM "+postTable
			+"WHERE Text LIKE ? ");
		s_SelectAllPosts= db.prep("SELECT "+postFields+"FROM "+postTable+"LIMIT ? OFFSET ?");
		s_SelectLonePosts= db.prep(
			"SELECT "+postFields+", l_in.Node, l_out.Node FROM "+postTable
			+"LEFT JOIN Link l_in ON l_in.Peer = p.ID "
			+"LEFT JOIN Link l_out ON l_out.Node = p.ID "
			+"WHERE l_in.Node IS NULL AND l_out.Node IS NULL");
		s_InsertPost= db.prep("INSERT INTO Post (Title, ContentType, Text, AuthorID, PostTime, EditTime) VALUES (?, ?, ?, ?, NOW(), NOW())");
		s_UpdatePost= db.prep("UPDATE Post SET Title = ?, ContentType = ?, Text = ?, EditTime = NOW() WHERE ID = ?");
		s_WipeKeywords= db.prep("DELETE FROM Keyword WHERE PostID = ?");
		s_InsertKeyword= db.prep("INSERT INTO Keyword (PostID, Keyword) VALUES (?, ?)");
		s_WipeLinks= db.prep("DELETE FROM Link WHERE Node = ?");
		s_InsertLink= db.prep("INSERT INTO Link (Node, Peer, Relation) VALUES (?, ?, ?)");
	}

	public Post loadById(int searchId) throws SQLException {
		s_SelectPostFields.setInt(1, searchId);
		s_SelectPostFields.execute();
		Post ret= null;
		ResultSet rs= s_SelectPostFields.getResultSet();
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
	}

	public Collection<ScoredPost> search(String phrase) throws SQLException {
		HashMap<Integer,ScoredPost> postScores= new HashMap<Integer,ScoredPost>();
		for (String word: phrase.split("[ \u0001\t\n\r]+")) {
			word= '%'+word+'%';
			s_SearchForPosts.setString(1, word);
			s_SearchForPosts.setString(2, word);
			s_SearchForPosts.setString(3, word);
			ResultSet rs= s_SearchForPosts.executeQuery();
			while (rs.next()) {
				int id= rs.getInt(1);
				int score= rs.getInt(rs.findColumn("Matches"));
				ScoredPost sp= postScores.get(id);
				if (sp == null) {
					sp= new ScoredPost(postFromResultSet(rs), score);
					postScores.put(id, sp);
				}
				else
					sp.score+= score;
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

	public void store(Post p) throws SQLException {
		boolean insert= (p.id == -1);
		if (insert && db.activeUser == null)
			throw new UserException("You must be logged in before you can create posts");
		if (!insert && db.activeUser != p.author)
			throw new UserException("You can only edit posts that you created");
		PreparedStatement stmt= insert? s_InsertPost : s_UpdatePost;
		stmt.setString(1, p.title);
		stmt.setString(2, p.content.getDBContentType());
		stmt.setString(3, p.content.getText());
		stmt.setInt(4, insert? p.author.id : p.id);
		stmt.execute();
		if (insert) {
			ResultSet rs= stmt.getGeneratedKeys();
			rs.next();
			p.updateId(rs.getInt(1));
			rs.close();
		}
		storeKeywords(p);
		storeLinks(p);
	}

	private Post postFromResultSet(ResultSet rs) throws SQLException {
		int id= rs.getInt(1);
		Post ret= db.postCache.get(id);
		if (ret == null) {
			ret= new Post();
			db.postCache.put(id, ret);
			ret.id= id;
			ret.author= db.userLoader.loadById(rs.getInt(2));
			ret.title= rs.getString(3);
			ret.postTime= rs.getTimestamp(4);
			ret.editTime= rs.getTimestamp(5);
			String contentType= rs.getString(6);
			if (contentType.equals("plain"))
				ret.content= new PostTextContent(rs.getString(7));
			else if (contentType.equals("wiki"))
				ret.content= new PostWikiContent(rs.getString(7));
//		else if (contentType.equals("img"))
//			ret.content= new
			else
				ret.content= NoContent.INSTANCE;
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

	private void storeKeywords(Post p) throws SQLException {
		s_WipeKeywords.setInt(1, p.id);
		s_WipeKeywords.execute();
		for (String keyword: p.keywords) {
			s_InsertKeyword.setInt(1, p.id);
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

	private void storeLinks(Post p) throws SQLException {
		s_WipeLinks.setInt(1, p.id);
		s_WipeLinks.execute();
		for (Set<Link> links: p.outEdges.values())
			for (Link l: links) {
				s_InsertLink.setInt(1, p.id);
				s_InsertLink.setInt(2, l.peer);
				s_InsertLink.setString(3, l.relation);
				s_InsertLink.addBatch();
			}
		int[] result= s_InsertLink.executeBatch();
		DB.assertBatchSucceed(result, "inserting keywords");
	}

	static final String PostSelect= null;
}
