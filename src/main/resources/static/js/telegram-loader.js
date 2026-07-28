(() => {
    const readyCallbacks = [];
    const unavailableCallbacks = [];
    let unavailable = false;

    function getWebApp() {
        return window.Telegram && window.Telegram.WebApp
            ? window.Telegram.WebApp
            : null;
    }

    function notifyReady() {
        const webApp = getWebApp();
        if (!webApp) {
            notifyUnavailable();
            return;
        }

        while (readyCallbacks.length) {
            readyCallbacks.shift()(webApp);
        }
    }

    function notifyUnavailable() {
        if (unavailable || getWebApp()) return;
        unavailable = true;

        while (unavailableCallbacks.length) {
            unavailableCallbacks.shift()();
        }
    }

    window.TelegramLoader = {
        onReady(callback) {
            const webApp = getWebApp();
            if (webApp) {
                callback(webApp);
                return;
            }
            readyCallbacks.push(callback);
        },
        onUnavailable(callback) {
            if (unavailable) {
                callback();
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
    script.src = '/js/telegram-web-app.js';
    script.async = true;
    script.onload = notifyReady;
    script.onerror = notifyUnavailable;
    document.head.appendChild(script);

    window.setTimeout(notifyUnavailable, 5000);
})();
