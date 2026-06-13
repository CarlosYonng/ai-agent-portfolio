"""大模型客户端。

这里先提供 mock 实现，保证没有配置模型凭证也能跑通端到端链路。
后续你只需要把 generate 方法替换为 OpenAI/DeepSeek/Qwen 的真实 HTTP 调用。
"""

from __future__ import annotations

import httpx

from app.core.settings import settings


class ModelClient:
    """统一的大模型调用入口。"""

    async def generate(self, system_prompt: str, user_prompt: str) -> str:
        """生成文本。

        如果没有配置模型凭证，就返回 mock 答案，避免开发早期被外部服务卡住。
        """

        llm_token = settings.llm_token.get_secret_value()
        if not llm_token or settings.llm_provider == "mock":
            return self._mock_generate(user_prompt)
        if not settings.llm_base_url or not settings.llm_model:
            raise RuntimeError("LLM_BASE_URL 和 LLM_MODEL 未配置，无法调用真实模型。")

        # 这里按 OpenAI compatible API 写，DeepSeek/Qwen 兼容服务也可以复用。
        headers = {"Authorization": f"Bearer {llm_token}"}
        payload = {
            "model": settings.llm_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.2,
        }
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(f"{settings.llm_base_url}/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]

    def _mock_generate(self, user_prompt: str) -> str:
        """没有真实模型时的兜底生成，方便 smoke test。"""

        return (
            "当前未检测到可用的真实模型配置，系统已使用离线兜底生成结果。"
            "问题已通过 Agent 链路处理，相关证据会在引用中展示。"
        )


model_client = ModelClient()
