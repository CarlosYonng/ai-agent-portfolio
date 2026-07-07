# Python AI 服务 Dockerfile。
# 注释写得偏详细，是为了你后续能讲清楚每一步。

FROM python:3.11-slim

LABEL org.opencontainers.image.version="1.1.0" \
      org.opencontainers.image.ref.name="ai-service"

WORKDIR /app

# 先复制 requirements，可以利用 Docker layer cache。
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 再复制业务代码。
COPY app ./app

# Docker Compose 单独部署，端口改 docker-compose.yml 里的 ai-service.ports 即可。
EXPOSE 8000

# uvicorn 提供 FastAPI HTTP 服务。
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
