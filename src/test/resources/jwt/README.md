# Chaves de teste — NÃO são segredos

Este par RSA existe apenas para assinar e verificar os JWT **nos testes automatizados**
(`TokenBuilder` assina, a aplicação verifica). Ele não protege nada e não tem valor
fora da suíte de testes — por isso é versionado de propósito, para que os testes
sejam herméticos e não dependam de chaves geradas na máquina de quem roda.

- `private.pem`: PKCS#8, 2048 bits, usado por `smallrye.jwt.sign.key.location`
- `public.pem`: chave pública correspondente, usada por `mp.jwt.verify.publickey.location`

As chaves de **desenvolvimento e produção** ficam fora do repositório (ver
`generate_jwtkeys.sh` e `.gitignore` na raiz do projeto).
