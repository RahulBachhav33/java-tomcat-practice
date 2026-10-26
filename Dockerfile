# Stage 1: compile the Java web application into a WAR file.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package

# Stage 2: run the WAR on Apache Tomcat 10.1 with Java 17.
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
ENV APP_ENV=local
EXPOSE 8080
CMD ["catalina.sh", "run"]
