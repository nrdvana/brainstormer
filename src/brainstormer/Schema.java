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
public class Schema {
	String[] upgradeCmds;

	public Schema(String[] upgradeCommands) {
		this.upgradeCmds= upgradeCommands;
	}

	public void deploy(DB db) throws SQLException {
		int schemaVersion= getVersion();
		Statement stmt= db.conn.createStatement();
		for (String cmd: upgradeCmds)
			stmt.execute(cmd);
		db.storeSchemaVersion(schemaVersion);
	}

	public int getVersion() {
		int version= -1;
		for (int i=0; i<SCHEMAS.length; i++)
			if (SCHEMAS[i] == this)
				version= i;
		if (version == -1)
			throw new RuntimeException("Cant find the index for this schema");
		return version;
	}

	static int getMaxVersion() {
		return SCHEMAS.length-1;
	}

	static final Schema[] SCHEMAS= new Schema[] {
		new Schema(new String[] {
			"CREATE TABLE SchemaVersion ( Version integer not null )",
			"INSERT INTO SchemaVersion VALUES (0)",
		}),
		new Schema(new String[] {
			"CREATE TABLE Post ("
			+" ID integer unsigned not null auto_increment,"
			+" AuthorID integer not null,"
			+" Title varchar(96),"
			+" PostTime timestamp not null default CURRENT_TIMESTAMP,"
			+" EditTime timestamp not null,"
			+" ContentType char(8) not null default 'plain',"
			+" Text text,"
			+" PRIMARY KEY (ID)"
			+")",
			"CREATE TABLE Keyword ("
			+" PostID integer unsigned not null,"
			+" Keyword varchar(24),"
			+" PRIMARY KEY (PostID, Keyword),"
			+" KEY PostLookup (PostID),"
			+" FULLTEXT KEY WordSearch (Keyword)"
			+")",
			"CREATE TABLE Link ("
			+" Node integer unsigned not null,"
			+" Peer integer unsigned not null,"
			+" Relation char(8),"
			+" PRIMARY KEY (Node, Peer, Relation)"
			+")",
			"CREATE TABLE User ("
			+" ID integer unsigned not null auto_increment,"
			+" IsGroup boolean not null default false,"
			+" Name varchar(32) not null,"
			+" PassHash varchar(32),"
			+" DateJoined timestamp not null default CURRENT_TIMESTAMP,"
			+" AvatarURL varchar(256),"
			+" PRIMARY KEY (ID),"
			+" UNIQUE KEY NameLookup (Name)"
			+")",
			"CREATE TABLE GroupMembership ("
			+" UserID integer unsigned not null,"
			+" GroupID integer unsigned not null,"
			+" PRIMARY KEY (UserID, GroupID),"
			+" KEY UserLookup (UserID)"
			+")",
			"INSERT INTO User (IsGroup, Name, PassHash) VALUES (1, 'Admin', '-'), (0, 'Guest', '-')",
			"UPDATE User SET ID = 0 WHERE Name = 'Admin'", // ensure admin group has ID = 0
			"INSERT INTO Post (AuthorID, Title, EditTime, ContentType, Text) VALUES (0, 'Root', NOW(), 'plain', 'Welcome to Brainstormer')",
			"UPDATE Post SET ID = 0", // ensure root post has ID = 0
		}),
	};
}

