(() => {
    const updateFilterState = importantOnly => {
        const allButton = document.getElementById('allBtn');
        const importantButton = document.getElementById('importantBtn');
        if (!allButton || !importantButton) return;

        allButton.classList.toggle('bg-primary-100', !importantOnly);
        allButton.classList.toggle('text-primary-700', !importantOnly);
        allButton.classList.toggle('bg-gray-100', importantOnly);
        allButton.classList.toggle('text-gray-700', importantOnly);
        importantButton.classList.toggle('bg-gray-100', !importantOnly);
        importantButton.classList.toggle('text-gray-700', !importantOnly);
        importantButton.classList.toggle('bg-yellow-100', importantOnly);
        importantButton.classList.toggle('text-yellow-800', importantOnly);
        allButton.setAttribute('aria-pressed', String(!importantOnly));
        importantButton.setAttribute('aria-pressed', String(importantOnly));
    };

    document.body.addEventListener('htmx:afterSwap', event => {
        const responseUrl = event.detail?.xhr?.responseURL || '';
        if (!responseUrl.includes('/announcements/list')) return;
        updateFilterState(new URL(responseUrl, window.location.origin).searchParams.get('importantOnly') === 'true');
    });
})();
