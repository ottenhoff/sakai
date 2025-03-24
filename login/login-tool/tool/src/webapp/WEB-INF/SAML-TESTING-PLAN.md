# Sakai SAML 2.0 Migration Testing Plan

This document outlines the testing approach for verifying the successful migration from the deprecated Spring Security SAML Extension to the official Spring Security SAML 2.0 implementation.

## Test Environment Setup

### MockSaml Test Environment

1. **Setup Instructions**:
   - Use the provided `run-with-mocksaml.sh` script to set up the test environment
   - The script will:
     - Clone the MockSaml repository (if not present)
     - Build and launch MockSaml on port 8080
     - Configure Sakai to use the MockSaml IdP
     - Start Tomcat with the appropriate configuration

2. **Configuration**:
   - The test environment uses `sakai-saml-mocksaml.properties`
   - MockSaml simulates a SAML IdP with configurable responses

### Production-Like Test Environment

1. **Setup Instructions**:
   - Use a copy of your production SAML configuration
   - Place IdP metadata in the appropriate directory
   - Configure properties following the migration documentation

## Test Cases

### 1. Basic Authentication Flow

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| AUTH-01 | SAML Login Initiation | 1. Access the Sakai portal<br>2. Click "Login with SAML" | User is redirected to the SAML IdP login page | |
| AUTH-02 | SAML Authentication | 1. Enter credentials at the IdP<br>2. Submit the login form | IdP authenticates the user and returns SAML response to Sakai | |
| AUTH-03 | Response Processing | 1. After IdP redirects back to Sakai<br>2. Observe response handling | Sakai processes the SAML assertion and establishes a user session | |
| AUTH-04 | Role and Attribute Mapping | 1. Authenticate with a test user<br>2. Check user attributes in Sakai | User attributes (EPPN/UPN) are correctly extracted and mapped | |

### 2. Logout Process

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| LOGOUT-01 | SP-Initiated Logout | 1. Login via SAML<br>2. Click logout in Sakai | User session in Sakai is terminated and SAML logout is initiated | |
| LOGOUT-02 | IdP-Initiated Logout | 1. Login via SAML<br>2. Initiate logout from IdP | User session in Sakai is terminated when receiving logout request | |
| LOGOUT-03 | Session Cleanup | 1. Perform a SAML logout<br>2. Attempt to access protected resources | User is redirected to login page after logout | |

### 3. Metadata Exchange

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| META-01 | SP Metadata Generation | 1. Access `/container/saml/metadata`<br>2. Examine the XML | Valid SAML SP metadata is generated with correct entityID and endpoints | |
| META-02 | IdP Metadata Processing | 1. Configure with test IdP metadata<br>2. Initiate authentication | Sakai correctly uses the IdP endpoints from metadata | |
| META-03 | Certificate Validation | 1. Configure with signed metadata<br>2. Test authentication | Certificates are correctly validated | |

### 4. Configuration and Properties

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| CONFIG-01 | Property Loading | 1. Set properties in different locations<br>2. Check which ones are applied | Properties are loaded with correct precedence order | |
| CONFIG-02 | Environment-Specific Config | 1. Set `sakai.saml.env=mocksaml`<br>2. Observe config loading | Environment-specific properties are correctly loaded | |
| CONFIG-03 | Tomcat Directory Config | 1. Place properties in `$CATALINA_HOME/sakai/saml/`<br>2. Start Sakai | Properties from Tomcat directory are loaded with highest priority | |

### 5. Error Handling and Edge Cases

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| ERR-01 | Invalid SAML Response | 1. Send malformed SAML response<br>2. Observe error handling | Application handles error gracefully and logs appropriate messages | |
| ERR-02 | Missing Attributes | 1. Configure IdP to omit required attributes<br>2. Attempt login | Application handles missing attributes gracefully | |
| ERR-03 | Expired Assertion | 1. Configure assertion with past expiration<br>2. Attempt login | Expired assertion is rejected with clear error | |

### 6. Compatibility Testing

| Test Case ID | Description | Steps | Expected Result | Status |
|--------------|-------------|-------|-----------------|--------|
| COMPAT-01 | Browser Compatibility | Test with Chrome, Firefox, Safari, Edge | Authentication works in all modern browsers | |
| COMPAT-02 | Mobile Compatibility | Test on mobile devices/browsers | Authentication flow works on mobile devices | |
| COMPAT-03 | Multiple IdP Testing | Test with different IdP implementations if available | Authentication works with different IdP types | |

## Test Data

### Test Users

| Username | Password | Attributes | Purpose |
|----------|----------|------------|---------|
| testuser | password | EPPN: testuser@example.com | Basic authentication testing |
| adminuser | password | UPN: adminuser@example.com | Testing admin role mapping |
| noattrs | password | No EPPN or UPN | Testing fallback attribute handling |

## Test Execution Process

1. **Setup Phase**:
   - Configure test environment using `run-with-mocksaml.sh`
   - Verify MockSaml is running correctly
   - Configure Sakai to use MockSaml IdP

2. **Test Execution**:
   - Execute test cases in the defined order
   - Document results in the status column
   - Capture and analyze logs for errors

3. **Validation Phase**:
   - Verify all test cases pass
   - Compare behavior with old implementation
   - Document any differences or issues

## Migration Verification

After completing all tests, perform these verification steps:

1. **Rename Configuration Files**:
   - Rename `xlogin-context.saml-new.xml` to `xlogin-context.saml.xml`
   - Restart Sakai and verify authentication still works

2. **Code Inspection**:
   - Verify all deprecated code is removed or marked for removal
   - Confirm no references to old SAML extension remain

3. **Security Review**:
   - Verify all authentication flows are secure
   - Check that SSL/TLS is properly enforced
   - Confirm certificate validation is working correctly

## References

- [Spring Security SAML 2.0 Documentation](https://docs.spring.io/spring-security/reference/servlet/saml2/index.html)
- [MockSaml Documentation](https://github.com/spring-projects/spring-security-samples/tree/main/servlet/spring-boot/java/saml2/mocksaml)
- [OpenSAML 4.x Documentation](https://shibboleth.atlassian.net/wiki/spaces/OSAML/overview)