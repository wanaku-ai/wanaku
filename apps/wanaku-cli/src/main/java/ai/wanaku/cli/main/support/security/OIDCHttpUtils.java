package ai.wanaku.cli.main.support.security;

import java.io.IOException;
import java.net.URL;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import net.minidev.json.JSONObject;

public class OIDCHttpUtils {

    private OIDCHttpUtils() {}

    public static OIDCProviderMetadata performHttpRequest(Issuer issuer, boolean insecure)
            throws GeneralException, IOException {
        final URL openIdConfigUrl = OIDCProviderMetadata.resolveURL(issuer);
        HTTPRequest httpRequest = new HTTPRequest(HTTPRequest.Method.GET, openIdConfigUrl);
        if (insecure) {
            try {
                httpRequest.setSSLSocketFactory(InsecureSSLHelper.getInsecureSSLSocketFactory());
                httpRequest.setHostnameVerifier(InsecureSSLHelper.getInsecureHostnameVerifier());
            } catch (Exception e) {
                throw new ServiceAuthException("Failed to configure insecure SSL: " + e.getMessage(), e);
            }
        }
        HTTPResponse httpResponse = httpRequest.send();
        if (httpResponse.getStatusCode() != 200) {
            throw new ServiceAuthException("Unable to download OpenID Provider metadata from " + openIdConfigUrl
                    + ": Status code " + httpResponse.getStatusCode());
        }
        JSONObject jsonObject = httpResponse.getBodyAsJSONObject();
        OIDCProviderMetadata resolvedOp = OIDCProviderMetadata.parse(jsonObject);
        return resolvedOp;
    }
}
