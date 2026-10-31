import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 8899;
const HOST = `http://10.0.2.2:${PORT}`;
// 演示用「真实域名」：截图时设置 ECH0_PUBLIC_HOST=http://echo.saltfish.top
// 并在模拟器里把该域名转发到 10.0.2.2:8899；不设置时回退到 10.0.2.2，保证开箱可跑。
const PUBLIC_HOST = (process.env.ECH0_PUBLIC_HOST || HOST).replace(/\/$/, '');
const now = () => Math.floor(Date.now() / 1000);
const likedIds = new Set();

// ---------- 标签 ----------
const tagDefs = [
  ['t1', '碎碎念'], ['t2', '建站'], ['t3', '摄影'], ['t4', '骑行'],
  ['t5', '代码'], ['t6', '音乐'], ['t7', '山野'], ['t8', '日常'],
  ['t9', '旅行'], ['t10', '阅读'], ['t11', '城市漫游'], ['t12', '开源']
];
const tags = tagDefs.map(([id, name], i) => ({
  id, name, usage_count: 0, created_at: now() - 86400 * (90 - i * 3)
}));
const tagByName = Object.fromEntries(tags.map((t) => [t.name, t]));
const tag = (id) => tags.find((t) => t.id === id);

// ---------- 图片尺寸 ----------
const dims = {
  'street.jpg': [1200, 900], 'mountain.jpg': [1200, 900], 'clouds.jpg': [1200, 900],
  'autumn.jpg': [1200, 800], 'bike.jpg': [1200, 800], 'books.jpg': [1200, 775],
  'camera.jpg': [1200, 1500], 'city.jpg': [1200, 800], 'coffee.jpg': [1200, 800],
  'desk.jpg': [1200, 799], 'food.jpg': [1200, 800], 'forest.jpg': [1200, 799],
  'lake.jpg': [1200, 800], 'mountain2.jpg': [1200, 800], 'music.jpg': [1200, 800],
  'night.jpg': [1200, 1501], 'rain.jpg': [1200, 1800], 'sea.jpg': [1200, 798],
  'stars.jpg': [1200, 801], 'travel.jpg': [1200, 800], 'workspace.jpg': [1200, 801]
};

const fileSize = (name) => {
  try { return fs.statSync(path.join(__dirname, 'files', name)).size; } catch { return 120000; }
};

const fileDto = (name) => {
  const [w, h] = dims[name] || [1200, 800];
  return {
    id: 'f-' + name,
    name,
    key: name,
    storage_type: 'local',
    url: `${PUBLIC_HOST}/api/files/${name}`,
    content_type: 'image/jpeg',
    category: 'image',
    size: fileSize(name),
    width: w,
    height: h,
    user_id: 'u1',
    created_at: now() - 86400 * 3
  };
};

const ext = (type, payload) => ({ type, payload });

// ---------- 说说数据 ----------
// age 单位为分钟；每次请求时换算成 created_at，保证「几小时前 / 昨天」始终自然。
const postSpecs = [
  {
    id: 'e01', age: 25, fav: 28,
    content: '周末的城市漫游。\n\n一杯手冲、一场小雨，还有一条从没走过的街。',
    images: ['city.jpg', 'coffee.jpg'], layout: 'grid',
    tags: ['城市漫游', '摄影']
  },
  {
    id: 'e02', age: 70, fav: 19,
    content: '循环了一下午的老歌，适合配着雨声写代码。\n\n雨落在窗台上，像给屏幕加了一层柔光滤镜。',
    extension: ext('MUSIC', { url: 'https://music.163.com/song?id=1974443814' }),
    tags: ['音乐', '代码']
  },
  {
    id: 'e03', age: 130, fav: 42,
    content: '把去年骑行川西的素材剪成了三分钟。\n\n山口的风格外大，镜头晃得厉害，但每次回看还是会想起那天的云。',
    extension: ext('VIDEO', { videoId: 'BV1GJ411x7h7' }),
    tags: ['骑行', '山野']
  },
  {
    id: 'e04', age: 240, fav: 37,
    content: '## 把博客引擎又重写了一遍\n\n删掉了一半的功能，反而清爽了很多。\n\n- 只保留 Markdown 渲染和图片上传\n- 去掉评论区的嵌套层级\n- 把主题色交给用户\n\n> 少即是多，慢即是快。\n\n只保留 `Markdown 渲染` 和 `图片上传` 两个核心模块，剩下的都交给时间。',
    tags: ['建站', '代码']
  },
  {
    id: 'e05', age: 300, fav: 35,
    content: '给 Ech0 提了个小 PR，顺手把客户端的暗色模式又打磨了一遍。\n\n开源的乐趣大概就是：你用别人的轮子，也把自己的轮子递出去。',
    extension: ext('GITHUBPROJ', { repoUrl: 'https://github.com/lin-snow/Ech0' }),
    tags: ['开源', '代码']
  },
  {
    id: 'e06', age: 26 * 60, fav: 56,
    content: '山里住了两晚，没有信号，只有风和星星。\n\n回来之后，手机里多了 400 张照片，和一点久违的安静。',
    images: ['forest.jpg', 'lake.jpg', 'mountain2.jpg', 'stars.jpg'], layout: 'waterfall',
    tags: ['山野', '摄影']
  },
  {
    id: 'e07', age: 30 * 60, fav: 31,
    content: '九月的一些碎片。\n\n把日子过慢一点，记录也会多一点。',
    images: ['autumn.jpg', 'books.jpg', 'camera.jpg', 'coffee.jpg', 'food.jpg', 'rain.jpg', 'sea.jpg', 'travel.jpg', 'workspace.jpg'],
    layout: 'grid', tags: ['日常', '摄影']
  },
  {
    id: 'e08', age: 2 * 24 * 60, fav: 18,
    content: '新写的一篇长文：从 WordPress 到静态博客，我做了一次彻底的减法。\n\n现在整站只有一个二进制和一堆 Markdown，备份就是打包一个文件夹。',
    extension: ext('WEBSITE', {
      site: 'https://echo.saltfish.top',
      title: '从 WordPress 到静态博客：一次彻底的减法'
    }),
    tags: ['建站', '阅读']
  },
  {
    id: 'e09', age: 3 * 24 * 60, fav: 44,
    content: '在洱海边坐了一下午。\n\n风把云推得很慢，慢到可以看完一整场日落。',
    images: ['sea.jpg'], layout: 'grid',
    extension: ext('LOCATION', { placeholder: '洱海 · 才村码头', latitude: '25.693', longitude: '100.164' }),
    tags: ['旅行', '摄影']
  },
  {
    id: 'e10', age: 4 * 24 * 60, fav: 22,
    content: '看到一句话，很适合今天：\n\n「不要把时间花在解释自己上，去把生活过成答案。」',
    extension: ext('TWEET', {
      url: 'https://x.com/saltfish/status/1712345678901234567',
      username: 'saltfish', statusId: '1712345678901234567'
    }),
    tags: ['日常']
  },
  {
    id: 'e11', age: 5 * 24 * 60, fav: 3, private: true,
    content: '一些还没想好要不要公开的碎碎念。\n\n先放在这里，等哪天想明白了再打开。',
    tags: ['碎碎念']
  },
  {
    id: 'e12', age: 6 * 24 * 60, fav: 26,
    content: '最近在做一件很笨的事：把过去五年的照片按年份重新整理。\n\n硬盘里有 3 个备份，手机里 2 个相册，云端还有一堆重复文件。真正开始整理才发现，最难的从来不是技术，而是决定哪些东西值得留下。\n\n> 记忆不是仓库，是一遍又一遍的挑选。\n\n删掉 40% 的截图之后，剩下的每一张都开始说话。',
    tags: ['日常', '摄影']
  },
  {
    id: 'e13', age: 8 * 24 * 60, fav: 17,
    content: '夜里十一点的便利店，和刚下过雨的路。',
    images: ['night.jpg', 'city.jpg', 'rain.jpg'], layout: 'grid',
    tags: ['城市漫游', '摄影']
  },
  {
    id: 'e14', age: 10 * 24 * 60, fav: 12,
    content: '最近单曲循环的一首，适合在回家的地铁上听。',
    extension: ext('MUSIC', { url: 'https://music.163.com/song?id=1824045033' }),
    tags: ['音乐']
  },
  {
    id: 'e15', age: 12 * 24 * 60, fav: 24,
    content: '秋天适合骑车。\n\n从城西到城东，穿过整条梧桐大道，耳机里刚好放到最喜欢的副歌。',
    images: ['bike.jpg', 'autumn.jpg'], layout: 'grid',
    tags: ['骑行', '日常']
  },
  {
    id: 'e16', age: 15 * 24 * 60, fav: 9,
    content: '把用了三年的旧键盘洗干净了。\n\n键帽拆下来泡在温水里，晾干，再一颗颗装回去。打字的声音好像也变轻了一点。',
    tags: ['日常']
  },
  {
    id: 'e17', age: 20 * 24 * 60, fav: 15,
    content: '这周读完的三本书：\n\n1. 《禅与摩托车维修艺术》\n2. 《设计中的设计》\n3. 《献给阿尔吉侬的花束》\n\n最喜欢第二本的留白。',
    images: ['books.jpg'], layout: 'grid',
    tags: ['阅读']
  },
  {
    id: 'e18', age: 28 * 24 * 60, fav: 21,
    content: '最近的饭、咖啡和下班路上。',
    images: ['food.jpg', 'coffee.jpg', 'city.jpg', 'rain.jpg', 'camera.jpg', 'night.jpg'],
    layout: 'grid', tags: ['日常', '摄影']
  },
  {
    id: 'e19', age: 35 * 24 * 60, fav: 33,
    content: '九月计划：早睡、跑步、少刷手机。\n\n目前完成度：0%。',
    tags: ['碎碎念']
  },
  {
    id: 'e20', age: 45 * 24 * 60, fav: 48,
    content: '早上六点的山，云海在脚下慢慢散开。',
    images: ['mountain2.jpg'], layout: 'grid',
    tags: ['山野', '摄影']
  },
  {
    id: 'e21', age: 60 * 24 * 60, fav: 27,
    content: '把旧博客的 300 篇文章迁移完了。\n\n有些文字现在看很幼稚，但删掉又可惜，就留着吧。它们都是来时的路。',
    tags: ['建站']
  },
  {
    id: 'e22', age: 75 * 24 * 60, fav: 14,
    content: '新的工作角落。\n\n一张桌子、一盏灯、一台旧 Mac，够了。',
    images: ['workspace.jpg'], layout: 'grid',
    tags: ['日常', '代码']
  },
  {
    id: 'e23', age: 95 * 24 * 60, fav: 11,
    content: '夏天结束了。\n\n冰箱里还剩半盒冰淇淋，和没看完的剧。',
    tags: ['碎碎念']
  },
  {
    id: 'e24', age: 120 * 24 * 60, fav: 38,
    content: '去年今天的泸沽湖。',
    images: ['travel.jpg'], layout: 'grid',
    tags: ['旅行']
  },
  {
    id: 'e25', age: 365 * 24 * 60, fav: 66,
    content: '一年前的今天，把 Ech0 部署到了第一台小主机上。\n\n当时怎么也想不到，它会陪我记录这么多日子。',
    tags: ['建站']
  },
  {
    id: 'e26', age: 400 * 24 * 60, fav: 5,
    content: '开始用 Ech0 的第一天。\n\n希望这里能装下一些不想发在朋友圈的话。',
    tags: ['碎碎念']
  }
];

const makeEcho = (spec, base = HOST) => ({
  id: spec.id,
  content: spec.content || '',
  username: spec.username || 'saltfish',
  echo_files: (spec.images || []).map((name, i) => ({
    id: `${spec.id}-f${i}`,
    echo_id: spec.id,
    file_id: 'f-' + name,
    sort_order: i,
    file: fileDto(name)
  })),
  layout: spec.layout || 'waterfall',
  private: !!spec.private,
  user_id: spec.user_id || 'u1',
  extension: spec.extension || null,
  tags: (spec.tags || []).map((name) => tagByName[name]).filter(Boolean),
  fav_count: spec.fav || 0,
  created_at: now() - (spec.age || 0) * 60
});

// 文件 URL 必须指向宿主根路径（remote 前缀只用于 API）
const posts = () => postSpecs.map((s) => makeEcho(s));

// ---------- 友链数据 ----------
const remote1Specs = [
  {
    id: 'r1-1', age: 2 * 60, fav: 9, username: 'wind',
    content: '新买的机械键盘到了，茶轴确实安静，适合深夜写代码。',
    images: ['desk.jpg'], layout: 'grid', tags: ['日常', '代码']
  },
  {
    id: 'r1-2', age: 7 * 60, fav: 3, username: 'wind',
    content: '今晚的月亮很亮，适合散步。'
  },
  {
    id: 'r1-3', age: 26 * 60, fav: 5, username: 'wind',
    content: '把博客的 RSS 修好了，欢迎订阅。',
    extension: ext('WEBSITE', { site: 'https://wind.example.com', title: '追风小站 · RSS' })
  },
  {
    id: 'r1-4', age: 3 * 24 * 60, fav: 7, username: 'wind',
    content: '周末给自己做了一顿饭，卖相一般，味道还行。',
    images: ['food.jpg'], layout: 'grid', tags: ['日常']
  },
  {
    id: 'r1-5', age: 6 * 24 * 60, fav: 2, username: 'wind',
    content: '城市里的晚霞，永远拍不腻。',
    images: ['city.jpg'], layout: 'grid', tags: ['摄影']
  }
];
const remote2Specs = [
  {
    id: 'r2-1', age: 3 * 60, fav: 12, username: 'moss',
    content: '山里的桂花开了，整条路都是甜的。',
    images: ['autumn.jpg'], layout: 'grid', tags: ['山野', '日常']
  },
  {
    id: 'r2-2', age: 28 * 60, fav: 4, username: 'moss',
    content: '今天把阳台收拾出来，种了两盆薄荷。'
  },
  {
    id: 'r2-3', age: 4 * 24 * 60, fav: 6, username: 'moss',
    content: '雨停之后，山里的雾慢慢升起来。',
    images: ['forest.jpg'], layout: 'grid', tags: ['山野', '摄影']
  },
  {
    id: 'r2-4', age: 9 * 24 * 60, fav: 1, username: 'moss',
    content: '最近在重读《瓦尔登湖》。'
  }
];

const remote1 = () => remote1Specs.map((s) => makeEcho(s));
const remote2 = () => remote2Specs.map((s) => makeEcho(s));

// ---------- 评论 ----------
const commentSpecs = [
  { id: 'c1', echo_id: 'e01', parent_id: null, nickname: '阿岚', content: '这组颜色太舒服了，是胶片拍的吗？', age: 40, hot: true },
  { id: 'c2', echo_id: 'e01', parent_id: 'c1', nickname: 'saltfish', content: '富士 X100V，经典负片模式。', age: 32 },
  { id: 'c3', echo_id: 'e01', parent_id: null, nickname: 'wind', content: '第二张的咖啡店在哪？下次去坐坐。', age: 18 },
  { id: 'c4', echo_id: 'e01', parent_id: null, nickname: 'moss', content: '喜欢这种不赶时间的记录。', age: 9 },
  { id: 'c5', echo_id: 'e04', parent_id: null, nickname: '阿岚', content: '少即是多，同意。', age: 120 },
  { id: 'c6', echo_id: 'e04', parent_id: 'c5', nickname: 'saltfish', content: '删功能比加功能难多了。', age: 110 },
  { id: 'c7', echo_id: 'e06', parent_id: null, nickname: 'moss', content: '云海太美了，是哪座山？', age: 300 },
  { id: 'c8', echo_id: 'e06', parent_id: 'c7', nickname: 'saltfish', content: '四姑娘山，早上六点的垭口。', age: 280 }
];

const comments = () => commentSpecs.map((c) => ({
  id: c.id, echo_id: c.echo_id, parent_id: c.parent_id,
  nickname: c.nickname, website: null, content: c.content,
  status: 'approved', hot: !!c.hot, source: null,
  created_at: now() - c.age * 60, updated_at: now() - c.age * 60
}));

// ---------- 工具 ----------
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

const localDate = (d) => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
};

const heatmap = () => {
  const pattern = [1, 0, 2, 3, 0, 1, 4, 2, 1, 0, 3, 5, 2, 1, 4, 6, 2, 0, 1, 3, 2, 4, 0, 2, 5, 3, 1, 2, 4, 5];
  const today = new Date();
  return pattern.map((count, index) => {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (29 - index));
    return { date: localDate(d), count };
  });
};

const filterPosts = (list, body) => {
  let result = list;
  if (body.search) {
    const q = String(body.search).toLowerCase();
    result = result.filter((p) => p.content.toLowerCase().includes(q));
  }
  if (Array.isArray(body.tagIds) && body.tagIds.length) {
    result = result.filter((p) => p.tags.some((t) => body.tagIds.includes(t.id)));
  }
  if (body.dateFrom) result = result.filter((p) => p.created_at >= Number(body.dateFrom));
  if (body.dateTo) result = result.filter((p) => p.created_at <= Number(body.dateTo));
  const sortBy = body.sortBy || 'created_at';
  const dir = body.sortOrder === 'asc' ? 1 : -1;
  return [...result].sort((a, b) => (Number(a[sortBy] || 0) - Number(b[sortBy] || 0)) * dir);
};

// ---------- 服务器 ----------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const p = url.pathname;
  console.log(new Date().toISOString(), req.method, p);

  try {
    // 静态图片：/api/files/x 与 /remoteN/api/files/x 都回源到本地 files
    const fileMatch = p.match(/^(?:\/remote[12])?\/api\/files\/(.+)$/);
    if (fileMatch && !/\/api\/files\/(upload|external|presign)$/.test(p)) {
      const name = decodeURIComponent(fileMatch[1]);
      const filePath = path.join(__dirname, 'files', name);
      if (!fs.existsSync(filePath)) {
        res.writeHead(404); res.end('not found'); return;
      }
      const ext = path.extname(filePath).toLowerCase();
      const type = ext === '.png' ? 'image/png'
        : ext === '.webp' ? 'image/webp'
        : ext === '.wav' ? 'audio/wav'
        : 'image/jpeg';
      const buf = fs.readFileSync(filePath);
      res.writeHead(200, { 'content-type': type, 'content-length': buf.length, 'cache-control': 'public,max-age=86400' });
      res.end(buf);
      return;
    }

    if (p === '/api/hello') return json(res, ok({
      hello: 'world', version: '5.8.2',
      commit: '47d8d34f00d7e4c2b0f5a0d1f9e0c1b2a3d4e5f6',
      license: 'AGPL-3.0-or-later', author: 'L1nSn0w',
      repo_url: 'https://github.com/lin-snow/Ech0'
    }));

    if (p === '/api/settings') return json(res, ok({
      site_title: '盐鱼的碎碎念',
      server_logo: null,
      server_name: '盐鱼的碎碎念',
      server_url: HOST,
      allow_register: false,
      footer_content: null,
      meting_api: `${HOST}/meting/api`
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

    // meting mock：音乐卡显示本地封面、曲名与可播放音频
    if (p === '/meting/api') {
      return json(res, [{
        name: '晚风', artist: '陈婧霏',
        url: `${PUBLIC_HOST}/api/files/silence.wav`, lrc: '',
        pic: `${PUBLIC_HOST}/api/files/music.jpg`
      }]);
    }

    // ---------- 本站时间线 ----------
    if (p === '/api/echo/query' && req.method === 'POST') {
      const body = await readBody(req);
      const filtered = filterPosts(posts(), body);
      const page = Number(body.page || 1);
      const pageSize = Number(body.pageSize || 20);
      const start = (page - 1) * pageSize;
      return json(res, ok({ items: filtered.slice(start, start + pageSize), total: filtered.length }));
    }

    // 友链远端分页
    const remotePage = p.match(/^\/remote([12])\/api\/echo\/page$/);
    if (remotePage && req.method === 'POST') {
      const body = await readBody(req);
      const list = remotePage[1] === '1' ? remote1() : remote2();
      const page = Number(body.page || 1);
      const pageSize = Number(body.pageSize || 10);
      const start = (page - 1) * pageSize;
      return json(res, ok({ items: list.slice(start, start + pageSize), total: list.length }));
    }
    const remoteDetail = p.match(/^\/remote([12])\/api\/echo\/([^/]+)$/);
    if (remoteDetail && req.method === 'GET') {
      const list = remoteDetail[1] === '1' ? remote1() : remote2();
      const post = list.find((e) => e.id === remoteDetail[2]);
      return post ? json(res, ok(post)) : json(res, fail('说说不存在', 'NOT_FOUND'), 404);
    }
    const remoteComments = p.match(/^\/remote([12])\/api\/comments$/);
    if (remoteComments && req.method === 'GET') {
      return json(res, ok([]));
    }

    if (p === '/api/echo/today') {
      const cutoff = now() - 86400;
      return json(res, ok(posts().filter((e) => e.created_at >= cutoff)));
    }
    if (p === '/api/echo/hot') {
      const limit = Number(url.searchParams.get('limit') || 8);
      return json(res, ok([...posts()].sort((a, b) => b.fav_count - a.fav_count).slice(0, limit)));
    }
    if (p === '/api/echo/onthisday') {
      const all = posts();
      return json(res, ok(all.filter((e) => e.id === 'e25')));
    }
    if (p === '/api/echo/random') {
      const all = posts();
      return json(res, ok(all[Math.floor(Math.random() * all.length)]));
    }

    if (p.startsWith('/api/echo/like/') && req.method === 'PUT') {
      const id = p.split('/').pop();
      const post = posts().find((e) => e.id === id);
      if (post && !likedIds.has(id)) {
        const spec = postSpecs.find((s) => s.id === id);
        if (spec) spec.fav = (spec.fav || 0) + 1;
        likedIds.add(id);
      }
      return json(res, ok(null));
    }

    if (p.startsWith('/api/echo/') && req.method === 'GET') {
      const id = p.split('/').pop();
      const post = posts().find((e) => e.id === id);
      return post ? json(res, ok(post)) : json(res, fail('说说不存在', 'NOT_FOUND'), 404);
    }

    if (p === '/api/echo' && req.method === 'POST') {
      const body = await readBody(req);
      const id = 'e' + (postSpecs.length + 1);
      const bodyTags = (body.tags || []).map((t) => tag(t.id) || tagByName[t.name] || {
        id: 't' + t.name, name: t.name, usage_count: 1, created_at: now()
      });
      postSpecs.unshift({
        id, age: 0, fav: 0, content: body.content || '',
        private: !!body.private, layout: body.layout || 'waterfall',
        extension: body.extension || null, tags: bodyTags.map((t) => t.name)
      });
      return json(res, ok(null));
    }
    if (p === '/api/echo' && req.method === 'PUT') {
      const body = await readBody(req);
      const spec = postSpecs.find((s) => s.id === body.id);
      if (spec) {
        spec.content = body.content ?? spec.content;
        spec.private = !!body.private;
        spec.layout = body.layout || spec.layout;
        spec.age = body.created_at ? Math.max(0, Math.round((now() - Number(body.created_at)) / 60)) : spec.age;
        spec.tags = (body.tags || []).map((t) => tag(t.id)?.name || t.name).filter(Boolean);
      }
      return json(res, ok(null));
    }
    if (p.startsWith('/api/echo/') && req.method === 'DELETE') return json(res, ok(null));

    if (p === '/api/tags') {
      const counts = {};
      for (const spec of postSpecs) {
        for (const name of spec.tags || []) counts[name] = (counts[name] || 0) + 1;
      }
      return json(res, ok(tags.map((t) => ({ ...t, usage_count: counts[t.name] || t.usage_count }))));
    }
    if (p === '/api/heatmap') return json(res, ok(heatmap()));

    if (p === '/api/connect') return json(res, ok({
      server_name: '盐鱼的碎碎念', server_url: PUBLIC_HOST, logo: null,
      total_echos: postSpecs.length, today_echos: 5,
      sys_username: 'saltfish', version: '5.8.2'
    }));

    if (p === '/api/connects/info') return json(res, ok([
      {
        server_name: '追风小站', server_url: `${PUBLIC_HOST}/remote1`, logo: null,
        total_echos: 128, today_echos: 2, sys_username: 'wind', version: '5.8.2'
      },
      {
        server_name: '山间来信', server_url: `${PUBLIC_HOST}/remote2`, logo: null,
        total_echos: 86, today_echos: 1, sys_username: 'moss', version: '5.8.1'
      }
    ]));

    if (p === '/api/comments/form') return json(res, ok({
      form_token: 'mock-form-token', min_submit_ms: 0, captcha_enabled: false,
      captcha_api_endpoint: null, enable_comment: true
    }));
    if (p === '/api/comments' && req.method === 'GET') {
      const echoId = url.searchParams.get('echo_id');
      return json(res, ok(comments().filter((c) => c.echo_id === echoId)));
    }
    if (p === '/api/comments' && req.method === 'POST') {
      const body = await readBody(req);
      const c = {
        id: 'c' + (commentSpecs.length + 1),
        echo_id: body.echo_id, parent_id: body.parent_id || null,
        nickname: body.nickname || '游客', website: body.website || null,
        content: body.content || '', status: 'approved', hot: false, source: null,
        created_at: now(), updated_at: now()
      };
      commentSpecs.push({
        id: c.id, echo_id: c.echo_id, parent_id: c.parent_id,
        nickname: c.nickname, content: c.content, age: 0
      });
      return json(res, ok({ id: c.id, status: 'approved' }));
    }

    if (p === '/api/files/upload' && req.method === 'POST') {
      return json(res, ok(fileDto('food.jpg')));
    }
    if (p === '/api/files/external' && req.method === 'POST') {
      const body = await readBody(req);
      return json(res, ok({
        id: 'f-external', name: 'external', key: 'external',
        storage_type: 'external', url: body.url || '', content_type: 'image/jpeg',
        category: 'image', size: 0, width: 1200, height: 800, user_id: 'u1', created_at: now()
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
