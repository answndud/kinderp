(() => {
    const list = document.getElementById('kindergartenList');
    if (!list) return;

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function setBusy(value) {
        list.setAttribute('aria-busy', String(value));
    }

    async function loadKindergartens() {
        setBusy(true);
        try {
            const response = await fetch('/api/v1/kindergartens', { credentials: 'same-origin' });
            const result = await response.json().catch(() => ({}));
            const kindergartens = Array.isArray(result.data) ? result.data : [];

            if (!response.ok || !result.success || kindergartens.length === 0) {
                list.innerHTML = '<p class="rounded-xl border border-gray-200 bg-white px-5 py-8 text-center text-sm leading-6 text-gray-500">등록된 유치원이 없습니다. 원장 계정이 먼저 유치원을 등록해야 합니다.</p>';
                return;
            }

            list.innerHTML = kindergartens.map((kindergarten) => `
                <button type="button"
                        class="flex min-h-16 w-full items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white px-4 py-3 text-left transition-colors hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                        data-action="selectKindergarten" data-id="${Number(kindergarten.id)}">
                    <span class="min-w-0">
                        <span class="block font-semibold text-gray-900">${escapeHtml(kindergarten.name)}</span>
                        <span class="mt-1 block text-sm text-gray-500">${escapeHtml(kindergarten.address || '주소 미등록')}</span>
                    </span>
                    <svg class="h-5 w-5 shrink-0 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7-7"/>
                    </svg>
                </button>
            `).join('');
        } catch {
            list.innerHTML = '<p class="rounded-lg border border-red-200 bg-red-50 px-4 py-5 text-center text-sm leading-6 text-red-700">유치원 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>';
        } finally {
            setBusy(false);
        }
    }

    async function selectKindergarten(kindergartenId) {
        const id = Number(kindergartenId);
        const button = list.querySelector(`[data-action="selectKindergarten"][data-id="${id}"]`);
        if (!id) return;

        button.disabled = true;
        const confirmed = await UI.confirm({
            title: '유치원 소속 선택',
            text: '이 유치원에 소속하시겠습니까?',
            confirmText: '소속하기',
            cancelText: '취소',
            icon: 'question',
            showCancelButton: true
        });
        if (!confirmed) {
            button.disabled = false;
            return;
        }

        try {
            const note = await UI.promptTextarea({
                title: '지원 메시지 (선택)',
                label: '원장님에게 전달할 메시지를 입력해 주세요',
                placeholder: '예) 담임 배정이 가능하면 빠르게 합류하고 싶습니다.'
            });
            if (note?.isDismissed) return;

            const response = await fetch('/api/v1/kindergarten-applications', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    kindergartenId: id,
                    message: note?.value?.trim() || null
                })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok || result.success === false) {
                throw new Error(result.message || '유치원 지원 요청에 실패했습니다.');
            }

            await UI.success('지원이 접수되었습니다. 승인 전까지 대기 페이지로 이동합니다.');
            window.location.href = '/applications/pending';
        } catch (error) {
            await UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
        } finally {
            button.disabled = false;
        }
    }

    window.selectKindergarten = selectKindergarten;
    loadKindergartens();
})();
