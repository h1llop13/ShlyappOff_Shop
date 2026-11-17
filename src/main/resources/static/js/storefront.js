(() => {
    window.TelegramLoader.onReady((app) => {
        app.ready();
        if (!app.initData) return;

        app.expand();
        if (app.isVersionAtLeast?.('6.9')) app.setHeaderColor?.('#ffffff');
        app.setBackgroundColor?.('#ffffff');
        app.setBottomBarColor?.('#ffffff');
        app.MainButton?.hide();

        const back = document.querySelector('[data-mini-back]');
        if (app.BackButton) {
            if (back) {
                app.BackButton.show();
                app.BackButton.onClick(() => window.location.assign(back.href));
            } else {
                app.BackButton.hide();
            }
        }

        const initData = document.getElementById('telegramInitData');
        if (initData) initData.value = app.initData;

        const name = document.getElementById('customerName');
        const user = app.initDataUnsafe?.user;
        if (name && !name.value && user?.first_name) {
            name.value = [user.first_name, user.last_name].filter(Boolean).join(' ');
        }

        if (document.body.hasAttribute('data-bind-cart') && window.TelegramShop) {
            window.TelegramShop.bindPersistentCart().then(({ changed }) => {
                if (changed) window.location.reload();
            }).catch(() => {
                // Keep the session cart available when the Telegram request fails.
            });
        }
    });
})();
