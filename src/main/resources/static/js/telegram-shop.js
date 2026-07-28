(() => {
    async function bindPersistentCart() {
        const tg = window.Telegram && window.Telegram.WebApp;
        if (!tg || !tg.initData) return { bound: false, changed: false };
        const response = await fetch('/api/cart/bind', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ initData: tg.initData }), credentials: 'same-origin'
        });
        return response.ok ? response.json() : { bound: false, changed: false };
    }
    window.TelegramShop = { bindPersistentCart };
})();
