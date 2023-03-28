FROM maven:eclipse-temurin as build
WORKDIR /app
RUN mkdir /app/src
ENV MAVEN_CONFIG=/var/maven/.m2
COPY pom.xml .
RUN mvn verify --fail-never -Duser.home=/var/maven
COPY src ./src
RUN mvn package -DskipTests -Duser.home=/var/maven

FROM eclipse-temurin:latest
COPY --from=build /app/target/measurement-api-1.0-SNAPSHOT-shaded.jar /app/server.jar
COPY oc /usr/bin/oc
COPY kubeconfig /app
RUN export KUBECONFIG=/app/kubeconfig
RUN chmod +x /usr/bin/oc
CMD ["java", "-XX:MaxRAMPercentage=90", "-jar", "/app/server.jar"]
