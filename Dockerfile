# =====================================================================
# zing-doctor 医生系统 - 后端 Dockerfile
# 基础镜像：openjdk:8-jre-slim（内网环境可提前 docker pull 或导入）
# 构建：docker build -t zing-doctor-backend:latest .
# 运行：docker run -d --name zing-doctor-backend -p 8081:8081 zing-doctor-backend
# =====================================================================
FROM openjdk:8-jre-slim

WORKDIR /app

# 拷贝后端可执行 jar（交付包 app/zing-doctor.jar）
COPY app/zing-doctor.jar /app/zing-doctor.jar

# 时区设置（避免日志时间差8小时）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 后端端口
EXPOSE 8081

# 日志目录（挂载用）
VOLUME ["/app/logs"]

# 启动命令（环境变量由 docker-compose.yml 或 docker run -e 传入）
ENTRYPOINT ["java", "-jar", "/app/zing-doctor.jar"]
