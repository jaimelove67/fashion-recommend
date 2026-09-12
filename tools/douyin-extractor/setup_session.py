"""User-assisted session setup. No automatic CAPTCHA solving."""
import argparse
import asyncio
import json
import os
import time
from pathlib import Path

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parent


async def setup(profile: Path, output: Path, wait: int | None = None):
    profile.mkdir(parents=True, exist_ok=True, mode=0o700)
    async with async_playwright() as p:
        options = {}
        if proxy := os.environ.get("DOUYIN_PROXY"):
            options["proxy"] = {"server": proxy}
        context = await p.chromium.launch_persistent_context(str(profile), channel="chromium", headless=False,
                                                            locale="zh-CN", **options)
        try:
            page = context.pages[0] if context.pages else await context.new_page()
            try:
                await page.goto("https://www.douyin.com/", wait_until="domcontentloaded", timeout=30000)
            except Exception:
                print("页面加载较慢，请在浏览器内确认页面已打开。", flush=True)
            if wait is None:
                await asyncio.to_thread(input, "如出现登录或验证，请手动完成。页面正常后按回车保存会话：")
            else:
                await asyncio.sleep(wait)
            info = await page.evaluate("""() => ({user_agent: navigator.userAgent, platform: navigator.platform,
                screen_width: screen.width, screen_height: screen.height, language: navigator.language,
                cpu_core_num: navigator.hardwareConcurrency, device_memory: navigator.deviceMemory || 8})""")
            info["cookies"] = [c for c in await context.cookies() if c["domain"].lstrip(".") == "douyin.com"
                               or c["domain"].lstrip(".").endswith(".douyin.com")]
            info["saved_at"] = int(time.time())
            from vendor.dtk_signing.websign import pick_uifid
            if not pick_uifid({c["name"]: c["value"] for c in info["cookies"]}):
                raise SystemExit("未取得必要的访客参数；未覆盖旧会话。请确认页面可正常访问后重试。")
            output.parent.mkdir(parents=True, exist_ok=True)
            temp = output.with_suffix(".tmp")
            with os.fdopen(os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as f:
                json.dump(info, f, ensure_ascii=False)
            temp.chmod(0o600)
            temp.replace(output)
            print(f"会话已保存：{output}（仅本地保存，不要上传此文件）", flush=True)
        finally:
            await context.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", type=Path, default=ROOT / ".api-profile")
    parser.add_argument("--output", type=Path, default=ROOT / ".api-session.json")
    parser.add_argument("--wait", type=int, help="仅用于已有会话的本地实测：等待若干秒后保存，不处理验证")
    args = parser.parse_args()
    asyncio.run(setup(args.profile.resolve(), args.output.resolve(), args.wait))
