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
RUN wget -c https://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.9.50/openshift-client-linux-4.9.50.tar.gz -O - | sudo tar -xzv -C /usr/bin oc &&\
    chmod +x /usr/bin/oc
RUN export KUBECONFIG=/etc/kubeconfig
CMD ["java", "-XX:MaxRAMPercentage=90", "-jar", "/app/server.jar"]
