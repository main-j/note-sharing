#!/usr/bin/env python3
"""DeepSeek OpenAI-compatible client for seed content generation."""

from __future__ import annotations

import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-pro"
CACHE_DIR = Path(__file__).resolve().parent / "cache"


def load_env_files() -> None:
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parents[1]
    candidates = [
        script_dir / ".env",
        repo_root / "ai_bff" / ".env",
        repo_root / ".env",
    ]
    for path in candidates:
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            os.environ.setdefault(key, value)


def resolve_api_key() -> str:
    load_env_files()
    for key in ("DEEPSEEK_API_KEY", "AI_MODEL_API_KEY"):
        value = os.environ.get(key, "").strip()
        if value:
            return value
    raise RuntimeError(
        "Missing DeepSeek API key. Set DEEPSEEK_API_KEY or AI_MODEL_API_KEY in "
        "scripts/seed/.env or ai_bff/.env"
    )


def resolve_base_url() -> str:
    load_env_files()
    return os.environ.get("DEEPSEEK_BASE_URL") or os.environ.get("AI_MODEL_BASE_URL") or DEFAULT_BASE_URL


def resolve_model() -> str:
    load_env_files()
    return os.environ.get("DEEPSEEK_MODEL") or os.environ.get("AI_MODEL_NAME") or DEFAULT_MODEL


def extract_message_content(choice: dict[str, Any]) -> str:
    message = choice.get("message") or {}
    chunks: list[str] = []
    for key in ("content", "reasoning_content"):
        value = message.get(key)
        if value is not None and str(value).strip():
            chunks.append(str(value).strip())
    return "\n\n".join(chunks)


class DeepSeekClient:
    def __init__(
        self,
        api_key: str | None = None,
        base_url: str | None = None,
        model: str | None = None,
        cache_dir: Path | None = None,
        refresh_cache: bool = False,
    ) -> None:
        self.api_key = api_key or resolve_api_key()
        self.base_url = (base_url or resolve_base_url()).rstrip("/")
        self.model = model or resolve_model()
        self.cache_dir = cache_dir or CACHE_DIR
        self.refresh_cache = refresh_cache

    def _cache_path(self, namespace: str, key: str, suffix: str) -> Path:
        digest = hashlib.sha256(key.encode("utf-8")).hexdigest()[:16]
        path = self.cache_dir / namespace / f"{digest}{suffix}"
        path.parent.mkdir(parents=True, exist_ok=True)
        return path

    def chat(
        self,
        messages: list[dict[str, str]],
        *,
        temperature: float = 0.7,
        max_tokens: int = 2200,
        thinking: bool = False,
        label: str = "DeepSeek request",
    ) -> str:
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "thinking": {"type": "disabled"},
        }
        if thinking:
            payload["thinking"] = {"type": "enabled"}
            payload["reasoning_effort"] = "high"

        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                if attempt == 0:
                    print(f"[DeepSeek] {label} ...", flush=True)
                else:
                    print(f"[DeepSeek] retry {attempt + 1}/3: {label}", flush=True)
                request = urllib.request.Request(
                    f"{self.base_url}/chat/completions",
                    data=body,
                    headers={
                        "Content-Type": "application/json",
                        "Authorization": f"Bearer {self.api_key}",
                    },
                    method="POST",
                )
                with urllib.request.urlopen(request, timeout=180) as response:
                    data = json.loads(response.read().decode("utf-8"))
                choices = data.get("choices") or []
                if not choices:
                    raise RuntimeError(f"DeepSeek returned no choices: {data}")
                choice = choices[0]
                content = extract_message_content(choice)
                if not content:
                    finish_reason = choice.get("finish_reason")
                    message = choice.get("message") or {}
                    raise RuntimeError(
                        "DeepSeek returned empty content "
                        f"(finish_reason={finish_reason}, message_keys={list(message.keys())})"
                    )
                print(f"[DeepSeek] done: {label}", flush=True)
                return str(content).strip()
            except urllib.error.HTTPError as exc:
                detail = exc.read().decode("utf-8", errors="replace")[:300]
                last_error = RuntimeError(f"HTTP {exc.code}: {detail}")
                time.sleep(2.0 * (attempt + 1))
            except (urllib.error.URLError, TimeoutError, RuntimeError, KeyboardInterrupt) as exc:
                last_error = exc
                if isinstance(exc, KeyboardInterrupt):
                    raise
                time.sleep(2.0 * (attempt + 1))
        raise RuntimeError(f"DeepSeek API failed after retries: {last_error}")

    def ping(self) -> str:
        return self.chat(
            [{"role": "user", "content": "Reply with OK only."}],
            max_tokens=32,
            temperature=0.0,
            label="connectivity test",
        )

    def generate_note_markdown(
        self,
        *,
        title: str,
        domain_label: str,
        topic_title: str,
        username: str,
    ) -> str:
        cache_key = f"{title}|{domain_label}|{topic_title}|{username}|{self.model}"
        cache_path = self._cache_path("notes", cache_key, ".md")
        if cache_path.exists() and not self.refresh_cache:
            return cache_path.read_text(encoding="utf-8")

        prompt = (
            f"请为一篇计算机专业学习笔记生成 Markdown 正文。\n"
            f"领域：{domain_label}\n"
            f"主题：{topic_title}\n"
            f"笔记标题：{title}\n"
            f"作者：{username}\n\n"
            "要求：\n"
            "1. 使用中文，500-800 字\n"
            "2. 第一行必须是 `# {标题}`\n"
            "3. 包含：摘要、核心概念、关键术语、示例说明、常见误区、练习建议\n"
            "4. 内容准确、适合本科/研究生复习\n"
            "5. 只输出 Markdown，不要输出解释性前后缀"
        )
        content = self.chat(
            [
                {
                    "role": "system",
                    "content": "你是计算机专业笔记作者，擅长编写结构清晰、准确实用的中文 Markdown 学习笔记。",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.6,
            max_tokens=1400,
            label=f"note {title}",
        )
        if not content.lstrip().startswith("#"):
            content = f"# {title}\n\n{content}"
        cache_path.write_text(content, encoding="utf-8")
        return content

    def generate_question(
        self,
        *,
        domain_label: str,
        topic_title: str,
        username: str,
    ) -> tuple[str, str]:
        cache_key = f"{domain_label}|{topic_title}|{username}|{self.model}|qa"
        cache_path = self._cache_path("questions", cache_key, ".json")
        if cache_path.exists() and not self.refresh_cache:
            cached = json.loads(cache_path.read_text(encoding="utf-8"))
            return cached["title"], cached["content"]

        prompt = (
            f"请生成一个计算机问答社区问题。\n"
            f"领域：{domain_label}\n"
            f"主题：{topic_title}\n"
            f"提问者：{username}\n\n"
            "要求：\n"
            "1. 标题 20-40 字，带【领域】前缀\n"
            "2. 正文 150-300 字，描述背景、困惑、希望得到的帮助\n"
            "3. 只返回 JSON：{\"title\":\"...\",\"content\":\"...\"}"
        )
        raw = self.chat(
            [
                {
                    "role": "system",
                    "content": "你是计算机学习社区的内容生成助手，只返回合法 JSON。",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
            max_tokens=600,
            label=f"question {domain_label}/{topic_title}",
        )
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if not match:
            raise RuntimeError(f"DeepSeek QA response is not JSON: {raw[:200]}")
        parsed = json.loads(match.group(0))
        title = str(parsed["title"]).strip()
        content = str(parsed["content"]).strip()
        cache_path.write_text(json.dumps({"title": title, "content": content}, ensure_ascii=False, indent=2), encoding="utf-8")
        return title, content

    def generate_answer_thread(
        self,
        *,
        question_title: str,
        question_content: str,
        answer_count: int = 3,
    ) -> list[dict[str, Any]]:
        cache_key = f"{question_title}|{question_content}|{answer_count}|{self.model}|answers"
        cache_path = self._cache_path("answers", cache_key, ".json")
        if cache_path.exists() and not self.refresh_cache:
            cached = json.loads(cache_path.read_text(encoding="utf-8"))
            return cached["answers"]

        prompt = (
            f"请为以下计算机问答社区问题生成 {answer_count} 条高质量回答，每条回答附带讨论线程。\n\n"
            f"问题标题：{question_title}\n"
            f"问题正文：{question_content}\n\n"
            "要求：\n"
            f"1. 恰好 {answer_count} 条回答，每条 120-220 字，中文，专业准确\n"
            "2. 每条回答包含 1-2 条一级评论（comments），每条评论 40-100 字\n"
            "3. 每条评论包含 1-2 条二级回复（replies），每条 30-80 字\n"
            "4. 只返回 JSON："
            '{"answers":[{"content":"...","comments":[{"content":"...","replies":[{"content":"..."}]}]}]}'
        )
        raw = self.chat(
            [
                {
                    "role": "system",
                    "content": "你是计算机学习社区的内容生成助手，只返回合法 JSON。",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
            max_tokens=2800,
            label=f"answers for {question_title[:24]}",
        )
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if not match:
            raise RuntimeError(f"DeepSeek answer response is not JSON: {raw[:200]}")
        parsed = json.loads(match.group(0))
        answers = parsed.get("answers") or []
        if len(answers) < answer_count:
            raise RuntimeError(f"Expected {answer_count} answers, got {len(answers)}")
        answers = answers[:answer_count]
        cache_path.write_text(json.dumps({"answers": answers}, ensure_ascii=False, indent=2), encoding="utf-8")
        return answers

    def generate_note_remark_thread(
        self,
        *,
        note_title: str,
        author_name: str,
        comment_count: int,
    ) -> list[dict[str, Any]]:
        cache_key = f"{note_title}|{author_name}|{comment_count}|{self.model}|remarks"
        cache_path = self._cache_path("remarks", cache_key, ".json")
        if cache_path.exists() and not self.refresh_cache:
            cached = json.loads(cache_path.read_text(encoding="utf-8"))
            return cached["comments"]

        prompt = (
            f"请为一篇计算机学习笔记生成 {comment_count} 条读者评论讨论线程。\n\n"
            f"笔记标题：{note_title}\n"
            f"作者：{author_name}\n\n"
            "要求：\n"
            f"1. 恰好 {comment_count} 条一级评论，每条 30-90 字，中文，像真实读者留言\n"
            "2. 每条一级评论包含 0-2 条回复（replies），每条 20-60 字\n"
            "3. 内容要贴合笔记主题，可提问、补充、讨论或感谢\n"
            "4. 只返回 JSON："
            '{"comments":[{"content":"...","replies":[{"content":"..."}]}]}'
        )
        raw = self.chat(
            [
                {
                    "role": "system",
                    "content": "你是笔记社区评论生成助手，只返回合法 JSON。",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.75,
            max_tokens=1600,
            label=f"remarks for {note_title[:24]}",
        )
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if not match:
            raise RuntimeError(f"DeepSeek remark response is not JSON: {raw[:200]}")
        parsed = json.loads(match.group(0))
        comments = parsed.get("comments") or []
        if len(comments) < comment_count:
            raise RuntimeError(f"Expected {comment_count} comments, got {len(comments)}")
        comments = comments[:comment_count]
        cache_path.write_text(
            json.dumps({"comments": comments}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return comments


def markdown_to_pdf_paragraphs(markdown: str, max_lines: int = 18) -> list[str]:
    lines: list[str] = []
    in_code = False
    for raw in markdown.splitlines():
        if raw.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            continue
        line = re.sub(r"^#+\s*", "", raw).strip()
        line = re.sub(r"[*_`>\-]", "", line).strip()
        if not line:
            continue
        lines.append(line[:90])
        if len(lines) >= max_lines:
            break
    return lines or ["计算机学习笔记"]
