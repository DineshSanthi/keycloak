package org.keycloak.testsuite.util;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.common.util.Base64;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.jose.jws.ES256TokenSignatureProviderFactory;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.TokenSignatureProvider;
import org.keycloak.keys.GeneratedEcdsaKeyProviderFactory;
import org.keycloak.keys.KeyProvider;
import org.keycloak.protocol.oidc.OIDCAdvancedConfigWrapper;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.KeysMetadataRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.arquillian.TestContext;

public class TokenSignatureUtil {
    private static Logger log = Logger.getLogger(TokenSignatureUtil.class);

    private static final String COMPONENT_SIGNATURE_ALGORITHM_KEY = "token.signed.response.alg";
    
    private static final String ECDSA_ELLIPTIC_CURVE_KEY = "ecdsaEllipticCurveKey";
    private static final String TEST_REALM_NAME = "test";

    public static void changeRealmTokenSignatureProvider(Keycloak adminClient, String toSigAlgName) {
        RealmRepresentation rep = adminClient.realm(TEST_REALM_NAME).toRepresentation();
        Map<String, String> attributes = rep.getAttributes();
        log.tracef("change realm test signature algorithm from %s to %s", attributes.get(COMPONENT_SIGNATURE_ALGORITHM_KEY), toSigAlgName);
        rep.setDefaultSignatureAlgorithm(toSigAlgName);
        rep.setAttributes(attributes);
        adminClient.realm(TEST_REALM_NAME).update(rep);
    }

    public static void changeClientAccessTokenSignatureProvider(ClientResource clientResource, String toSigAlgName) {
        ClientRepresentation clientRep = clientResource.toRepresentation();
        log.tracef("change client %s access token signature algorithm from %s to %s", clientRep.getClientId(), clientRep.getAttributes().get(OIDCConfigAttributes.ACCESS_TOKEN_SIGNED_RESPONSE_ALG), toSigAlgName);
        clientRep.getAttributes().put(OIDCConfigAttributes.ACCESS_TOKEN_SIGNED_RESPONSE_ALG, toSigAlgName);
        clientResource.update(clientRep);
    }

    public static void changeClientIdTokenSignatureProvider(ClientResource clientResource, String toSigAlgName) {
        ClientRepresentation clientRep = clientResource.toRepresentation();
        log.tracef("change client %s access token signature algorithm from %s to %s", clientRep.getClientId(), clientRep.getAttributes().get(OIDCConfigAttributes.ID_TOKEN_SIGNED_RESPONSE_ALG), toSigAlgName);
        clientRep.getAttributes().put(OIDCConfigAttributes.ID_TOKEN_SIGNED_RESPONSE_ALG, toSigAlgName);
        clientResource.update(clientRep);
    }

    public static boolean verifySignature(String sigAlgName, String token, Keycloak adminClient) throws Exception {
        PublicKey publicKey = getRealmPublicKey(TEST_REALM_NAME, sigAlgName, adminClient);
        JWSInput jws = new JWSInput(token);
        Signature verifier = getSignature(sigAlgName);
        verifier.initVerify(publicKey);
        verifier.update(jws.getEncodedSignatureInput().getBytes("UTF-8"));
        return verifier.verify(jws.getSignature());
    }

    public static void registerKeyProvider(String ecNistRep, Keycloak adminClient, TestContext testContext) {
        long priority = System.currentTimeMillis();

        ComponentRepresentation rep = createKeyRep("valid", GeneratedEcdsaKeyProviderFactory.ID);
        rep.setConfig(new MultivaluedHashMap<>());
        rep.getConfig().putSingle("priority", Long.toString(priority));
        rep.getConfig().putSingle(ECDSA_ELLIPTIC_CURVE_KEY, ecNistRep);

        Response response = adminClient.realm(TEST_REALM_NAME).components().add(rep);
        String id = ApiUtil.getCreatedId(response);
        testContext.getOrCreateCleanup(TEST_REALM_NAME).addComponentId(id);
        response.close();
    }

    private static ComponentRepresentation createTokenSignatureRep(String name, String providerId) {
        ComponentRepresentation rep = new ComponentRepresentation();
        rep.setName(name);
        rep.setParentId(TEST_REALM_NAME);
        rep.setProviderId(providerId);
        rep.setProviderType(TokenSignatureProvider.class.getName());
        rep.setConfig(new MultivaluedHashMap<>());
        return rep;
    }

    private static ComponentRepresentation createKeyRep(String name, String providerId) {
        ComponentRepresentation rep = new ComponentRepresentation();
        rep.setName(name);
        rep.setParentId(TEST_REALM_NAME);
        rep.setProviderId(providerId);
        rep.setProviderType(KeyProvider.class.getName());
        rep.setConfig(new MultivaluedHashMap<>());
        return rep;
    }

    private static PublicKey getRealmPublicKey(String realm, String sigAlgName, Keycloak adminClient) {
        KeysMetadataRepresentation keyMetadata = adminClient.realms().realm(realm).keys().getKeyMetadata();
        String activeKid = keyMetadata.getActive().get(sigAlgName);
        PublicKey publicKey = null;
        for (KeysMetadataRepresentation.KeyMetadataRepresentation rep : keyMetadata.getKeys()) {
            if (rep.getKid().equals(activeKid)) {
                X509EncodedKeySpec publicKeySpec = null;
                try {
                    publicKeySpec = new X509EncodedKeySpec(Base64.decode(rep.getPublicKey()));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                KeyFactory kf = null;
                try {
                    kf = KeyFactory.getInstance(rep.getType());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                try {
                    publicKey = kf.generatePublic(publicKeySpec);
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                }
            }
        }
        return publicKey;
    }

    private static Signature getSignature(String sigAlgName) {
        try {
            // use Bouncy Castle for signature verification intentionally
            Signature signature = Signature.getInstance(JavaAlgorithm.getJavaAlgorithm(sigAlgName), "BC");
            return signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
