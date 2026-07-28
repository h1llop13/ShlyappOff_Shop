(() => {
    const getTelegram = () => window.Telegram && window.Telegram.WebApp;
    const setState = (button, active) => {
        button.classList.toggle('is-favorite', active);
        button.setAttribute('aria-pressed', String(active));
        button.title = active ? 'Убрать из избранного' : 'Добавить в избранное';
        button.textContent = active ? '♥' : '♡';
    };
    async function request(path, productId) {
        const tg = getTelegram();
        if (!tg || !tg.initData) return null;
        const response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ initData: tg.initData, productId }) });
        return response.ok ? response.json() : null;
    }
    async function init() {
        const buttons = [...document.querySelectorAll('[data-favorite-button]')];
        if (!buttons.length) return;
        const result = await request('/api/favorites/me');
        const saved = new Set(result?.productIds || []);
        buttons.forEach(button => {
            setState(button, saved.has(Number(button.dataset.favoriteButton)));
            button.addEventListener('click', async event => {
                event.preventDefault(); event.stopPropagation();
                const result = await request('/api/favorites/toggle', Number(button.dataset.favoriteButton));
                if (result) setState(button, result.favorite);
            });
        });
    }
    function initializeWhenTelegramIsReady() {
        if (window.TelegramLoader) {
            window.TelegramLoader.onReady(init);
            return;
        }
        init();
    }

    document.readyState === 'loading'
        ? document.addEventListener('DOMContentLoaded', initializeWhenTelegramIsReady, { once: true })
        : initializeWhenTelegramIsReady();
})();
