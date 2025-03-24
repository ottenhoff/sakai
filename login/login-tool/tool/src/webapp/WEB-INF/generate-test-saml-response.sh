#!/bin/bash
# Test SAML Response Generator
# This script generates test SAML responses with different attributes and conditions

# Load OpenSAML libraries
LIB_DIR="$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/lib"
CLASSPATH=""
for jar in "$LIB_DIR"/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

# Create output directory
OUTPUT_DIR="saml-test-responses"
mkdir -p "$OUTPUT_DIR"

echo "Generating test SAML responses..."
echo "This may take a few seconds..."

# Use Java to generate SAML responses
java -cp "$CLASSPATH" org.sakaiproject.login.saml.TestSamlResponseGenerator "$OUTPUT_DIR"

# If Java class doesn't exist, create a simple XML template
if [ $? -ne 0 ]; then
  echo "Java SAML generator not available, creating XML templates instead"
  
  # Create a basic SAML response template
  cat > "$OUTPUT_DIR/basic-saml-response.xml" << EOF
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_response_id"
                Version="2.0"
                IssueInstant="2023-01-01T12:00:00Z"
                Destination="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                InResponseTo="_request_id">
  <saml:Issuer>mocksaml-idp</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion_id"
                  Version="2.0"
                  IssueInstant="2023-01-01T12:00:00Z">
    <saml:Issuer>mocksaml-idp</saml:Issuer>
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">testuser@example.com</saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
        <saml:SubjectConfirmationData InResponseTo="_request_id"
                                      Recipient="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                                      NotOnOrAfter="2023-01-01T12:10:00Z"/>
      </saml:SubjectConfirmation>
    </saml:Subject>
    <saml:Conditions NotBefore="2023-01-01T11:55:00Z"
                     NotOnOrAfter="2023-01-01T12:10:00Z">
      <saml:AudienceRestriction>
        <saml:Audience>sakai-sp</saml:Audience>
      </saml:AudienceRestriction>
    </saml:Conditions>
    <saml:AuthnStatement AuthnInstant="2023-01-01T12:00:00Z"
                         SessionIndex="_session_index">
      <saml:AuthnContext>
        <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
      </saml:AuthnContext>
    </saml:AuthnStatement>
    <saml:AttributeStatement>
      <saml:Attribute Name="urn:oid:1.3.6.1.4.1.5923.1.1.1.6" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri">
        <saml:AttributeValue>testuser@example.edu</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri">
        <saml:AttributeValue>testuser@example.com</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="firstName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
        <saml:AttributeValue>Test</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="lastName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
        <saml:AttributeValue>User</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="email" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
        <saml:AttributeValue>testuser@example.com</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
EOF

  # Create a SAML response with EPPN only
  cat > "$OUTPUT_DIR/eppn-only-response.xml" << EOF
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_response_id"
                Version="2.0"
                IssueInstant="2023-01-01T12:00:00Z"
                Destination="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                InResponseTo="_request_id">
  <saml:Issuer>mocksaml-idp</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion_id"
                  Version="2.0"
                  IssueInstant="2023-01-01T12:00:00Z">
    <saml:Issuer>mocksaml-idp</saml:Issuer>
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified">testuser</saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
        <saml:SubjectConfirmationData InResponseTo="_request_id"
                                      Recipient="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                                      NotOnOrAfter="2023-01-01T12:10:00Z"/>
      </saml:SubjectConfirmation>
    </saml:Subject>
    <saml:Conditions NotBefore="2023-01-01T11:55:00Z"
                     NotOnOrAfter="2023-01-01T12:10:00Z">
      <saml:AudienceRestriction>
        <saml:Audience>sakai-sp</saml:Audience>
      </saml:AudienceRestriction>
    </saml:Conditions>
    <saml:AuthnStatement AuthnInstant="2023-01-01T12:00:00Z"
                         SessionIndex="_session_index">
      <saml:AuthnContext>
        <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
      </saml:AuthnContext>
    </saml:AuthnStatement>
    <saml:AttributeStatement>
      <saml:Attribute Name="urn:oid:1.3.6.1.4.1.5923.1.1.1.6" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri">
        <saml:AttributeValue>eppnuser@example.edu</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
EOF

  # Create a SAML response with UPN only
  cat > "$OUTPUT_DIR/upn-only-response.xml" << EOF
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_response_id"
                Version="2.0"
                IssueInstant="2023-01-01T12:00:00Z"
                Destination="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                InResponseTo="_request_id">
  <saml:Issuer>mocksaml-idp</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion_id"
                  Version="2.0"
                  IssueInstant="2023-01-01T12:00:00Z">
    <saml:Issuer>mocksaml-idp</saml:Issuer>
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified">testuser</saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
        <saml:SubjectConfirmationData InResponseTo="_request_id"
                                      Recipient="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                                      NotOnOrAfter="2023-01-01T12:10:00Z"/>
      </saml:SubjectConfirmation>
    </saml:Subject>
    <saml:Conditions NotBefore="2023-01-01T11:55:00Z"
                     NotOnOrAfter="2023-01-01T12:10:00Z">
      <saml:AudienceRestriction>
        <saml:Audience>sakai-sp</saml:Audience>
      </saml:AudienceRestriction>
    </saml:Conditions>
    <saml:AuthnStatement AuthnInstant="2023-01-01T12:00:00Z"
                         SessionIndex="_session_index">
      <saml:AuthnContext>
        <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
      </saml:AuthnContext>
    </saml:AuthnStatement>
    <saml:AttributeStatement>
      <saml:Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri">
        <saml:AttributeValue>upnuser@example.com</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
EOF

  # Create a SAML response with no identity attributes (fallback to NameID)
  cat > "$OUTPUT_DIR/no-attrs-response.xml" << EOF
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_response_id"
                Version="2.0"
                IssueInstant="2023-01-01T12:00:00Z"
                Destination="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                InResponseTo="_request_id">
  <saml:Issuer>mocksaml-idp</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion_id"
                  Version="2.0"
                  IssueInstant="2023-01-01T12:00:00Z">
    <saml:Issuer>mocksaml-idp</saml:Issuer>
    <saml:Subject>
      <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">fallbackuser@example.com</saml:NameID>
      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
        <saml:SubjectConfirmationData InResponseTo="_request_id"
                                      Recipient="http://localhost:8080/sakai-login-tool/container/saml/SSO"
                                      NotOnOrAfter="2023-01-01T12:10:00Z"/>
      </saml:SubjectConfirmation>
    </saml:Subject>
    <saml:Conditions NotBefore="2023-01-01T11:55:00Z"
                     NotOnOrAfter="2023-01-01T12:10:00Z">
      <saml:AudienceRestriction>
        <saml:Audience>sakai-sp</saml:Audience>
      </saml:AudienceRestriction>
    </saml:Conditions>
    <saml:AuthnStatement AuthnInstant="2023-01-01T12:00:00Z"
                         SessionIndex="_session_index">
      <saml:AuthnContext>
        <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
      </saml:AuthnContext>
    </saml:AuthnStatement>
    <saml:AttributeStatement>
      <saml:Attribute Name="firstName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
        <saml:AttributeValue>Fallback</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="lastName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
        <saml:AttributeValue>User</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
EOF
fi

echo "SAML test responses generated in: $OUTPUT_DIR"
echo "These responses can be used for testing the SAML attribute handling in SakaiSamlAuthenticationConverter."
echo ""
echo "Files generated:"
ls -l "$OUTPUT_DIR"
echo ""
echo "Note: These are XML templates and not valid SAML messages (not signed or encoded)."
echo "They are intended for code review and understanding the structure, not for direct use."