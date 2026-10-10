/**
 * Human-assisted trend capture, running as a bookmarklet in the page the user is already
 * reading.
 *
 * What this is: you browse the platform yourself, open a post, and click a bookmark. The script
 * reads the page that is already on your screen and fills in a form for you to confirm. What this
 * is not: it never navigates, never paginates, never runs unattended, never issues its own
 * requests and never tries to get past a check the page did not already pass. Nothing here
 * touches cookies, tokens or private endpoints. The only thing it does that a careful
 * copy-and-paste would not is put the values into the right fields.
 *
 * Two consequences of that design, both deliberate:
 *   - The human confirms every record, so a wrong guess is caught before it is stored and a
 *     platform redesign degrades the tool instead of corrupting the data.
 *   - Interaction counts are passed through exactly as displayed. "1.2万" is a rounded display
 *     string, not a measurement, so it is kept verbatim and the importer drops it as a counter
 *     rather than pretending 12000 is an observation.
 *
 * Pure helpers are exported when loaded under Node so they can be unit tested without a browser.
 */
(function () {
  'use strict';

  var SCHEMA_VERSION = 1;
  var STORE_PREFIX = 'fashionTrendCapture.';
  var MAX_IMAGES = 9;

  var PLATFORMS = [
    {
      platform: 'douyin',
      label: '抖音',
      hosts: ['douyin.com'],
      idPatterns: [/\/video\/(\d{6,})/, /\/note\/(\d{6,})/],
      idField: 'aweme_id',
      urlField: 'aweme_url'
    },
    {
      platform: 'weibo',
      label: '微博',
      hosts: ['weibo.com', 'weibo.cn'],
      idPatterns: [/\/detail\/([0-9a-zA-Z]{6,})/, /\/status(?:es)?\/([0-9a-zA-Z]{6,})/],
      idField: 'note_id',
      urlField: 'note_url'
    }
  ];

  var EXTRACT = {
    douyin: {
      title: ['[data-e2e="video-desc"]', '[data-e2e="note-desc"]', 'h1'],
      content: ['[data-e2e="video-desc"]', '[data-e2e="note-desc"]'],
      author: ['[data-e2e="video-author-name"]', '[data-e2e="user-name"]', '.account-name'],
      date: ['[data-e2e="video-publish-time"]', '[data-e2e="note-publish-time"]'],
      images: ['[data-e2e="video-detail"] img', 'img[src*="douyinpic"]'],
      counters: { likes: ['点赞'], favorites: ['收藏'], comments: ['评论'], reposts: ['分享', '转发'] }
    },
    weibo: {
      title: ['.detail_wbtext', '.weibo-text', '.wbtext', 'h1'],
      content: ['.detail_wbtext', '.weibo-text', '.wbtext'],
      author: ['.ProfileHeader_name', '.head-info_name', '.username', '.name'],
      date: ['.head-info_time', '.time', '.publish-time'],
      images: ['.woo-picture img', '.picture img', 'img[src*="sinaimg"]'],
      counters: { likes: ['赞', '点赞'], favorites: [], comments: ['评论'], reposts: ['转发', '分享'] }
    }
  };

  // ---------- pure helpers ----------

  function detectPlatform(hostname) {
    var host = String(hostname || '').toLowerCase();
    for (var i = 0; i < PLATFORMS.length; i++) {
      var entry = PLATFORMS[i];
      for (var j = 0; j < entry.hosts.length; j++) {
        var domain = entry.hosts[j];
        if (host === domain || host.endsWith('.' + domain)) return entry;
      }
    }
    return null;
  }

  function itemIdFor(entry, href) {
    var path = String(href || '').split('#')[0].split('?')[0];
    for (var i = 0; i < entry.idPatterns.length; i++) {
      var match = entry.idPatterns[i].exec(path);
      if (match) return match[1];
    }
    return '';
  }

  /** Drops the query string so an ephemeral share token is never written to the database. */
  function canonicalUrl(href) {
    var url;
    try {
      url = new URL(String(href));
    } catch (error) {
      return '';
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
    return url.origin + url.pathname;
  }

  function clean(value) {
    return String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
  }

  /**
   * Normalises a displayed counter as literally as possible. Thousands separators are removed, but
   * a rounded value such as "1.2万", "999+" or "约 300" is returned untouched on purpose: the
   * importer only accepts pure digits, so a rounded display is recorded as "no exact counter"
   * rather than silently inflated into a number the platform never published.
   */
  function normalizeCounter(value) {
    var raw = clean(value);
    if (!raw) return '';
    var digits = raw.replace(/[,\s\u00a0]/g, '');
    return /^\d+$/.test(digits) ? digits : raw;
  }

  /**
   * Reads the publish date a platform page shows. Returns { date, time, precision } where time is
   * null unless the page actually displayed one. Relative wording is converted only as far as it
   * is precise: "2小时前" is derived to the minute, "3天前" only to the day, because inventing a
   * clock time the page never showed would be fabricating data.
   */
  function parseDisplayDate(input, now) {
    var reference = now instanceof Date ? now : new Date();
    var text = clean(input);
    if (!text) return null;
    var match;

    if ((match = /(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})日?(?:[ T](\d{1,2}):(\d{2}))?/.exec(text))) {
      return stamp(Number(match[1]), Number(match[2]), Number(match[3]), match[4], match[5]);
    }
    if ((match = /(?:^|[^\d])(\d{1,2})[-/月](\d{1,2})日?(?:[ T](\d{1,2}):(\d{2}))?(?!\d)/.exec(text))) {
      var month = Number(match[1]);
      var day = Number(match[2]);
      if (month < 1 || month > 12 || day < 1 || day > 31) return null;
      var year = reference.getFullYear();
      var candidate = stamp(year, month, day, match[3], match[4]);
      // A month-and-day display belongs to the current year, unless that would be in the future.
      if (candidate && new Date(candidate.iso + 'T00:00:00') > reference) {
        candidate = stamp(year - 1, month, day, match[3], match[4]);
      }
      return candidate;
    }
    if ((match = /(\d+)\s*(分钟|小时|天|周|个月|月)前/.exec(text))) {
      var amount = Number(match[1]);
      var minutes = { '分钟': 1, '小时': 60, '天': 1440, '周': 10080, '个月': 43200, '月': 43200 }[match[2]];
      var shifted = new Date(reference.getTime() - amount * minutes * 60000);
      // Sub-day wording carries a clock reading; day-and-above wording does not.
      var dayOnly = minutes >= 1440;
      return {
        iso: localIso(shifted, dayOnly ? null : clock(shifted)),
        precision: dayOnly ? 'day' : 'minute',
        relative: true
      };
    }
    if (/前天/.test(text)) return shiftDays(reference, -2);
    if (/昨天|昨日/.test(text)) return shiftDays(reference, -1);
    if (/今天|刚刚/.test(text)) return shiftDays(reference, 0);
    return null;

    function shiftDays(base, days) {
      var shifted = new Date(base.getTime() + days * 86400000);
      return { iso: localIso(shifted, null), precision: 'day', relative: true };
    }
    function stamp(year, month, day, hour, minute) {
      var probe = new Date(year, month - 1, day);
      if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) return null;
      var time = hour != null ? pad(Number(hour)) + ':' + pad(Number(minute)) : null;
      return { iso: isoOf(year, month, day, time), precision: time ? 'minute' : 'day', relative: false };
    }
  }

  function clock(date) {
    return pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  function pad(value) {
    return String(value).padStart(2, '0');
  }

  function isoOf(year, month, day, time) {
    return pad(year).length === 4
      ? String(year) + '-' + pad(month) + '-' + pad(day) + 'T' + (time || '00:00') + offsetOf(new Date())
      : '';
  }

  function localIso(date, time) {
    return isoOf(date.getFullYear(), date.getMonth() + 1, date.getDate(), time);
  }

  function offsetOf(date) {
    var minutes = -date.getTimezoneOffset();
    var sign = minutes < 0 ? '-' : '+';
    var absolute = Math.abs(minutes);
    return sign + pad(Math.floor(absolute / 60)) + ':' + pad(absolute % 60);
  }

  /**
   * Builds the row the importer expects, using the same field names as a MediaCrawler export so
   * no translation step exists between capture and import.
   */
  function buildRecord(platform, fields, observed) {
    var entry = null;
    for (var i = 0; i < PLATFORMS.length; i++) if (PLATFORMS[i].platform === platform) entry = PLATFORMS[i];
    if (!entry) throw new Error('unsupported platform: ' + platform);
    var id = clean(fields.id);
    if (!id) throw new Error('内容 ID 缺失');
    var source = canonicalUrl(fields.sourceUrl);
    if (!source) throw new Error('原文链接无效');
    var published = parseDisplayDate(fields.publishedText, observed);
    if (!published) throw new Error('发布日期无法确定，请手工填写');
    var images = (fields.images || []).map(canonicalImage).filter(Boolean).slice(0, MAX_IMAGES);
    var reviewedImage = canonicalImage(fields.fullBodyImageUrl);
    var row = {
      title: clean(fields.title).slice(0, 200),
      desc: clean(fields.content).slice(0, 600),
      time: published.iso,
      last_modify_ts: Math.floor((observed instanceof Date ? observed : new Date()).getTime() / 1000),
      image_list: images,
      cover_url: images[0] || '',
      full_body_image_url: fields.fullBodyImageVerified === true && images.includes(reviewedImage) ? reviewedImage : '',
      full_body_image_verified: fields.fullBodyImageVerified === true && images.includes(reviewedImage),
      nickname: clean(fields.author).slice(0, 60),
      liked_count: normalizeCounter(fields.likes),
      collected_count: normalizeCounter(fields.favorites),
      comment_count: normalizeCounter(fields.comments),
      share_count: normalizeCounter(fields.reposts)
    };
    row[entry.idField] = id;
    row[entry.urlField] = source;
    return row;
  }

  function canonicalImage(value) {
    var url;
    try {
      url = new URL(String(value));
    } catch (error) {
      return '';
    }
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : '';
  }

  function trimCounters(row) {
    Object.keys(row).forEach(function (key) {
      if (/_(count)$/.test(key) && !row[key]) delete row[key];
    });
    return row;
  }

  /** Keeps the newest capture of a post; re-capturing after an edit must not duplicate it. */
  function mergeRecord(store, record, entry) {
    var idField = entry ? entry.idField : 'note_id';
    var id = record[idField] || record.aweme_id;
    var kept = [];
    for (var i = 0; i < store.length; i++) {
      if ((store[i][idField] || store[i].aweme_id) !== id) kept.push(store[i]);
    }
    kept.push(record);
    return kept;
  }

  // ---------- browser side ----------

  var api = {
    detectPlatform: detectPlatform,
    itemIdFor: itemIdFor,
    canonicalUrl: canonicalUrl,
    normalizeCounter: normalizeCounter,
    parseDisplayDate: parseDisplayDate,
    buildRecord: buildRecord,
    mergeRecord: mergeRecord,
    EXTRACT: EXTRACT,
    PLATFORMS: PLATFORMS,
    trimCounters: trimCounters
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
    return;
  }
  globalThis.FashionCapture = api;
  bootstrap(api);

  function bootstrap(api) {
    var entry = api.detectPlatform(location.hostname);
    if (!entry) {
      alert('这个页面不在支持的平台上。支持：抖音、微博。');
      return;
    }
    var storeKey = STORE_PREFIX + entry.platform;
    var action = (globalThis.__fashionCaptureAction || 'capture');
    globalThis.__fashionCaptureAction = 'capture';
    if (action === 'export') showStore(entry, storeKey);
    else startCapture(entry, storeKey);
  }

  function readStore(storeKey) {
    try {
      var parsed = JSON.parse(localStorage.getItem(storeKey) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function writeStore(storeKey, rows) {
    localStorage.setItem(storeKey, JSON.stringify(rows));
  }

  function firstText(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var node = document.querySelector(selectors[i]);
      var value = node && node.textContent ? node.textContent.trim() : '';
      if (value) return value;
    }
    return '';
  }

  function metaContent(names) {
    for (var i = 0; i < names.length; i++) {
      var node = document.querySelector('meta[property="' + names[i] + '"], meta[name="' + names[i] + '"]');
      var value = node && node.getAttribute('content') ? node.getAttribute('content').trim() : '';
      if (value) return value;
    }
    return '';
  }

  function firstImage(selectors) {
    var seen = [];
    for (var i = 0; i < selectors.length; i++) {
      var nodes = document.querySelectorAll(selectors[i]);
      for (var j = 0; j < nodes.length && seen.length < MAX_IMAGES; j++) {
        var source = nodes[j].currentSrc || nodes[j].src || '';
        if (!source) continue;
        var absolute = api.canonicalUrl(source) ? new URL(source).href : '';
        if (absolute && seen.indexOf(absolute) < 0) seen.push(absolute);
      }
      if (seen.length) break;
    }
    return seen;
  }

  /** Finds "评论 12" style labels, because label wording survives redesign better than classes. */
  function counterByLabel(labels) {
    if (!labels || !labels.length) return '';
    var nodes = document.querySelectorAll('span, a, button, div, p');
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (node.children.length > 1) continue;
      var text = clean(node.textContent || '');
      if (!text || text.length > 24) continue;
      for (var j = 0; j < labels.length; j++) {
        var label = labels[j];
        var after = new RegExp(label + '\\s*([0-9][0-9,.\\s]*(?:万|亿|w|k)?)', 'i').exec(text);
        if (after) return after[1];
        var before = new RegExp('([0-9][0-9,.\\s]*(?:万|亿|w|k)?)\\s*' + label, 'i').exec(text);
        if (before) return before[1];
      }
    }
    return '';
  }

  function extract(entry) {
    var rule = api.EXTRACT[entry.platform];
    var href = location.href;
    return {
      id: api.itemIdFor(entry, href),
      sourceUrl: href,
      title: firstText(rule.title) || metaContent(['og:title', 'twitter:title']) || document.title,
      content: firstText(rule.content) || metaContent(['og:description', 'description']),
      author: firstText(rule.author) || metaContent(['og:article:author', 'author']),
      publishedText: firstText(rule.date),
      images: firstImage(rule.images).concat(
        metaContent(['og:image']).split(',').map(function (value) { return clean(value); }).filter(Boolean)
      ).filter(function (value, index, all) { return all.indexOf(value) === index; }).slice(0, MAX_IMAGES),
      likes: counterByLabel(rule.counters.likes),
      favorites: counterByLabel(rule.counters.favorites),
      comments: counterByLabel(rule.counters.comments),
      reposts: counterByLabel(rule.counters.reposts)
    };
  }

  function startCapture(entry, storeKey) {
    var draft;
    try {
      draft = buildRecord(entry.platform, extract(entry), new Date());
    } catch (error) {
      draft = null;
    }
    var raw = extract(entry);
    openDialog(entry, storeKey, raw, draft, '');
  }

  function openDialog(entry, storeKey, raw, draft, message) {
    var host = document.createElement('div');
    host.style.cssText = 'all:initial;position:fixed;inset:0;z-index:2147483647';
    var root = host.attachShadow({ mode: 'open' });
    var existing = readStore(storeKey).length;
    var record = draft || {};
    var imageCandidates = (raw.images || []).map(canonicalImage).filter(Boolean).slice(0, MAX_IMAGES);
    var fields = [
      ['title', '标题', 'text'],
      ['content', '正文摘要', 'textarea'],
      ['author', '作者', 'text'],
      ['publishedText', '发布日期', 'text'],
      ['likes', '点赞', 'text'],
      ['favorites', '收藏', 'text'],
      ['comments', '评论', 'text'],
      ['reposts', '转发', 'text']
    ];
    var values = {
      title: record.title || raw.title || '',
      content: record.desc || raw.content || '',
      author: record.nickname || raw.author || '',
      publishedText: raw.publishedText || '',
      likes: raw.likes || '',
      favorites: raw.favorites || '',
      comments: raw.comments || '',
      reposts: raw.reposts || ''
    };
    var rows = fields.map(function (field) {
      var key = field[0];
      var control = field[2] === 'textarea'
        ? '<textarea data-k="' + key + '" rows="4"></textarea>'
        : '<input data-k="' + key + '" />';
      var hint = key === 'publishedText'
        ? '<em>页面显示什么就填什么，例如 2026-09-01 或 3天前；留空则整条无法导入</em>'
        : (key === 'likes' || key === 'favorites' || key === 'comments' || key === 'reposts'
          ? '<em>原样照抄，例如 342 或 1.2万；四舍五入的显示值不会被当作精确计数</em>' : '');
      return '<label><span>' + field[1] + '</span>' + control + hint + '</label>';
    }).join('');
    root.innerHTML = [
      '<style>',
      ':host{all:initial}*{box-sizing:border-box;font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif}',
      '.back{position:fixed;inset:0;background:rgba(20,20,20,.45)}',
      '.panel{position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);width:min(560px,92vw);max-height:88vh;overflow:auto;background:#fff;color:#1f1f1f;border-radius:14px;padding:20px}',
      'h2{font-size:16px;margin:0 0 4px;font-weight:600}.sub{font-size:12px;color:#666;margin:0 0 14px}',
      '.msg{font-size:12px;background:#FAEEDA;color:#633806;border-radius:8px;padding:8px 10px;margin:0 0 12px}',
      'label,.image-picker{display:block;margin:0 0 10px}label>span,.image-picker>span{display:block;font-size:12px;color:#333;margin:0 0 4px}',
      'input,textarea{width:100%;font-size:13px;padding:7px 9px;border:1px solid #ccc;border-radius:8px;background:#fff;color:#1f1f1f;font-family:inherit}',
      '.image-choices{display:flex;gap:10px;overflow-x:auto;padding:4px 0 10px}.image-choice{flex:0 0 112px;cursor:pointer}.image-choice input{width:auto}.image-choice img{display:block;width:100%;height:142px;object-fit:contain;background:#f4f4f4;border-radius:6px}.verify{display:flex;align-items:flex-start;gap:8px;font-size:12px}.verify input{width:auto;margin-top:3px}',
      'em{display:block;font-size:11px;color:#888;font-style:normal;margin:3px 0 0}',
      '.meta{font-size:11px;color:#777;word-break:break-all;margin:0 0 12px}',
      '.bar{display:flex;gap:8px;justify-content:flex-end;margin-top:14px}',
      'button{font-size:13px;padding:8px 14px;border-radius:8px;border:1px solid #ccc;background:#fff;color:#1f1f1f;cursor:pointer}',
      'button.go{background:#1f1f1f;color:#fff;border-color:#1f1f1f}',
      '</style>',
      '<div class="back"></div>',
      '<div class="panel">',
      '<h2>确认这条内容（' + entry.label + '）</h2>',
      '<p class="sub">已采集 ' + existing + ' 条。下面的值是从当前页面猜出来的，请核对后再保存。</p>',
      message ? '<p class="msg">' + message + '</p>' : '',
      rows,
      '<div class="image-picker"><span>画廊全身穿搭照（可不选）</span><div class="image-choices" data-image-choices></div><em>请选择从头到脚完整入镜、能看清整套搭配的真人照片；杂志、画报、秀场、半身照及单品图不能选。</em></div>',
      '<label class="verify"><input type="checkbox" data-full-body-verified /><span>我已查看所选图片，并确认它符合全身穿搭照要求</span></label>',
      '<p class="meta">内容 ID：' + (raw.id || '未识别') + '<br>原文链接：' + api.canonicalUrl(raw.sourceUrl) + '<br>图片 ' + (raw.images || []).length + ' 张</p>',
      '<div class="bar"><button data-act="cancel">取消</button><button class="go" data-act="save">保存这条</button></div>',
      '</div>'
    ].join('');
    document.documentElement.appendChild(host);
    Object.keys(values).forEach(function (key) {
      var control = root.querySelector('[data-k="' + key + '"]');
      if (control) control.value = values[key];
    });
    var choices = root.querySelector('[data-image-choices]');
    imageCandidates.forEach(function (url, index) {
      var label = document.createElement('label');
      label.className = 'image-choice';
      var input = document.createElement('input');
      input.type = 'radio';
      input.name = 'full-body-image';
      input.value = url;
      var image = document.createElement('img');
      image.src = url;
      image.alt = '候选图片 ' + (index + 1);
      label.appendChild(input);
      label.appendChild(image);
      choices.appendChild(label);
    });
    root.querySelector('[data-act="cancel"]').addEventListener('click', function () { host.remove(); });
    root.querySelector('[data-act="save"]').addEventListener('click', function () {
      var edited = {};
      Object.keys(values).forEach(function (key) {
        var control = root.querySelector('[data-k="' + key + '"]');
        edited[key] = control ? control.value : '';
      });
      var row;
      try {
        row = api.buildRecord(entry.platform, {
          id: raw.id, sourceUrl: raw.sourceUrl, images: raw.images,
          fullBodyImageUrl: root.querySelector('input[name="full-body-image"]:checked')?.value || '',
          fullBodyImageVerified: root.querySelector('[data-full-body-verified]').checked,
          title: edited.title, content: edited.content, author: edited.author,
          publishedText: edited.publishedText, likes: edited.likes,
          favorites: edited.favorites, comments: edited.comments, reposts: edited.reposts
        }, new Date());
      } catch (error) {
        openDialog(entry, storeKey, raw, draft, '无法保存：' + error.message);
        host.remove();
        return;
      }
      var merged = api.mergeRecord(readStore(storeKey), api.trimCounters(row), entry);
      writeStore(storeKey, merged);
      host.remove();
      alert('已保存。当前共 ' + merged.length + ' 条，继续浏览下一条即可。');
    });
  }

  function showStore(entry, storeKey) {
    var rows = readStore(storeKey);
    var host = document.createElement('div');
    host.style.cssText = 'all:initial;position:fixed;inset:0;z-index:2147483647';
    var root = host.attachShadow({ mode: 'open' });
    root.innerHTML = [
      '<style>',
      ':host{all:initial}*{box-sizing:border-box;font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif}',
      '.back{position:fixed;inset:0;background:rgba(20,20,20,.45)}',
      '.panel{position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);width:min(520px,92vw);max-height:88vh;overflow:auto;background:#fff;color:#1f1f1f;border-radius:14px;padding:20px}',
      'h2{font-size:16px;margin:0 0 4px;font-weight:600}.sub{font-size:12px;color:#666;margin:0 0 12px}',
      'ol{font-size:12px;color:#333;padding-left:18px;margin:0 0 14px;line-height:1.7}',
      '.bar{display:flex;gap:8px;justify-content:flex-end}',
      'button{font-size:13px;padding:8px 14px;border-radius:8px;border:1px solid #ccc;background:#fff;color:#1f1f1f;cursor:pointer}',
      'button.go{background:#1f1f1f;color:#fff;border-color:#1f1f1f}',
      'button.warn{color:#A32D2D;border-color:#F09595}',
      '</style>',
      '<div class="back"></div>',
      '<div class="panel">',
      '<h2>' + entry.label + '：已采集 ' + rows.length + ' 条</h2>',
      '<p class="sub">下载后把文件交给导入命令：python collector.py import --input 文件.json --platform ' + entry.platform + '</p>',
      rows.length ? '<ol data-capture-list></ol>' : '<p class="sub">还没有采集任何内容。回到帖子页面点「采集这篇」。</p>',
      '<div class="bar">',
      rows.length ? '<button class="warn" data-act="clear">清空</button>' : '',
      rows.length ? '<button class="go" data-act="download">下载 JSON</button>' : '',
      '<button data-act="close">关闭</button>',
      '</div>',
      '</div>'
    ].join('');
    if (rows.length) {
      var list = root.querySelector('[data-capture-list]');
      rows.forEach(function (row, index) {
        var item = document.createElement('li');
        item.textContent = (index + 1) + '. ' + clean(row.title).slice(0, 40) + ' · ' + (row.time || '无日期');
        list.appendChild(item);
      });
    }
    document.documentElement.appendChild(host);
    root.querySelector('[data-act="close"]').addEventListener('click', function () { host.remove(); });
    var clear = root.querySelector('[data-act="clear"]');
    if (clear) clear.addEventListener('click', function () {
      if (!confirm('清空本平台已采集的 ' + rows.length + ' 条？此操作不可撤销。')) return;
      writeStore(storeKey, []);
      host.remove();
    });
    var download = root.querySelector('[data-act="download"]');
    if (download) download.addEventListener('click', function () {
      var payload = {
        schemaVersion: SCHEMA_VERSION,
        platform: entry.platform,
        capturedAt: new Date().toISOString(),
        items: rows
      };
      var blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
      var link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = entry.platform + '-capture-' + new Date().toISOString().slice(0, 10) + '.json';
      link.click();
      setTimeout(function () { URL.revokeObjectURL(link.href); }, 4000);
    });
  }
})();
