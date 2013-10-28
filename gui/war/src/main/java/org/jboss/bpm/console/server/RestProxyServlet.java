/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.bpm.console.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.overlord.commons.config.ConfigurationFactory;

/**
 * A simple proxy servlet for REST invocations.  The primary purpose of this servlet is
 * to provide a way to inject appropriate authentication mechanisms when invoking a
 * protected REST service directly from the Browser.  When that is impossible (because
 * the browser does not have the appropriate credentials available to it) then the
 * browser can invoke this servlet instead.  This servlet will in turn invoke the
 * protected REST service and proxy the response.
 *
 * @author eric.wittmann@redhat.com
 */
public class RestProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 8689929059530563599L;

    public static final String CONFIG_FILE_NAME     = "bpel-console.config.file.name"; //$NON-NLS-1$
    public static final String CONFIG_FILE_REFRESH  = "bpel-console.config.file.refresh"; //$NON-NLS-1$
    public static final String PROXY_URL = "bpel-console.rest-proxy.proxy-url"; //$NON-NLS-1$
    public static final String AUTH_PROVIDER = "bpel-console.rest-proxy.authentication.provider"; //$NON-NLS-1$
    public static final String BASIC_AUTH_USER = "bpel-console.rest-proxy.authentication.basic.user"; //$NON-NLS-1$
    public static final String BASIC_AUTH_PASS = "bpel-console.rest-proxy.authentication.basic.password"; //$NON-NLS-1$

    public static Configuration appconfig;
    static {
        String configFile = System.getProperty(CONFIG_FILE_NAME);
        String refreshDelayStr = System.getProperty(CONFIG_FILE_REFRESH);
        Long refreshDelay = 5000l;
        if (refreshDelayStr != null) {
            refreshDelay = new Long(refreshDelayStr);
        }

        appconfig = ConfigurationFactory.createConfig(
                configFile,
                "bpel-console.properties", //$NON-NLS-1$
                refreshDelay,
                null,
                RestProxyServlet.class);
    }
    
    private String proxyUrl;
    private String authProviderClassName;
    private Map<String, String> params = new HashMap<String, String>();

    /**
     * Constructor.
     */
    public RestProxyServlet() {
    }

    /**
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        proxyUrl = appconfig.getString(PROXY_URL);
        if (proxyUrl == null)
            throw new ServletException("Missing config property: " + PROXY_URL);
        authProviderClassName = appconfig.getString(AUTH_PROVIDER);
        
        @SuppressWarnings("unchecked")
        Iterator<String> keys = appconfig.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            String val = appconfig.getString(key);
            params.put(key, val);
        }
    }
    
    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        proxyRequest(req, resp, true);
    }

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        proxyRequest(req, resp, false);
    }

    /**
     * Proxies the request.
     * @param req
     * @param resp
     * @param isPost
     * @throws MalformedURLException
     * @throws IOException
     * @throws ProtocolException
     * @throws ServletException
     */
    private void proxyRequest(HttpServletRequest req, HttpServletResponse resp, boolean isPost)
            throws MalformedURLException, IOException, ProtocolException,
            ServletException {
        // Connect to proxy URL.
        String urlStr = getProxyUrl(req);
        if (urlStr.endsWith("/")) {
            urlStr = urlStr.substring(0, urlStr.length()-1);
        }
        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            urlStr += pathInfo;
        }
        String queryString = req.getQueryString();
        if (queryString != null) {
            urlStr = urlStr + "?" + queryString;
        }
        URL url = new URL(urlStr);
        System.out.println("Proxying: " + url);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (isPost) {
            conn.setDoOutput(true);
            System.out.println("            ^^^^^^^^ is a POST");
        }
        conn.setRequestMethod("GET");

        // Proxy all of the request headers.
        Enumeration<?> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = String.valueOf(headerNames.nextElement());
            Enumeration<?> headerValues = req.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String headerValue = String.valueOf(headerValues.nextElement());
                conn.addRequestProperty(headerName, headerValue);
            }
        }

        // Handle authentication
        RestProxyAuthProvider authProvider = getAuthProvider();
        if (authProvider != null) {
            authProvider.provideAuthentication(conn);
        }
        
        // If we're dealing with a POST, make sure to proxy the request body
        if (isPost) {
            InputStream is = null;
            try {
                is = req.getInputStream();
                IOUtils.copy(is, conn.getOutputStream());
            } finally {
            }
        }

        // Now connect and proxy the response.
        InputStream proxyUrlResponseStream = null;
        try {
            proxyUrlResponseStream = conn.getInputStream();
            resp.setStatus(conn.getResponseCode());
            // Proxy the response headers
            for (Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                String respHeaderName = entry.getKey();
                if (respHeaderName != null && !respHeaderName.equalsIgnoreCase("transfer-encoding")) {
                    for (String respHeaderValue : entry.getValue()) {
                        resp.addHeader(respHeaderName, respHeaderValue);
                    }
                }
            }

            // Proxy the response body.
            IOUtils.copy(proxyUrlResponseStream, resp.getOutputStream());
        } finally {
            IOUtils.closeQuietly(proxyUrlResponseStream);
            conn.disconnect();
        }
    }

    /**
     * @param req the inbound http request
     * @return the proxy url, with property substitutions
     */
    private String getProxyUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        String port = String.valueOf(req.getServerPort());
        return this.proxyUrl.replace("SCHEME", scheme).replace("HOST", host).replace("PORT", port);
    }

    /**
     * Gets the authentication provider.  This will look up the auth provider to used either from a
     * servlet init param or from the configuration properties.
     */
    private RestProxyAuthProvider getAuthProvider() throws ServletException {
        String classname = authProviderClassName;
        if (classname != null) {
            try {
                Class<?> authProviderClass = Class.forName(classname);
                RestProxyAuthProvider authProvider = (RestProxyAuthProvider) authProviderClass.newInstance();
                authProvider.setConfiguration(params);
                return authProvider;
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }

        return null;
    }

}
