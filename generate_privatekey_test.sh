mkdir src/test/resources/
cd src/test/resources
openssl rsa -outform der -in privateKey.pem -out ../../../test/resources/jwt/private.key