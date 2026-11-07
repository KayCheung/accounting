# 前端 Nginx 镜像
FROM nginx:alpine

LABEL maintainer="KayCheung"

# 设置系统时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 替换默认 Nginx 配置文件
COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf

# 拷贝静态资源产物
COPY accounting-ui/dist /usr/share/nginx/html

# 暴露前端端口
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
