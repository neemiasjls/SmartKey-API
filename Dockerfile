# =============================================================================
# Dockerfile = a "receita" que o Render usa para montar e rodar a API na nuvem.
#
# VOCE NUNCA PRECISA RODAR ISSO NA SUA MAQUINA.
# Este arquivo so existe para o servidor ler. Voce da "git push" e pronto.
#
# Sao duas etapas. A primeira compila o projeto. A segunda so roda o resultado.
# Isso deixa a imagem final pequena (~180 MB em vez de ~800 MB), o que importa
# porque o Render free tem apenas 512 MB de memoria.
# =============================================================================

# -----------------------------------------------------------------------------
# ETAPA 1 - COMPILAR
# -----------------------------------------------------------------------------

# "Comece com um computador que ja tem Java 21 e Maven instalados."
FROM maven:3.9-eclipse-temurin-21 AS build

# "Trabalhe dentro da pasta /app."
WORKDIR /app

# "Copie so a lista de dependencias primeiro e baixe tudo."
# Feito separado de proposito: se voce mudar so o codigo, o servidor reaproveita
# as dependencias ja baixadas e o deploy fica muito mais rapido.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# "Agora copie o codigo-fonte e compile, gerando o arquivo .jar."
COPY src ./src
RUN mvn -B clean package -DskipTests

# -----------------------------------------------------------------------------
# ETAPA 2 - RODAR
# -----------------------------------------------------------------------------

# "Comece de novo, com um computador que so tem o Java 21 para EXECUTAR."
# Nao precisamos do Maven nem do codigo-fonte aqui, so do .jar pronto.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Boa pratica de seguranca: criar um usuario comum em vez de rodar como root.
RUN addgroup -S smartkey && adduser -S smartkey -G smartkey

# "Pegue o .jar que a Etapa 1 produziu e traga para ca."
COPY --from=build /app/target/*.jar app.jar

USER smartkey

# A porta que a aplicacao escuta.
EXPOSE 8080

# Limites de memoria da JVM, ajustados para os 512 MB do plano free do Render.
# Sem isso, a JVM tenta usar memoria demais e o servico e derrubado.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# "Finalmente: rode o programa."
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
