(() => {
    const readyCallbacks = [];
    const unavailableCallbacks = [];
    let unavailable = false;

    function getWebApp() {
        return window.Telegram && window.Telegram.WebApp
            ? window.Telegram.WebApp
            : null;
    }

    function invoke(callback, value) {
        try { callback(value); } catch (error) { console.error("Mini App initialization failed", error); }
    }

    function notifyReady() {
        const webApp = getWebApp();
        if (!webApp) {
            notifyUnavailable();
            return;
        }

        unavailable = false;
        try { webApp.ready(); } catch (_) { /* Browser preview */ }
        while (readyCallbacks.length) {
            invoke(readyCallbacks.shift(), webApp);
        }
    }

    function notifyUnavailable() {
        if (unavailable || getWebApp()) return;
        unavailable = true;

        while (unavailableCallbacks.length) {
            invoke(unavailableCallbacks.shift());
        }
    }

    window.TelegramLoader = {
        onReady(callback) {
            const webApp = getWebApp();
            if (webApp) {
                invoke(callback, webApp);
                return;
            }
            readyCallbacks.push(callback);
        },
        onUnavailable(callback) {
            if (unavailable) {
                invoke(callback);
                return;
            }
            unavailableCallbacks.push(callback);
        }
    };

    if (getWebApp()) {
        notifyReady();
        return;
    }

    const script = document.createElement('script');
    // Локального telegram-web-app.js в приложении нет. Официальный SDK
    // загружается асинхронно, поэтому не задерживает рендер витрины.
    script.src = 'https://telegram.org/js/telegram-web-app.js?61';
    script.async = true;
    script.onload = notifyReady;
    script.onerror = notifyUnavailable;
    document.head.appendChild(script);

    window.setTimeout(notifyUnavailable, 5000);
})();
