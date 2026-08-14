(() => {
    const page = document.getElementById('kid-detail-page');
    if (!page) return;

    const kidId = page.dataset.kidId;

    const fetchKidDetail = async () => {
        if (!kidId) return null;
        const response = await fetch(`/api/v1/kids/${encodeURIComponent(kidId)}`, { credentials: 'same-origin' });
        if (!response.ok) return null;
        const payload = await response.json().catch(() => ({}));
        return payload.data || null;
    };

    const fetchAvailableParents = async currentParents => {
        const currentIds = (currentParents || []).map(parent => parent.parentId);
        const response = await fetch('/api/v1/members/parents', { credentials: 'same-origin' });
        if (!response.ok) return [];
        const payload = await response.json().catch(() => ({}));
        return (payload.data || []).filter(parent => !currentIds.includes(parent.id));
    };

    const escapeHtml = value => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');

    const escapeAttribute = value => escapeHtml(value).replace(/`/g, '&#096;');

    const openParentManageModal = async () => {
        const kidDetail = await fetchKidDetail();
        if (!kidDetail) {
            await UI.error('원생 정보를 불러오지 못했습니다.');
            return;
        }

        const availableParents = await fetchAvailableParents(kidDetail.parents || []);
        const parentsHtml = kidDetail.parents?.length
            ? kidDetail.parents.map(parent => `
                <div class="flex items-center justify-between border-b border-gray-100 py-2 last:border-0">
                    <div>
                        <span class="font-medium text-gray-900">${escapeHtml(parent.parentName)}</span>
                        <span class="ml-2 text-sm text-gray-600">(${escapeHtml(parent.relationship)})</span>
                    </div>
                    <button type="button" data-action="removeParent" data-kid-id="${kidId}" data-id="${parent.parentId}" data-name="${escapeAttribute(parent.parentName)}" class="min-h-11 rounded-lg px-3 py-2 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50">
                        연결 해제
                    </button>
                </div>`).join('')
            : '<div class="py-4 text-center text-gray-500">연결된 학부모가 없습니다.</div>';

        const parentOptions = availableParents.length
            ? availableParents.map(parent => `<option value="${parent.id}">${escapeHtml(parent.name)}</option>`).join('')
            : '<option value="">추가 가능한 학부모가 없습니다</option>';

        window.Swal.fire({
            title: '학부모 관리',
            html: `
                <div class="text-left space-y-4">
                    <div>
                        <div class="mb-2 font-semibold text-gray-900">${escapeHtml(kidDetail.name)} 원생의 학부모</div>
                        <div id="parent-list">${parentsHtml}</div>
                    </div>
                    <div class="border-t border-gray-200 pt-4">
                        <div class="mb-2 font-medium text-gray-900">학부모 추가</div>
                        <div class="space-y-2">
                            <div>
                                <label for="parent-select" class="mb-1 block text-sm font-medium text-gray-700">학부모 선택 *</label>
                                <select id="parent-select" required class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                                    <option value="">선택</option>${parentOptions}
                                </select>
                                <p class="mt-1 text-xs text-gray-500">학부모 계정만 목록에 표시됩니다.</p>
                            </div>
                            <div>
                                <label for="relationship-select" class="mb-1 block text-sm font-medium text-gray-700">관계 *</label>
                                <select id="relationship-select" required class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                                    <option value="">선택</option>
                                    <option value="FATHER">아버지</option>
                                    <option value="MOTHER">어머니</option>
                                    <option value="GRANDFATHER">할아버지</option>
                                    <option value="GRANDMOTHER">할머니</option>
                                    <option value="GUARDIAN">보호자</option>
                                </select>
                            </div>
                            <button type="button" data-action="assignParent" class="min-h-11 w-full rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-primary-700">
                                학부모 추가
                            </button>
                        </div>
                    </div>
                </div>`,
            showConfirmButton: false,
            showCancelButton: true,
            cancelButtonText: '닫기',
            customClass: {
                popup: 'rounded-xl',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            }
        });
    };

    const assignParent = async () => {
        const parentId = document.getElementById('parent-select')?.value;
        const relationship = document.getElementById('relationship-select')?.value;
        if (!parentId || !relationship) {
            await UI.error('학부모와 관계를 선택해 주세요.');
            return;
        }

        const response = await fetch(`/api/v1/kids/${encodeURIComponent(kidId)}/parents`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ parentId: Number(parentId), relationship })
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '학부모 연결에 실패했습니다.');
            return;
        }
        await UI.success('학부모가 연결되었습니다.');
        window.location.reload();
    };

    const removeParent = async (targetKidId, parentId, parentName) => {
        const confirmed = await UI.confirm({
            title: '학부모 연결 해제',
            text: `${parentName}님의 연결을 해제하시겠습니까?`,
            icon: 'warning',
            confirmButtonText: '해제',
            cancelButtonText: '취소',
            customClass: {
                confirmButton: 'min-h-11 rounded-lg bg-red-600 px-5 py-2.5 font-semibold text-white hover:bg-red-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 hover:bg-gray-50'
            }
        });
        if (!confirmed) return;

        const response = await fetch(`/api/v1/kids/${encodeURIComponent(targetKidId)}/parents/${encodeURIComponent(parentId)}`, {
            method: 'DELETE',
            credentials: 'same-origin'
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '학부모 연결 해제에 실패했습니다.');
            return;
        }
        await UI.success('학부모 연결이 해제되었습니다.');
        window.location.reload();
    };

    window.openParentManageModal = openParentManageModal;
    window.assignParent = assignParent;
    window.removeParent = removeParent;
})();
