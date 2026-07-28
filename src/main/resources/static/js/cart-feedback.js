(() => {
    const REQUEST_HEADER = 'X-Requested-With';
    const TOAST_TIMEOUT_MS = 3600;
    let dismissTimer;

    function getToast() {
        let toast = document.getElementById('cartToast');
        if (toast) return toast;

        toast = document.createElement('section');
        toast.id = 'cartToast';
        toast.className = 'cart-toast';
        toast.setAttribute('role', 'status');
        toast.setAttribute('aria-live', 'polite');

        const icon = document.createElement('span');
        icon.className = 'cart-toast__icon';
        icon.setAttribute('aria-hidden', 'true');

        const content = document.createElement('div');
        content.className = 'cart-toast__content';
        const title = document.createElement('div');
        title.className = 'cart-toast__title';
        const message = document.createElement('div');
        message.className = 'cart-toast__message';
        content.append(title, message);

        const action = document.createElement('button');
        action.className = 'cart-toast__action';
        action.type = 'button';
        action.textContent = 'В корзину';
        action.addEventListener('click', () => window.location.assign('/cart'));

        toast.append(icon, content, action);
        document.body.append(toast);
        return toast;
    }

    function triggerHaptic(kind) {
        const telegram = window.Telegram && window.Telegram.WebApp;
        if (!telegram || !telegram.HapticFeedback) return;
        try {
            telegram.HapticFeedback.notificationOccurred(kind);
        } catch (_) {
            // Haptic feedback is optional and unavailable in some Telegram clients.
        }
    }

    function showToast({ title, message, error = false }) {
        const toast = getToast();
        window.clearTimeout(dismissTimer);
        toast.classList.toggle('cart-toast--error', error);
        toast.querySelector('.cart-toast__icon').textContent = error ? '!' : '✓';
        toast.querySelector('.cart-toast__title').textContent = title;
        toast.querySelector('.cart-toast__message').textContent = message || '';
        toast.classList.add('is-visible');
        dismissTimer = window.setTimeout(() => toast.classList.remove('is-visible'), TOAST_TIMEOUT_MS);
    }

    async function submitCartForm(form) {
        const button = form.querySelector('button[type="submit"]');
        const originalLabel = button ? button.textContent : null;
        if (button) {
            button.disabled = true;
            button.textContent = 'Добавляем…';
        }

        try {
            if (window.TelegramShop) {
                await window.TelegramShop.bindPersistentCart();
            }
            const response = await window.fetch(form.action, {
                method: form.method || 'POST',
                body: new FormData(form),
                credentials: 'same-origin',
                headers: { [REQUEST_HEADER]: 'XMLHttpRequest', Accept: 'application/json' }
            });
            const payload = await response.json().catch(() => null);
            if (!response.ok || !payload || !payload.success) {
                throw new Error(payload && payload.message ? payload.message : 'Не удалось добавить товар в корзину');
            }

            const itemWord = payload.itemCount === 1 ? 'товар' : 'товаров';
            showToast({
                title: `«${payload.productName}» добавлен в корзину`,
                message: `В корзине: ${payload.itemCount} ${itemWord}`
            });
            triggerHaptic('success');
        } catch (error) {
            showToast({
                title: 'Не удалось добавить товар',
                message: error.message || 'Проверьте подключение и повторите попытку',
                error: true
            });
            triggerHaptic('error');
        } finally {
            if (button) {
                button.disabled = false;
                button.textContent = originalLabel;
            }
        }
    }

    function initialize() {
        if (!window.fetch) return;
        document.querySelectorAll('form[data-cart-form]').forEach((form) => {
            form.addEventListener('submit', (event) => {
                event.preventDefault();
                submitCartForm(form);
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize, { once: true });
    } else {
        initialize();
    }
})();
