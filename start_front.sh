#!/bin/bash

# Potero Frontend Server Startup Script
# Starts the Svelte dev server on port 5173

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SVELTE_DIR="$SCRIPT_DIR/potero-svelte"

echo "=========================================="
echo "  Potero Frontend (Svelte)"
echo "=========================================="
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "Error: Node.js is not installed or not in PATH"
    echo "Please install Node.js 18 or later"
    exit 1
fi

NODE_VERSION=$(node -v)
echo "Node.js version: $NODE_VERSION"

cd "$SVELTE_DIR"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo ""
    echo "Installing dependencies..."
    npm install
fi

echo ""
echo "Starting Svelte dev server..."
echo "Frontend will be available at: http://localhost:5173"
echo ""
echo "Note: Make sure the backend server is running (./start_back.sh)"
echo "Press Ctrl+C to stop the server"
echo "=========================================="
echo ""

# Run the dev server
npm run dev
