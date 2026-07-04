"""大模型客户端。

支持：
- `generate()` — 简单 system + user prompt，兼容现有调用
- `chat()` — 多轮 messages + 可选的 tool-use（ReAct 循环用）
- `chat_stream()` — 流式多轮 messages + tool-use 检测，逐 token 产出
"""

from __future__ import annotations

import json as jsonlib
import time
from collections.abc import AsyncGenerator
from dataclasses import dataclass
from typing import Any
import asyncio

import httpx

from app.core import metrics
from app.core.settings import settings


@dataclass
class ChatResponse:
    """统一的 chat 响应。"""

    content: str | None = None
    tool_call: tuple[str, dict[str, Any]] | None = None
    """(tool_name, tool_args)"""
    finish_reason: str = "stop"


@dataclass
class StreamChunk:
    """流式 chat 的单个事件块。

    type 取值：
    - "token": 文本内容增量，content 字段有值
    - "tool_call": 完整的工具调用，tool_name + tool_args 有值
    - "error": 调用出错，content 为错误描述
    - "done": 流正常结束
    """

    type: str
    content: str | None = None
    tool_name: str | None = None
    tool_args: dict[str, Any] | None = None
    finish_reason: str = "stop"


class ModelClient:
    """统一的大模型调用入口。"""

    async def generate(self, system_prompt: str, user_prompt: str) -> str:
        """简单双轮生成（兼容现有调用）。"""

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]
        self._ensure_llm_configured()
        return await self._llm_chat(messages)

    async def chat(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> ChatResponse:
        """多轮消息 + 可选的 tool-use。

        Args:
            messages: [{role, content}, ...]
            tools: OpenAI-compatible tool definitions

        Returns:
            ChatResponse，可能包含 tool_call 或 content
        """
        self._ensure_llm_configured()

        # 首次尝试：带工具调用；只有模型明确不支持 tool-use 时才退回纯文本。
        if tools:
            payload = self._build_payload(messages, tools)
            try:
                data = await self._post(payload)
                return self._parse_chat_response(data)
            except httpx.HTTPStatusError as exc:
                if exc.response.status_code not in {400, 422}:
                    raise
                # 部分 OpenAI-compatible 模型不支持 tools 参数，保留真实模型纯文本路径。

        # 工具调用不可用时退回真实模型纯文本，不吞掉认证、超时或服务端错误。
        clean_msgs = [m for m in messages if m.get("role") != "tool"]
        payload = self._build_payload(clean_msgs, None)
        data = await self._post(payload)
        content = data["choices"][0]["message"]["content"]
        return ChatResponse(content=content)

    def _ensure_llm_configured(self) -> None:
        """真实业务链路必须显式配置模型服务，避免生成看似正常的离线答案。"""

        token = settings.llm_token.get_secret_value()
        missing = []
        if not token:
            missing.append("LLM_TOKEN")
        if not settings.llm_base_url:
            missing.append("LLM_BASE_URL")
        if not settings.llm_model:
            missing.append("LLM_MODEL")
        if missing:
            raise RuntimeError(f"真实 LLM 配置不完整，缺少: {', '.join(missing)}")

    async def _llm_chat(self, messages: list[dict[str, Any]]) -> str:
        """真实 LLM 调用，纯文本响应。"""

        payload = self._build_payload(messages)
        data = await self._post(payload)
        return data["choices"][0]["message"]["content"]

    async def chat_stream(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> AsyncGenerator[StreamChunk, None]:
        """流式多轮对话 + 工具调用检测。

        逐 token 产出 StreamChunk。当 LLM 输出文本时实时 yield token 块；
        当 LLM 调用工具时累积 tool_call 参数，流结束后一次性 yield tool_call 块。

        Args:
            messages: [{role, content}, ...]
            tools: OpenAI-compatible tool definitions

        Yields:
            StreamChunk(type="token"|"tool_call"|"error"|"done")
        """
        self._ensure_llm_configured()

        payload = self._build_payload(messages, tools)
        payload["stream"] = True
        payload["stream_options"] = {"include_usage": False}

        headers = {"Authorization": f"Bearer {settings.llm_token.get_secret_value()}"}

        # 累积 tool_call 信息（跨 delta 拼接）
        tool_call_name: str | None = None
        tool_call_args_buffer: list[str] = []
        has_streamed_content = False

        try:
            async with httpx.AsyncClient(timeout=180) as client:
                async with client.stream(
                    "POST",
                    f"{settings.llm_base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                ) as response:
                    response.raise_for_status()
                    async for line in response.aiter_lines():
                        if not line.startswith("data: "):
                            continue
                        data = line[6:]
                        if data.strip() == "[DONE]":
                            break
                        try:
                            chunk = jsonlib.loads(data)
                        except jsonlib.JSONDecodeError:
                            continue

                        choice = (chunk.get("choices", [{}]) or [{}])[0]
                        delta = choice.get("delta", {})
                        finish = choice.get("finish_reason") or ""

                        # 检测文本内容增量
                        content = delta.get("content")
                        if content:
                            has_streamed_content = True
                            yield StreamChunk(type="token", content=content)
                            continue

                        # 检测工具调用增量
                        tool_calls = delta.get("tool_calls")
                        if tool_calls:
                            for tc in tool_calls:
                                func = tc.get("function", {})
                                name = func.get("name")
                                if name:
                                    tool_call_name = name
                                args_delta = func.get("arguments")
                                if args_delta:
                                    tool_call_args_buffer.append(args_delta)

                        # 流结束，检查是否有未产出的 tool_call
                        if finish == "tool_calls" and tool_call_name and not has_streamed_content:
                            try:
                                args_str = "".join(tool_call_args_buffer)
                                tool_args = jsonlib.loads(args_str) if args_str.strip() else {}
                            except jsonlib.JSONDecodeError:
                                tool_args = {}
                            yield StreamChunk(
                                type="tool_call",
                                tool_name=tool_call_name,
                                tool_args=tool_args,
                                finish_reason="tool_calls",
                            )
                            return

                        if finish == "stop" and not has_streamed_content:
                            # LLM 直接返回了空内容或极短答案（未走 token 流）
                            pass

        except (httpx.TimeoutException, httpx.TransportError, httpx.HTTPStatusError) as e:
            yield StreamChunk(type="error", content=f"LLM 流式调用失败: {e}")
        except Exception as e:
            yield StreamChunk(type="error", content=f"流式处理异常: {e}")

        yield StreamChunk(type="done")

    def _build_payload(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        """构造 OpenAI-compatible 请求体。"""
        payload: dict[str, Any] = {
            "model": settings.llm_model,
            "messages": messages,
            "temperature": 0.2,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        return payload

    async def _post(self, payload: dict[str, Any]) -> dict[str, Any]:
        """发送 HTTP POST 到 LLM API，短暂故障时指数退避重试。"""
        if not settings.llm_base_url:
            raise RuntimeError("LLM_BASE_URL 未配置")
        headers = {"Authorization": f"Bearer {settings.llm_token.get_secret_value()}"}
        last_error: Exception | None = None
        for attempt in range(3):
            started_at = time.perf_counter()
            try:
                async with httpx.AsyncClient(timeout=60) as client:
                    response = await client.post(
                        f"{settings.llm_base_url}/chat/completions",
                        headers=headers,
                        json=payload,
                    )
                    response.raise_for_status()
                    metrics.LLM_REQUESTS.labels("chat_completion", settings.llm_model or "unknown", "success", "none").inc()
                    metrics.LLM_DURATION.labels("chat_completion", settings.llm_model or "unknown").observe(time.perf_counter() - started_at)
                    return response.json()
            except (httpx.TimeoutException, httpx.TransportError, httpx.HTTPStatusError) as error:
                last_error = error
                error_label = self._llm_error_type(error)
                metrics.LLM_REQUESTS.labels("chat_completion", settings.llm_model or "unknown", "error", error_label).inc()
                metrics.LLM_DURATION.labels("chat_completion", settings.llm_model or "unknown").observe(time.perf_counter() - started_at)
                if isinstance(error, httpx.HTTPStatusError) and error.response.status_code < 500:
                    raise
                if attempt < 2:
                    await asyncio.sleep(0.5 * (2 ** attempt))
        raise RuntimeError(f"LLM API 连续重试失败: {last_error}")

    @staticmethod
    def _llm_error_type(error: Exception) -> str:
        if isinstance(error, httpx.TimeoutException):
            return "timeout"
        if isinstance(error, httpx.HTTPStatusError):
            status = error.response.status_code
            if status == 429:
                return "rate_limited"
            if status >= 500:
                return "server_error"
            return f"http_{status}"
        if isinstance(error, httpx.TransportError):
            return "transport_error"
        return metrics.error_type(error)

    def _parse_chat_response(self, data: dict[str, Any]) -> ChatResponse:
        """解析 LLM 响应，提取文本或 tool_call。"""
        msg = data["choices"][0]["message"]
        finish = data["choices"][0].get("finish_reason", "stop")

        tool_calls = msg.get("tool_calls")
        if tool_calls and len(tool_calls) > 0:
            tc = tool_calls[0]
            import json
            try:
                func_args = json.loads(tc["function"]["arguments"])
            except json.JSONDecodeError:
                func_args = {}
            return ChatResponse(
                tool_call=(tc["function"]["name"], func_args),
                finish_reason=finish,
            )

        return ChatResponse(content=msg.get("content", ""), finish_reason=finish)

    async def generate_stream(self, messages: list[dict[str, Any]]) -> AsyncGenerator[str, None]:
        """流式生成，逐 token 产出。

        用于 SSE 推送，调用真实 LLM API 的 stream=True。
        """
        self._ensure_llm_configured()

        payload = self._build_payload(messages)
        payload["stream"] = True
        payload["stream_options"] = {"include_usage": False}

        headers = {"Authorization": f"Bearer {settings.llm_token.get_secret_value()}"}

        async with httpx.AsyncClient(timeout=120) as client:
            async with client.stream(
                "POST",
                f"{settings.llm_base_url}/chat/completions",
                headers=headers,
                json=payload,
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data = line[6:]
                        if data.strip() == "[DONE]":
                            break
                        try:
                            import json
                            chunk = json.loads(data)
                            delta = chunk.get("choices", [{}])[0].get("delta", {})
                            content = delta.get("content", "")
                            if content:
                                yield content
                        except json.JSONDecodeError:
                            continue

model_client = ModelClient()
