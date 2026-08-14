(() => {
    const page = document.querySelector('[data-content-editor]');
    const form = page?.querySelector('form');
    const textarea = page?.querySelector('textarea[name="content"]');
    if (!page || !form || !textarea) return;

    const resize = () => {
        textarea.style.height = 'auto';
        textarea.style.height = `${textarea.scrollHeight}px`;
    };

    textarea.addEventListener('input', resize);
    resize();

    form.addEventListener('submit', () => {
        form.setAttribute('aria-busy', 'true');
        const submitButton = form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = '저장 중…';
        }
    });
})();
