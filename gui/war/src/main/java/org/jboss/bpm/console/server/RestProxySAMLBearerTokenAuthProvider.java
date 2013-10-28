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
import java.security.KeyPair;
import java.security.KeyStore;
import java.util.Map;

import org.overlord.commons.auth.jboss7.SAMLBearerTokenUtil;

/**
 * An auth provider that uses SAML bearer token authentication.
 * @author eric.wittmann@redhat.com
 */
public class RestProxySAMLBearerTokenAuthProvider implements RestProxyAuthProvider {

    public static final String SAML_AUTH_ISSUER = "bpel-console.rest-proxy.authentication.saml.issuer"; //$NON-NLS-1$
    public static final String SAML_AUTH_SERVICE = "bpel-console.rest-proxy.authentication.saml.service"; //$NON-NLS-1$
    public static final String SAML_AUTH_SIGN_ASSERTIONS = "bpel-console.rest-proxy.authentication.saml.sign-assertions"; //$NON-NLS-1$
    public static final String SAML_AUTH_KEYSTORE = "bpel-console.rest-proxy.authentication.saml.keystore"; //$NON-NLS-1$
    public static final String SAML_AUTH_KEYSTORE_PASSWORD = "bpel-console.rest-proxy.authentication.saml.keystore-password"; //$NON-NLS-1$
    public static final String SAML_AUTH_KEY_ALIAS = "bpel-console.rest-proxy.authentication.saml.key-alias"; //$NON-NLS-1$
    public static final String SAML_AUTH_KEY_PASSWORD = "bpel-console.rest-proxy.authentication.saml.key-password"; //$NON-NLS-1$

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
                KeyStore keystore = SAMLBearerTokenUtil.loadKeystore(getKeystorePath(), getKeystorePassword());
                KeyPair keyPair = SAMLBearerTokenUtil.getKeyPair(keystore, getAlias(), getAliasPassword());
                samlAssertion = SAMLBearerTokenUtil.signSAMLAssertion(samlAssertion, keyPair);
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
        return this.configProperties.get(SAML_AUTH_ISSUER);
    }

    /**
     * @return the configured saml service
     */
    private String getService() {
        return this.configProperties.get(SAML_AUTH_SERVICE);
    }

    /**
     * @return whether saml assertions should be digitally signed
     */
    private boolean isSignAssertions() {
        return "true".equals(this.configProperties.get(SAML_AUTH_SIGN_ASSERTIONS));
    }

    /**
     * @return the configured digital signature keystore
     */
    protected String getKeystorePath() {
        return this.configProperties.get(SAML_AUTH_KEYSTORE);
    }

    /**
     * @return the configured keystore password
     */
    protected String getKeystorePassword() {
        return this.configProperties.get(SAML_AUTH_KEYSTORE_PASSWORD);
    }

    /**
     * @return the configured keystore alias
     */
    protected String getAlias() {
        return this.configProperties.get(SAML_AUTH_KEY_ALIAS);
    }

    /**
     * @return the configured keystore alias password
     */
    protected String getAliasPassword() {
        return this.configProperties.get(SAML_AUTH_KEY_PASSWORD);
    }

}
