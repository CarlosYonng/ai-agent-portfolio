import type { ChatStreamEvent } from "./types";

export type ParsedSse = {
  event: string;
  data: string;
};

export function parseSseBlock(block: string): ParsedSse | null {
  const lines = block.split(/\r?\n/);
  const event = lines.find((line) => line.startsWith("event:"))?.slice("event:".length).trim() ?? "message";
  const data = lines
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice("data:".length).trimStart())
    .join("\n");

  return data ? { event, data } : null;
}

/**
 * EventSource 不支持 POST 请求；聊天请求需要 body，所以用 fetch 读取 SSE 字节流。
 */
export async function streamChat(
  payload: { tenantId: number; userId: number; sessionId?: number; question: string },
  onEvent: (event: ChatStreamEvent) => void
) {
  const response = await fetch("/api/chat/messages/stream", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });

  if (!response.ok || !response.body) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? "";

    for (const block of blocks) {
      const parsed = parseSseBlock(block);
      if (parsed) {
        onEvent(JSON.parse(parsed.data) as ChatStreamEvent);
      }
    }
  }
}
