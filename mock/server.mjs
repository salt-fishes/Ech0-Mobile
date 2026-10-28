import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 8899;
const now = () => Math.floor(Date.now() / 1000);

const likedIds = new Set();
const tags = [
  { id: 't1', name: '碎碎念', usage_count: 4, created_at: now() - 86400 * 30 },
  { id: 't2', name: '建站', usage_count: 2, created_at: now() - 86400 * 20 },
  { id: 't3', name: '摄影', usage_count: 3, created_at: now() - 86400 * 10 },
  { id: 't4', name: '骑行', usage_count: 1, created_at: now() - 86400 * 8 },
  { id: 't5', name: '代码', usage_count: 1, created_at: now() - 86400 * 4 }
];
const tag = (id) => tags.find((t) => t.id === id);

const file = (name, w = 1200, h = 900) => ({
  id: 'f-' + name,
  name,
  key: name,
  storage_type: 'local',
  url: '/api/files/' + name,
  content_type: 'image/jpeg',
  category: 'image',
  size: 220000,
  width: w,
  height: h,
  user_id: 'u1',
  created_at: now() - 86400 * 2
});

const echo = (id, content, minutesAgo, extra = {}) => ({
  id,
  content,
  username: 'saltfish',
  echo_files: [],
  layout: 'waterfall',
  private: false,
  user_id: 'u1',
  extension: null,
  tags: [],
  fav_count: 0,
  created_at: now() - minutesAgo * 60,
  ...extra
});

const posts = [
  echo(
    'e1',
    '深秋的午后，把博客引擎又重写了一遍。删掉了一半的功能，反而清爽了很多。\n\n> 少即是多，慢即是快。\n\n代码里只剩 `Markdown 渲染` 和 **图片上传** 两个核心模块，剩下的都交给时间。',
    38,
    { tags: [tag('t1'), tag('t2')], fav_count: 12 }
  ),
  echo('e2', '周末扫街的一组片子，光线刚好。', 3 * 60, {
    echo_files: [
      { id: 'ef1', echo_id: 'e2', file_id: 'f-street.jpg', sort_order: 0, file: file('street.jpg') },
      { id: 'ef2', echo_id: 'e2', file_id: 'f-mountain.jpg', sort_order: 1, file: file('mountain.jpg') }
    ],
    layout: 'waterfall',
    tags: [tag('t3')],
    fav_count: 8
  }),
  echo('e3', '山里的云海，值得早起两个小时。', 6 * 60, {
    echo_files: [
      { id: 'ef3', echo_id: 'e3', file_id: 'f-clouds.jpg', sort_order: 0, file: file('clouds.jpg', 1200, 800) }
    ],
    layout: 'waterfall',
    tags: [tag('t3')],
    fav_count: 5
  }),
  echo(
    'e4',
    '一年前的今天，把这个说说程序部署到了第一台小主机上。当时怎么也想不到它会陪伴我这么久。',
    365 * 24 * 60,
    { tags: [tag('t2')], fav_count: 21 }
  ),
  echo('e5', '环湖骑行的第三天，腿已经不是自己的了，但风景值得。', 2 * 24 * 60, {
    tags: [tag('t4')],
    fav_count: 9
  }),
  echo(
    'e6',
    '写了一段小脚本，自动把相册里的截图按月份归档：\n\n```bash\nfind ~/Pictures -name "*.png" | sort\n```\n\n终于不用手动整理了。',
    26 * 60,
    { tags: [tag('t5')], fav_count: 6 }
  )
];

const remotePosts = [
  echo('r1', '新买的机械键盘到了，茶轴确实安静，适合深夜写代码。', 2 * 60, {
    username: 'wind',
    echo_files: [
      { id: 'er1', echo_id: 'r1', file_id: 'f-street.jpg', sort_order: 0, file: file('street.jpg') }
    ],
    fav_count: 3
  }),
  echo('r2', '今晚的月亮很亮，适合散步。', 8 * 60, { username: 'wind', fav_count: 1 })
];
// 模拟远端分页：30 条，用来验证 Hub 上滑加载更多
for (let i = 3; i <= 30; i++) {
  remotePosts.push(
    echo(`r${i}`, `远端往期动态 #${i}：把日子过慢一点，记录也会多一点。`, i * 3600, {
      username: 'wind',
      fav_count: i % 5
    })
  );
}

const comments = [
  {
    id: 'c1',
    echo_id: 'e1',
    parent_id: null,
    nickname: '阿岚',
    website: null,
    content: '把复杂留给系统，把简单留给自己。',
    status: 'approved',
    hot: false,
    source: null,
    created_at: now() - 2 * 3600,
    updated_at: now() - 2 * 3600
  },
  {
    id: 'c2',
    echo_id: 'e1',
    parent_id: null,
    nickname: 'wind',
    website: null,
    content: '这个引用很喜欢。',
    status: 'approved',
    hot: true,
    source: null,
    created_at: now() - 3600,
    updated_at: now() - 3600
  }
];

const ok = (data) => ({ code: 1, msg: '', data, error_code: null, message_key: null });
const fail = (msg, code = 'MOCK_ERROR') => ({ code: 0, msg, data: null, error_code: code, message_key: null });

const json = (res, value, status = 200) => {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body)
  });
  res.end(body);
};

const readBody = (req) => new Promise((resolve) => {
  let body = '';
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', () => {
    try { resolve(body ? JSON.parse(body) : {}); } catch { resolve({}); }
  });
});

const filterPosts = (list, body) => {
  let result = list;
  if (body.search) {
    const q = String(body.search).toLowerCase();
    result = result.filter((p) => p.content.toLowerCase().includes(q));
  }
  if (Array.isArray(body.tagIds) && body.tagIds.length) {
    result = result.filter((p) => p.tags.some((t) => body.tagIds.includes(t.id)));
  }
  return result;
};

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const p = url.pathname;
  console.log(new Date().toISOString(), req.method, p);

  try {
    if (p.startsWith('/api/files/')) {
      const name = decodeURIComponent(p.slice('/api/files/'.length));
      const filePath = path.join(__dirname, 'files', name);
      if (!fs.existsSync(filePath)) {
        res.writeHead(404); res.end('not found'); return;
      }
      const ext = path.extname(filePath).toLowerCase();
      const type = ext === '.png' ? 'image/png' : 'image/jpeg';
      const buf = fs.readFileSync(filePath);
      res.writeHead(200, { 'content-type': type, 'content-length': buf.length });
      res.end(buf);
      return;
    }

    if (p === '/api/hello') return json(res, ok({
      hello: 'world',
      version: '5.8.2',
      commit: '47d8d34f00d7e4c2b0f5a0d1f9e0c1b2a3d4e5f6',
      license: 'AGPL-3.0-or-later',
      author: 'L1nSn0w',
      repo_url: 'https://github.com/lin-snow/Ech0'
    }));

    if (p === '/api/settings') return json(res, ok({
      site_title: '盐鱼的碎碎念',
      server_logo: null,
      server_name: '盐鱼的碎碎念',
      server_url: 'http://10.0.2.2:8899',
      allow_register: false,
      footer_content: null,
      meting_api: null
    }));

    if (p === '/api/init/status') return json(res, ok({ initialized: true, owner_exists: true }));

    if (p === '/api/login' && req.method === 'POST') {
      return json(res, ok({ access_token: 'mock-access-token', expires_in: 86400 }));
    }
    if (p === '/api/auth/refresh') return json(res, ok({ access_token: 'mock-access-token', expires_in: 86400 }));
    if (p === '/api/auth/logout') return json(res, ok(null));
    if (p === '/api/user') return json(res, ok({
      id: 'u1', username: 'saltfish', email: null, is_admin: true, is_owner: true,
      avatar: null, locale: 'zh-CN'
    }));

    if (p === '/api/echo/query' && req.method === 'POST') {
      const body = await readBody(req);
      const filtered = filterPosts(posts, body).sort((a, b) => b.created_at - a.created_at);
      const page = Number(body.page || 1);
      const pageSize = Number(body.pageSize || 20);
      const start = (page - 1) * pageSize;
      return json(res, ok({ items: filtered.slice(start, start + pageSize), total: filtered.length }));
    }

    if (p === '/api/echo/page' && req.method === 'POST') {
      const body = await readBody(req);
      const page = Number(body.page || 1);
      const pageSize = Number(body.pageSize || 10);
      const start = (page - 1) * pageSize;
      return json(res, ok({ items: remotePosts.slice(start, start + pageSize), total: remotePosts.length }));
    }

    if (p === '/remote/api/echo/page' && req.method === 'POST') {
      const body = await readBody(req);
      const page = Number(body.page || 1);
      const pageSize = Number(body.pageSize || 10);
      const start = (page - 1) * pageSize;
      return json(res, ok({ items: remotePosts.slice(start, start + pageSize), total: remotePosts.length }));
    }
    if (p.startsWith('/remote/api/echo/') && req.method === 'GET') {
      const id = p.split('/').pop();
      const post = remotePosts.find((e) => e.id === id);
      return post ? json(res, ok(post)) : json(res, fail('说说不存在', 'NOT_FOUND'), 404);
    }
    if (p === '/remote/api/comments' && req.method === 'GET') {
      return json(res, ok([]));
    }

    if (p === '/api/echo/today') {
      const cutoff = now() - 86400;
      return json(res, ok(posts.filter((e) => e.created_at >= cutoff)));
    }
    if (p === '/api/echo/hot') {
      const limit = Number(url.searchParams.get('limit') || 5);
      return json(res, ok([...posts].sort((a, b) => b.fav_count - a.fav_count).slice(0, limit)));
    }
    if (p === '/api/echo/onthisday') return json(res, ok([posts[3]]));
    if (p === '/api/echo/random') return json(res, ok(posts[Math.floor(Math.random() * posts.length)]));

    if (p.startsWith('/api/echo/like/') && req.method === 'PUT') {
      const id = p.split('/').pop();
      const post = posts.find((e) => e.id === id);
      // 服务端点赞是幂等的：同一客户端重复 PUT 不重复计数
      if (post && !likedIds.has(id)) {
        post.fav_count += 1;
        likedIds.add(id);
      }
      return json(res, ok(null));
    }

    if (p.startsWith('/api/echo/') && req.method === 'GET') {
      const id = p.split('/').pop();
      const post = posts.find((e) => e.id === id) || remotePosts.find((e) => e.id === id);
      if (!post) return json(res, fail('说说不存在', 'NOT_FOUND'), 404);
      return json(res, ok(post));
    }

    if (p === '/api/echo' && req.method === 'POST') {
      const body = await readBody(req);
      const id = 'e' + (posts.length + 1);
      const bodyTags = (body.tags || []).map((t) => tag(t.id) || { id: 't' + t.name, name: t.name, usage_count: 1, created_at: now() }).filter(Boolean);
      posts.unshift(echo(id, body.content || '', 0, {
        tags: bodyTags,
        fav_count: 0,
        layout: body.layout || 'waterfall',
        private: !!body.private
      }));
      return json(res, ok(null));
    }
    if (p === '/api/echo' && req.method === 'PUT') {
      const body = await readBody(req);
      const post = posts.find((e) => e.id === body.id);
      if (post) {
        post.content = body.content ?? post.content;
        post.private = !!body.private;
        post.layout = body.layout || post.layout;
        post.created_at = body.created_at ?? post.created_at;
        post.tags = (body.tags || [])
          .map((t) => tag(t.id) || { id: 't' + t.name, name: t.name, usage_count: 1, created_at: now() })
          .filter(Boolean);
      }
      return json(res, ok(null));
    }
    if (p.startsWith('/api/echo/') && req.method === 'DELETE') return json(res, ok(null));

    if (p === '/api/tags') return json(res, ok(tags));
    if (p === '/api/heatmap') {
      const today = new Date();
      const days = [];
      for (let i = 29; i >= 0; i--) {
        const d = new Date(today.getTime() - i * 86400000);
        const date = d.toISOString().slice(0, 10);
        const count = i % 7 === 0 ? 0 : (i % 5) + 1;
        days.push({ date, count });
      }
      return json(res, ok(days));
    }

    if (p === '/api/connect') return json(res, ok({
      server_name: '盐鱼的碎碎念',
      server_url: 'http://10.0.2.2:8899',
      logo: null,
      total_echos: 9,
      today_echos: 3,
      sys_username: 'saltfish',
      version: '5.8.2'
    }));

    if (p === '/api/connects/info') return json(res, ok([{
      server_name: '追风小站',
      server_url: 'http://10.0.2.2:8899/remote',
      logo: null,
      total_echos: 128,
      today_echos: 2,
      sys_username: 'wind',
      version: '5.8.2'
    }]));

    if (p === '/api/comments/form') return json(res, ok({
      form_token: 'mock-form-token',
      min_submit_ms: 0,
      captcha_enabled: false,
      captcha_api_endpoint: null,
      enable_comment: true
    }));
    if (p === '/api/comments' && req.method === 'GET') {
      const echoId = url.searchParams.get('echo_id');
      return json(res, ok(comments.filter((c) => c.echo_id === echoId)));
    }
    if (p === '/api/comments' && req.method === 'POST') {
      const body = await readBody(req);
      const c = {
        id: 'c' + (comments.length + 1),
        echo_id: body.echo_id,
        parent_id: body.parent_id || null,
        nickname: body.nickname || '游客',
        website: body.website || null,
        content: body.content || '',
        status: 'approved',
        hot: false,
        source: null,
        created_at: now(),
        updated_at: now()
      };
      comments.push(c);
      return json(res, ok({ id: c.id, status: 'approved' }));
    }

    if (p === '/api/files/upload' && req.method === 'POST') {
      return json(res, ok({
        id: 'f-upload',
        name: 'upload.jpg',
        key: 'upload.jpg',
        storage_type: 'local',
        url: '/api/files/street.jpg',
        content_type: 'image/jpeg',
        category: 'image',
        size: 100000,
        width: 1200,
        height: 900,
        user_id: 'u1',
        created_at: now()
      }));
    }
    if (p === '/api/files/external' && req.method === 'POST') {
      const body = await readBody(req);
      return json(res, ok({
        id: 'f-external', name: 'external', key: 'external',
        storage_type: 'external', url: body.url || '', content_type: 'image/jpeg',
        category: 'image', size: 0, width: 1200, height: 900, user_id: 'u1', created_at: now()
      }));
    }

    return json(res, fail('mock endpoint not found: ' + p, 'NOT_FOUND'), 404);
  } catch (e) {
    console.error(e);
    return json(res, fail(e.message || 'mock error', 'MOCK_EXCEPTION'), 500);
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock ech0 server listening on http://0.0.0.0:${PORT}`);
});
