# Imagen del backend para producción (Render). Se construye desde la carpeta backend/.
#   docker build -t ganadera-backend backend
#   docker run -p 8080:8080 --env-file .env ganadera-backend

# ---------- 1) Compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primero solo el pom: las dependencias se bajan una vez y quedan en caché entre deploys
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Los tests corren en GitHub Actions (CI); acá solo se arma el jar
RUN mvn -B -ntp -DskipTests package

# ---------- 2) Correr ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=America/Argentina/Buenos_Aires \
    SPRING_PROFILES_ACTIVE=prod \
    # La instancia gratis de Render tiene 512 MB: Java usa como máximo el 75% y un GC liviano
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
COPY --from=build /app/target/*.jar app.jar
# Render indica el puerto con la variable PORT (application.yml: server.port=${PORT:8080})
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
