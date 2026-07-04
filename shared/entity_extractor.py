"""面向 RAG 学习版的实体抽取器。

主流 RAG 项目里的实体抽取通常先把核心闭环做清楚：
  1. 规则抽取：错误码、API 路径这类格式稳定的实体。
  2. 领域词典：业务术语由知识库或业务配置提供，不写死在算法里。
  3. 规范化和去重：同一实体多次出现时合并 mention，便于写入图谱。

这版刻意不做复杂的实体治理、别名库、代码对象识别和多层过滤。它适合学习
RAG/GraphRAG 中实体抽取的实际使用方式，也保留后续替换 NER/LLM 抽取器的接口。
"""

from __future__ import annotations

from dataclasses import dataclass, field
import re
from typing import Any, Mapping


DomainTerms = Mapping[str, str]

ERROR_CODE_PATTERN = re.compile(r"\b[A-Z][A-Z0-9]{1,30}(?:_[A-Z0-9]{2,40})+\b")
API_PATTERN = re.compile(r"(?<![\w.-])/api(?:/[A-Za-z0-9._~:%@!$&'()*+,;=\-{}]+)+")
ERROR_SUFFIXES = (
    "ERROR",
    "FAILED",
    "FAIL",
    "TIMEOUT",
    "DENIED",
    "INVALID",
    "NOT_FOUND",
    "UNAUTHORIZED",
    "FORBIDDEN",
)

# 中文常见虚词和通用动词，避免被误抽为业务术语
_CHINESE_STOPWORDS = frozenset({
    "但是", "因为", "所以", "如果", "虽然", "而且", "或者", "不过", "然后",
    "因此", "同时", "此外", "就是", "可以", "没有", "这个", "那个", "什么",
    "怎么", "如何", "一个", "一些", "两个", "这些", "那些", "这样", "那样",
    "一种", "进行", "通过", "根据", "关于", "对于", "除了", "包括", "以及",
    "及其", "等等", "不是", "还是", "还是", "所有", "每个", "其中", "之后",
    "之前", "当前", "主要", "需要", "使用", "提供", "实现", "支持", "能够",
    "应该", "可能", "可以", "必须", "不会", "不能", "一定", "已经", "正在",
    "开始", "结束", "完成", "进入", "分为", "是否", "属于", "具有", "包括",
    "由于", "该当", "为此", "以至", "以来", "以上", "以下", "以内", "以外",
})

TYPE_ALIASES = {
    "api": "API_ENDPOINT",
    "endpoint": "API_ENDPOINT",
    "error": "ERROR_CODE",
    "errorcode": "ERROR_CODE",
    "error_code": "ERROR_CODE",
    "concept": "BUSINESS_TERM",
    "business_concept": "BUSINESS_TERM",
    "term": "BUSINESS_TERM",
    "system": "SYSTEM",
}


@dataclass(frozen=True)
class Mention:
    """实体在原文中的一次出现位置。"""

    text: str
    start: int
    end: int

    def to_dict(self) -> dict[str, Any]:
        return {"text": self.text, "start": self.start, "end": self.end}


@dataclass
class Entity:
    """抽取结果的最小标准结构。"""

    name: str
    type: str
    source: str
    confidence: float
    mentions: list[Mention] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "type": self.type,
            "source": self.source,
            "confidence": round(self.confidence, 3),
            "mentions": [mention.to_dict() for mention in self.mentions],
            "occurrence_count": len(self.mentions),
        }


def normalize_entity_type(entity_type: str | None) -> str:
    """统一实体类型命名，避免同一类实体写出多种标签。"""

    if not entity_type:
        return "BUSINESS_TERM"
    key = entity_type.strip().lower().replace("-", "_").replace(" ", "_")
    return TYPE_ALIASES.get(key, entity_type.strip().upper())


def normalize_entity_name(name: str, entity_type: str) -> str:
    """统一实体名称，减少图谱节点重复。"""

    cleaned = name.strip().strip("，。；;、,.!?！？)]}》\"'")
    if entity_type == "ERROR_CODE":
        return cleaned.upper()
    if entity_type == "API_ENDPOINT":
        return cleaned.rstrip("/")
    return re.sub(r"\s+", " ", cleaned)


def _looks_like_error_code(token: str) -> bool:
    parts = token.split("_")
    return any(part.isdigit() for part in parts) or token.endswith(ERROR_SUFFIXES)


def _is_ascii_term(term: str) -> bool:
    return bool(re.fullmatch(r"[A-Za-z0-9_.:/-]+", term))


def _has_word_boundary(text: str, start: int, end: int) -> bool:
    before = text[start - 1] if start > 0 else ""
    after = text[end] if end < len(text) else ""
    return not (before and before.isalnum()) and not (after and after.isalnum())


def _iter_term_mentions(text: str, term: str) -> list[Mention]:
    flags = re.IGNORECASE if _is_ascii_term(term) else 0
    mentions: list[Mention] = []
    for match in re.finditer(re.escape(term), text, flags):
        if flags and not _has_word_boundary(text, match.start(), match.end()):
            continue
        mentions.append(Mention(match.group(0), match.start(), match.end()))
    return mentions


def _add_entity(
    entities: dict[tuple[str, str], Entity],
    *,
    name: str,
    entity_type: str,
    mention: Mention,
    source: str,
    confidence: float,
) -> None:
    normalized_type = normalize_entity_type(entity_type)
    normalized_name = normalize_entity_name(name, normalized_type)
    if not normalized_name:
        return

    key = (normalized_type, normalized_name.casefold())
    entity = entities.setdefault(
        key,
        Entity(
            name=normalized_name,
            type=normalized_type,
            source=source,
            confidence=confidence,
        ),
    )
    entity.confidence = max(entity.confidence, confidence)
    entity.mentions.append(mention)


def _extract_rule_entities(text: str, entities: dict[tuple[str, str], Entity]) -> None:
    """抽取格式稳定、误判成本较低的实体。"""

    for match in API_PATTERN.finditer(text):
        value = match.group(0)
        _add_entity(
            entities,
            name=value,
            entity_type="API_ENDPOINT",
            mention=Mention(value, match.start(), match.end()),
            source="rule",
            confidence=0.98,
        )

    for match in ERROR_CODE_PATTERN.finditer(text):
        value = match.group(0)
        if not _looks_like_error_code(value):
            continue
        _add_entity(
            entities,
            name=value,
            entity_type="ERROR_CODE",
            mention=Mention(value, match.start(), match.end()),
            source="rule",
            confidence=0.96,
        )


def _extract_dictionary_entities(
    text: str,
    entities: dict[tuple[str, str], Entity],
    domain_terms: DomainTerms | None,
) -> None:
    """抽取业务术语。术语来自调用方配置，避免把业务知识写死在抽取器里。"""

    if not domain_terms:
        return

    # 长词优先只是为了避免"支付回调"抢先解释"支付回调流程"。
    for term, entity_type in sorted(domain_terms.items(), key=lambda item: len(item[0]), reverse=True):
        for mention in _iter_term_mentions(text, term):
            _add_entity(
                entities,
                name=term,
                entity_type=entity_type,
                mention=mention,
                source="dictionary",
                confidence=0.9,
            )


def _extract_chinese_terms(text: str, entities: dict[tuple[str, str], Entity]) -> None:
    """抽取中文业务术语。

    覆盖纯中文文档（如医疗、金融类）没有英文错误码/API 的场景。
    策略：
      1. 先用中文标点（，。、；：（）「」）把长文本切成短句。
      2. 在每个短句内提取 3~6 字的 n-gram，按短句去重计数。
      3. 只保留跨短句出现 >= 2 次的 n-gram。
      4. 被更长词包含且频次不更高的短词丢弃。
      5. 短词（3 字且出现 2 次正好）confidence 略低，长且高频的 confidence 更高。
    """
    # 先用标点把文本切成短句，大幅减少跨句噪音
    sentences = re.split(r"[，。、；：！？（）【】\n\r\t]", text)
    short_segments: list[str] = []
    for sent in sentences:
        # 从短句中提取连续中文段
        for m in re.finditer(r"[一-鿿㐀-䶿]{3,}", sent):
            seg = m.group(0)
            if len(seg) >= 3:
                short_segments.append(seg)

    # 跨短句的 n-gram 计数
    counts: dict[str, int] = {}
    for seg in short_segments:
        seen: set[str] = set()
        slen = len(seg)
        for n in range(3, min(7, slen + 1)):
            for i in range(slen - n + 1):
                cand = seg[i:i + n]
                if cand not in _CHINESE_STOPWORDS and cand not in seen:
                    counts[cand] = counts.get(cand, 0) + 1
                    seen.add(cand)

    # 4 字以上单次出现术语也保留（低频但有价值），3 字必须 >= 2 次
    ranked = sorted(counts.keys(), key=len, reverse=True)
    for term in ranked:
        cnt = counts[term]
        if cnt == 1 and len(term) <= 3:
            continue
        if cnt == 1 and len(term) >= 4:
            pass  # 长词出现一次也保留
        elif cnt < 2:
            continue
        if any(term != lt and term in lt and counts.get(lt, 0) >= cnt
               for lt in ranked if len(lt) > len(term)):
            continue
        et = "BUSINESS_TERM"
        key = (et, term.casefold())
        entity = entities.setdefault(
            key,
            Entity(name=term, type=et, source="auto_extract",
                   confidence=0.70 if cnt >= 3 and len(term) >= 4 else 0.60),
        )
        for m in re.finditer(re.escape(term), text):
            mention = Mention(term, m.start(), m.end())
            if mention not in entity.mentions:
                entity.mentions.append(mention)


def extract_entities(
    text: str,
    domain_terms: DomainTerms | None = None,
    *,
    max_entities: int = 30,
) -> list[dict[str, Any]]:
    """从文本中抽取 RAG 图谱召回所需的实体。

    Args:
        text: 待抽取文本，通常是一个 chunk 或用户 query。
        domain_terms: 业务术语表，格式为 {"支付回调": "BUSINESS_TERM", "支付系统": "SYSTEM"}。
        max_entities: 单段文本最多返回的实体数。

    Returns:
        实体列表，每个实体包含 name/type/source/confidence/mentions/occurrence_count。
    """

    if not text:
        return []

    entities: dict[tuple[str, str], Entity] = {}
    _extract_rule_entities(text, entities)
    _extract_dictionary_entities(text, entities, domain_terms)
    _extract_chinese_terms(text, entities)

    results = [entity.to_dict() for entity in entities.values()]
    results.sort(
        key=lambda item: (
            float(item["confidence"]),
            int(item["occurrence_count"]),
            len(str(item["name"])),
        ),
        reverse=True,
    )
    return results[:max_entities]
