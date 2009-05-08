package org.sitemesh.webapp;

import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.content.ContentProperty;
import org.sitemesh.webapp.contentfilter.BasicSelector;
import org.sitemesh.webapp.contentfilter.HttpServletResponseBuffer;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

/**
 * SiteMesh {@link SiteMeshContext} implementation specifically for webapps running in a Servlet
 * container. Makes {@link HttpServletRequest}, {@link HttpServletResponse} and
 * {@link ServletContext} available to web-app specific SiteMesh components.
 *
 * @author Joe Walnes
 * @author Mike Cannon-Brookes
 */
public class WebAppContext implements SiteMeshContext {

    /**
     * Key that the {@link ContentProperty} is stored under in the {@link HttpServletRequest}
     * attribute. It is "org.sitemesh.content.Content".
     */
    public static final String CONTENT_KEY = Content.class.getName();

    /**
     * Key that the {@link WebAppContext} is stored under in the {@link HttpServletRequest}
     * attribute. It is "org.sitemesh.SiteMeshContext".
     */
    public static final String CONTEXT_KEY = SiteMeshContext.class.getName();

    private final String contentType;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ServletContext servletContext;
    private final ContentProcessor contentProcessor;

    private Content currentContent;

    public WebAppContext(String contentType, HttpServletRequest request,
                         HttpServletResponse response, ServletContext servletContext,
                         ContentProcessor contentProcessor) {
        this.contentType = contentType;
        this.request = request;
        this.response = response;
        this.servletContext = servletContext;
        this.contentProcessor = contentProcessor;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public String getRequestPath() {
        return getRequestPath(request);
    }

    public static String getRequestPath(HttpServletRequest request) {
        String result = request.getServletPath();
        // getServletPath() returns null unless the mapping corresponds to a servlet
        if (result == null) {
            String requestURI = request.getRequestURI();
            if (request.getPathInfo() != null) {
                // strip the pathInfo from the requestURI
                return requestURI.substring(0, requestURI.indexOf(request.getPathInfo()));
            } else {
                return requestURI;
            }
        } else if ("".equals(result)) {
            // in servlet 2.4, if a request is mapped to '/*', getServletPath returns null (SIM-130)
            return request.getPathInfo();
        } else {
            return result;
        }
    }

    @Override
    public Content decorate(String decoratorName, Content content) throws IOException {
        if (decoratorName == null) {
            return null;
        }

        class CharBufferWriter extends CharArrayWriter {
            public CharBuffer toCharBuffer() {
                return CharBuffer.wrap(this.buf, 0, this.count);
            }
        }
        CharBufferWriter out = new CharBufferWriter();
        decorate(decoratorName, content, out);

        CharBuffer decorated = out.toCharBuffer();

        Content lastContent = currentContent;
        currentContent = content;
        try {
            // TODO: Don't reprocess the content properties.
            return contentProcessor.build(decorated, this);
        } finally {
            currentContent = lastContent;
        }
    }

    @Override
    public Content getContentToMerge() {
        return currentContent;
    }

    /**
     * Dispatches to another path to render a decorator.
     *
     * <p>This path may anything that handles a standard request (e.g. Servlet,
     * JSP, MVC framework, etc).</p>
     *
     * <p>The end point can access the {@link ContentProperty} and {@link SiteMeshContext} by using
     * looking them up as {@link HttpServletRequest} attributes under the keys
     * {@link #CONTENT_KEY} and
     * {@link #CONTEXT_KEY} respectively.</p>
     */
    protected void decorate(String decoratorPath, Content content, Writer out) throws IOException {
        // Wrap response so output gets buffered.
        HttpServletResponseBuffer responseBuffer = new HttpServletResponseBuffer(response, new BasicSelector() {
            @Override
            public boolean shouldBufferForContentType(String contentType, String mimeType, String encoding) {
                return true; // We know we should buffer.
            }
        });
        responseBuffer.setContentType(response.getContentType()); // Trigger buffering.

        // It's possible that this is reentrant, so we need to take a copy
        // of additional request attributes so we can restore them afterwards.
        Object oldContent = request.getAttribute(CONTENT_KEY);
        Object oldContext = request.getAttribute(CONTEXT_KEY);

        request.setAttribute(CONTENT_KEY, content);
        request.setAttribute(CONTEXT_KEY, this);

        try {
            // Main dispatch.
            dispatch(request, responseBuffer, decoratorPath);

            // Write out the buffered output.
            CharBuffer buffer = responseBuffer.getBuffer();
            out.append(buffer);
        } catch (ServletException e) {
            throw new IOException("Could not dispatch to decorator: ", e);
        } finally {
            // Restore previous state.
            request.setAttribute(CONTENT_KEY, oldContent);
            request.setAttribute(CONTEXT_KEY, oldContext);
        }
    }

    /**
     * Dispatch to the actual path. This method can be overriden to provide different ways of dispatching
     * (such as cross web-app).
     */
    protected void dispatch(HttpServletRequest request, HttpServletResponse response, String path)
            throws ServletException, IOException {
        RequestDispatcher dispatcher = servletContext.getRequestDispatcher(path);
        if (dispatcher == null) {
            throw new ServletException("Not found: " + path);
        }
        dispatcher.include(request, response);
    }

}
