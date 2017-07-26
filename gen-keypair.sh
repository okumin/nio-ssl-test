#!/bin/bash -eux

keytool -genkeypair -v \
  -alias nio-ssl-test-ca \
  -dname "CN=nio-ssl-test-ca, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US" \
  -keystore src/main/resources/nio-ssl-test-ca.jks \
  -keypass 12345678 \
  -storepass 12345678 \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999

keytool -export -v \
  -alias nio-ssl-test-ca \
  -file src/main/resources/nio-ssl-test-ca.crt \
  -keypass 12345678 \
  -storepass 12345678 \
  -keystore src/main/resources/nio-ssl-test-ca.jks \
  -rfc

keytool -genkeypair -v \
  -alias nio-ssl-test-server \
  -dname "CN=nio-ssl-test-server, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US" \
  -keystore src/main/resources/nio-ssl-test-server.jks \
  -keypass 12345678 \
  -storepass 12345678 \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385

keytool -certreq -v \
  -alias nio-ssl-test-server \
  -keypass 12345678 \
  -storepass 12345678 \
  -keystore src/main/resources/nio-ssl-test-server.jks \
  -file src/main/resources/nio-ssl-test-server.csr

keytool -gencert -v \
  -alias nio-ssl-test-ca \
  -keypass 12345678 \
  -storepass 12345678 \
  -keystore src/main/resources/nio-ssl-test-ca.jks \
  -infile src/main/resources/nio-ssl-test-server.csr \
  -outfile src/main/resources/nio-ssl-test-server.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:example.com" \
  -rfc

keytool -import -v \
  -alias nio-ssl-test-ca \
  -file src/main/resources/nio-ssl-test-ca.crt \
  -keystore src/main/resources/nio-ssl-test-server.jks \
  -storetype JKS \
  -storepass 12345678 << EOF
y
EOF

keytool -import -v \
  -alias nio-ssl-test-server \
  -file src/main/resources/nio-ssl-test-server.crt \
  -keystore src/main/resources/nio-ssl-test-server.jks \
  -storetype JKS \
  -storepass 12345678

keytool -list -v \
  -keystore src/main/resources/nio-ssl-test-server.jks \
  -storepass 12345678
