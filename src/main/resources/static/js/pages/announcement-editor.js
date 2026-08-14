(() => {
    const previewButton = document.getElementById('previewBtn');
    const previewArea = document.getElementById('previewArea');
    const previewContent = document.getElementById('previewContent');
    const content = document.getElementById('content');
    if (!previewButton || !previewArea || !previewContent || !content) return;

    const openLabel = '<span class="flex items-center gap-1"><span aria-hidden="true">⌃</span>미리보기 닫기</span>';
    const closedLabel = '<span class="flex items-center gap-1"><span aria-hidden="true">⌄</span>미리보기</span>';

    previewButton.addEventListener('click', () => {
        const isOpen = previewArea.classList.toggle('hidden') === false;
        previewContent.textContent = content.value || '내용이 없습니다.';
        previewButton.setAttribute('aria-expanded', String(isOpen));
        previewButton.innerHTML = isOpen ? openLabel : closedLabel;
    });

    content.addEventListener('input', () => {
        content.style.height = 'auto';
        content.style.height = `${content.scrollHeight}px`;
        if (!previewArea.classList.contains('hidden')) {
            previewContent.textContent = content.value || '내용이 없습니다.';
        }
    });
})();
