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

import java.net.HttpURLConnection;
import java.util.Map;

import org.overlord.commons.auth.jboss7.SAMLBearerTokenUtil;

/**
 * An auth provider that uses SAML bearer token authentication.
 * @author eric.wittmann@redhat.com
 */
public class RestProxySAMLBearerTokenAuthProvider implements RestProxyAuthProvider {

    private Map<String, String> configProperties;

    /**
     * Constructor.
     */
    public RestProxySAMLBearerTokenAuthProvider() {
    }

    /* (non-Javadoc)
     * @see org.jboss.bpm.console.server.RestProxyAuthProvider#setConfiguration(java.util.Properties)
     */
    @Override
    public void setConfiguration(Map<String, String> configProperties) {
        this.configProperties = configProperties;
    }

    /**
     * @see org.overlord.gadgets.web.server.servlets.RestProxyAuthProvider#provideAuthentication(java.net.HttpURLConnection)
     */
    @Override
    public void provideAuthentication(HttpURLConnection connection) {
        String headerValue = RestProxyBasicAuthProvider.createBasicAuthHeader("SAML-BEARER-TOKEN", createSAMLBearerTokenAssertion()); //$NON-NLS-1$
        connection.setRequestProperty("Authorization", headerValue); //$NON-NLS-1$
    }

    /**
     * Creates the SAML Bearer Token that will be used to authenticate to the
     * S-RAMP Atom API.
     */
    private String createSAMLBearerTokenAssertion() {
        String samlAssertion = SAMLBearerTokenUtil.createSAMLAssertion(getIssuer(), getService());
        if (isSignAssertions()) {
            try {
//                KeyStore keystore = SAMLBearerTokenUtil.loadKeystore(getKeystorePath(), getKeystorePassword());
//                KeyPair keyPair = SAMLBearerTokenUtil.getKeyPair(keystore, getAlias(), getAliasPassword());
//                samlAssertion = SAMLBearerTokenUtil.signSAMLAssertion(samlAssertion, keyPair);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return samlAssertion;
    }

    /**
     * @return the configured saml issuer
     */
    private String getIssuer() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.issuer";
        return this.configProperties.get(propKey);
    }

    /**
     * @return the configured saml service
     */
    private String getService() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.service";
        return this.configProperties.get(propKey);
    }

    /**
     * @return whether saml assertions should be digitally signed
     */
    private boolean isSignAssertions() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.sign-assertions";
        return "true".equals(this.configProperties.get(propKey));
    }

    /**
     * @return the configured digital signature keystore
     */
    protected String getKeystorePath() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.keystore";
        return this.configProperties.get(propKey);
    }

    /**
     * @return the configured keystore password
     */
    protected String getKeystorePassword() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.keystore-password";
        return this.configProperties.get(propKey);
    }

    /**
     * @return the configured keystore alias
     */
    protected String getAlias() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.key-alias";
        return this.configProperties.get(propKey);
    }

    /**
     * @return the configured keystore alias password
     */
    protected String getAliasPassword() {
        String propKey = "gwt-console.rest-proxy.authentication.saml.key-password";
        return this.configProperties.get(propKey);
    }

}
