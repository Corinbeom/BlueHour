# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
# CVE-2026-91403: distrust roots that can chain to the compromised eMudhra CAs.
RUN set -eu; \
    keytool -list -cacerts -storepass changeit >/dev/null; \
    for alias in "emsignrootcag1 [jdk]" "emsignrootcag2 [jdk]" "emsigneccrootcag3 [jdk]" "comodoaaaca [jdk]" \
        "cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" \
        "cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]"; do \
        if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
            keytool -delete -cacerts -storepass changeit -alias "$alias"; \
        fi; \
    done; \
    if keytool -list -cacerts -storepass changeit -v | grep -Eq 'Owner: CN=(emSign Root CA - G1|emSign Root CA - G2|emSign ECC Root CA - G3|AAA Certificate Services)(,|$)'; then exit 1; fi
COPY . .
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Apply the same trust restriction to the runtime image.
RUN set -eu; \
    keytool -list -cacerts -storepass changeit >/dev/null; \
    for alias in "emsignrootcag1 [jdk]" "emsignrootcag2 [jdk]" "emsigneccrootcag3 [jdk]" "comodoaaaca [jdk]" \
        "cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" \
        "cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]"; do \
        if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then \
            keytool -delete -cacerts -storepass changeit -alias "$alias"; \
        fi; \
    done; \
    if keytool -list -cacerts -storepass changeit -v | grep -Eq 'Owner: CN=(emSign Root CA - G1|emSign Root CA - G2|emSign ECC Root CA - G3|AAA Certificate Services)(,|$)'; then exit 1; fi
COPY --from=builder /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx360m -XX:+UseSerialGC -XX:MaxMetaspaceSize=150m \
  -XX:TieredStopAtLevel=1 -Dspring.jmx.enabled=false"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]
