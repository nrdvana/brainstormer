package brainstormer;

import java.sql.*;
import javax.servlet.http.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

/**
 * <p>Project: Brainstormer</p>
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright Copyright (c) 2007</p>
 *
 * @author Michael Conrad
 * @version $Revision$
 */
public class Auth {
	DB db;
	public Auth(DB db) {
		this.db= db;
	}

	private static final BigInteger MASK= BigInteger.ONE.shiftLeft(165).subtract(BigInteger.ONE);
	public static final String hashPassword(String username, String password) {
		try {
			password= Util.trimPossibleNull(password);
			MessageDigest md5= MessageDigest.getInstance("MD5");
			md5.update(username.getBytes("UTF-8"));
			md5.update(password.getBytes("UTF-8"));
			return new BigInteger(md5.digest()).and(MASK).toString(36);
		}
		catch (java.io.UnsupportedEncodingException irrelevantBullshitException) {
			throw new RuntimeException(irrelevantBullshitException);
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static final int getPrefUserID(HttpServletRequest req) {
		Cookie c= getAuthCookie(req);
		if (c != null)
			return ((Integer)getIDAndPass(c.getValue())[0]).intValue();
		else
			return -1;
	}

	public final User authenticateCurrentUser(HttpServletRequest req) throws SQLException {
		Cookie c= getAuthCookie(req);
		if(c == null)
			return null;
		Object[] idAndPass= getIDAndPass(c.getValue());
		int claimedUser= ((Integer)idAndPass[0]).intValue();
		String passHash= (String) idAndPass[1];
		User u= claimedUser == -1? db.userLoader.loadByName("Guest")
			: db.userLoader.loadById(claimedUser);
		return (u.passHash == null || u.passHash.equals(passHash))? u : null;
	}

	public final boolean tryLogin(String name, String pass) throws SQLException {
		User u= db.userLoader.loadByName(name);
		if (u == null)
			return false;
		if (u.passHash == null) {
			if (pass != null && pass.length() > 0)
				return false;
		}
		else if (!u.passHash.equals(hashPassword(name, pass)))
			return false;
		db.activeUser= u;
		return true;
	}

	public final Cookie getLoginCookie() {
		return new Cookie("UserID", ""+db.activeUser.id+":"+db.activeUser.passHash);
	}

	public static final Cookie getLogoutCookie() {
		Cookie result= new Cookie("UserID", "");
		result.setMaxAge(0);
		return result;
	}

	static final Cookie getAuthCookie(HttpServletRequest req) {
		Cookie[] cList= req.getCookies();
		if (cList != null)
			for (Cookie c: cList)
				if ("UserID".equals(c.getName()))
					return c;
		return null;
	}

	static Object[] getIDAndPass(String cookieVal) {
		Object[] result= new Object[2];
		if (cookieVal == null) {
			result[0]= new Integer( -1);
			result[1]= null;
		}
		else {
			int splitPos= cookieVal.indexOf(':');
			if (splitPos != -1)
				result[0]= Util.parseOrDefault(cookieVal.substring(0, splitPos), -1);
			result[1]= cookieVal.substring(splitPos+1);
		}
		return result;
	}
}
