package org.sakaiproject.login.saml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.core.impl.AttributeBuilder;
import org.opensaml.saml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml.saml2.core.impl.AttributeValueBuilder;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Utility class to generate test SAML responses for testing the SAML authentication flow.
 * This generates various test cases for the SakaiSamlAuthenticationConverter.
 */
public class TestSamlResponseGenerator {

    private static final String TEST_IDP_ENTITY_ID = "mocksaml-idp";
    private static final String TEST_SP_ENTITY_ID = "sakai-sp";
    private static final String TEST_DESTINATION = "http://localhost:8080/sakai-login-tool/container/saml/SSO";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: TestSamlResponseGenerator <output_directory>");
            System.exit(1);
        }
        
        String outputDir = args[0];
        Path dir = Paths.get(outputDir);
        
        try {
            // Create output directory if it doesn't exist
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            // Initialize OpenSAML
            initializeOpenSAML();
            
            // Generate test responses
            generateStandardResponse(dir);
            generateEppnOnlyResponse(dir);
            generateUpnOnlyResponse(dir);
            generateNoAttributesResponse(dir);
            generateExpiredResponse(dir);
            
            System.out.println("Successfully generated SAML test responses in: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error generating SAML responses: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void initializeOpenSAML() throws InitializationException {
        // Initialize the OpenSAML library
        InitializationService.initialize();
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> SAMLObjectBuilder<T> getSAMLBuilder(Class<T> clazz) {
        XMLObjectBuilderFactory builderFactory = 
            ConfigurationService.get(XMLObjectProviderRegistry.class).getBuilderFactory();
        return (SAMLObjectBuilder<T>) builderFactory.getBuilder(clazz);
    }
    
    private static Response createBaseResponse() {
        // Create basic SAML response structure
        Instant now = Instant.now();
        String responseId = "_" + UUID.randomUUID().toString();
        String requestId = "_" + UUID.randomUUID().toString();
        
        // Create the Response
        SAMLObjectBuilder<Response> responseBuilder = getSAMLBuilder(Response.class);
        Response response = responseBuilder.buildObject();
        response.setID(responseId);
        response.setIssueInstant(now);
        response.setDestination(TEST_DESTINATION);
        response.setInResponseTo(requestId);
        response.setVersion(SAMLVersion.VERSION_20);
        
        // Create the Issuer
        SAMLObjectBuilder<Issuer> issuerBuilder = getSAMLBuilder(Issuer.class);
        Issuer issuer = issuerBuilder.buildObject();
        issuer.setValue(TEST_IDP_ENTITY_ID);
        response.setIssuer(issuer);
        
        // Create the Status
        SAMLObjectBuilder<Status> statusBuilder = getSAMLBuilder(Status.class);
        Status status = statusBuilder.buildObject();
        
        SAMLObjectBuilder<StatusCode> statusCodeBuilder = getSAMLBuilder(StatusCode.class);
        StatusCode statusCode = statusCodeBuilder.buildObject();
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        
        response.setStatus(status);
        
        return response;
    }
    
    private static Assertion createBaseAssertion(NameID nameId) {
        Instant now = Instant.now();
        String assertionId = "_" + UUID.randomUUID().toString();
        String requestId = "_" + UUID.randomUUID().toString();
        String sessionIndex = "_" + UUID.randomUUID().toString();
        
        // Create the Assertion
        SAMLObjectBuilder<Assertion> assertionBuilder = getSAMLBuilder(Assertion.class);
        Assertion assertion = assertionBuilder.buildObject();
        assertion.setID(assertionId);
        assertion.setIssueInstant(now);
        assertion.setVersion(SAMLVersion.VERSION_20);
        
        // Create the Issuer
        SAMLObjectBuilder<Issuer> issuerBuilder = getSAMLBuilder(Issuer.class);
        Issuer issuer = issuerBuilder.buildObject();
        issuer.setValue(TEST_IDP_ENTITY_ID);
        assertion.setIssuer(issuer);
        
        // Create the Subject
        SAMLObjectBuilder<Subject> subjectBuilder = getSAMLBuilder(Subject.class);
        Subject subject = subjectBuilder.buildObject();
        
        // Set the NameID
        subject.setNameID(nameId);
        
        // Create the SubjectConfirmation
        SAMLObjectBuilder<SubjectConfirmation> subjectConfirmationBuilder = 
            getSAMLBuilder(SubjectConfirmation.class);
        SubjectConfirmation subjectConfirmation = subjectConfirmationBuilder.buildObject();
        subjectConfirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
        
        // Create the SubjectConfirmationData
        SAMLObjectBuilder<SubjectConfirmationData> subjectConfirmationDataBuilder = 
            getSAMLBuilder(SubjectConfirmationData.class);
        SubjectConfirmationData subjectConfirmationData = subjectConfirmationDataBuilder.buildObject();
        subjectConfirmationData.setInResponseTo(requestId);
        subjectConfirmationData.setRecipient(TEST_DESTINATION);
        subjectConfirmationData.setNotOnOrAfter(now.plus(10, ChronoUnit.MINUTES));
        
        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        assertion.setSubject(subject);
        
        // Create the Conditions
        SAMLObjectBuilder<Conditions> conditionsBuilder = getSAMLBuilder(Conditions.class);
        Conditions conditions = conditionsBuilder.buildObject();
        conditions.setNotBefore(now.minus(5, ChronoUnit.MINUTES));
        conditions.setNotOnOrAfter(now.plus(10, ChronoUnit.MINUTES));
        
        // Create the AudienceRestriction
        SAMLObjectBuilder<AudienceRestriction> audienceRestrictionBuilder = 
            getSAMLBuilder(AudienceRestriction.class);
        AudienceRestriction audienceRestriction = audienceRestrictionBuilder.buildObject();
        
        // Create the Audience
        SAMLObjectBuilder<Audience> audienceBuilder = getSAMLBuilder(Audience.class);
        Audience audience = audienceBuilder.buildObject();
        audience.setURI(TEST_SP_ENTITY_ID);
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        assertion.setConditions(conditions);
        
        // Create the AuthnStatement
        SAMLObjectBuilder<AuthnStatement> authnStatementBuilder = getSAMLBuilder(AuthnStatement.class);
        AuthnStatement authnStatement = authnStatementBuilder.buildObject();
        authnStatement.setAuthnInstant(now);
        authnStatement.setSessionIndex(sessionIndex);
        
        // Create the AuthnContext
        SAMLObjectBuilder<AuthnContext> authnContextBuilder = getSAMLBuilder(AuthnContext.class);
        AuthnContext authnContext = authnContextBuilder.buildObject();
        
        // Create the AuthnContextClassRef
        SAMLObjectBuilder<AuthnContextClassRef> authnContextClassRefBuilder = 
            getSAMLBuilder(AuthnContextClassRef.class);
        AuthnContextClassRef authnContextClassRef = authnContextClassRefBuilder.buildObject();
        authnContextClassRef.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
        authnContext.setAuthnContextClassRef(authnContextClassRef);
        
        authnStatement.setAuthnContext(authnContext);
        assertion.getAuthnStatements().add(authnStatement);
        
        return assertion;
    }
    
    private static NameID createNameID(String value, String format) {
        SAMLObjectBuilder<NameID> nameIdBuilder = getSAMLBuilder(NameID.class);
        NameID nameId = nameIdBuilder.buildObject();
        nameId.setValue(value);
        nameId.setFormat(format);
        return nameId;
    }
    
    private static Attribute createAttribute(String name, String value) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        Attribute attribute = attributeBuilder.buildObject();
        attribute.setName(name);
        attribute.setNameFormat(Attribute.URI_REFERENCE);
        
        // Create the attribute value
        XSStringBuilder stringBuilder = new XSStringBuilder();
        XSString stringValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
        stringValue.setValue(value);
        
        attribute.getAttributeValues().add(stringValue);
        return attribute;
    }
    
    private static void addAttributeStatement(Assertion assertion, List<Attribute> attributes) {
        if (attributes.isEmpty()) {
            return;
        }
        
        // Create the AttributeStatement
        AttributeStatementBuilder attributeStatementBuilder = new AttributeStatementBuilder();
        AttributeStatement attributeStatement = attributeStatementBuilder.buildObject();
        
        // Add all attributes
        for (Attribute attribute : attributes) {
            attributeStatement.getAttributes().add(attribute);
        }
        
        assertion.getAttributeStatements().add(attributeStatement);
    }
    
    private static void saveResponse(Response response, Path dir, String filename) 
            throws Exception {
        // Get the marshaller factory
        MarshallerFactory marshallerFactory = 
            ConfigurationService.get(XMLObjectProviderRegistry.class).getMarshallerFactory();
        
        // Get the Response marshaller
        Marshaller marshaller = marshallerFactory.getMarshaller(response);
        
        // Marshal the Response to a DOM Element
        Element element = marshaller.marshall(response);
        
        // Write the Element to a file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        DOMSource source = new DOMSource(element);
        FileOutputStream fos = new FileOutputStream(dir.resolve(filename).toFile());
        StreamResult result = new StreamResult(fos);
        transformer.transform(source, result);
        fos.close();
    }
    
    private static void generateStandardResponse(Path dir) throws Exception {
        Response response = createBaseResponse();
        
        // Create NameID
        NameID nameId = createNameID("testuser@example.com", NameID.EMAIL);
        
        // Create the Assertion
        Assertion assertion = createBaseAssertion(nameId);
        
        // Create attributes
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(createAttribute("urn:oid:1.3.6.1.4.1.5923.1.1.1.6", "testuser@example.edu"));
        attributes.add(createAttribute("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn", "testuser@example.com"));
        attributes.add(createAttribute("firstName", "Test"));
        attributes.add(createAttribute("lastName", "User"));
        attributes.add(createAttribute("email", "testuser@example.com"));
        
        // Add attribute statement to assertion
        addAttributeStatement(assertion, attributes);
        
        // Add assertion to response
        response.getAssertions().add(assertion);
        
        // Save the response
        saveResponse(response, dir, "standard-response.xml");
    }
    
    private static void generateEppnOnlyResponse(Path dir) throws Exception {
        Response response = createBaseResponse();
        
        // Create NameID
        NameID nameId = createNameID("testuser", NameID.UNSPECIFIED);
        
        // Create the Assertion
        Assertion assertion = createBaseAssertion(nameId);
        
        // Create attributes
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(createAttribute("urn:oid:1.3.6.1.4.1.5923.1.1.1.6", "eppnuser@example.edu"));
        
        // Add attribute statement to assertion
        addAttributeStatement(assertion, attributes);
        
        // Add assertion to response
        response.getAssertions().add(assertion);
        
        // Save the response
        saveResponse(response, dir, "eppn-only-response.xml");
    }
    
    private static void generateUpnOnlyResponse(Path dir) throws Exception {
        Response response = createBaseResponse();
        
        // Create NameID
        NameID nameId = createNameID("testuser", NameID.UNSPECIFIED);
        
        // Create the Assertion
        Assertion assertion = createBaseAssertion(nameId);
        
        // Create attributes
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(createAttribute("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn", "upnuser@example.com"));
        
        // Add attribute statement to assertion
        addAttributeStatement(assertion, attributes);
        
        // Add assertion to response
        response.getAssertions().add(assertion);
        
        // Save the response
        saveResponse(response, dir, "upn-only-response.xml");
    }
    
    private static void generateNoAttributesResponse(Path dir) throws Exception {
        Response response = createBaseResponse();
        
        // Create NameID
        NameID nameId = createNameID("fallbackuser@example.com", NameID.EMAIL);
        
        // Create the Assertion
        Assertion assertion = createBaseAssertion(nameId);
        
        // Create attributes (just some basic attributes, no identity attributes)
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(createAttribute("firstName", "Fallback"));
        attributes.add(createAttribute("lastName", "User"));
        
        // Add attribute statement to assertion
        addAttributeStatement(assertion, attributes);
        
        // Add assertion to response
        response.getAssertions().add(assertion);
        
        // Save the response
        saveResponse(response, dir, "no-attrs-response.xml");
    }
    
    private static void generateExpiredResponse(Path dir) throws Exception {
        Response response = createBaseResponse();
        
        // Create NameID
        NameID nameId = createNameID("testuser@example.com", NameID.EMAIL);
        
        // Create the Assertion
        Assertion assertion = createBaseAssertion(nameId);
        
        // Override the conditions to be expired
        Instant now = Instant.now();
        SAMLObjectBuilder<Conditions> conditionsBuilder = getSAMLBuilder(Conditions.class);
        Conditions conditions = conditionsBuilder.buildObject();
        conditions.setNotBefore(now.minus(20, ChronoUnit.MINUTES));
        conditions.setNotOnOrAfter(now.minus(10, ChronoUnit.MINUTES)); // Expired
        
        // Create the AudienceRestriction
        SAMLObjectBuilder<AudienceRestriction> audienceRestrictionBuilder = 
            getSAMLBuilder(AudienceRestriction.class);
        AudienceRestriction audienceRestriction = audienceRestrictionBuilder.buildObject();
        
        // Create the Audience
        SAMLObjectBuilder<Audience> audienceBuilder = getSAMLBuilder(Audience.class);
        Audience audience = audienceBuilder.buildObject();
        audience.setURI(TEST_SP_ENTITY_ID);
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        assertion.setConditions(conditions);
        
        // Create attributes
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(createAttribute("urn:oid:1.3.6.1.4.1.5923.1.1.1.6", "testuser@example.edu"));
        
        // Add attribute statement to assertion
        addAttributeStatement(assertion, attributes);
        
        // Add assertion to response
        response.getAssertions().add(assertion);
        
        // Save the response
        saveResponse(response, dir, "expired-response.xml");
    }
    
    /**
     * Helper class for creating simple string attribute values.
     */
    private static class XSStringBuilder {
        public static final QName TYPE_NAME = new QName("http://www.w3.org/2001/XMLSchema", "string", "xs");
        
        public XSString buildObject(QName elementName, QName typeName) {
            return new XSString(elementName, typeName);
        }
    }
    
    /**
     * Simple implementation of an XSString for attribute values.
     */
    private static class XSString implements AttributeValue {
        private QName elementName;
        private QName typeName;
        private String value;
        
        public XSString(QName elementName, QName typeName) {
            this.elementName = elementName;
            this.typeName = typeName;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        @Override
        public QName getElementQName() {
            return elementName;
        }
        
        @Override
        public QName getSchemaType() {
            return typeName;
        }
        
        @Override
        public String getDOM() {
            return null;
        }
        
        @Override
        public void setDOM(Element element) {
            // Not implemented
        }
        
        @Override
        public Element getDOM() {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Element element = factory.newDocumentBuilder().newDocument().createElementNS(
                        elementName.getNamespaceURI(), elementName.getLocalPart());
                element.setTextContent(value);
                return element;
            } catch (Exception e) {
                return null;
            }
        }
        
        // Additional required methods with minimal implementations
        @Override
        public void detach() {}
        
        @Override
        public AttributeValue clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException();
        }
        
        @Override
        public List<XMLObject> getOrderedChildren() {
            return null;
        }
        
        @Override
        public boolean hasChildren() {
            return false;
        }
        
        @Override
        public boolean hasParent() {
            return false;
        }
        
        @Override
        public XMLObject getParent() {
            return null;
        }
        
        @Override
        public void setParent(XMLObject parent) {
            // Not implemented
        }
        
        @Override
        public String getNamespacePrefix() {
            return elementName.getPrefix();
        }
        
        @Override
        public String getNamespaceURI() {
            return elementName.getNamespaceURI();
        }
        
        @Override
        public String getID() {
            return null;
        }
        
        @Override
        public void setID(String newID) {
            // Not implemented
        }
        
        @Override
        public void releaseDOM() {
            // Not implemented
        }
        
        @Override
        public void releaseChildrenDOM(boolean propagateRelease) {
            // Not implemented
        }
        
        @Override
        public void releaseParentDOM(boolean propagateRelease) {
            // Not implemented
        }
    }
}