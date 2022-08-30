# Simple Quarkus Project

### Architecture
- Java 11
- Quarkus 2.7.5
- [Lombok](https://projectlombok.org) to avoid boilerplate code
- [Mapstruct](https://mapstruct.org) for conversions between domains
- [SL4J](https://www.slf4j.org/manual.html) for logging
- [JUnit 5](https://junit.org/junit5/docs/current/user-guide) for unit tests
- [Mockito](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Hamcrest](http://hamcrest.org/JavaHamcrest) for alternative asserts
- [EasyRandom](https://github.com/j-easy/easy-random) for generate randomic objects
- [Wiremock](https://wiremock.org/docs/junit-jupiter) for integrations tests

### Instructions
- Clone the project
- Run the project: `$ mvn quarkus:dev -DdebugHost=0.0.0.0`

### About the application
It's a RESTFull API for booking and searching a hotel with JWT authentication.

### JWT Authentication 
```shell
curl --request POST \
--url http://localhost:8080/auth \
--header 'Content-Type: application/json' \
--data '{
"user": "usuario",
"passwd": "senha"
}'
```

### Access the application
```shell
curl --request GET \
--url http://localhost:8080/hotel/6 \
--header 'Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ0ZXN0ZWN2YyIsInN1YiI6InVzdWFyaW8iLCJncm91cHMiOlsidXNlciJdLCJleHAiOjE2NjE5ODc4NDMsImlhdCI6MTY2MTkwMTQ0NCwianRpIjoiZTE0M2Y3Y2YtYWZkYi00NmRmLWE5ZWYtYTc2YjVhZjQwMTM2In0.S_NyIIyLxKxBZ0z_9-UXXe3OVQ1uDiMQKt1uhaICCkvL_UPRQ3zQ3Sy2s05pN7muRoyD-dqdhlRj_1IxgbUmvz1YfsbfRIrqNVZyL_nXqFr5ECcpi43PEKQ3ce1Qyy9GmqEE3e5vkn1Ja--AbsHeID8vRf-iqUCZXoUTwQBS9f3jLqFbfkxbLK67ek_b6nrgP27jBHP5Aqa3rHtpq8w7KcyUqKI_9_Kgtgxu1azwckv4DpdB3Z-sW768ME_heH8gMx5IopAKmidKxi7dThwy2vJivUxyTLM0pqb8H-0TnByBZKhoF8H9dEfdLYpdhPeOHrm4zCKjYph77x0i2R9snA'
```

### References
- [Getting Started With Quarkus](https://www.section.io/engineering-education/how-to-work-with-lombok-on-quarkus)
