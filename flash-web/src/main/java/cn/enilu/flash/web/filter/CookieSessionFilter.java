package cn.enilu.flash.web.filter;

import com.google.common.base.Charsets;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CookieSessionFilter extends BaseFilter {
	public static final String DEFAULT_SESSION_KEY = "s";
	private CookieBasedSessionStore store;

	private Pattern pathPattern;
	private Pattern excludePattern;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		String secret = filterConfig.getInitParameter("secret");
		if (secret == null) {
			throw new IllegalArgumentException(
					"secret must not be defined in filter parameter");
		}
		String sessionKey = filterConfig.getInitParameter("sessionKey");
		if (sessionKey == null) {
			sessionKey = DEFAULT_SESSION_KEY;
		}

		String permanentParameter = filterConfig.getInitParameter("permanent");
		boolean permanent = Boolean.valueOf(permanentParameter);
		store = new CookieBasedSessionStore();
		store.setSecret(secret.getBytes(Charsets.UTF_8));
		store.setSessionKey(sessionKey);
		store.setCookiePath(filterConfig.getInitParameter("cookiePath"));
		store.setDomain(filterConfig.getInitParameter("domain"));
		store.setHttpOnly(Boolean.valueOf(filterConfig
				.getInitParameter("httpOnly")));
		store.setPermanent(permanent);
		store.setIgnoreSign(Boolean.valueOf(filterConfig
				.getInitParameter("ignoreSign")));

		pathPattern = initPattern(filterConfig, "pathPattern");
		excludePattern = initPattern(filterConfig, "excludePattern");
	}

	private Pattern initPattern(FilterConfig filterConfig, String parameterName) {
		String config = filterConfig.getInitParameter(parameterName);
		if (config != null) {
			return Pattern.compile(config);
		}
		return null;
	}

	@Override
	protected void doFilter(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (isPathMatch(request)) {
			RequestWrapper requestWrapper = new RequestWrapper(request);
			store.restore(requestWrapper);

			ResponseWrapper responseWrapper = new ResponseWrapper(store,
					requestWrapper, response);
			chain.doFilter(requestWrapper, responseWrapper);
			responseWrapper.flushBuffer();
		} else {
			chain.doFilter(request, response);
		}
	}

	private boolean isPathMatch(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (excludePattern != null) {
			Matcher m = excludePattern.matcher(uri);
			if (m.find()) {
				return false;
			}
		}
		
		if (pathPattern == null) {
			return true;
		}
		Matcher m = pathPattern.matcher(uri);
		return m.find();
	}

	/**
	 * 禁止生成JSESSIONID cookie.
	 */
	@SuppressWarnings("deprecation")
	static class HttpSessionWrapper implements HttpSession {
		private Map<String, Object> data = new HashMap<String, Object>();
		private HttpServletRequestWrapper req;

		public HttpSessionWrapper(HttpServletRequestWrapper req) {
			this.req = req;
		}

		@Override
		public long getCreationTime() {
			return 0;
		}

		@Override
		public String getId() {
			return null;
		}

		@Override
		public long getLastAccessedTime() {
			return 0;
		}

		@Override
		public ServletContext getServletContext() {
			return req.getServletContext();
		}

		@Override
		public void setMaxInactiveInterval(int interval) {
		}

		@Override
		public int getMaxInactiveInterval() {
			return 0;
		}

		public javax.servlet.http.HttpSessionContext getSessionContext() {
			return null;
		}

		@Override
		public Object getAttribute(String name) {
			return data.get(name);
		}

		@Override
		public Object getValue(String name) {
			return getAttribute(name);
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			return new Vector<String>(data.keySet()).elements();
		}

		@Override
		public String[] getValueNames() {
			return data.keySet().toArray(new String[0]);
		}

		@Override
		public void setAttribute(String name, Object value) {
			data.put(name, value);
		}

		@Override
		public void putValue(String name, Object value) {
			setAttribute(name, value);
		}

		@Override
		public void removeAttribute(String name) {
			data.remove(name);
		}

		@Override
		public void removeValue(String name) {
			removeAttribute(name);
		}

		@Override
		public void invalidate() {
			data.clear();
		}

		@Override
		public boolean isNew() {
			return false;
		}

	}

	static class RequestWrapper extends HttpServletRequestWrapper {
		private HttpSessionWrapper session = new HttpSessionWrapper(this);

		public RequestWrapper(HttpServletRequest request) {
			super(request);
		}

		@Override
		public HttpSessionWrapper getSession() {
			return session;
		}

		@Override
		public HttpSessionWrapper getSession(boolean create) {
			return session;
		}
	}

	/**
	 * 覆盖encode*Url, 禁止通过urlrewrite加入jsessionid.
	 */
	@SuppressWarnings("deprecation")
	static class ResponseWrapper extends HttpServletResponseWrapper {
		private final ServletOutputStreamWrapper output;
		private final PrintWriter writer;

		private final CookieBasedSessionStore store;
		private final HttpServletRequest req;
		private final HttpServletResponse resp;
		private boolean sessionSaved;

		public ResponseWrapper(CookieBasedSessionStore store,
				HttpServletRequest req, HttpServletResponse resp)
				throws IOException {
			super(resp);
			output = new ServletOutputStreamWrapper(resp.getOutputStream());
			this.store = store;
			this.req = req;
			this.resp = resp;
			writer = new PrintWriter(output, true);
		}

		@Override
		public String encodeRedirectUrl(String url) {
			return url;
		}

		@Override
		public String encodeRedirectURL(String url) {
			return url;
		}

		@Override
		public String encodeUrl(String url) {
			return url;
		}

		@Override
		public String encodeURL(String url) {
			return url;
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			return output;
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			return writer;
		}

		@Override
		public void flushBuffer() throws IOException {
			flushSessionCookie();
			writer.flush();
		}

		@Override
		public void sendError(int sc) throws IOException {
			flushSessionCookie();
			super.sendError(sc);
		}

		@Override
		public void sendError(int sc, String msg) throws IOException {
			flushSessionCookie();
			super.sendError(sc, msg);
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			flushSessionCookie();
			super.sendRedirect(location);
		}

		private void flushSessionCookie() {
			if (!sessionSaved) {
				store.generate(req, resp);
				sessionSaved = true;
			}
		}

		@SuppressWarnings("deprecation")
		class ServletOutputStreamWrapper extends ServletOutputStream {
			private final FilterOutputStream output;

			public ServletOutputStreamWrapper(ServletOutputStream output) {
				this.output = new FilterOutputStream(output);
			}

			@Override
			public void write(int b) throws IOException {
				flushSessionCookie();
				output.write(b);
			}

			@Override
			public void flush() throws IOException {
				flushSessionCookie();
				output.flush();
			}

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        }

	}

}
