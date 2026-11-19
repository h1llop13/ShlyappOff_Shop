(() => {
    let binding;
    let boundAt = 0;
    let submittingForms = 0;
    const sdkReady = new Promise(resolve => {
        window.TelegramLoader.onReady(resolve);
        window.TelegramLoader.onUnavailable(() => resolve(null));
    });

    function isTelegramLaunch() {
        if (window.location.hash.includes('tgWebAppData=')) return true;
        try { return Boolean(sessionStorage.getItem('__telegram__initParams')?.includes('tgWebAppData')); }
        catch (_) { return false; }
    }

    async function requestJson(url, options = {}) {
        const controller = new AbortController();
        const timer = window.setTimeout(() => controller.abort(), 12000);
        try {
            const response = await fetch(url, {
                ...options, credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
                headers: { 'Content-Type': 'application/json', Accept: 'application/json',
                    'ngrok-skip-browser-warning': '1', ...options.headers }
            });
            if (response.status === 401) throw new Error('Сессия Telegram истекла. Закройте магазин и откройте снова через бота.');
            if (response.status === 403) {
                binding = null; boundAt = 0;
                throw new Error('Сессия магазина обновилась. Повторите действие или перезагрузите страницу.');
            }
            if (response.status === 409 && url === '/api/cart/bind') throw new Error('Не удалось привязать корзину. Закройте магазин и откройте снова.');
            if (!response.ok) {
                const error = await response.json().catch(() => null);
                throw new Error(error?.message || error?.detail || 'Сервис временно недоступен. Повторите попытку.');
            }
            if (!response.headers.get('content-type')?.includes('application/json')) {
                throw new Error('Туннель вернул страницу вместо ответа магазина. Проверьте подключение ngrok.');
            }
            return await response.json();
        } catch (error) {
            if (error.name === 'AbortError') throw new Error('Сервер долго не отвечает. Проверьте подключение и повторите попытку.');
            throw error;
        } finally { window.clearTimeout(timer); }
    }

    function showError(error) {
        let banner = document.getElementById('telegramConnectionError');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'telegramConnectionError';
            banner.setAttribute('role', 'alert');
            Object.assign(banner.style, { position: 'fixed', top: '0', left: '0', right: '0',
                zIndex: '4000', background: '#fff', color: '#111', padding: '16px', borderBottom: '1px solid #111' });
            document.body.appendChild(banner);
        }
        banner.replaceChildren(document.createTextNode(error.message + ' '));
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.textContent = 'Повторить';
        retry.onclick = () => window.location.reload();
        banner.appendChild(retry);
    }

    function bindPersistentCart() {
        if (binding && (!boundAt || Date.now() - boundAt < 30000)) return binding;
        boundAt = 0;
        binding = (async () => {
            // A late SDK success can recover even after the initial timeout.
            const tg = window.Telegram?.WebApp || await sdkReady;
            if (!tg?.initData) {
                if (isTelegramLaunch()) throw new Error('Не удалось загрузить Telegram. Проверьте соединение и откройте магазин снова.');
                return { bound: false, changed: false };
            }
            const result = await requestJson('/api/cart/bind', {
                method: 'POST', body: JSON.stringify({ initData: tg.initData })
            });
            // Check a second request BEFORE reloading. Blocked cookies otherwise cause an endless loop.
            if (result.changed) {
                const session = await requestJson('/api/cart/session');
                if (!session.bound) throw new Error('Telegram не сохраняет сессию магазина. Откройте его в приложении Telegram или разрешите cookies.');
            }
            if (result.csrfToken) document.querySelectorAll('input[name="_csrf"]').forEach(input => {
                input.value = result.csrfToken;
            });
            document.getElementById('telegramConnectionError')?.remove();
            boundAt = Date.now();
            return result;
        })().catch(error => { binding = null; throw error; });
        return binding;
    }

    async function refreshCartPage() {
        try {
            const result = await bindPersistentCart();
            if (result.changed && !submittingForms) window.location.reload();
        } catch (error) { showError(error); }
    }

    // Native form submissions must also wait for identity and refreshed CSRF after a server restart.
    document.querySelectorAll('form').forEach(form => {
        const path = new URL(form.action, window.location.href).pathname;
        if (form.hasAttribute('data-cart-form') || (path !== '/checkout' && !path.startsWith('/cart/'))) return;
        let submitting = false;
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (submitting) return;
            submitting = true;
            submittingForms++;
            try {
                await bindPersistentCart();
                const initData = form.querySelector('[name="telegramInitData"]');
                if (initData) initData.value = window.Telegram?.WebApp?.initData || '';
                form.querySelectorAll('[type="submit"]').forEach(button => { button.disabled = true; });
                try { window.Telegram?.WebApp?.MainButton?.disable(); } catch (_) { /* Optional native UI */ }
                // form.submit() omits the clicked button's name/value (quantity controls).
                if (event.submitter?.name) {
                    const value = document.createElement('input');
                    value.type = 'hidden'; value.name = event.submitter.name; value.value = event.submitter.value;
                    form.appendChild(value);
                }
                HTMLFormElement.prototype.submit.call(form);
            } catch (error) { submitting = false; submittingForms--; showError(error); }
        });
    });

    window.TelegramShop = { bindPersistentCart, requestJson, showError, refreshCartPage };
    window.TelegramLoader.onReady(tg => {
        if (tg.initData) bindPersistentCart().catch(showError);
    });
    window.TelegramLoader.onUnavailable(() => {
        if (isTelegramLaunch()) showError(new Error('Не удалось загрузить Telegram. Проверьте соединение.'));
    });
})();
