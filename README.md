# Simple Quarkus Project

### Architecture
- Java 11
- Quarkus 2.7.5
- [Lombok](https://projectlombok.org) to avoid boilerplate code
- [Mapstruct](https://mapstruct.org) for conversions between domains
- [WebClient](https://www.baeldung.com/spring-5-webclient): a reactive client HTTP
- [SL4J](https://www.slf4j.org/manual.html) for logging
- [JUnit 5](https://junit.org/junit5/docs/current/user-guide) for unit tests
- [Mockito](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Hamcrest](http://hamcrest.org/JavaHamcrest) for alternative asserts
- [EasyRandom](https://github.com/j-easy/easy-random) for generate randomic objects
- [Wiremock](https://wiremock.org/docs/junit-jupiter) for integrations tests

### Instructions
- Clone the project
- Run the project: $ mvn quarkus:dev -DdebugHost=0.0.0.0

### About the application
It's a RESTFull API for booking and searching a hotel.

### Access the application
http://localhost:8080/booking/9626/2019-09-24/2019-09-30/1/0, where:
- 9626: city code
- 2019-09-24: checkin
- 2019-09-30: checkout
- 1: adults
- 0: child

http://localhost:8080/hotel/6, where:
- 6: hotel ID

### References
- [Getting Started With Quarkus](https://www.section.io/engineering-education/how-to-work-with-lombok-on-quarkus)
