mkdir src/main/resources/jwt
cd src/main/resources/jwt
openssl genrsa 2048 -out rsaPrivateKey.pem
openssl rsa -pubout -in rsaPrivateKey.pem -out publicKey.pem
openssl pkcs8 -topk8 -nocrypt -inform pem -in rsaPrivateKey.pem -outform pem -out privateKey.pem
chmod 600 rsaPrivateKey.pem
chmod 600 privateKey.pem
