(() => {
    const page = document.getElementById('classrooms-page');
    const list = document.getElementById('classroom-list');
    const form = document.getElementById('classroom-form');
    const submitButton = document.getElementById('createClassroomSubmit');
    let kindergartenId = Number(page?.dataset.kindergartenId) || null;

    if (!page || !list || !form || !submitButton) return;

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function setListState(message, tone = 'muted') {
        const color = tone === 'error' ? 'text-red-700' : 'text-gray-500';
        list.innerHTML = `<div class="py-8 text-center text-sm leading-6 ${color}">${message}</div>`;
        list.setAttribute('aria-busy', 'false');
    }

    async function ensureKindergartenId() {
        if (kindergartenId) return kindergartenId;
        const response = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
        if (!response.ok) return null;
        const result = await response.json().catch(() => ({}));
        kindergartenId = Number(result.data?.kindergartenId) || null;
        return kindergartenId;
    }

    async function loadClassrooms() {
        list.setAttribute('aria-busy', 'true');
        const id = await ensureKindergartenId();
        if (!id) {
            setListState('소속 유치원이 없어 반 목록을 불러올 수 없습니다. 승인/배정을 먼저 완료하세요.');
            return;
        }

        try {
            const response = await fetch(`/api/v1/classrooms?kindergartenId=${id}`, { credentials: 'same-origin' });
            const result = await response.json().catch(() => ({}));
            if (!response.ok || !Array.isArray(result.data)) {
                setListState('반 목록을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.', 'error');
                return;
            }

            if (result.data.length === 0) {
                setListState('등록된 반이 없습니다. 왼쪽에서 첫 반을 생성하세요.');
                return;
            }

            list.innerHTML = result.data.map((classroom) => {
                const teacher = classroom.teacherName ? `담임 ${escapeHtml(classroom.teacherName)}` : '담임 미배정';
                const age = classroom.ageGroup
                    ? `<span class="rounded-full bg-primary-50 px-2.5 py-1 text-xs font-semibold text-primary-700">${escapeHtml(classroom.ageGroup)}</span>`
                    : '<span class="text-sm text-gray-500">연령대 미지정</span>';
                return `
                    <div class="flex flex-col gap-2 py-4 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <div class="font-semibold text-gray-900">${escapeHtml(classroom.name)}</div>
                            <div class="mt-1 text-sm text-gray-600">${teacher}</div>
                        </div>
                        <div>${age}</div>
                    </div>
                `;
            }).join('');
            list.setAttribute('aria-busy', 'false');
        } catch {
            setListState('반 목록을 불러오지 못했습니다. 네트워크 상태를 확인해 주세요.', 'error');
        }
    }

    async function createClassroom(event) {
        event.preventDefault();
        submitButton.disabled = true;
        try {
            const id = await ensureKindergartenId();
            if (!id) {
                await UI.alert({ title: '소속 유치원이 필요해요', text: '반을 생성하려면 먼저 유치원 소속이 필요합니다.', icon: 'info' });
                return;
            }

            const name = document.getElementById('classroom-name').value.trim();
            const ageGroup = document.getElementById('classroom-age').value.trim();
            const response = await fetch('/api/v1/classrooms', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ kindergartenId: id, name, ageGroup: ageGroup || null })
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                await UI.error(payload.message || '반 생성에 실패했습니다.');
                return;
            }

            await UI.success('반이 생성되었습니다.');
            form.reset();
            await loadClassrooms();
        } catch {
            await UI.error('반 생성 중 오류가 발생했습니다. 네트워크 상태를 확인해 주세요.');
        } finally {
            submitButton.disabled = false;
        }
    }

    window.loadClassrooms = loadClassrooms;
    form.addEventListener('submit', createClassroom);
    loadClassrooms();
})();
