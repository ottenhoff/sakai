#!/bin/bash
# Script to launch Sakai with MockSaml for SAML testing

# Check if CATALINA_HOME is set
if [ -z "$CATALINA_HOME" ]; then
  echo "Error: CATALINA_HOME environment variable is not set"
  echo "Please set CATALINA_HOME to your Tomcat installation directory"
  exit 1
fi

# Stop Tomcat if it's running
if [ -f "$CATALINA_HOME/bin/shutdown.sh" ]; then
  echo "Stopping Tomcat..."
  "$CATALINA_HOME/bin/shutdown.sh"
  sleep 5
fi

# Clone MockSaml if it doesn't exist
if [ ! -d "mocksaml" ]; then
  echo "Cloning MockSaml..."
  git clone https://github.com/spring-projects/spring-security-samples.git
  cd spring-security-samples/servlet/spring-boot/java/saml2/mocksaml
  ./gradlew bootJar
  cd -
  mkdir -p mocksaml
  cp spring-security-samples/servlet/spring-boot/java/saml2/mocksaml/build/libs/mocksaml-*.jar mocksaml/mocksaml.jar
  rm -rf spring-security-samples
  echo "MockSaml downloaded and built successfully"
fi

# Start MockSaml in the background
echo "Starting MockSaml..."
java -jar mocksaml/mocksaml.jar &
MOCKSAML_PID=$!
echo "MockSaml started with PID: $MOCKSAML_PID"
sleep 5

# Set the SAML environment to mocksaml
export JAVA_OPTS="$JAVA_OPTS -Dsakai.saml.env=mocksaml"

# Start Tomcat with MockSaml configuration
echo "Starting Tomcat with MockSaml configuration..."
"$CATALINA_HOME/bin/startup.sh"

echo ""
echo "Sakai is starting with MockSaml integration..."
echo "MockSaml is running at http://localhost:8080/mocksaml"
echo ""
echo "To test SAML authentication:"
echo "1. Go to http://localhost:8080/portal"
echo "2. Click on the SAML login option"
echo "3. You will be redirected to MockSaml"
echo "4. Enter test credentials (any username/password will work)"
echo ""
echo "To stop both Tomcat and MockSaml:"
echo "1. Stop Tomcat: $CATALINA_HOME/bin/shutdown.sh"
echo "2. Stop MockSaml: kill $MOCKSAML_PID"
echo ""
echo "Press Ctrl+C to stop this script (MockSaml will be terminated)"

# Wait for user to press Ctrl+C
trap "echo 'Stopping MockSaml...'; kill $MOCKSAML_PID; echo 'MockSaml stopped'" EXIT
wait $MOCKSAML_PID