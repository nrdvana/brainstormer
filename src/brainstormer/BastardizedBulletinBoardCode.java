package brainstormer;

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
public class BastardizedBulletinBoardCode {
	enum Tag {
		U, U_END(U), I, I_END(I), B, B_END(B), CODE, CODE_END(CODE),
		IMG, URL, URL_END(URL),
		TABLE, COL, ROW, TABLE_END(TABLE), LIST, NEXT, LIST_END(LIST);

		public final String html;
		private Tag complement= null;
		public boolean isStart= false;;
		private Tag() {
			html= "<"+name().toLowerCase()+">";
		}
		private Tag(Tag startTag) {
			complement= startTag;
			complement.complement= this;
			complement.isStart= true;
			html= "</"+startTag.name().toLowerCase()+">";
		}

		static final int MAX_TAG_STRING_LEN= 6;
		static final HashMap<String,Tag> map= new HashMap<String,Tag>();
		static {
			for (Tag t: Tag.values())
				map.put(t.html.substring(1, t.html.length()-1), t);
		}

		public Tag getComplement() {
			return complement;
		}
	}

	static class TagTokenizer {
		LinkedList<Tag> tagStack= new LinkedList<Tag>();
		private int pos, nextPos;
		private String source;
		private String tagParam= null;
		private String leftover= null;
		private boolean passFirst= true;

		public TagTokenizer(String str) {
			nextPos= -1;
			source= str;
//			if (str.length() > 0)
//				passFirst= str.charAt(0) != '[';
		}

		public boolean hasNext() {
			return nextPos < source.length() || leftover != null;
		}

		/** Parse the next tag, or string.
		 * This extracts expected tags.  Unexpected tags are ignored and
		 * returned as plain text in an effort to let the user know they
		 * goofed, but without messy parse error messages.
		 *
		 * @return Object Either a Tag or String
		 */
		public Object next() {
			tagParam= null;
			if (leftover != null) {
				String temp= leftover;
				leftover= null;
				return temp;
			}

			pos= nextPos+1;
			if (pos >= source.length())
				throw new NoSuchElementException();
			nextPos= source.indexOf('[', pos);
			if (nextPos == -1)
				nextPos= source.length();
			String fragment= source.substring(pos, nextPos);
			if (pos == 0)
				return fragment;


			if (fragment.length() < 2)
				return "["+fragment;

			int tagEnd= -1;
			int stop= Math.min(fragment.length(), Tag.MAX_TAG_STRING_LEN+1);
			for (int i=0; i<stop; i++) {
				char ch= fragment.charAt(i);
				if (ch == '=' || ch == ']') {
					tagEnd= i;
					break;
				}
			}
			if (tagEnd == -1)
				return "["+fragment;

			int remainderStart= tagEnd+1;
			if (fragment.charAt(tagEnd) == '=') {
				int paramEnd= fragment.indexOf(']');
				if (paramEnd == -1)
					return "["+fragment;
				tagParam= fragment.substring(tagEnd+1, paramEnd);
				remainderStart= paramEnd+1;
			}

			Tag t= Tag.map.get(fragment.substring(0, tagEnd).toLowerCase());
			if (t == null)
				return "["+fragment;
			if (t.isStart)
				tagStack.add(t);
			else if (t.complement != null) {
				if (tagStack.size() == 0 || t.complement != tagStack.getLast())
					return "["+fragment;
				tagStack.removeLast();
			}

			for (int i=0; i<2 && remainderStart < fragment.length(); i++)
				if (fragment.charAt(tagEnd) == drop[i])
					remainderStart++;
			if (remainderStart < fragment.length())
				leftover= fragment.substring(remainderStart);
			return t;
		}
		private static char[] drop= new char[] { '\r', '\n' };

		public String getTagParam() {
			return tagParam;
		}
	}

	static void render(String text, HtmlGL hgl) {
		TagTokenizer tok= new TagTokenizer(text);
		while (tok.hasNext()) {
			Object elem= tok.next();
			if (elem instanceof Tag) {
				Tag t= (Tag) elem;
				switch (t) {
				case LIST: hgl.p("<ul><li>"); break;
				case NEXT: hgl.p("</li><li>"); break;
				case LIST_END: hgl.p("</li></ul>"); break;
				case TABLE: hgl.p("<table><tr><td>"); break;
				case ROW: hgl.p("</td></tr><tr><td>"); break;
				case COL: hgl.p("</td><td>"); break;
				case TABLE_END: hgl.p("</td></tr></table>"); break;
				case IMG:
					try {
						java.net.URI url= new java.net.URI(tok.getTagParam());
						java.io.File path= new java.io.File(url.getPath());
						hgl.p("<img src='").pText(url.toString()).p("' alt='").p(path.getAbsolutePath()).p("'/>");
					}
					catch (Exception ex) {
						hgl.p("(Invalid image url)");
					}
					break;
				case URL:
					try {
						String urlText= tok.getTagParam();
						boolean autoHref= urlText == null;
						if (autoHref)
							urlText= (String) tok.next();
						java.net.URI url= new java.net.URI(urlText);
						hgl.p("<a href='").pText(url.toString()).p("'>");
						if (autoHref)
							hgl.pText(urlText);
					}
					catch (Exception ex) {
						hgl.p("(Invalid url)");
					}
					break;
				case URL_END: hgl.p("</a>"); break;
				default:
					hgl.p(t.html);
				}
			}
			else
				hgl.pTextMultiline((String)elem);
		}
		// now, clean up the html by emitting any missing end tags
		while (tok.tagStack.size() > 0) {
			Tag t= tok.tagStack.removeLast();
			hgl.p(t.getComplement().html);
		}
	}
}
