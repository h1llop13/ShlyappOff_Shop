(() => {
    const impactStyles = new Set(['light', 'medium', 'heavy', 'rigid', 'soft']);
    const notificationTypes = new Set(['success', 'warning', 'error']);

    function hapticFeedback() {
        const telegram = window.Telegram && window.Telegram.WebApp;
        return telegram && telegram.HapticFeedback ? telegram.HapticFeedback : null;
    }

    function impact(style = 'light') {
        const haptic = hapticFeedback();
        if (!haptic) return;
        try {
            haptic.impactOccurred(impactStyles.has(style) ? style : 'light');
        } catch (_) {
            // Haptics are optional and may be unavailable in an older Telegram client.
        }
    }

    function selection() {
        const haptic = hapticFeedback();
        if (!haptic) return;
        try {
            haptic.selectionChanged();
        } catch (_) {
            // Haptics are optional and may be unavailable in an older Telegram client.
        }
    }

    function notify(type = 'success') {
        const haptic = hapticFeedback();
        if (!haptic) return;
        try {
            haptic.notificationOccurred(notificationTypes.has(type) ? type : 'success');
        } catch (_) {
            // Haptics are optional and may be unavailable in an older Telegram client.
        }
    }

    window.MiniAppHaptics = { impact, selection, notify };

    function styleFor(control) {
        const explicitStyle = control.dataset.haptic;
        if (explicitStyle) return explicitStyle;
        if (control.matches('.delete-btn, .clear-btn, .danger')) return 'medium';
        if (control.matches('.checkout-btn, .submit-btn')) return 'medium';
        if (control.matches('.btn-cart, .add-to-cart-btn')) return 'soft';
        return 'light';
    }

    document.addEventListener('click', (event) => {
        const control = event.target.closest('a[href], button, [role="button"], input[type="checkbox"], input[type="radio"]');
        if (!control || control.disabled || control.dataset.haptic === 'none') return;
        if (control.matches('input[type="checkbox"], input[type="radio"]')) return;
        impact(styleFor(control));
    });

    document.addEventListener('change', (event) => {
        if (event.target.matches('input[type="checkbox"], input[type="radio"], select')) {
            selection();
        }
    });
})();
