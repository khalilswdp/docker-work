#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-certs}"
mkdir -p "$OUT_DIR"

CA_PASS="${CA_PASS:-capass}"
TRUSTSTORE_PASS="${TRUSTSTORE_PASS:-truststore-password}"
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}"
KEY_PASS="${KEY_PASS:-changeit}"

command -v openssl >/dev/null
command -v keytool >/dev/null

CA_KEY="$OUT_DIR/ca.key"
CA_CERT="$OUT_DIR/ca.crt"
TRUSTSTORE="$OUT_DIR/kafka.truststore.jks"

make_ca() {
  rm -f "$CA_KEY" "$CA_CERT" "$OUT_DIR/ca.srl" "$TRUSTSTORE"

  openssl req -new -x509 \
    -keyout "$CA_KEY" \
    -out "$CA_CERT" \
    -days 3650 \
    -passout pass:"$CA_PASS" \
    -subj "/CN=local-es-root-ca" >/dev/null 2>&1

  keytool -importcert -noprompt \
    -alias CARoot \
    -file "$CA_CERT" \
    -keystore "$TRUSTSTORE" \
    -storetype JKS \
    -storepass "$TRUSTSTORE_PASS" >/dev/null
}

make_keystore() {
  local alias="$1"
  local cn="$2"
  local san="$3"
  local keystore="$4"
  local usage="$5"

  local csr="$OUT_DIR/$alias.csr"
  local signed="$OUT_DIR/$alias-signed.pem"
  local ext="$OUT_DIR/$alias-ext.cnf"

  rm -f "$keystore" "$csr" "$signed" "$ext"

  keytool -genkeypair \
    -alias "$alias" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -storetype JKS \
    -keystore "$keystore" \
    -storepass "$KEYSTORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=$cn, OU=Dev, O=Local, L=Local, ST=Local, C=US" \
    ${san:+-ext "SAN=$san"} >/dev/null

  keytool -certreq \
    -alias "$alias" \
    -keystore "$keystore" \
    -storepass "$KEYSTORE_PASS" \
    -keypass "$KEY_PASS" \
    -file "$csr" >/dev/null

  cat > "$ext" <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=$usage
${san:+subjectAltName=$san}
EOF

  openssl x509 -req \
    -in "$csr" \
    -CA "$CA_CERT" \
    -CAkey "$CA_KEY" \
    -CAcreateserial \
    -out "$signed" \
    -days 3650 \
    -passin pass:"$CA_PASS" \
    -extfile "$ext" >/dev/null 2>&1

  keytool -importcert -noprompt \
    -alias CARoot \
    -file "$CA_CERT" \
    -keystore "$keystore" \
    -storetype JKS \
    -storepass "$KEYSTORE_PASS" >/dev/null

  keytool -importcert -noprompt \
    -alias "$alias" \
    -file "$signed" \
    -keystore "$keystore" \
    -storetype JKS \
    -storepass "$KEYSTORE_PASS" \
    -keypass "$KEY_PASS" >/dev/null
}

make_ca
make_keystore broker1 broker1 "DNS:broker1,DNS:localhost,IP:127.0.0.1" "$OUT_DIR/broker01.keystore.jks" "serverAuth,clientAuth"
make_keystore broker2 broker2 "DNS:broker2,DNS:localhost,IP:127.0.0.1" "$OUT_DIR/broker02.keystore.jks" "serverAuth,clientAuth"
make_keystore broker3 broker3 "DNS:broker3,DNS:localhost,IP:127.0.0.1" "$OUT_DIR/broker03.keystore.jks" "serverAuth,clientAuth"
make_keystore schema-registry schema-registry "DNS:schema-registry,DNS:localhost,IP:127.0.0.1" "$OUT_DIR/schema-registry.keystore.jks" "serverAuth,clientAuth"
make_keystore kafka-ui-client kafka-ui-client "" "$OUT_DIR/client.keystore.jks" "clientAuth"

echo "Generated in $OUT_DIR"
echo "truststore password: $TRUSTSTORE_PASS"
echo "keystore password:   $KEYSTORE_PASS"
echo "key password:        $KEY_PASS"