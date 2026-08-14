(() => {
    const form = document.getElementById('kindergartenForm');
    const submitButton = document.getElementById('createKindergartenSubmit');
    if (!form || !submitButton) return;

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        submitButton.disabled = true;

        const data = {
            name: document.getElementById('name').value.trim(),
            address: document.getElementById('address').value.trim(),
            phone: document.getElementById('phone').value.trim(),
            openTime: document.getElementById('openTime').value,
            closeTime: document.getElementById('closeTime').value
        };

        try {
            const response = await fetch('/api/v1/kindergartens', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify(data)
            });
            const result = await response.json().catch(() => ({}));

            if (!response.ok || !result.success) {
                await UI.error(result.message || '유치원 등록에 실패했습니다.');
                return;
            }

            await UI.success('유치원 등록이 완료되었습니다.');
            window.location.href = '/';
        } catch (error) {
            await UI.error('유치원 등록 중 오류가 발생했습니다. 네트워크 상태를 확인해 주세요.');
        } finally {
            submitButton.disabled = false;
        }
    });
})();
