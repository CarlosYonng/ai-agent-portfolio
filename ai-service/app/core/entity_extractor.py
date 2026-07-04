"""AI 服务内置的学习版实体抽取器。

查询侧与入库脚本使用同一套抽取思路：规则实体 + 领域词典 + 规范化去重。
这里保留少量默认领域词，是为了让用户问题在没有外部词典时也能演示 GraphRAG 召回。
"""

from __future__ import annotations

from dataclasses import dataclass, field
import re
from typing import Any, Mapping


DomainTerms = Mapping[str, str]

DEFAULT_DOMAIN_TERMS: DomainTerms = {
    "知识库": "BUSINESS_TERM",
    "向量检索": "BUSINESS_TERM",
    "图谱检索": "BUSINESS_TERM",
    "GraphRAG": "BUSINESS_TERM",
    "支付回调": "BUSINESS_TERM",
    "签名校验": "BUSINESS_TERM",
    "订单创建": "BUSINESS_TERM",
    "优惠券": "BUSINESS_TERM",
    "库存预占": "BUSINESS_TERM",
    "自动发货": "BUSINESS_TERM",
    "订单系统": "SYSTEM",
    "支付系统": "SYSTEM",
}

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

_CHINESE_STOPWORDS = frozenset({
    "但是", "因为", "所以", "如果", "虽然", "而且", "或者", "不过", "然后",
    "因此", "同时", "此外", "就是", "可以", "没有", "这个", "那个", "什么",
    "怎么", "如何", "一个", "一些", "两个", "这些", "那些", "这样", "那样",
    "一种", "进行", "通过", "根据", "关于", "对于", "除了", "包括", "以及",
    "及其", "等等", "不是", "还是", "所有", "每个", "其中", "之后",
    "之前", "当前", "主要", "需要", "使用", "提供", "实现", "支持", "能够",
    "应该", "可能", "必须", "不会", "不能", "一定", "已经", "正在",
    "开始", "结束", "完成", "进入", "分为", "是否", "属于", "具有",
    "由于", "为此", "以至", "以来", "以上", "以下", "以内", "以外",
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
    策略：先用中文标点切成短句，在短句内提取 3~6 字 n-gram，
    跨短句计数后只保留出现 >= 2 次或 4 字以上的单次术语。
    """
    # 按标点分成短句
    sentences = re.split(r"[，。、；：！？（）【】\n\r\t]", text)
    segments: list[str] = []
    seg_offsets: list[tuple[int, int, str]] = []  # (text_pos, seg_len, segment)
    for sent in sentences:
        for m in re.finditer(r"[一-鿿㐀-䶿]{3,}", sent):
            seg = m.group(0)
            if len(seg) >= 3:
                segments.append(seg)

    # 跨短句 n-gram 计数
    counts: dict[str, int] = {}
    for seg in segments:
        seen: set[str] = set()
        slen = len(seg)
        for n in range(3, min(7, slen + 1)):
            for i in range(slen - n + 1):
                cand = seg[i:i + n]
                if cand not in _CHINESE_STOPWORDS and cand not in seen:
                    counts[cand] = counts.get(cand, 0) + 1
                    seen.add(cand)

    # 写入实体（含包含过滤）
    ranked = sorted(counts.keys(), key=len, reverse=True)
    for term in ranked:
        cnt = counts[term]
        if cnt == 1 and len(term) <= 3:
            continue
        if cnt == 1 and len(term) >= 4:
            pass  # 长词单次也保留
        elif cnt < 2:
            continue
        # 被更长词包含且频次不更高的丢弃
        if any(term != lt and term in lt and counts.get(lt, 0) >= cnt
               for lt in ranked if len(lt) > len(term)):
            continue
        entity = entities.setdefault(
            ("BUSINESS_TERM", term.casefold()),
            Entity(name=term, type="BUSINESS_TERM", source="auto_extract",
                   confidence=0.70 if (cnt >= 3 and len(term) >= 4) else 0.60),
        )
        # 在原文中找到所有出现位置
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
    """从文本中抽取 RAG 图谱召回所需的实体。"""

    if not text:
        return []

    terms = DEFAULT_DOMAIN_TERMS if domain_terms is None else domain_terms
    entities: dict[tuple[str, str], Entity] = {}
    _extract_rule_entities(text, entities)
    _extract_dictionary_entities(text, entities, terms)
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
