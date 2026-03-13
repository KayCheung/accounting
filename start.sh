#!/bin/bash
# Kill any existing process holding port 5000
fuser -k 5000/tcp 2>/dev/null || true
# Wait for port to fully release
sleep 1
# Start the server
exec node /home/runner/workspace/serve.js
