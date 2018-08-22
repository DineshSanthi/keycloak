package org.keycloak.jose.jws;

import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.models.KeycloakSession;

public class HS512TokenSignatureProviderFactory implements TokenSignatureProviderFactory  {

    public static final String ID = Algorithm.HS512.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public TokenSignatureProvider create(KeycloakSession session) {
        return new HMACTokenSignatureProvider(session, Algorithm.HS512.name(), JavaAlgorithm.HS512);
    }

}
