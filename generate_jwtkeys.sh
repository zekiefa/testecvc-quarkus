#!/usr/bin/env bash
#
# Gera o par RSA 2048 usado para assinar (RS256) e verificar os JWT.
#
# As chaves são segredos locais e NÃO são versionadas (ver .gitignore).
# Rode este script uma vez após clonar o projeto; em produção, monte o par
# a partir de um secret manager e aponte as variáveis JWT_SIGN_KEY_LOCATION
# e JWT_VERIFY_KEY_LOCATION para os arquivos montados.
#
# Uso:
#   ./generate_jwtkeys.sh                    # gera em src/main/resources/jwt
#   ./generate_jwtkeys.sh /etc/app/secrets   # gera em outro diretório
#   FORCE=true ./generate_jwtkeys.sh         # sobrescreve chaves existentes
#
set -euo pipefail

TARGET_DIR="${1:-src/main/resources/jwt}"
PRIVATE_KEY="${TARGET_DIR}/privateKey.pem"
PUBLIC_KEY="${TARGET_DIR}/publicKey.pem"

mkdir -p "${TARGET_DIR}"

if [ -f "${PRIVATE_KEY}" ] && [ "${FORCE:-false}" != "true" ]; then
  echo "Já existe ${PRIVATE_KEY}. Use FORCE=true para sobrescrever." >&2
  exit 1
fi

# PKCS#8 (BEGIN PRIVATE KEY) é o formato aceito por smallrye.jwt.sign.key
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${PRIVATE_KEY}" 2>/dev/null
openssl pkey -in "${PRIVATE_KEY}" -pubout -out "${PUBLIC_KEY}" 2>/dev/null

chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

echo "Chaves geradas:"
echo "  ${PRIVATE_KEY}  (PKCS#8, 2048 bits, 600)"
echo "  ${PUBLIC_KEY}"
