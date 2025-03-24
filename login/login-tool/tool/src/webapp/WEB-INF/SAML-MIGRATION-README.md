# Sakai SAML Migration Implementation

This document summarizes the work done so far to migrate from the deprecated Spring Security SAML Extension to the official Spring Security SAML 2.0 implementation.

## Completed Work

We've created the following components for the migration:

1. **Configuration**
   - `SakaiSamlConfiguration.java` - Java configuration class that replaces XML configuration
   - `SakaiSamlPropertiesConfiguration.java` - Properties configuration class
   - `xlogin-context.saml-new.xml` - Transitional XML configuration that bridges to the Java config
   - `sakai-saml.properties` - Configuration properties for SAML settings
   - Updated `web.xml` with new filter and servlet mappings

2. **Authentication**
   - `SakaiSamlAuthenticationConverter.java` - Converter to extract username from SAML attributes

3. **Logout Handling**
   - `SakaiSamlLogoutFilter.java` - Custom filter to handle Sakai-specific logout processing

4. **Migration Utilities**
   - `MetadataConverter.java` - Utility to convert metadata files from old to new format
   - `migrate-saml.sh` - Shell script to assist with migration
   - `SAML-MIGRATION-PLAN.md` - Detailed migration plan and timeline

## Implementation Complete ✓

1. **Dependencies Updated** ✓
   - Replaced `spring-security-saml2-core` with `spring-security-saml2-service-provider`
   - Updated to use Spring Security 5.8.16
   - Added explicit OpenSAML 4.3.2 dependencies

2. **Configuration** ✓
   - Created Java configuration classes
   - Updated web.xml with proper filter chain and URL mappings
   - Added properties-based configuration

3. **Testing Setup**
   - See the testing plan in SAML-MIGRATION-PLAN.md
   - Test with mocksaml or testsaml.com

4. **Migration Support** ✓
   - Created MetadataConverter utility for metadata conversion
   - Added migration script migrate-saml.sh
   - Created comprehensive documentation

## How to Enable the New Implementation

During the transition phase, both implementations can coexist. To enable the new implementation:

1. Update the Maven dependencies in `pom.xml` (already completed)
2. Run the migration script to prepare the environment:
   ```
   cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
   chmod +x migrate-saml.sh
   ./migrate-saml.sh
   ```
3. The migration script will rename the configuration files for you. If you prefer to do it manually:
   ```
   cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
   # Backup the old configuration first
   cp xlogin-context.saml.xml xlogin-context.saml.xml.old
   # Replace with the new configuration
   cp xlogin-context.saml-new.xml xlogin-context.saml.xml
   ```
4. Restart Tomcat to apply all changes

## IMPORTANT: Runtime Error Troubleshooting

### Error 1: Old SAML Configuration

If you see an error like this after restarting:
```
ERROR [Catalina-utility-3] o.s.w.c.ContextLoader.initWebApplicationContext Context initialization failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'samlFilter' defined in ServletContext resource [/WEB-INF/xlogin-context.saml.xml]: Cannot create inner bean '(inner bean)#74fa4bd5' of type [org.springframework.security.web.DefaultSecurityFilterChain] while setting constructor argument with key [0]; nested exception is org.springframework.beans.factory.BeanCreationException: Error creating bean with name '(inner bean)#74fa4bd5': Cannot resolve reference to bean 'samlEntryPoint' while setting constructor argument with key [0]; nested exception is org.springframework.beans.factory.CannotLoadBeanClassException: Cannot find class [org.springframework.security.saml.SAMLEntryPoint] for bean with name 'samlEntryPoint'
```

This means you're still using the old SAML configuration file. Make sure:

1. The file `xlogin-context.saml.xml` contains the new configuration (referencing our Java configuration)
2. You've run the migration script or manually copied `xlogin-context.saml-new.xml` to `xlogin-context.saml.xml`
3. The Spring profiles and environment variables are set correctly

### Error 2: Missing Spring Security Crypto

If you see an error like this:
```
Caused by: java.lang.NoClassDefFoundError: org/springframework/security/crypto/password/PasswordEncoder
        at java.base/java.lang.Class.getDeclaredMethods0(Native Method) ~[?:?]
        at java.base/java.lang.Class.privateGetDeclaredMethods(Class.java:3402) ~[?:?]
        at java.base/java.lang.Class.getDeclaredMethods(Class.java:2504) ~[?:?]
        at org.springframework.util.ReflectionUtils.getDeclaredMethods(ReflectionUtils.java:467) ~[spring-core-5.3.39.jar:5.3.39]
        ... 31 more
Caused by: java.lang.ClassNotFoundException: org.springframework.security.crypto.password.PasswordEncoder
```

This means your project is missing the Spring Security Crypto dependency. Make sure your pom.xml includes:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>${sakai.spring.security.version}</version>
</dependency>
```

You'll need to rebuild the project after making this change.

### Error 3: Missing Sakai Service Beans

If you see an error like this:
```
ERROR [Catalina-utility-4] o.s.w.c.ContextLoader.initWebApplicationContext Context initialization failed
org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'sakaiSamlConfiguration': Unsatisfied dependency expressed through field 'usageSessionService'; nested exception is org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'org.sakaiproject.event.api.UsageSessionService' available: expected at least 1 bean which qualifies as autowire candidate.
```

This means that the Sakai services are not properly declared in your Spring context. The SAML configuration needs to access Sakai services like UsageSessionService and SessionManager.

Make sure your xlogin-context.saml.xml file contains the proper bean definitions for accessing Sakai services:

```xml
<!-- Import required Sakai component manager -->
<bean id="componentManager" 
      class="org.sakaiproject.component.cover.ComponentManager" 
      factory-method="getInstance" />
      
<!-- Legacy service beans (for backward compatibility) -->
<bean id="org.sakaiproject.tool.api.SessionManager" 
      factory-bean="componentManager"
      factory-method="get">
    <constructor-arg value="org.sakaiproject.tool.api.SessionManager" />
</bean>

<bean id="org.sakaiproject.event.api.UsageSessionService" 
      factory-bean="componentManager"
      factory-method="get">
    <constructor-arg value="org.sakaiproject.event.api.UsageSessionService" />
</bean>

<!-- Autowireable service proxy beans (with exact type names) -->
<bean id="sessionManager" 
      class="org.sakaiproject.tool.api.SessionManager" 
      factory-bean="componentManager"
      factory-method="get">
    <constructor-arg value="org.sakaiproject.tool.api.SessionManager" />
</bean>

<bean id="usageSessionService" 
      class="org.sakaiproject.event.api.UsageSessionService" 
      factory-bean="componentManager"
      factory-method="get">
    <constructor-arg value="org.sakaiproject.event.api.UsageSessionService" />
</bean>
```

This approach properly wires the Sakai services into your SAML configuration by:
1. Using the Sakai ComponentManager cover class to access services
2. Defining beans with the exact field names used in @Autowired annotations
3. Supporting both traditional reference-based wiring and modern annotation-based autowiring

## Comprehensive Testing Approach

We've developed a comprehensive testing framework for the SAML migration:

### 1. Testing Tools

The following testing tools are available:

- **run-with-mocksaml.sh** - Script to set up a test environment with MockSaml
- **verify-saml-config.sh** - Script to verify SAML configuration
- **generate-test-saml-response.sh** - Script to generate test SAML responses
- **SAML-TESTING-PLAN.md** - Comprehensive testing plan
- **SAML-TESTING-README.md** - Detailed testing guide

### 2. Testing Options

We've included easy integration with two testing solutions:

#### Option 1: Online Testing with MockSAML.com (Recommended)

MockSAML.com is a free online SAML IdP service that makes testing easy:

1. Set up MockSAML.com integration in one step:
   ```
   cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
   chmod +x test-with-mocksamlcom.sh
   ./test-with-mocksamlcom.sh
   ```

2. Alternatively, manually set the environment variable and start Tomcat:
   ```
   export JAVA_OPTS="$JAVA_OPTS -Dsaml.env=mocksamlcom -Dspring.profiles.active=saml"
   $CATALINA_HOME/bin/startup.sh
   ```

3. Benefits of MockSAML.com:
   - No local setup required
   - Always-available SAML IdP
   - Consistent, predictable responses
   - Free to use for testing
   - No maintenance needed

4. You can customize the MockSAML.com settings in:
   - `$CATALINA_HOME/sakai/saml/sakai-saml-mocksamlcom.properties`

#### Option 2: Local Testing with Spring Security MockSaml

For offline testing, we've included integration with a local MockSaml instance:

1. Set up local MockSaml and Sakai in one step:
   ```
   cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
   chmod +x run-with-mocksaml.sh
   ./run-with-mocksaml.sh
   ```

2. Alternatively, manually set the environment variable and start Tomcat:
   ```
   export JAVA_OPTS="$JAVA_OPTS -Dsaml.env=mocksaml -Dspring.profiles.active=saml"
   $CATALINA_HOME/bin/startup.sh
   ```

3. The local MockSaml configuration:
   - Uses a simplified SAML configuration suitable for testing
   - Allows any username/password combination
   - Provides standard test attributes (username, firstName, lastName, email)
   - Enables verbose logging for SAML operations

4. You can customize the local MockSaml settings in:
   - `$CATALINA_HOME/sakai/saml/sakai-saml-mocksaml.properties`

### 3. Verifying Your Configuration

The `verify-saml-config.sh` script helps you diagnose issues with your SAML setup:

```
cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
chmod +x verify-saml-config.sh
./verify-saml-config.sh
```

This script checks directory structures, configuration files, properties settings, and metadata files, then provides testing recommendations.

### 4. Testing Different SAML Attribute Scenarios

Use the `generate-test-saml-response.sh` script to generate test SAML responses for different attribute scenarios:

```
cd /path/to/sakai/webapps/sakai-login-tool/WEB-INF/
chmod +x generate-test-saml-response.sh
./generate-test-saml-response.sh
```

These responses help verify that the SAML authentication converter correctly handles different attribute combinations (EPPN, UPN, or fallback to NameID).

### 5. Executing the Test Plan

Follow the comprehensive test plan in `SAML-TESTING-PLAN.md` to verify all aspects of the SAML integration:

- Authentication flow
- Logout process
- Metadata exchange
- Error handling
- Compatibility with different browsers and IdPs

See `SAML-TESTING-README.md` for detailed testing instructions.

## References

- [Spring Security SAML 2.0 Documentation](https://docs.spring.io/spring-security/reference/servlet/saml2/index.html)
- [SAML 2.0 Migration Guide (Spring Security Wiki)](https://github.com/spring-projects/spring-security/wiki/SAML-2.0-Migration-Guide)