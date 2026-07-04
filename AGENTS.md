# AI Agent Portfolio — 项目总览

本项目是集成企业知识库 RAG Agent 和 Java 微服务故障诊断能力的全栈演示系统。

详细开发指南见 [CLAUDE.md](./CLAUDE.md)。

## 项目结构速览

| 目录 | 说明 |
|------|------|
| `backend-java/` | Spring Boot 3.3.5 + Java 21 后端，5 模块分层 |
| `ai-service/` | Python FastAPI Agent 服务（ReAct RAG） |
| `frontend/` | React + Vite 管理控制台 |
| `shared/` | Python 脚本工具库 |
| `infra/` | Docker Compose 编排 |
| `scripts/` | 文档入库脚本、冒烟测试 |
| `docs/` | 架构设计文档 |
