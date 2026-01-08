#!/bin/bash

# Potero Full Stack Startup Script
# Starts both backend and frontend servers

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================="
echo "  Potero - Full Stack Startup"
echo "=========================================="
echo ""
echo "This will start:"
echo "  - Backend (Kotlin/Ktor) on http://127.0.0.1:8080"
echo "  - Frontend (Svelte) on http://localhost:5173"
echo ""

# Function to cleanup background processes on exit
cleanup() {
    echo ""
    echo "Shutting down servers..."
    kill $BACKEND_PID 2>/dev/null || true
    kill $FRONTEND_PID 2>/dev/null || true
    exit 0
}

trap cleanup SIGINT SIGTERM

# Start backend in background
echo "Starting backend server..."
"$SCRIPT_DIR/start_back.sh" &
BACKEND_PID=$!

# Wait a bit for backend to start
sleep 5

# Start frontend in background
echo ""
echo "Starting frontend server..."
"$SCRIPT_DIR/start_front.sh" &
FRONTEND_PID=$!

echo ""
echo "=========================================="
echo "  Both servers are starting..."
echo "  Backend PID: $BACKEND_PID"
echo "  Frontend PID: $FRONTEND_PID"
echo ""
echo "  Press Ctrl+C to stop all servers"
echo "=========================================="

# Wait for both processes
wait
