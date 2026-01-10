#!/bin/bash

# Potero Backend Server Startup Script
# Starts the Kotlin/Ktor server on port 8080

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KMP_DIR="$SCRIPT_DIR/potero-kmp"

echo "=========================================="
echo "  Potero Backend Server"
echo "=========================================="
echo ""

# Try to find Java 17+
if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    echo "Please install JDK 17 or later:"
    echo "  sudo apt-get update && sudo apt-get install -y openjdk-17-jdk"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)

# Check Java version is 17+
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    echo "Error: Java 17 or later is required (found Java $JAVA_VERSION)"
    echo "Please install JDK 17:"
    echo "  sudo apt-get update && sudo apt-get install -y openjdk-17-jdk"
    echo ""
    echo "Then either:"
    echo "  1. Set JAVA_HOME: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
    echo "  2. Or set default: sudo update-alternatives --config java"
    exit 1
fi
echo "Java version: $JAVA_VERSION"

# Check if Gradle wrapper exists, if not download it
if [ ! -f "$KMP_DIR/gradlew" ]; then
    echo "Downloading Gradle wrapper..."
    cd "$KMP_DIR"

    # Download gradle wrapper jar
    mkdir -p gradle/wrapper
    curl -sL "https://github.com/gradle/gradle/raw/v8.10.2/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar

    # Create gradlew script
    cat > gradlew << 'GRADLEW'
#!/bin/sh
exec java -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"
GRADLEW
    chmod +x gradlew
fi

cd "$KMP_DIR"

# Get WSL IP for display
WSL_IP=$(hostname -I | awk '{print $1}')

# Kill any existing backend processes
echo ""
echo "Checking for existing backend processes..."
EXISTING_PID=$(lsof -ti:8080 2>/dev/null || true)
if [ -n "$EXISTING_PID" ]; then
    echo "Found existing backend process(es) on port 8080: $EXISTING_PID"
    echo "Stopping existing backend..."
    kill -9 $EXISTING_PID 2>/dev/null || true
    sleep 1
    echo "Existing backend stopped."
else
    echo "No existing backend process found."
fi

echo ""
echo "Building and starting server..."
echo "Server will be available at:"
echo "  - From WSL: http://localhost:8080"
echo "  - From Windows: http://${WSL_IP}:8080"
echo ""
echo "API endpoints: /api/*"
echo "Health check: /health"
echo ""
echo "Press Ctrl+C to stop the server"
echo "=========================================="
echo ""

# Run the server
./gradlew :server:run --console=plain
