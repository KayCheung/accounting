# 基础镜像：官方轻量级 OpenJDK 21 JRE 镜像
FROM eclipse-temurin:21-jre-jammy

# 维护者信息
LABEL maintainer="KayCheung"

# 设置环境变量及系统时区
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_ARGS=""

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置应用工作目录
WORKDIR /app

# 创建日志挂载目录
RUN mkdir -p /data/logs /tmp
VOLUME ["/data/logs", "/tmp"]

# 从构建上下文复制 jar 包
COPY accounting-core/target/*.jar app.jar

# 暴露端口：8080 (Web API), 9999 (XXL-Job 执行器)
EXPOSE 8080 9999

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 容器启动入口
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar $SPRING_ARGS"]
