#!/bin/bash
# LeafDash log receiver exposed over ngrok (static free domain), so the app
# can stream logs over LTE while driving.
# App setting: Stream log to URL = https://shantae-unhygienic-yamileth.ngrok-free.dev/log
set -e
cd "$(dirname "$0")/.."

python3 tools/log_server.py 8765 &
SERVER_PID=$!
cleanup() { kill $SERVER_PID 2>/dev/null; exit 0; }
trap cleanup SIGINT SIGTERM

ngrok http 8765 --log=stdout --url=https://shantae-unhygienic-yamileth.ngrok-free.dev

kill $SERVER_PID 2>/dev/null
