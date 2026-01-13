#!/bin/bash

BASE_URL="http://127.0.0.1:1234"
DB_PATH="diffusion_desk.db"

echo "--- DiffusionDesk Database Diagnostic ---"

# 1. Check DB File
if [ -f "$DB_PATH" ]; then
    SIZE=$(du -h "$DB_PATH" | cut -f1)
    echo "[OK] Database file found: $DB_PATH ($SIZE)"
else
    echo "[ERROR] Database file 'diffusion_desk.db' not found in current directory!"
    # Don't exit, as it might be created on startup
fi

# 2. Check API
echo -e "\nTesting API: $BASE_URL/v1/history/images ..."
RESPONSE=$(curl -s "$BASE_URL/v1/history/images")
if [ $? -eq 0 ]; then
    COUNT=$(echo "$RESPONSE" | grep -o '"id":' | wc -l)
    echo "[OK] API responded. Found $COUNT items (approx)."
    
    if [ "$COUNT" -gt 0 ]; then
        echo -e "\nSample Data (Raw JSON):"
        echo "$RESPONSE" | cut -c 1-200
    else
        echo "[WARN] Database is empty. No history items returned."
    fi
else
    echo "[ERROR] Failed to connect to Orchestrator. Is it running?"
fi

echo -e "\nTesting Tags API: $BASE_URL/v1/history/tags ..."
TAGS_RESPONSE=$(curl -s "$BASE_URL/v1/history/tags")
if [ $? -eq 0 ]; then
    TAG_COUNT=$(echo "$TAGS_RESPONSE" | grep -o '"name":' | wc -l)
    echo "[OK] Found $TAG_COUNT tags."
    if [ "$TAG_COUNT" -gt 0 ]; then
        echo "Tags Response (Raw):"
        echo "$TAGS_RESPONSE"
    fi
else
    echo "[ERROR] Tags API failed."
fi
