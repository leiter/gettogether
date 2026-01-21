#!/bin/bash
# update_jamiId.sh - Updates /sdcard/jami.txt on connected Android devices with current Jami IDs
#
# Usage: ./script/update_jamiId.sh
#
# This script:
# 1. Detects connected Android devices
# 2. Extracts account IDs and Jami IDs from app logs
# 3. Updates /sdcard/jami.txt on all connected devices

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Jami ID Updater ==="
echo ""

# Get list of connected devices
DEVICES=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}')
DEVICE_COUNT=$(echo "$DEVICES" | grep -c . || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}Error: No devices connected${NC}"
    exit 1
fi

echo -e "${GREEN}Found $DEVICE_COUNT device(s)${NC}"
echo ""

# Arrays to store device info
declare -A DEVICE_MODELS
declare -A DEVICE_ACCOUNTS
declare -A DEVICE_JAMI_IDS
declare -A DEVICE_NAMES

# First pass: collect account IDs and display names from each device's own logs
for SERIAL in $DEVICES; do
    MODEL=$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')
    DEVICE_MODELS[$SERIAL]="$MODEL"

    echo "Processing $MODEL ($SERIAL)..."

    # Get account ID from presence publish logs
    ACCOUNT_ID=$(adb -s "$SERIAL" logcat -d 2>/dev/null | \
        grep -oP "Publishing presence for account \K[a-f0-9]+" | \
        tail -1)

    if [ -n "$ACCOUNT_ID" ]; then
        DEVICE_ACCOUNTS[$SERIAL]="$ACCOUNT_ID"
        echo "  Account ID: $ACCOUNT_ID"
    else
        echo -e "  ${YELLOW}Warning: Could not find account ID${NC}"
    fi

    # Try to get display name from logs (clean any quotes or special chars)
    DISPLAY_NAME=$(adb -s "$SERIAL" logcat -d 2>/dev/null | \
        grep -oP 'displayName["\s:=]+\K[^",\s\r]+' | \
        tr -d "'" | \
        head -1)

    if [ -n "$DISPLAY_NAME" ]; then
        DEVICE_NAMES[$SERIAL]=$(echo "$DISPLAY_NAME" | tr -d "'\r")
    else
        # Fallback to model name
        DEVICE_NAMES[$SERIAL]=$(echo "$MODEL" | sed 's/ /_/g')
    fi
done

echo ""

# Second pass: get Jami IDs from nearbyPeerNotification cross-reference
# Device A's Jami ID appears in Device B's nearbyPeerNotification logs
for SERIAL in $DEVICES; do
    for OTHER_SERIAL in $DEVICES; do
        if [ "$SERIAL" != "$OTHER_SERIAL" ]; then
            # Look for this device's name in the other device's logs
            # Clean the name (remove trailing spaces, convert to lowercase for matching)
            NAME="${DEVICE_NAMES[$SERIAL]}"
            NAME_CLEAN=$(echo "$NAME" | tr -d ' \r' | tr '[:upper:]' '[:lower:]')

            # Extract Jami ID from nearbyPeerNotification where DisplayName matches
            # The log format is: AccountId: XXX, BuddyUri: YYY, State: Z, DisplayName: NAME
            JAMI_ID=$(adb -s "$OTHER_SERIAL" logcat -d 2>/dev/null | \
                grep "nearbyPeerNotification" -A1 | \
                tr '[:upper:]' '[:lower:]' | \
                grep "displayname.*$NAME_CLEAN" | \
                grep -oP "buddyuri: \K[a-f0-9]+" | \
                tail -1)

            if [ -n "$JAMI_ID" ]; then
                DEVICE_JAMI_IDS[$SERIAL]="$JAMI_ID"
                echo "Found Jami ID for ${DEVICE_NAMES[$SERIAL]}: $JAMI_ID"
                break
            fi
        fi
    done

    # If still not found, try alternative: PresenceChanged events on OTHER devices
    # These show the Jami ID of the device that changed presence
    if [ -z "${DEVICE_JAMI_IDS[$SERIAL]}" ]; then
        for OTHER_SERIAL in $DEVICES; do
            if [ "$SERIAL" != "$OTHER_SERIAL" ]; then
                # PresenceChanged logs show partial Jami ID like "375face12b8f909f..."
                # We need the full 40-char ID, so try to match from BuddyUri in same logs
                PARTIAL_ID=$(adb -s "$OTHER_SERIAL" logcat -d 2>/dev/null | \
                    grep "PresenceChanged for" | \
                    grep -oP "for \K[a-f0-9]+" | \
                    tail -1)

                if [ -n "$PARTIAL_ID" ] && [ ${#PARTIAL_ID} -ge 16 ]; then
                    # Try to find full ID that starts with this partial
                    FULL_ID=$(adb -s "$OTHER_SERIAL" logcat -d 2>/dev/null | \
                        grep -oP "BuddyUri: \K[a-f0-9]{40}" | \
                        grep "^$PARTIAL_ID" | \
                        tail -1)

                    if [ -n "$FULL_ID" ]; then
                        DEVICE_JAMI_IDS[$SERIAL]="$FULL_ID"
                        echo "Found Jami ID for ${DEVICE_NAMES[$SERIAL]} (presence): $FULL_ID"
                        break
                    fi
                fi
            fi
        done
    fi

    # Last resort: try to find from account registration or other logs on own device
    if [ -z "${DEVICE_JAMI_IDS[$SERIAL]}" ]; then
        JAMI_ID=$(adb -s "$SERIAL" logcat -d 2>/dev/null | \
            grep -oP "registeredName.*hash[\":\s]+\K[a-f0-9]{40}" | \
            tail -1)

        if [ -n "$JAMI_ID" ]; then
            DEVICE_JAMI_IDS[$SERIAL]="$JAMI_ID"
            echo "Found Jami ID for ${DEVICE_NAMES[$SERIAL]} (alt): $JAMI_ID"
        fi
    fi
done

echo ""

# Generate the jami.txt content
DATE=$(date +%Y-%m-%d)
JAMI_TXT="/tmp/jami.txt"

cat > "$JAMI_TXT" << EOF
=== Device Jami IDs ===
EOF

for SERIAL in $DEVICES; do
    MODEL="${DEVICE_MODELS[$SERIAL]}"
    NAME=$(echo "${DEVICE_NAMES[$SERIAL]}" | tr -d '\r' | sed 's/[[:space:]]*$//')
    ACCOUNT="${DEVICE_ACCOUNTS[$SERIAL]:-unknown}"
    JAMI_ID="${DEVICE_JAMI_IDS[$SERIAL]:-unknown}"

    cat >> "$JAMI_TXT" << EOF
$NAME ($MODEL) - Account: $ACCOUNT
  JamiId: $JAMI_ID

EOF
done

cat >> "$JAMI_TXT" << EOF
=== Session Notes ($DATE) ===

1. Jami IDs updated via update_jamiId.sh script

2. To add contact:
   - Copy the other device's JamiId
   - Go to Contacts > Add Contact
   - Paste the JamiId

3. Useful commands:
   - Monitor logs: adb logcat -s "SwigJamiBridge" -s "ConversationRepository"
   - Clear app data: adb shell pm clear com.gettogether.app
   - Launch app: adb shell am start -n com.gettogether.app/.MainActivity
EOF

echo "=== Generated jami.txt ==="
cat "$JAMI_TXT"
echo ""
echo "=========================="
echo ""

# Push to all devices
for SERIAL in $DEVICES; do
    MODEL="${DEVICE_MODELS[$SERIAL]}"
    echo -n "Pushing to $MODEL ($SERIAL)... "
    if adb -s "$SERIAL" push "$JAMI_TXT" /sdcard/jami.txt > /dev/null 2>&1; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAILED${NC}"
    fi
done

echo ""
echo -e "${GREEN}Done!${NC}"
