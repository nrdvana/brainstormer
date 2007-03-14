package brainstormer;

import java.io.*;
import java.util.*;
import javax.servlet.http.*;
import java.text.SimpleDateFormat;
import java.sql.Connection;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class HtmlGL {
	HttpServletRequest request;
	HttpServletResponse response;
	private boolean expandCollapseImported= false;
	private boolean htmlTagOpen= false;
	private String pathToServletRoot= null;
	SimpleDateFormat dateFormatter= new SimpleDateFormat("yyyy-MM-dd HH:mm");

	PrintWriter out;
	public HtmlGL(HttpServletRequest req, HttpServletResponse resp) {
		this.response= resp;
		this.request= req;
		String path= req.getServletPath()+Util.trimPossibleNull(req.getPathInfo());
		int pos= 0; // this skips the first '/' which is part of the servlet name
		while ((pos= path.indexOf('/', pos+1)) != -1)
			pathToServletRoot= Util.trimPossibleNull(pathToServletRoot)+"../";
	}
	private void initStreamIfNeeded() {
		if (out == null) {
			response.setContentType("text/html; charset=UTF-8");
			try {
				out= response.getWriter();
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}
	public void pDebugData(DB db) {
		p("<!--");
		try {
			p("\nTime = "+System.currentTimeMillis());
			for (java.lang.reflect.Method func: request.getClass().getMethods()) {
				if (func.getReturnType() == String.class && func.getParameterTypes().length == 0) {
					String val= (String) func.invoke(request, new Object[0]);
					p("\n").pText(func.getName()+" = "+val);
				}
			}
			for (Map.Entry entry: (Set<Map.Entry>) request.getParameterMap().entrySet()) {
				p("\n").p(entry.getKey().toString());
				if (entry.getValue() == null)
					p("= null");
				else {
					p("=[");
					for (String val: (String[]) entry.getValue())
						pText(val).p(", ");
					p("]");
				}
			}
			if (request.getCookies() != null)
				for (Cookie c: request.getCookies()) {
					p("\nCookie:");
					for (java.lang.reflect.Method func: c.getClass().getMethods()) {
						if (func.getName().startsWith("get") && func.getParameterTypes().length == 0) {
							Object val= func.invoke(c, new Object[0]);
							pText("\n   "+func.getName()+" = "+val);
						}
					}
				}
			if (db != null) {
				p("\nDB object: "+db+"  Uses: "+(db.getRecycleCount()+1));
				p("\nTransaction Isolation: ");
				int iso= db.conn.getTransactionIsolation();
				switch (iso) {
				case Connection.TRANSACTION_READ_UNCOMMITTED: p("READ UNCOMITTED"); break;
				case Connection.TRANSACTION_READ_COMMITTED:   p("READ COMMITTED"); break;
				case Connection.TRANSACTION_REPEATABLE_READ:  p("REPEATABLE READ"); break;
				case Connection.TRANSACTION_SERIALIZABLE:     p("SERIALIZABLE"); break;
				case Connection.TRANSACTION_NONE: p("Not supported"); break;
				default:
					p("Unknown");
				}
				p("\nAutocommit: "+db.conn.getAutoCommit());
			}
		}
		catch (Exception miscelaneousReflectionBullshitException) {
			throw new RuntimeException(miscelaneousReflectionBullshitException);
		}
		p("\n-->\n");
	}

	void beginPageIfNeeded() {
		if (!htmlTagOpen)
			beginPage("Error", new String[] {"Page.css"}, null);
	}
	void endPageIfNeeded() {
		if (htmlTagOpen)
			endPage();
	}

	public final HtmlGL p(String str) {
		out.print(str);
		return this;
	}

	public final HtmlGL p(int val) {
		out.print(Integer.toString(val));
		return this;
	}

	public final HtmlGL pText(String unescapedText) {
		out.print(esc(unescapedText));
		return this;
	}

	public final HtmlGL pTextMultiline(String unescapedText) {
		int pos= 0, nextPos;
		while ((nextPos= unescapedText.indexOf('\n', pos)) != -1) {
			pText(unescapedText.substring(pos, nextPos)).p("<br/>");
			pos= nextPos+1;
		}
		return pText(unescapedText.substring(pos));
	}

	public final String encodeURL(String pathFromRoot) {
		if (pathToServletRoot != null)
			return pathToServletRoot+response.encodeURL(pathFromRoot);
		else
			return response.encodeURL(pathFromRoot);
	}

	public final HtmlGL pURL(String pathFromRoot) {
		if (pathToServletRoot != null)
			out.print(pathToServletRoot);
		out.print(response.encodeURL(pathFromRoot));
		return this;
	}

	public final HtmlGL pTimestamp(Date d) {
		out.print(dateFormatter.format(d));
		return this;
	}

	private static final char[] hexChars= "0123456789ABCDEF".toCharArray();
	public static final String esc(String text) {
		int i= 0, stop= text.length();
		while (i < stop && !needEsc(text.charAt(i)))
			i++;
		if (i == stop)
			return text;
		StringBuffer altered= new StringBuffer(text.substring(0, i));
		for (; i<stop; i++) {
			char ch= text.charAt(i);
			if (needEsc(ch))
				altered.append('&').append('#').append(Integer.toString(ch)).append(';');
			else
				altered.append(ch);
		}
		return altered.toString();
	}

	public static final String urlEsc(String text) {
		try {
			return java.net.URLEncoder.encode(text, "UTF-8");
		}
		catch (Exception irrelevantBullshitException) {
			throw new RuntimeException(irrelevantBullshitException);
		}
	}

	private static final boolean needEsc(char c) {
		return (c == '&' || c == '\'' || c == '"' || c == '<' || c == '>' || c == '\\');
	}

	public void beginPage(String title, String[] stylesheets, DB db) {
		initStreamIfNeeded();
		p("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
			+"<html>\n"
			+"<head>\n"
			+"  <title>").p(title).p("</title>\n");
		for (int i=0; i<stylesheets.length; i++)
			p("  <link rel='stylesheet' type='text/css' href='").pURL("skin/style/"+stylesheets[i]).p("'/>\n");
		p("</head>\n<body>\n");
		p("<div id='content'>\n");
		p("<div class='lame-browser'>This page not intended for use with old browsers.  In particular, IE6 just kinda sucks.</div>\n");
		p("<div class='navbar'>\n"
			+"  <ul>\n"
			+"    <li><a href='").pURL("index.html").p("'>Home</a></li>\n"
			+"    <li><a href='").pURL("search").p("'>Search</a></li>\n");
		if (db != null && db.activeUser != null) {
			p("   <li>").pText(db.activeUser.name).p("</li>");
			p("    <li><a href='").pURL("create").p("'>New Post</a></li>\n"
				+"    <li><a href='").pURL("logout").p("'>Log Out</a></li>\n");
		}
		else
			p("    <li><a href='").pURL("login").p("'>Log In</a></li>\n");
		p("  </ul>\n"
			+"</div>\n");
		htmlTagOpen= true;
		if (request.getParameter("debug") != null)
			pDebugData(db);
	}

	public void endPage() {
		p("<div id='roomForFooter'></div>\n"
			+"</div>\n"
			+"<div id='footer'>\n"
			+"  <a class='validate' href='http://validator.w3.org/check?uri=referer'><img src='http://www.w3.org/Icons/valid-xhtml10-blue' alt='Valid XHTML 1.0 Strict' height='31' width='88'/></a>\n"
			+"</div>\n"
			+"</body>\n"
			+"</html>\n");
		out.flush();
		out.close();
		htmlTagOpen= false;
	}

	public final void beginErrorMsg() {
		beginErrorMsg(null);
	}
	public void beginErrorMsg(String title) {
		beginPageIfNeeded();
		p("<div class='error'>");
		beginGroupBox();
		if (title != null)
			p("  <h4>").pText(title).p("</h4>");
		p("  <p>\n");
	}

	public void endErrorMsg() {
		p("  </p>\n");
		endGroupBox();
		p("</div>\n");
	}

	public void beginTabControl(String name, String[] tabs, int selectedTab) {
		p("<div class='tab-control'>\n"
			+"  <div class='tab-header'>\n");
		for (int i=0; i<tabs.length; i++)
			p("    <input type='submit' name='").p(name).p("' value='").p(esc(tabs[i]))
				.p("' class='").p(i==selectedTab? "tab-selected" : "tab").p("'/>\n");
		p("  </div>\n  <div class='tab-body'>\n");
	}

	public void endTabControl() {
		p("  </div>\n</div>\n");
	}

	private static final String[] toggleVisStyle= new String[] {
		"style='visibility:hidden; position:absolute;'",
		"style='visibility:visible; position:relative;'",
	};
	public void beginContentToggle(String name, boolean isActive) {
		if (!expandCollapseImported) {
			p("<script type='text/javascript' src='").pURL("scripts/ExpandCollapse.js").p("'></script>\n");
			expandCollapseImported= true;
		}
		p("<div class='container'>\n");
		p("  <div class='toggle' id='").p(name).p("c0' ").p(toggleVisStyle[isActive?1:0]).p(">\n");
	}

	public void nextContentToggle(String name, int idx, boolean isActive) {
		p("  </div>\n"
			+"  <div class='toggle' id='").p(name+"c"+idx).p("' ").p(toggleVisStyle[isActive?1:0]).p(">\n");
	}

	public void endContentToggle() {
		p("  </div>\n</div>\n");
	}

	public HtmlGL beginContentSelectorButton(String name, int idx, boolean toggleChildren) {
		String thirdParam= toggleChildren? ",1)'>" : ",0)'>";
		return p("<a class='btn' href='javascript:SelectContent(\"").p(name+"\","+idx+thirdParam);
	}

	public void pException(Exception ex) {
		beginPageIfNeeded();
		beginErrorMsg();
		if (ex instanceof UserException) {
			pText(ex.getMessage());
		}
		else if (ex instanceof DBInitException) {
			p("<h3>Misconfigured Database</h3>\n"
				+"If you are a server administrator, head to the "
				+"<a href='").pURL("setup").p("'>setup</a> page to correct this problem");
		}
		else {
			p("Internal error:\n<pre>");
			pText(ex.getClass().getName());
			String msg= ex.getMessage();
			if (msg != null)
				p(": ").pText(msg);
			p("\n");
			ex.printStackTrace(out);
			p("</pre>\n");
		}
		endErrorMsg();
	}

	public HtmlGL pPrompt(String html) {
		return p("<span class='prompt'>").p(html).p("</span>");
	}

	public HtmlGL pTextBlank(String name, String value) {
		return p("<input type='text' name='").p(name).p("' value='").pText(value).p("'/>");
	}
	public HtmlGL pTextBlank(String name, String value, String style) {
		return p("<input class='").p(style).p("' type='text' name='").p(name).p("' value='").pText(value).p("'/>");
	}

	public HtmlGL pSpace(String width, String height) {
		if (height == null)
			return p("<span style='pading-left:").p(width).p("'/>");
		else {
			p("<div class='container' style='height:").p(height);
			if (width != null)
				p("; width:").p(width);
			return p(";'></div>");
		}
	}

	public void beginGroupBox(String caption) {
		p("<div class='groupbox'><span class='caption'>").pText(caption).p("</span>\n");
	}

	public void beginGroupBox() {
		p("<div class='groupbox'>\n");
	}

	public void endGroupBox() {
		p("</div>\n");
	}

	public HtmlGL beginMenu(String captionHtml) {
		return p("<div class='menu'>").p("<!--[if lte IE 6]><a class='IE-hack' href='#nogo'><table><tr><td><![endif]-->\n"
			+"  <span class='caption'>").p(captionHtml).p("</span>\n"
			+"  <dl>\n"
			+"    <dt><span class='caption'>").p(captionHtml).p("</span></dt>\n");
	}
	public HtmlGL endMenu() {
		return p("  </dl><!--[if lte IE 6]></td></tr></table></a><![endif]-->\n"
			+"</div>\n");
	}

	public static class CheckListItem {
		public String key;
		public String html;
		public boolean selected;
		public CheckListItem() {}
		public CheckListItem(String key, String html, boolean selected) {
			this.key= key;
			this.html= html;
			this.selected= selected;
		}
	}

	public void pCheckList(String name, Collection<CheckListItem> items) {
		p("<div class='checkbox-list'>\n");
		for (CheckListItem item: items) {
			String checkStatus= item.selected? "' checked='checked'/>" : "'/>";
			p("<input type='checkbox' name='").p(name).p("' value='").p(esc(item.key))
				.p(checkStatus).p(item.html).p("<br/>\n");
		}
		p("</div>\n");
	}

	public void pDropdown(String name, String[] values, String[] display, String selected) {
		p("<select name='").p(name).p("'>");
		for (int i=0; i<values.length; i++) {
			p("<option value='").p(values[i]);
			p(values[i].equals(selected)? "' selected='selected'>":"'>");
			pText(display[i]).p("</option>");
		}
		p("</select>");
	}

	public HtmlGL pContentExpandButton(String name) {
		return beginContentSelectorButton(name, 1, false).p("<img src='").pURL("skin/img/Plus.gif").p("' alt='expand'/></a>");
	}
	public HtmlGL pContentCollapseButton(String name) {
		return beginContentSelectorButton(name, 0, false).p("<img src='").pURL("skin/img/Minus.gif").p("' alt='collapse'/></a>");
	}
}
