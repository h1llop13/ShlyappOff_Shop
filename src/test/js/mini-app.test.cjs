const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = name => fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', name), 'utf8');

function shopHarness({ bound = true, status = 200, sdk = true, forms = [], contentType = 'application/json' } = {}) {
    const onReady = [], onUnavailable = [];
    const calls = [], timers = [], errors = [], csrf = { value: 'old' };
    let reloads = 0;
    const window = {
        location: { hash: '#tgWebAppData=signed', href: 'https://shop.test/cart', reload: () => reloads++ },
        Telegram: sdk ? { WebApp: { initData: 'signed' } } : undefined,
        TelegramLoader: { onReady: cb => { onReady.push(cb); }, onUnavailable: cb => { onUnavailable.push(cb); } },
        setTimeout: fn => { timers.push(fn); return timers.length; }, clearTimeout() {}
    };
    const document = {
        querySelectorAll: selector => selector === 'form' ? forms : selector.includes('_csrf') ? [csrf] : [],
        getElementById: () => null,
        createTextNode: text => { errors.push(text); return text; },
        createElement: () => ({ style: {}, setAttribute() {}, replaceChildren() {}, appendChild() {} }),
        body: { appendChild() {} }
    };
    const fetch = async (url, options) => {
        calls.push({ url, options });
        return { status, ok: status === 200, headers: { get: () => contentType },
            json: async () => url.endsWith('/session') ? { bound } : { bound: true, changed: true, csrfToken: 'fresh' } };
    };
    const context = { window, document, fetch, sessionStorage: { getItem: () => null }, AbortController, URL, console, HTMLFormElement: { prototype: { submit() { this.sent = true; } } } };
    vm.runInNewContext(source('telegram-shop.js'), context);
    return { window, calls, errors, csrf, timers,
        ready() { onReady.forEach(cb => cb(window.Telegram.WebApp)); }, unavailable() { onUnavailable.forEach(cb => cb()); },
        reloads: () => reloads };
}

test('simultaneous cart actions share authentication and refresh stale CSRF', async () => {
    const h = shopHarness();
    const [one, two] = await Promise.all([h.window.TelegramShop.bindPersistentCart(), h.window.TelegramShop.bindPersistentCart()]);
    assert.equal(one, two);
    assert.equal(h.calls.length, 2);
    assert.equal(h.csrf.value, 'fresh');
    assert.equal(h.calls[0].options.headers['ngrok-skip-browser-warning'], '1');
});

test('blocked cookies produce a useful error without a reload loop', async () => {
    const h = shopHarness({ bound: false });
    await h.window.TelegramShop.refreshCartPage();
    assert.equal(h.reloads(), 0);
    assert.match(h.errors.join(), /не сохраняет сессию/);
});

test('expired signature stops cart operation and does not probe or reload', async () => {
    const h = shopHarness({ status: 401 });
    await assert.rejects(h.window.TelegramShop.bindPersistentCart(), /Сессия Telegram истекла/);
    assert.equal(h.calls.length, 1);
    assert.equal(h.reloads(), 0);
});

test('a click before SDK readiness waits instead of creating a guest cart', async () => {
    const h = shopHarness({ sdk: false });
    const binding = h.window.TelegramShop.bindPersistentCart();
    await Promise.resolve();
    assert.equal(h.calls.length, 0);
    h.window.Telegram = { WebApp: { initData: 'late-signed' } };
    h.ready();
    const result = await binding;
    assert.equal(result.bound, true);
});

test('request timeout is surfaced and does not automatically repeat a write', async () => {
    const h = shopHarness();
    const context = { window: h.window, document: { querySelectorAll: () => [] },
        AbortController, URL, console, sessionStorage: { getItem: () => null },
        fetch: (url, options) => new Promise((resolve, reject) => {
            options.signal.addEventListener('abort', () => reject(Object.assign(new Error(), { name: 'AbortError' })));
        }) };
    vm.runInNewContext(source('telegram-shop.js'), context);
    const request = h.window.TelegramShop.requestJson('/cart/add', { method: 'POST' });
    h.timers.at(-1)();
    await assert.rejects(request, /Сервер долго не отвечает/);
});

test('loader isolates broken callbacks and recovers from delayed SDK delivery', () => {
    const timers = [];
    let script, ready = 0, unavailable = 0;
    const window = { setTimeout: fn => timers.push(fn) };
    const document = { createElement: () => ({}), head: { appendChild: s => { script = s; } } };
    vm.runInNewContext(source('telegram-loader.js'), { window, document, console: { error() {} } });
    window.TelegramLoader.onReady(() => { throw new Error('broken extension'); });
    window.TelegramLoader.onReady(() => ready++);
    window.TelegramLoader.onUnavailable(() => unavailable++);
    timers[0]();
    assert.equal(unavailable, 1);
    window.Telegram = { WebApp: { ready() {} } };
    script.onload();
    assert.equal(ready, 1);
});

test('loading overlay disappears on DOM readiness while remote assets still load', () => {
    const events = [], classes = [];
    const overlay = { classList: { add: name => classes.push(name) }, setAttribute() {}, remove() {} };
    const document = { readyState: 'loading', createElement: () => overlay, body: { prepend() {} },
        addEventListener: (name, cb) => events.push({ name, cb }) };
    const window = { setTimeout() {}, addEventListener() { throw new Error('must not wait for load'); } };
    vm.runInNewContext(source('loading-states.js'), { window, document, requestAnimationFrame: cb => cb() });
    assert.equal(events[0].name, 'DOMContentLoaded');
    events[0].cb();
    assert.deepEqual(classes, ['is-hidden']);
});


test('ngrok HTML is reported as a tunnel error instead of treated as successful JSON', async () => {
    const h = shopHarness({ contentType: 'text/html' });
    await assert.rejects(h.window.TelegramShop.bindPersistentCart(), /Туннель вернул страницу/);
});

test('native quantity form preserves submitter value and blocks double submission', async () => {
    const inputs = [];
    let submit;
    const form = {
        action: 'https://shop.test/cart/update', hasAttribute: () => false,
        addEventListener: (type, callback) => { submit = callback; },
        querySelector: () => null, querySelectorAll: () => [], appendChild: input => inputs.push(input)
    };
    const h = shopHarness({ forms: [form] });
    const event = { preventDefault() {}, submitter: { name: 'quantity', value: '3' } };
    await Promise.all([submit(event), submit(event)]);
    assert.equal(form.sent, true);
    assert.equal(inputs.length, 1);
    assert.equal(inputs[0].name, 'quantity');
    assert.equal(inputs[0].value, '3');
    assert.equal(h.calls.length, 2);
});
