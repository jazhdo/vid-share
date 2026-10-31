FROM sourcemation/jdk-25

WORKDIR /

COPY . /

RUN sh mvnw clean package

EXPOSE 443

CMD ["java", "-jar", "target/VidShare-0.1.0.jar"]