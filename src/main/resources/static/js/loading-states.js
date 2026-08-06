(() => {
    const overlay = document.createElement('div');
    overlay.className = 'page-loading-overlay';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.innerHTML = `
        <div class="skeleton skeleton-title"></div>
        <div class="skeleton-grid">
            <div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div>
            <div class="skeleton skeleton-card"></div><div class="skeleton skeleton-card"></div>
        </div>`;
    document.body.prepend(overlay);

    const hide = () => {
        requestAnimationFrame(() => overlay.classList.add('is-hidden'));
        window.setTimeout(() => overlay.remove(), 250);
    };
    if (document.readyState === 'complete') hide();
    else window.addEventListener('load', hide, { once: true });
})();
