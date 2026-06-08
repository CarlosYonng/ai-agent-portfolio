# MCP 风格工具服务 Dockerfile。

FROM python:3.11-slim

LABEL org.opencontainers.image.version="1.1.0" \
      org.opencontainers.image.ref.name="mcp-server"

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8100

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
