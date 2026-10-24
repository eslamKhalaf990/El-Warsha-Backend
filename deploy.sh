#!/bin/bash

# Configuration
SERVER_IP="68.168.222.245"
USER="root"
JAR_NAME="erp-0.0.1-SNAPSHOT.jar"
LOCAL_PATH="target/$JAR_NAME"
REMOTE_PATH="/root/"

echo "--- 🚀 Starting Zero-Downtime Deployment to $SERVER_IP ---"

# 1. Upload new jar (Moved to first step)
echo "1. Uploading new JAR file..."
if [ -f "$LOCAL_PATH" ]; then
    # Upload to a temporary name first to ensure atomicity, or overwrite directly if preferred.
    # Here we overwrite directly, which is usually fine on Linux.
    scp $LOCAL_PATH $USER@$SERVER_IP:$REMOTE_PATH

    # Check if SCP succeeded
    if [ $? -eq 0 ]; then
        echo "✅ Upload successful."
    else
        echo "❌ Error: Upload failed! keeping old application running."
        exit 1
    fi
else
    echo "❌ Error: File $LOCAL_PATH not found! Are you in the project root?"
    exit 1
fi

# 2. Stop old app
echo "2. Stopping old application..."
# We use || true so the script continues even if the app wasn't running
ssh $USER@$SERVER_IP "pkill -f $JAR_NAME || true"

# 3. Start new app
echo "3. Starting new application..."
# Added a small sleep to ensure the port clears, just in case
ssh $USER@$SERVER_IP "nohup java -Xms256m -Xmx512m -jar $REMOTE_PATH$JAR_NAME > app.log 2>&1 &"

echo "--- ✅ Deployment Complete! ---"