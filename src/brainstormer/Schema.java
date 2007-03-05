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
	Table[] tables;

	public Schema(Table[] tables) {
		this.tables= tables;
	}

	public Table getTable(String name) {
		for (int i=0; i<tables.length; i++)
			if (tables[i].name.equals(name))
				return tables[i];
		return null;
	}

	public static class Table {
		String name;
		String[] columns;
		String[] keys;
		public Table(String name, String[] columns, String[] keys) {
			this.name= name;
			this.columns= columns;
			this.keys= keys;
		}

		public String getColumnName(String columnDef) {
			return columnDef.substring(0, columnDef.indexOf(' ')-1);
		}

		public String getColumnByName(String name) {
			for (int i=0; i<columns.length; i++)
				if (getColumnName(columns[i]).equals(name))
					return columns[i];
			return null;
		}
	}

//	public static void transition(DB db, int fromSchemaVersion, int toSchemaVersion) throws SQLException {
//		Schema from= SCHEMAS[fromSchemaVersion];
//		Schema to= SCHEMAS[toSchemaVersion];
//		for (int tblIdx= 0; tblIdx < to.tables.length; tblIdx++) {
//			Table tbl= to.tables[tblIdx];
//			Table oldTable= from.getTable(tbl.name);
//			if (oldTable == null)
//				db.createTable(tbl);
//			else
//				reviseTable(db, oldTable, tbl);
//		}
//	}

	public void deploy(DB db) throws SQLException {
		int schemaVersion= getVersion();
		for (int i=0; i<tables.length; i++)
			db.createTable(tables[i]);
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
		new Schema(new Table[0]),
		new Schema(new Table[] {
			new Table("SchemaVersion",
				new String[] {
					"Version integer not null"
				},
				new String[0]
			),
			new Table("Post",
				new String[] {
					"ID integer unsigned not null auto_increment",
					"AuthorID integer not null",
					"Title varchar(96)",
					"PostTime timestamp not null default CURRENT_TIMESTAMP",
					"EditTime timestamp not null",
					"ContentType char(8)",
					"Text text",
				},
				new String[] {
					"PRIMARY KEY (ID)",
				}
			),
			new Table("Keyword",
				new String[] {
					"PostID integer unsigned not null",
					"Keyword varchar(24)"
				},
				new String[] {
					"PRIMARY KEY (PostID, Keyword)",
					"KEY PostLookup (PostID)",
					"FULLTEXT KEY WordSearch (Keyword)",
				}
			),
			new Table("Link",
				new String[] {
					"Node integer unsigned not null",
					"Peer integer unsigned not null",
					"Relation char(8)",
				},
				new String[] {
					"PRIMARY KEY (Node, Peer, Relation)"
				}
			),
			new Table("User",
				new String[] {
					"ID integer unsigned not null auto_increment",
					"IsGroup boolean not null",
					"Name varchar(32) not null",
					"PassHash varchar(32)",
					"DateJoined timestamp not null default CURRENT_TIMESTAMP",
					"AvatarURL varchar(256)",
				},
				new String[] {
					"PRIMARY KEY (ID)",
					"UNIQUE KEY NameLookup (Name)",
				}
			),
			new Table ("GroupMembership",
				new String[] {
					"UserID integer unsigned not null",
					"GroupID integer unsigned not null",
				},
				new String[] {
					"PRIMARY KEY (UserID, GroupID)",
					"KEY UserLookup (UserID)"
				}
			),
		}),
	};
}

