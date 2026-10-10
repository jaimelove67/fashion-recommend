'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const capture = require('./capture');

test('capture supports Douyin and Weibo, but not Xiaohongshu', () => {
  assert.deepEqual(capture.PLATFORMS.map((entry) => entry.platform), ['douyin', 'weibo']);
  assert.equal(capture.detectPlatform('www.xiaohongshu.com'), null);
});

test('gallery image requires an explicitly confirmed image from the captured post', () => {
  const fields = {
    id: '12345678', sourceUrl: 'https://www.douyin.com/video/12345678',
    title: '秋季穿搭', publishedText: '2026-09-20',
    images: ['https://images.example/cover.jpg', 'https://images.example/full.jpg'],
    fullBodyImageUrl: 'https://images.example/full.jpg'
  };
  const observed = new Date('2026-09-21T12:00:00+08:00');
  assert.equal(capture.buildRecord('douyin', fields, observed).full_body_image_url, '');
  assert.equal(capture.buildRecord('douyin', { ...fields, fullBodyImageVerified: true }, observed).full_body_image_url,
    'https://images.example/full.jpg');
  assert.equal(capture.buildRecord('douyin', { ...fields, fullBodyImageUrl: 'https://images.example/other.jpg', fullBodyImageVerified: true }, observed).full_body_image_url, '');
});

class FakeElement {
  constructor(tagName) {
    this.tagName = tagName;
    this.children = [];
    this.listeners = new Map();
    this.style = {};
    this.textContent = '';
  }

  addEventListener(name, handler) {
    this.listeners.set(name, handler);
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }

  attachShadow() {
    this.shadowRoot = new FakeShadowRoot();
    return this.shadowRoot;
  }

  remove() {}
}

class FakeShadowRoot {
  constructor() {
    this.children = [];
    this.list = new FakeElement('ol');
    this.actions = new Map(['close', 'clear', 'download'].map((name) => [
      `[data-act="${name}"]`, new FakeElement('button')
    ]));
    this.html = '';
  }

  set innerHTML(value) {
    this.html = value;
  }

  get innerHTML() {
    return this.html;
  }

  querySelector(selector) {
    if (selector === '[data-capture-list]') return this.list;
    return this.actions.get(selector) || null;
  }
}

function openExportStore(rows) {
  const hosts = [];
  const context = {
    __fashionCaptureAction: 'export',
    location: { hostname: 'www.douyin.com' },
    document: {
      createElement: (tagName) => new FakeElement(tagName),
      documentElement: { appendChild: (host) => hosts.push(host) }
    },
    localStorage: { getItem: (key) => {
      assert.equal(key, 'fashionTrendCapture.douyin');
      return JSON.stringify(rows);
    } },
    alert: () => { throw new Error('export view should not display an alert'); }
  };
  const source = fs.readFileSync(path.join(__dirname, 'capture.js'), 'utf8');
  vm.runInNewContext(source, context, { filename: 'capture.js' });
  assert.equal(hosts.length, 1, 'the real export path should attach its dialog');
  return hosts[0].shadowRoot;
}

test('export dialog renders stored external titles as text', () => {
  const hostileTitle = '<img src=x onerror=alert(1)>';
  const root = openExportStore([{ title: hostileTitle, time: '2026-09-20T12:00:00+08:00' }]);

  assert.equal(root.innerHTML.includes(hostileTitle), false);
  assert.equal(root.list.children.length, 1);
  assert.equal(root.list.children[0].textContent,
    `1. ${hostileTitle} · 2026-09-20T12:00:00+08:00`);
});
