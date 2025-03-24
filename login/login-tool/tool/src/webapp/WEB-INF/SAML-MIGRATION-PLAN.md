# Sakai SAML Migration Plan

This document outlines the plan to migrate from the deprecated Spring Security SAML Extension to the official Spring Security SAML 2.0 implementation.

## Current Status

We've started the migration process with:

1. Created Java configuration class `SakaiSamlConfiguration.java` to replace XML configuration
2. Created new SAML logout filter `SakaiSamlLogoutFilter.java` adapted for modern Spring Security
3. Created a new XML context file to serve as a bridge during the migration

## Remaining Work

### 1. Update Maven Dependencies ✓

The `pom.xml` has been updated to replace:
```xml
<dependency>
    <groupId>org.springframework.security.extensions</groupId>
    <artifactId>spring-security-saml2-core</artifactId>
    <version>1.0.10.RELEASE</version>
</dependency>
```

With:
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-saml2-service-provider</artifactId>
    <version>${sakai.spring.security.version}</version>
</dependency>
```

Also added explicit OpenSAML 4.3.2 dependencies:
```xml
<!-- OpenSAML dependencies -->
<dependency>
    <groupId>org.opensaml</groupId>
    <artifactId>opensaml-core</artifactId>
    <version>4.3.2</version>
</dependency>
<dependency>
    <groupId>org.opensaml</groupId>
    <artifactId>opensaml-saml-api</artifactId>
    <version>4.3.2</version>
</dependency>
<dependency>
    <groupId>org.opensaml</groupId>
    <artifactId>opensaml-saml-impl</artifactId>
    <version>4.3.2</version>
</dependency>
```

### 2. Configuration Updates ✓

1. ✓ Created Java configuration class `SakaiSamlConfiguration.java` for Spring Security SAML
2. ✓ Created properties configuration `SakaiSamlPropertiesConfiguration.java` 
3. ✓ Created transitional XML configuration `xlogin-context.saml-new.xml`
4. ✓ Modified `web.xml` to use the new filter chain configuration
5. ✓ Added SAML-specific servlet mappings for endpoints
6. Replace `xlogin-context.saml.xml` with `xlogin-context.saml-new.xml` after testing (TODO)

### 3. Metadata Handling ✓

1. ✓ Created `MetadataConverter.java` utility to transform existing metadata files
2. ✓ Added SP metadata generation to `SakaiSamlConfiguration.java` 
3. ✓ Created `sakai-saml.properties` for configuration

### 4. User Attribute Processing ✓

1. ✓ Created `SakaiSamlAuthenticationConverter.java` to handle SAML attribute mappings
2. ✓ Implemented special handling for eduPersonPrincipalName (EPPN) and UserPrincipalName (UPN)

### 5. Testing Plan ✓

1. ✓ Created test environment with a mock SAML IdP
   - ✓ Created `run-with-mocksaml.sh` script to automate MockSaml setup
   - ✓ Configured `sakai-saml-mocksaml.properties` for testing
   - ✓ Documented testing process in `SAML-TESTING-README.md`
2. ✓ Created comprehensive testing plan
   - ✓ Documented detailed test cases in `SAML-TESTING-PLAN.md`
   - ✓ Created `verify-saml-config.sh` for configuration validation
   - ✓ Created `generate-test-saml-response.sh` for test data generation
   - ✓ Implemented `TestSamlResponseGenerator.java` for SAML attribute testing
3. Test authentication flow
   - Access `/container/saml/login` to initiate authentication
   - Verify redirection to IdP
   - Verify successful authentication and assertion processing
   - Confirm attributes are correctly extracted (EPPN/UPN)
4. Test logout process
   - Test SP-initiated logout via `/container/saml/logout`
   - Test IdP-initiated logout
   - Verify both Sakai session and security context are cleared
5. Test metadata exchange
   - Verify SP metadata is correctly generated at `/container/saml/metadata`
   - Confirm IdP metadata is correctly processed
6. Compatibility testing
   - Test with multiple browsers
   - Test with multiple IdPs if applicable

### 6. Documentation

1. Update the Sakai documentation with new configuration instructions
2. Document migration steps for administrators

### 7. Deployment Strategy

1. Run both implementations in parallel during the migration period
2. Provide toggle to switch between implementations
3. Set a timeline for deprecating the old implementation

## Implementation Timeline

1. **Phase 1:** Core implementation and testing (Current)
2. **Phase 2:** User attribute handling and IdP integration (Next)
3. **Phase 3:** Documentation and migration tools
4. **Phase 4:** Full deployment and old code removal

## Notes for Implementers

- The new Spring Security SAML 2.0 uses OpenSAML 4.x instead of 2.x
- Metadata handling is substantially different in the new implementation
- The authentication flow is more standardized with fewer customization points
- Response validation is much more strict in the new implementation
- Certificate handling has changed significantly

## References

- [Spring Security SAML 2.0 Documentation](https://docs.spring.io/spring-security/reference/servlet/saml2/index.html)
- [OpenSAML 4.x Documentation](https://shibboleth.atlassian.net/wiki/spaces/OSAML/overview)