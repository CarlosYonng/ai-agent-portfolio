"""AI 服务内置实体抽取。

这份规则与 shared/entity_extractor.py 保持一致，保证 Docker 镜像自包含。
生产版本可以替换为 LLM 抽取、NER 模型或领域词典。
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
    """从查询文本中抽取实体。"""

    entities: dict[str, dict[str, str]] = {}
    for code in ERROR_CODE_PATTERN.findall(text):
        entities[code] = {"name": code, "type": "ErrorCode"}
    for api in API_PATTERN.findall(text):
        entities[api] = {"name": api, "type": "API"}
    for term, entity_type in DOMAIN_TERMS.items():
        if term in text:
            entities[term] = {"name": term, "type": entity_type}
    return list(entities.values())

