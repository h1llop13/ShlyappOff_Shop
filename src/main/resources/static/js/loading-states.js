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
    // The server already rendered the catalog. Never wait for images or the remote SDK.
    if (document.readyState !== 'loading') hide();
    else document.addEventListener('DOMContentLoaded', hide, { once: true });
    window.setTimeout(hide, 1500);
})();
