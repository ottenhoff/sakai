package org.sakaiproject.tool.assessment.services.assessment;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.assessment.data.ifc.assessment.AssessmentMetaDataIfc;
import org.sakaiproject.tool.assessment.data.ifc.assessment.PublishedAssessmentIfc;
import org.sakaiproject.tool.assessment.data.ifc.assessment.SecureDeliveryModuleIfc;
import org.sakaiproject.tool.assessment.shared.api.assessment.SecureDeliveryServiceAPI;
import org.sakaiproject.tool.assessment.shared.api.assessment.SecureDeliveryServiceAPI.Phase;
import org.sakaiproject.tool.assessment.shared.api.assessment.SecureDeliveryServiceAPI.PhaseStatus;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriUtils;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class SecureDeliveryProctorio implements SecureDeliveryModuleIfc {

	final private static String ENCODING = java.nio.charset.StandardCharsets.US_ASCII.toString();
	private static String proctorioKey;
	private static String proctorioSecret;
	private static String proctorioUrl;

	private SessionManager sessionManager = ComponentManager.get(SessionManager.class);
	private UserDirectoryService userDirectoryService = ComponentManager.get(UserDirectoryService.class);
	private ServerConfigurationService serverConfigurationService = ComponentManager.get(ServerConfigurationService.class);

	@Override
	public boolean initialize() {
		proctorioKey = serverConfigurationService.getString("proctorio.key", null);
		proctorioSecret = serverConfigurationService.getString("proctorio.secret", null);
		proctorioUrl = serverConfigurationService.getString("proctorio.url", null);
		
		System.out.println("zz01: " + proctorioKey + ":" + proctorioSecret + ":" + proctorioUrl);

		return (proctorioKey != null) && (proctorioSecret != null) && (proctorioUrl != null);
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String getModuleName(Locale locale) {
		return "Proctorio";
	}

	@Override
	public String getTitleDecoration(Locale locale) {
		return " (Proctorio required)	";
	}

	@Override
	public PhaseStatus validatePhase(Phase phase, PublishedAssessmentIfc assessment, HttpServletRequest request) {
		System.out.println("zz01: validate : " + phase);
		return SecureDeliveryServiceAPI.PhaseStatus.SUCCESS;
	}

	@Override
	public String getInitialHTMLFragment(HttpServletRequest request, Locale locale) {
		return ""; //"<strong>Proctorio initial HTML fragment</strong>";
	}

	@Override
	public String getHTMLFragment(PublishedAssessmentIfc assessment, HttpServletRequest request, Phase phase,
			PhaseStatus status, Locale locale) {
		
		
		switch (phase) {
			case ASSESSMENT_START:
			case ASSESSMENT_FINISH:
			case ASSESSMENT_REVIEW:
				return "";
		}

		return "<strong>Proctorio HTML</strong>";
	}

	@Override
	public boolean validateContext(Object context) {
		return true;
	}

	@Override
	public String encryptPassword(String password) {
		return "";
	}

	@Override
	public String decryptPassword(String password) {
		return "";
	}

	@Override
	public String getAlternativeDeliveryUrl (PublishedAssessmentIfc assessment) {
		System.out.println("zz02: " + assessment.getTitle());

		final Session sakaiSession = sessionManager.getCurrentSession();
		final String userId = sakaiSession.getUserId();
		User user = null;

		try {
			user = userDirectoryService.getUser(userId);
		} catch (UserNotDefinedException e) {
			log.warn("ProctorIO secure delivery could not find user ({})", userId);
			return "";
		}
		
		final String assessmentPath = serverConfigurationService.getServerUrl() + 
				"/samigo-app/servlet/Login?id=" + assessment.getAssessmentMetaDataByLabel(AssessmentMetaDataIfc.ALIAS);
		System.out.println("zz03: " + user.toString() + "::" + assessment.toString() + "::" + assessmentPath);
		
		try {
			return buildURL(user.getEid(), user.getDisplayName(), assessment.getAssessmentId(), assessmentPath);
		} catch (IOException e) {
			log.warn("ProctorIO could not build the URL", e);
			return "ProctorIO " + e.getMessage();
		}

	}

	/*
	 * private static String[] UriRfc3986CharsToEscape = { "!", "*", "'", "(", ")"
	 * };
	 * 
	 * private static String EscapeUriDataStringRfc3986(String value) { // Start
	 * with RFC 2396 escaping by calling the .NET method to do the
	 * work.documentation; // If it does, the escaping we do that follows it will be
	 * a no-op since the string StringBuilder escaped = new
	 * StringBuilder(URLEncoder.encode(value,
	 * java.nio.charset.StandardCharsets.ISO_8859_1.toString())); // Upgrade the
	 * escaping to RFC 3986, if necessary. for (String s : UriRfc3986CharsToEscape)
	 * { escaped.Replace(s, Uri.HexEscape(s[0])); }
	 * 
	 * // Return the fully-RFC3986-escaped string. return escaped.toString(); }
	 */

	private static String toNormalizedString(Map<String, String> collection, List<String> excludedNames) {
		StringBuilder normalizedString = new StringBuilder();

		for (String key : collection.keySet()) {
			if (excludedNames != null && excludedNames.contains(key)) {
				continue;
			}
			
			String value = collection.getOrDefault(key, "");

			String encodedKey = null;
			String encodedValue = null;
			try {
				String decodedKey = UriUtils.decode(key, ENCODING);
				encodedKey = UriUtils.encode(decodedKey, ENCODING);
				
				String decodedValue = UriUtils.decode(value, ENCODING);
				encodedValue = UriUtils.encode(decodedValue, ENCODING);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			normalizedString.append("&").append(encodedKey != null ? encodedKey : key).append("=").append(encodedValue != null ? encodedValue : value);
		}

		return normalizedString.substring(1); // remove the leading ampersand
	}

	private String buildURL(String eid, String fullname, Long assessmentId, String launchUrl) throws ClientProtocolException, IOException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("launch_url", launchUrl);
        parameters.put("user_id", eid);
        parameters.put("oauth_consumer_key", proctorioKey);
        parameters.put("exam_start", "http(.*)/samigo-app/servlet/Login(.*)");
        parameters.put("exam_take", "http(.*)/samigo-app/jsf/delivery(.*)");
        parameters.put("exam_end", "http(.*)confirmSubmit(.*)");
        parameters.put("exam_settings", "recordvideo");
        parameters.put("fullname", fullname);
        parameters.put("exam_tag", assessmentId + "");
        parameters.put("oauth_signature_method", "HMAC-SHA1");
        parameters.put("oauth_version", "1.0");
        parameters.put("oauth_timestamp", (System.currentTimeMillis() / 1000) + "");
        parameters.put("oauth_nonce", (int)(Math.random() * 100000000) + "");
        
        String signature_base_string = "POST&" 
                    + UriUtils.encode(proctorioUrl, ENCODING) 
                    + "&" + UriUtils.encode(toNormalizedString(parameters, null), ENCODING);
        
        log.debug("Proctorio signature_base_string: {}", signature_base_string);

        byte[] hmac = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, proctorioSecret).hmac(signature_base_string);
        //  BASE64 ENCODED
        String oauthSignature = Base64.getEncoder().encodeToString(hmac);
        //  Add the signature to the params list
        parameters.put("oauth_signature", oauthSignature);

        //  Build data parameters
        StringBuilder parameterBuilder = new StringBuilder();
        for (String paramKey : parameters.keySet()) {
            //  build it for HTTP transport
        	String value = parameters.getOrDefault(paramKey, "");
            parameterBuilder.append("&");
            parameterBuilder.append(paramKey);
            parameterBuilder.append("=");
            parameterBuilder.append(UriUtils.encode(value, ENCODING));
        }
        
        //  Convert to a single string
        String parameterString = parameterBuilder.toString();
        //  Slice off the leading ampersand
        parameterString = parameterString.substring(1);
        
        log.debug("Proctorio parameterString: {}", parameterString);
        
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(proctorioUrl);
        httpPost.setEntity(new StringEntity(parameterString));
        CloseableHttpResponse response = client.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity returnEntity = response.getEntity();
        String r = EntityUtils.toString(returnEntity);
        System.out.println("zz50: " + statusCode + ":" + r);
        return r;
	}

}
