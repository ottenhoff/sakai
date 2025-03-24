# Sakai SAML 2.0 Migration Testing Guide

This guide provides instructions for testing the migration from the deprecated Spring Security SAML Extension to the official Spring Security SAML 2.0 implementation.

## Testing Tools

The following testing tools are available in this directory:

1. **run-with-mocksaml.sh** - Script to set up a test environment with MockSaml
2. **verify-saml-config.sh** - Script to verify SAML configuration
3. **generate-test-saml-response.sh** - Script to generate test SAML responses
4. **SAML-TESTING-PLAN.md** - Comprehensive testing plan

## Testing with MockSaml

[MockSaml](https://github.com/spring-projects/spring-security-samples/tree/main/servlet/spring-boot/java/saml2/mocksaml) is a simple SAML Identity Provider (IdP) implementation that you can use for testing SAML integration without needing a real IdP.

### Setup Instructions

1. Set your `CATALINA_HOME` environment variable to your Tomcat installation directory:
   ```bash
   export CATALINA_HOME=/path/to/tomcat
   ```

2. Run the MockSaml setup script:
   ```bash
   chmod +x run-with-mocksaml.sh
   ./run-with-mocksaml.sh
   ```

3. This will:
   - Clone the MockSaml repository if needed
   - Build and launch MockSaml on port 8080
   - Configure Sakai to use the MockSaml IdP
   - Start Tomcat with the appropriate configuration

4. Once Tomcat starts, access Sakai at:
   ```
   http://localhost:8080/portal
   ```

5. Click on the SAML login option, which will redirect you to the MockSaml login page.
   You can use any username/password for testing.

### MockSaml Configuration

MockSaml is configured using the `sakai-saml-mocksaml.properties` file. Key properties:

- `sakai.saml.mock.enabled=true` - Enables MockSaml mode
- `sakai.saml.mock.baseUrl=http://localhost:8080/mocksaml` - MockSaml URL
- `sakai.saml.auth.useEppn=true` - Use eduPersonPrincipalName for user identification
- `sakai.saml.auth.useUpn=true` - Use UserPrincipalName as fallback

## Verifying Your Configuration

The `verify-saml-config.sh` script checks your SAML configuration and helps diagnose issues:

```bash
chmod +x verify-saml-config.sh
./verify-saml-config.sh
```

This script:
1. Verifies directory structures
2. Checks configuration files
3. Validates SAML properties
4. Checks metadata files
5. Provides testing recommendations

## Testing SAML Attribute Handling

The `generate-test-saml-response.sh` script generates test SAML responses with different attributes:

```bash
chmod +x generate-test-saml-response.sh
./generate-test-saml-response.sh
```

This creates several SAML response XML files:
- `standard-response.xml` - Contains all attributes (EPPN, UPN, etc.)
- `eppn-only-response.xml` - Contains only eduPersonPrincipalName
- `upn-only-response.xml` - Contains only UserPrincipalName
- `no-attrs-response.xml` - Contains no identity attributes (falls back to NameID)
- `expired-response.xml` - Contains an expired assertion

These responses help verify that `SakaiSamlAuthenticationConverter` correctly handles different SAML attribute scenarios.

## Manual Testing

Refer to `SAML-TESTING-PLAN.md` for a comprehensive test plan. Key test scenarios:

1. **Authentication Flow**
   - Test SAML login initiation
   - Verify authentication response processing
   - Check attribute mapping

2. **Logout Process**
   - Test SP-initiated logout
   - Test IdP-initiated logout
   - Verify session cleanup

3. **Metadata Exchange**
   - Verify SP metadata generation
   - Test IdP metadata processing
   - Check certificate validation

4. **Error Handling**
   - Test with invalid SAML responses
   - Test with missing attributes
   - Test with expired assertions

## Switching Implementations

After successful testing, switch from the old to the new implementation:

1. Rename the configuration files:
   ```bash
   cd $CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/
   mv xlogin-context.saml.xml xlogin-context.saml.xml.bak
   mv xlogin-context.saml-new.xml xlogin-context.saml.xml
   ```

2. Restart Tomcat:
   ```bash
   $CATALINA_HOME/bin/shutdown.sh
   $CATALINA_HOME/bin/startup.sh
   ```

3. Verify that authentication still works with the new implementation

## Debugging

If you encounter issues during testing, check the following logs:

- `$CATALINA_HOME/logs/catalina.out` - Tomcat main log
- `$CATALINA_HOME/logs/sakai/sakai.log` - Sakai application log

To enable verbose SAML logging, set:
```
sakai.saml.verbose.logging=true
```
in your SAML properties file.

## Troubleshooting

Common issues and solutions:

1. **MockSaml fails to start**
   - Check that port 8080 is not already in use
   - Ensure you have Java and Git installed

2. **Authentication fails**
   - Verify that properties are correctly loaded (use verify-saml-config.sh)
   - Check for mismatched entityIDs
   - Ensure the SAML endpoints are correctly configured in web.xml

3. **Metadata issues**
   - Verify that IdP metadata is valid XML
   - Check that the entityID in the metadata matches the configuration

4. **Authentication converter issues**
   - Review logs for attribute mapping errors
   - Verify that the configured attribute names match those sent by the IdP

## Reference

- [Spring Security SAML 2.0 Documentation](https://docs.spring.io/spring-security/reference/servlet/saml2/index.html)
- [OpenSAML 4.x Documentation](https://shibboleth.atlassian.net/wiki/spaces/OSAML/overview)
- [SAML 2.0 Specification](http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf)