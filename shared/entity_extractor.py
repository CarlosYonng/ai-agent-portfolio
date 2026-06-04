"""轻量实体抽取工具。

生产版本可以替换为 LLM 抽取、规则+NER 模型或领域词典。
当前规则足够支撑演示：错误码、接口路径、服务名、关键业务术语。
"""

from __future__ import annotations

import re


ERROR_CODE_PATTERN = re.compile(r"\b[A-Z]{2,10}_\d{3,6}\b")
API_PATTERN = re.compile(r"/api/[A-Za-z0-9_/\-{}]+")

DOMAIN_TERMS = {
    "支付回调": "Concept",
    "签名校验": "Concept",
    "订单创建": "Concept",
    "优惠券": "Concept",
    "库存预占": "Concept",
    "自动发货": "Concept",
    "订单系统": "System",
    "支付系统": "System",
}


def extract_entities(text: str) -> list[dict[str, str]]:
    """从文本中抽取实体。"""

    entities: dict[str, dict[str, str]] = {}
    for code in ERROR_CODE_PATTERN.findall(text):
        entities[code] = {"name": code, "type": "ErrorCode"}
    for api in API_PATTERN.findall(text):
        entities[api] = {"name": api, "type": "API"}
    for term, entity_type in DOMAIN_TERMS.items():
        if term in text:
            entities[term] = {"name": term, "type": entity_type}
    return list(entities.values())

