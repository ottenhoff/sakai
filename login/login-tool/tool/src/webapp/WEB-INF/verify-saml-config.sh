#!/bin/bash
# SAML Configuration Verification Script
# This script helps verify the SAML configuration and test different aspects of the implementation

# Text formatting
BOLD="\033[1m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

# Check if CATALINA_HOME is set
if [ -z "$CATALINA_HOME" ]; then
  echo -e "${RED}Error: CATALINA_HOME environment variable is not set${RESET}"
  echo "Please set CATALINA_HOME to your Tomcat installation directory"
  exit 1
fi

# Function to display section header
section() {
  echo -e "\n${BOLD}$1${RESET}"
  echo "------------------------------------------------------------"
}

# Function to check if a directory exists
check_directory() {
  if [ -d "$1" ]; then
    echo -e "${GREEN}✓ Directory exists:${RESET} $1"
  else
    echo -e "${RED}✗ Directory missing:${RESET} $1"
  fi
}

# Function to check if a file exists
check_file() {
  if [ -f "$1" ]; then
    echo -e "${GREEN}✓ File exists:${RESET} $1"
  else
    echo -e "${RED}✗ File missing:${RESET} $1"
  fi
}

# Function to check SAML configuration properties
check_properties() {
  local prop_file=$1
  local prop_name=$2
  
  if [ -f "$prop_file" ]; then
    if grep -q "$prop_name" "$prop_file"; then
      local prop_value=$(grep "$prop_name" "$prop_file" | cut -d'=' -f2-)
      echo -e "${GREEN}✓ Property${RESET} $prop_name=${prop_value}"
    else
      echo -e "${YELLOW}! Property not found:${RESET} $prop_name in $prop_file"
    fi
  else
    echo -e "${RED}✗ Cannot check property:${RESET} File $prop_file not found"
  fi
}

# Main script

echo -e "${BOLD}Sakai SAML Configuration Verification Tool${RESET}"
echo "============================================================"
echo "This script will check your SAML configuration and help diagnose issues."

section "Environment Information"
echo "CATALINA_HOME: $CATALINA_HOME"
echo "Current directory: $(pwd)"
sakai_version=$(grep "<version>" "$CATALINA_HOME/webapps/sakai-login-tool/META-INF/maven/org.sakaiproject.login/sakai-login-tool/pom.xml" 2>/dev/null | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
echo "Sakai version: ${sakai_version:-Unknown}"
echo "Spring Security version: $(grep -A 3 "spring-security-saml2-service-provider" "$CATALINA_HOME/webapps/sakai-login-tool/META-INF/maven/org.sakaiproject.login/sakai-login-tool/pom.xml" 2>/dev/null | grep version | sed 's/.*<version>\(.*\)<\/version>.*/\1/' || echo "Unknown")"

section "Directory Structure Verification"
# Check Tomcat SAML configuration directories
check_directory "$CATALINA_HOME/sakai"
check_directory "$CATALINA_HOME/sakai/saml"
check_directory "$CATALINA_HOME/sakai/saml/metadata"

section "Configuration Files Verification"
# Check for configuration files
check_file "$CATALINA_HOME/sakai/saml/sakai-saml.properties"
check_file "$CATALINA_HOME/sakai/saml/metadata/idp-metadata.xml"
check_file "$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/xlogin-context.saml.xml"
check_file "$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/xlogin-context.saml-new.xml"

section "SAML Properties Verification"
# Check key properties if the properties file exists
prop_file="$CATALINA_HOME/sakai/saml/sakai-saml.properties"
if [ -f "$prop_file" ]; then
  check_properties "$prop_file" "sakai.saml.sp.entityId"
  check_properties "$prop_file" "sakai.saml.idp.entityId"
  check_properties "$prop_file" "sakai.saml.idp.metadata.url"
  check_properties "$prop_file" "sakai.saml.auth.useEppn"
  check_properties "$prop_file" "sakai.saml.auth.useUpn"
else
  # Check if properties are in the default location
  prop_file="$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/sakai-saml.properties"
  if [ -f "$prop_file" ]; then
    echo -e "${YELLOW}! Using default properties location rather than Tomcat directory${RESET}"
    check_properties "$prop_file" "sakai.saml.sp.entityId"
    check_properties "$prop_file" "sakai.saml.idp.entityId"
  else
    echo -e "${RED}✗ No SAML properties file found${RESET}"
  fi
fi

section "SAML Metadata Verification"
# Check if metadata files exist and are valid XML
idp_metadata="$CATALINA_HOME/sakai/saml/metadata/idp-metadata.xml"
if [ -f "$idp_metadata" ]; then
  if command -v xmllint >/dev/null 2>&1; then
    if xmllint --noout "$idp_metadata" 2>/dev/null; then
      echo -e "${GREEN}✓ IdP metadata is valid XML${RESET}"
      # Extract entityID from metadata
      entity_id=$(xmllint --xpath "string(/*[local-name()='EntityDescriptor']/@entityID)" "$idp_metadata" 2>/dev/null)
      if [ -n "$entity_id" ]; then
        echo -e "${GREEN}✓ IdP entityID:${RESET} $entity_id"
      else
        echo -e "${YELLOW}! Could not extract entityID from metadata${RESET}"
      fi
    else
      echo -e "${RED}✗ IdP metadata is not valid XML${RESET}"
    fi
  else
    echo -e "${YELLOW}! xmllint not available, skipping XML validation${RESET}"
    grep -q "EntityDescriptor" "$idp_metadata" && echo -e "${GREEN}✓ Metadata file contains EntityDescriptor${RESET}" || echo -e "${RED}✗ Metadata file may not be valid SAML metadata${RESET}"
  fi
else
  echo -e "${RED}✗ IdP metadata file not found${RESET}"
fi

section "Web Application Configuration"
# Check if the filter and servlet configuration is correct
web_xml="$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/web.xml"
if [ -f "$web_xml" ]; then
  if grep -q "samlEntryPoint" "$web_xml"; then
    echo -e "${YELLOW}! Using old SAML configuration in web.xml${RESET}"
  fi
  
  if grep -q "samlLogoutFilter" "$web_xml"; then
    echo -e "${GREEN}✓ SAML logout filter is configured${RESET}"
  else
    echo -e "${RED}✗ SAML logout filter not found in web.xml${RESET}"
  fi
  
  if grep -q "saml/SSO" "$web_xml" && grep -q "saml/metadata" "$web_xml"; then
    echo -e "${GREEN}✓ SAML endpoints are configured in web.xml${RESET}"
  else
    echo -e "${RED}✗ SAML endpoints not properly configured in web.xml${RESET}"
  fi
else
  echo -e "${RED}✗ web.xml not found${RESET}"
fi

section "MockSaml Integration Check"
# Check if MockSaml is configured for testing
if [ -f "$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/sakai-saml-mocksaml.properties" ]; then
  echo -e "${GREEN}✓ MockSaml configuration is available${RESET}"
  if [ -f "$CATALINA_HOME/webapps/sakai-login-tool/WEB-INF/run-with-mocksaml.sh" ]; then
    echo -e "${GREEN}✓ MockSaml startup script is available${RESET}"
  else
    echo -e "${RED}✗ MockSaml startup script not found${RESET}"
  fi
else
  echo -e "${YELLOW}! MockSaml configuration not found${RESET}"
fi

section "Testing Recommendations"
echo -e "1. ${BOLD}Basic Configuration Test:${RESET}"
echo "   - Run './run-with-mocksaml.sh' to test with MockSaml"
echo "   - Access http://localhost:8080/portal and try SAML login"
echo ""
echo -e "2. ${BOLD}For Production Testing:${RESET}"
echo "   - Copy your IdP metadata to $CATALINA_HOME/sakai/saml/metadata/idp-metadata.xml"
echo "   - Configure sakai-saml.properties with your IdP settings"
echo "   - Test authentication with your production IdP"
echo ""
echo -e "3. ${BOLD}Migration Testing:${RESET}"
echo "   - To switch from old to new implementation, rename:"
echo "     xlogin-context.saml-new.xml → xlogin-context.saml.xml"
echo "   - Restart Tomcat and test authentication"

echo ""
echo "============================================================"
echo -e "${BOLD}Verification Complete${RESET}"
echo "See the SAML-TESTING-PLAN.md for detailed testing steps"