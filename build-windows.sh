#!/bin/bash
# Windows Installer Build Script for Potero
# Usage: ./build-windows.sh

set -e

echo "========================================"
echo "  Potero Windows Installer Build"
echo "========================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Step 1: Build Backend JAR
echo "[1/4] Building backend JAR..."
cd potero-kmp
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :server:buildFatJar
cd ..
echo "Backend JAR built successfully."
echo ""

# Step 2: Install frontend dependencies
echo "[2/4] Installing frontend dependencies..."
cd potero-svelte
npm install
echo ""

# Step 3: Download JRE (if not exists)
echo "[3/4] Downloading JRE..."
npm run download:jre
echo ""

# Step 4: Build Electron app
echo "[4/4] Building Electron installer..."
npm run electron:build:win
echo ""

echo "========================================"
echo "  Build Complete!"
echo "========================================"
echo ""
echo "Installer location:"
echo "  potero-svelte/dist/Potero-Setup-0.1.0.exe"
echo ""
