"""AI 服务配置。

所有环境变量集中在这里读取，避免业务代码到处 os.getenv。
"""

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """运行时配置对象。"""

    model_config = SettingsConfigDict(
        env_file=("../.env", ".env"),
        case_sensitive=False,
        extra="ignore",
    )

    mysql_dsn: str = "mysql://agent:agent123@localhost:3306/agentdb"
    qdrant_url: str = "http://localhost:6333"
    neo4j_http_url: str = "http://localhost:7474"
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = "agent123456"
    mcp_base_url: str = "http://localhost:8100"
    llm_provider: str = "mock"
    llm_token: SecretStr = SecretStr("")
    llm_base_url: str = ""
    llm_model: str = "mock"
    embedding_provider: str = "hash"
    embedding_token: SecretStr = SecretStr("")
    dashscope_token: SecretStr = SecretStr("")
    embedding_base_url: str = ""
    embedding_model: str = "hash"
    embedding_dimensions: int = 128

settings = Settings()
