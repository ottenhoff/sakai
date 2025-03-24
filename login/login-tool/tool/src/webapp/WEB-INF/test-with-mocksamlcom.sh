#!/bin/bash
# Script to use MockSAML.com for SAML testing
# MockSAML.com is a free online SAML IdP service that's perfect for testing

# Check if CATALINA_HOME is set
if [ -z "$CATALINA_HOME" ]; then
  echo "Error: CATALINA_HOME environment variable is not set"
  echo "Please set CATALINA_HOME to your Tomcat installation directory"
  exit 1
fi

# Create SAML configuration directory if it doesn't exist
SAML_CONFIG_DIR="$CATALINA_HOME/sakai/saml"
if [ ! -d "$SAML_CONFIG_DIR" ]; then
  echo "Creating SAML configuration directory: $SAML_CONFIG_DIR"
  mkdir -p "$SAML_CONFIG_DIR"
  mkdir -p "$SAML_CONFIG_DIR/metadata"
fi

# Download the latest MockSAML.com metadata
echo "Downloading MockSAML.com metadata..."
curl -s https://mocksaml.com/api/saml/metadata > "$SAML_CONFIG_DIR/metadata/mocksamlcom-metadata.xml"

if [ $? -eq 0 ]; then
  echo "✅ Successfully downloaded MockSAML.com metadata"
else
  echo "❌ Failed to download MockSAML.com metadata"
  exit 1
fi

# Copy the MockSAML.com properties
echo "Setting up MockSAML.com properties..."
cp "$(dirname "$0")/sakai-saml-mocksamlcom.properties" "$SAML_CONFIG_DIR/"

# Stop Tomcat if it's running
if [ -f "$CATALINA_HOME/bin/shutdown.sh" ]; then
  echo "Stopping Tomcat..."
  "$CATALINA_HOME/bin/shutdown.sh"
  sleep 5
fi

# Set the SAML environment to mocksamlcom and activate the Spring profile
export JAVA_OPTS="$JAVA_OPTS -Dsaml.env=mocksamlcom -Dspring.profiles.active=saml"

# Start Tomcat with MockSAML.com configuration
echo "Starting Tomcat with MockSAML.com configuration..."
"$CATALINA_HOME/bin/startup.sh"

echo ""
echo "Sakai is starting with MockSAML.com integration..."
echo ""
echo "To test SAML authentication:"
echo "1. Go to http://localhost:8080/portal"
echo "2. Click on the SAML login option"
echo "3. You will be redirected to MockSAML.com"
echo "4. Use any username/password combination (e.g., user1/user1pass)"
echo ""
echo "For more information about MockSAML.com, visit: https://mocksaml.com"
echo ""
echo "⚠️  Important: MockSAML.com is a public service, so don't use it for sensitive testing"
echo "    It's perfect for basic authentication flow testing but not for production use"
echo ""
echo "To stop Tomcat: $CATALINA_HOME/bin/shutdown.sh"