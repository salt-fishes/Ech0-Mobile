#!/usr/bin/env python3
# 演示数据 mock 服务器：仅用于生成 README 截图
import json, time, random
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

NOW = int(time.time())
def ago(h): return NOW - int(h * 3600)

def pic(seed, w, h):
    return {"id": "", "name": f"{seed}.jpg", "key": "", "storage_type": "external",
            "url": f"https://picsum.photos/seed/{seed}/{w}/{h}",
            "content_type": "image/jpeg", "category": "image", "size": 0,
            "width": w, "height": h}

def file_entry(f, sort=0):
    return {"id": "", "echo_id": "", "file_id": "", "sort_order": sort, "file": f}

def echo(id, content, hours, fav=0, tags=(), files=(), layout=None, ext=None, user="saltfish"):
    return {"id": id, "content": content, "username": user,
            "echo_files": [file_entry(f, i) for i, f in enumerate(files)],
            "layout": layout, "private": False, "user_id": "u0",
            "extension": ext, "tags": [{"id": f"t{i}", "name": t, "usage_count": 1, "created_at": NOW} for i, t in enumerate(tags)],
            "fav_count": fav, "created_at": ago(hours)}

LOCAL = [
    echo("e1", "深秋的午后，把博客引擎又重写了一遍。删掉了一半的功能，反而清爽了很多。\n\n> 少即是多，慢即是快。\n\n代码里只剩 `Markdown 渲染` 和 **图片上传** 两个核心模块，剩下的都交给时间。", 0.6, 12, ("碎碎念", "建站")),
    echo("e2", "周末扫街的一组片子，光线刚好。", 3, 8, ("摄影", "日常"),
         files=[pic("ech0a1", 900, 900), pic("ech0a2", 900, 900), pic("ech0a3", 900, 900), pic("ech0a4", 900, 900)], layout="grid"),
    echo("e3", "山里的云海，值得早起两个小时。", 6, 23, ("旅行",),
         files=[pic("ech0b1", 1200, 800)]),
    echo("e4", "把家里的小工作站换成了无风扇主机，世界瞬间安静了。", 20, 5, ("硬件",)),
    echo("e5", "写了一段小脚本，自动把相册里的截图按月份归档：\n\n```kotlin\nval months = photos.groupBy { it.date.monthValue }\nmonths.forEach { (m, list) -> archive(m, list) }\n```\n\n从此告别手动整理。", 44, 15, ("效率", "代码")),
    echo("e6", "环湖骑行的第三天，腿已经不是自己的了，但风景值得。", 70, 31, ("骑行", "旅行"),
         files=[pic("ech0c1", 800, 1000), pic("ech0c2", 800, 800), pic("ech0c3", 1000, 750)], layout="horizontal"),
    echo("e7", "单曲循环一下午。", 96, 6, ("音乐",),
         ext={"type": "MUSIC", "payload": {"url": "https://music.example.com/track/42"}}),
    echo("e8", "给朋友的新站点写了个小工具，顺便开源了。", 120, 9, ("开源",),
         ext={"type": "GITHUBPROJ", "payload": {"repoUrl": "https://github.com/salt-fishes/ech0-mobile"}}),
    echo("e9", "一年前的今天，把这个说说程序部署到了第一台小主机上。当时怎么也想不到它会陪伴我这么久。\n\n谢谢你，也谢谢每一个路过的人。", 24 * 365, 42, ("周年", "碎碎念")),
]

FRIEND_POSTS = [
    echo("f1", "新买的机械键盘到了，茶轴确实安静，适合深夜写代码。", 2, 3, ("数码",), user="wind"),
    echo("f2", "今晚的月亮特别好，随手拍了一张。", 8, 7, ("夜拍",), files=[pic("wind1", 1000, 750)], user="wind"),
    echo("f3", "读完《山月记》，原来我害怕的从来不是变成老虎，而是变成平庸的老虎。", 30, 18, ("读书",), user="wind"),
    echo("f4", "给博客加了个 RSS 全文输出，欢迎订阅。", 52, 4, ("建站",), user="wind"),
    echo("f5", "周末去爬了趟后山，山顶的风把一周的疲惫都吹散了。", 76, 11, ("徒步",), user="wind"),
    echo("f6", "尝试用纯 CSS 复刻了一个玻璃拟态卡片，效果意外地好。", 100, 6, ("前端",), user="wind"),
]

TAGS = ["碎碎念", "摄影", "旅行", "建站", "代码", "音乐", "读书", "骑行", "效率", "开源", "日常", "硬件"]
random.seed(42)
HEAT = [{"date": time.strftime("%Y-%m-%d", time.localtime(NOW - i * 86400)),
         "count": random.choice([0, 0, 1, 2, 2, 3, 3, 4, 5, 6])} for i in range(29, -1, -1)]

def env(data, code=1, msg="ok"):
    return json.dumps({"code": code, "msg": msg, "data": data}, ensure_ascii=False).encode()

SELF = {"server_name": "盐鱼的碎碎念", "server_url": "http://10.0.2.2:8899", "logo": None,
        "total_echos": len(LOCAL), "today_echos": 3, "sys_username": "saltfish", "version": "5.8.2"}
FRIENDS = [{"server_name": "追风小站", "server_url": "http://10.0.2.2:8899", "logo": None,
            "total_echos": 128, "today_echos": 2, "sys_username": "wind", "version": "5.8.2"}]

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _send(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        p = urlparse(self.path).path
        q = parse_qs(urlparse(self.path).query)
        if p == "/api/hello":
            return self._send(env({"hello": "Hello, Ech0! 👋", "copyright": "Copyright (C) 2025-2026 L1nSn0w",
                "version": "5.8.2", "commit": "47d8d34", "build_time": "2026-10-22T06:11:49Z",
                "license": "AGPL-3.0-or-later", "author": "L1nSn0w", "repo_url": "https://github.com/lin-snow/Ech0"}))
        if p == "/api/settings":
            return self._send(env({"site_title": "盐鱼的碎碎念", "server_logo": None,
                "server_name": "盐鱼的碎碎念", "server_url": "http://10.0.2.2:8899",
                "allow_register": False, "footer_content": None, "meting_api": None}))
        if p == "/api/connect":
            return self._send(env(SELF))
        if p == "/api/connects/info":
            return self._send(env(FRIENDS))
        if p == "/api/echo/today":
            return self._send(env(LOCAL[:3]))
        if p == "/api/echo/hot":
            limit = int(q.get("limit", ["5"])[0])
            return self._send(env(sorted(LOCAL, key=lambda e: -e["fav_count"])[:limit]))
        if p == "/api/echo/onthisday":
            return self._send(env([LOCAL[-1]]))
        if p == "/api/echo/random":
            return self._send(env(LOCAL[3]))
        if p == "/api/tags":
            return self._send(env([{"id": f"tag{i}", "name": n, "usage_count": 12 - i, "created_at": NOW} for i, n in enumerate(TAGS)]))
        if p == "/api/heatmap":
            return self._send(env(HEAT))
        return self._send(env(None, 0, "not found"))
    def do_POST(self):
        p = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        if p == "/api/echo/query":
            return self._send(env({"items": LOCAL, "total": len(LOCAL)}))
        if p == "/api/echo/page":
            return self._send(env({"items": FRIEND_POSTS, "total": 128}))
        return self._send(env(None, 0, "not found"))

if __name__ == "__main__":
    print("mock ech0 server on :8899")
    ThreadingHTTPServer(("0.0.0.0", 8899), H).serve_forever()
