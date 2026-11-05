#!/bin/bash

set -euo pipefail

# Configuration

KEYSTORE="ifacex.jks"

CERTIFICATE="nifi-client.cer"
KEY_ALIAS="nifi-client"

SERVER_CERTIFICATE="nifi-server.cer"
SERVER_ALIAS="nifi-server"

SERVER_URL="localhost:8443"

CONTAINER="nifi"

TRUSTSTORE="/opt/nifi/nifi-current/conf/truststore.p12"
NIFI_HOME="/opt/nifi/nifi-current"

VALIDITY=3650

DNAME="CN=ifaceX NiFi Client, OU=Integration, O=autumo GmbH, C=CH"

TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

# Check prerequisites

for CMD in keytool docker openssl; do
    if ! command -v "$CMD" >/dev/null 2>&1; then
        echo "ERROR: $CMD not found."
        exit 1
    fi
done

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "ERROR: Docker container '$CONTAINER' not found."
    exit 1
fi

if [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" != "true" ]]; then
    echo "ERROR: Docker container '$CONTAINER' is not running."
    exit 1
fi

# Backup existing local keystore

if [[ -f "$KEYSTORE" ]]; then
    cp "$KEYSTORE" "${KEYSTORE}.${TIMESTAMP}.bak"
    echo "Local keystore backup created."
fi

# Remove previous client alias

if [[ -f "$KEYSTORE" ]] &&
   keytool -list -keystore "$KEYSTORE" \
       -storetype PKCS12 -alias "$KEY_ALIAS" >/dev/null 2>&1; then

    keytool -delete \
        -alias "$KEY_ALIAS" \
        -keystore "$KEYSTORE" \
        -storetype PKCS12
fi

# Generate client key pair and certificate

echo "Generating client key pair..."

keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 3072 \
    -validity "$VALIDITY" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -dname "$DNAME" \
    -ext KU=digitalSignature,keyEncipherment \
    -ext EKU=clientAuth

# Export public client certificate

echo "Exporting client certificate..."

keytool -exportcert \
    -alias "$KEY_ALIAS" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -file "$CERTIFICATE"

keytool -printcert -file "$CERTIFICATE"

# Export NiFi server certificate

echo "Exporting NiFi server certificate..."

openssl s_client \
    -connect "$SERVER_URL" \
    -servername localhost \
    </dev/null 2>/dev/null |
    openssl x509 -out "$SERVER_CERTIFICATE"

if [[ ! -s "$SERVER_CERTIFICATE" ]]; then
    echo "ERROR: NiFi server certificate could not be exported."
    exit 1
fi

# Import server certificate into local keystore

echo "Importing NiFi server certificate into local keystore..."

keytool -delete \
    -alias "$SERVER_ALIAS" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 2>/dev/null || true

keytool -importcert -noprompt \
    -alias "$SERVER_ALIAS" \
    -file "$SERVER_CERTIFICATE" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12

# Copy client certificate into NiFi container

echo "Copying client certificate to NiFi..."

docker cp "$CERTIFICATE" "$CONTAINER:/tmp/$CERTIFICATE"

# Backup NiFi truststore

echo "Backing up NiFi truststore..."

docker exec "$CONTAINER" sh -c \
    "cp '$TRUSTSTORE' '${TRUSTSTORE}.${TIMESTAMP}.bak'"

# Import client certificate into NiFi truststore

echo "Importing client certificate into NiFi truststore..."

docker exec "$CONTAINER" sh -c "

    cd '$NIFI_HOME'

    PASS=\$(sed -n 's/^nifi.security.truststorePasswd=//p' conf/nifi.properties)

    if [ -z \"\$PASS\" ]; then
        echo 'ERROR: NiFi truststore password not found.'
        exit 1
    fi

    keytool -delete \
        -alias '$KEY_ALIAS' \
        -keystore '$TRUSTSTORE' \
        -storetype PKCS12 \
        -storepass \"\$PASS\" 2>/dev/null || true

    keytool -importcert -noprompt \
        -alias '$KEY_ALIAS' \
        -file '/tmp/$CERTIFICATE' \
        -keystore '$TRUSTSTORE' \
        -storetype PKCS12 \
        -storepass \"\$PASS\"
"

# Verify NiFi truststore

echo "Verifying NiFi truststore..."

docker exec "$CONTAINER" sh -c "

    cd '$NIFI_HOME'

    PASS=\$(sed -n 's/^nifi.security.truststorePasswd=//p' conf/nifi.properties)

    keytool -list \
        -keystore '$TRUSTSTORE' \
        -storetype PKCS12 \
        -storepass \"\$PASS\" \
        -alias '$KEY_ALIAS'
"

# Verify local keystore

echo "Verifying local keystore..."

keytool -list -keystore "$KEYSTORE" -storetype PKCS12

echo
echo "======================================"
echo "Client and server certificate setup completed."
echo "======================================"
echo "Keystore:          $KEYSTORE"
echo "Client certificate: $CERTIFICATE"
echo "Client alias:       $KEY_ALIAS"
echo "Server certificate: $SERVER_CERTIFICATE"
echo "Server alias:      $SERVER_ALIAS"
echo
echo "NiFi restart may be required."
echo "ifaceX restart required to reload the keystore."
