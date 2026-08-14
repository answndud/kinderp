    const UI = window.UI;
    const pageRoot = document.getElementById('kids-page');
    const initialKindergartenId = Number(pageRoot?.dataset.kindergartenId) || null;
    const currentRole = pageRoot?.dataset.role || null;
    const isPrincipal = currentRole === 'PRINCIPAL';
    let kindergartenId = initialKindergartenId;
    let classroomsCache = [];
    let classroomCounts = {};
    let currentPage = 1;
    const pageSize = 8;
    let totalPages = 1;
    let pendingAction = null;
    let pendingKidId = null;

    async function fetchMe() {
        const response = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
        if (!response.ok) return null;
        const result = await response.json().catch(() => ({}));
        return result.data || null;
    }

    async function ensureKindergartenId() {
        if (kindergartenId) return kindergartenId;
        const me = await fetchMe();
        if (me && me.kindergartenId) kindergartenId = me.kindergartenId;
        return kindergartenId;
    }

    function renderClassroomOptions(select, classrooms, counts = {}) {
        if (!select) return;
        select.innerHTML = '<option value="">전체</option>' + (classrooms || [])
            .map(c => {
                const count = counts[c.id] || 0;
                return `<option value="${c.id}">${escapeHtml(c.name)} (${count}명)</option>`;
            })
            .join('');
    }

    async function loadClassroomCounts() {
        const id = await ensureKindergartenId();
        if (!id) return {};
        const response = await fetch(`/api/v1/kids/classroom-counts?kindergartenId=${id}`, { credentials: 'same-origin' });
        if (!response.ok) return {};
        const payload = await response.json().catch(() => ({}));
        const list = payload.data || [];
        const counts = {};
        list.forEach(item => {
            counts[item.classroomId] = item.count;
        });
        classroomCounts = counts;
        return counts;
    }

    async function fetchKidsPage(params) {
        const response = await fetch(`/api/v1/kids/page?${params}`, { credentials: 'same-origin' });
        if (!response.ok) return { items: [], totalElements: 0, totalPages: 1 };
        const result = await response.json().catch(() => ({}));
        const data = result.data || {};
        return {
            items: data.content || [],
            totalElements: data.totalElements || 0,
            totalPages: data.totalPages || 1
        };
    }

    function updatePagination(totalCount, pageCount = totalPages) {
        totalPages = Math.max(1, pageCount || Math.ceil(totalCount / pageSize));
        const prevBtn = document.getElementById('kid-prev');
        const nextBtn = document.getElementById('kid-next');
        const indicator = document.getElementById('kid-page-indicator');

        if (prevBtn) prevBtn.disabled = currentPage <= 1;
        if (nextBtn) nextBtn.disabled = currentPage >= totalPages;
        if (prevBtn) prevBtn.dataset.page = String(Math.max(1, currentPage - 1));
        if (nextBtn) nextBtn.dataset.page = String(Math.min(totalPages, currentPage + 1));
        if (indicator) indicator.textContent = `${currentPage} / ${totalPages}`;

        if (prevBtn) {
            prevBtn.classList.toggle('opacity-40', currentPage <= 1);
            prevBtn.classList.toggle('cursor-not-allowed', currentPage <= 1);
        }
        if (nextBtn) {
            nextBtn.classList.toggle('opacity-40', currentPage >= totalPages);
            nextBtn.classList.toggle('cursor-not-allowed', currentPage >= totalPages);
        }

        const summary = document.getElementById('kid-summary');
        if (summary) summary.textContent = `총 ${totalCount}명`;
    }

    function changePage(page) {
        if (page < 1 || page > totalPages) return;
        currentPage = page;
        loadKids({ page: currentPage });
    }

    async function handlePendingAction() {
        if (!pendingAction || !pendingKidId) return;
        const action = pendingAction;
        const kidId = Number(pendingKidId);
        pendingAction = null;
        pendingKidId = null;

        if (action === 'edit') {
            await showEditKidModal(kidId);
        }

        if (action === 'parents') {
            await showParentManageModal(kidId);
        }
    }

    async function loadClassrooms() {
        const id = await ensureKindergartenId();
        const select = document.getElementById('classroom-filter');

        if (!id) {
            select.innerHTML = '<option value="">소속 유치원 없음</option>';
            return;
        }

        const response = await fetch(`/api/v1/classrooms?kindergartenId=${id}`, { credentials: 'same-origin' });
        if (!response.ok) {
            select.innerHTML = '<option value="">반 목록 로드 실패</option>';
            return;
        }

        const result = await response.json().catch(() => ({}));
        classroomsCache = result.data || [];

        const counts = await loadClassroomCounts();
        renderClassroomOptions(select, classroomsCache, counts);
    }

    async function loadKids({ refreshCounts = false, page = currentPage } = {}) {
        const id = await ensureKindergartenId();
        const container = document.getElementById('kid-list');
        if (container) container.setAttribute('aria-busy', 'true');
        const classroomId = document.getElementById('classroom-filter').value;
        const name = document.getElementById('name-filter').value;
        const sortKey = document.getElementById('sort-filter').value || 'name';

        if (!id) {
            container.innerHTML = '<div class="py-8 text-center text-sm leading-6 text-gray-500">소속 유치원이 없어 원생 목록을 불러올 수 없습니다. 승인/배정을 먼저 완료하세요.</div>';
            container.setAttribute('aria-busy', 'false');
            updatePagination(0);
            return;
        }

        const params = new URLSearchParams({
            kindergartenId: id,
            page: Math.max(page - 1, 0),
            size: pageSize,
            sort: sortKey
        });
        if (classroomId) params.append('classroomId', classroomId);
        if (name) params.append('name', name.trim());

        const [pageData] = await Promise.all([
            fetchKidsPage(params),
            refreshCounts ? loadClassroomCounts() : Promise.resolve(classroomCounts)
        ]);

        const kids = pageData.items || [];

        if (classroomsCache.length > 0) {
            renderClassroomOptions(document.getElementById('classroom-filter'), classroomsCache, classroomCounts);
        }

        if (!kids.length) {
            container.innerHTML = '<div class="py-8 text-center text-sm leading-6 text-gray-500">조건에 맞는 원생이 없습니다. 필터를 초기화하거나 새 원생을 등록하세요.</div>';
            container.setAttribute('aria-busy', 'false');
            updatePagination(0, 1);
            return;
        }

        const totalCount = pageData.totalElements || kids.length;
        totalPages = pageData.totalPages || 1;
        currentPage = Math.min(Math.max(page, 1), totalPages);

        updatePagination(totalCount, totalPages);

        container.innerHTML = kids.map(k => {
            const age = k.age ? `(${k.age}세)` : '';
            const classroomName = k.classroomName || '반 미배정';
            const genderText = k.gender === 'MALE' ? '남' : (k.gender === 'FEMALE' ? '여' : '성별 없음');
            const changeClassroomButton = isPrincipal
                ? `<button type="button" data-action="openChangeClassroom" data-id="${k.id}" data-name="${escapeAttribute(k.name)}" data-classroom-id="${k.classroomId || ''}" class="min-h-11 rounded-lg bg-primary-50 px-3 py-2 text-sm font-semibold text-primary-700 transition-colors hover:bg-primary-100">반 변경</button>`
                : '';

            return `
                <div class="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between">
                    <div class="min-w-0">
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="font-semibold text-gray-900">${escapeHtml(k.name)}</span>
                            <span class="text-sm text-gray-500">${escapeHtml(age)}</span>
                            <span class="rounded-full bg-primary-50 px-2.5 py-1 text-xs font-semibold text-primary-700">${escapeHtml(classroomName)}</span>
                        </div>
                        <div class="mt-1 text-sm text-gray-600">${escapeHtml(k.birthDate || '생년월일 없음')} · ${genderText}</div>
                    </div>
                    <div class="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:justify-end">
                        ${changeClassroomButton}
                        <a href="/kids/${k.id}" class="inline-flex min-h-11 items-center justify-center rounded-lg bg-primary-50 px-3 py-2 text-sm font-semibold text-primary-600 transition-colors hover:bg-primary-100">
                            상세
                        </a>
                        <button type="button" data-action="showParentManageModal" data-id="${k.id}" class="min-h-11 rounded-lg bg-primary-50 px-3 py-2 text-sm font-semibold text-primary-600 transition-colors hover:bg-primary-100">
                            학부모 관리
                        </button>
                        <a href="/kids/${k.id}/edit" class="inline-flex min-h-11 items-center justify-center rounded-lg bg-gray-100 px-3 py-2 text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-200">
                            편집
                        </a>
                        <button type="button" data-action="openDeleteKid" data-id="${k.id}" data-name="${escapeAttribute(k.name)}" class="min-h-11 rounded-lg bg-red-50 px-3 py-2 text-sm font-semibold text-red-600 transition-colors hover:bg-red-100">
                            삭제
                        </button>
                    </div>
                </div>
            `;
        }).join('');
        container.setAttribute('aria-busy', 'false');

        await handlePendingAction();
    }

    function resetFilters() {
        document.getElementById('classroom-filter').value = '';
        document.getElementById('name-filter').value = '';
        document.getElementById('sort-filter').value = 'name';
        currentPage = 1;
        loadKids({ page: 1 });
    }

    function openDeleteKid(button) {
        const kidId = Number(button.dataset.id);
        const kidName = button.dataset.name || '원생';
        deleteKid(kidId, kidName);
    }

    function openChangeClassroom(button) {
        const kidId = Number(button.dataset.id);
        const kidName = button.dataset.name || '원생';
        const classroomId = Number(button.dataset.classroomId || 0);
        showChangeClassroomModal(kidId, kidName, classroomId);
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function escapeAttribute(value) {
        return escapeHtml(value).replace(/`/g, '&#096;');
    }

    function jsStringForAttribute(value) {
        return escapeHtml(JSON.stringify(String(value ?? '')));
    }

    async function showChangeClassroomModal(kidId, kidName, currentClassroomId) {
        if (!isPrincipal) {
            await UI.alert({ title: '권한 없음', text: '반 변경은 원장만 할 수 있습니다.', icon: 'info' });
            return;
        }

        if (!classroomsCache.length) {
            await loadClassrooms();
        }

        if (!classroomsCache.length) {
            await UI.alert({ title: '반 없음', text: '반을 먼저 생성해 주세요.', icon: 'info' });
            return;
        }

        const result = await window.Swal.fire({
            title: '반 변경',
            html: `
                <div class="text-left space-y-4 mt-4">
                    <div class="text-sm text-gray-600">${kidName} 원생의 반을 변경합니다.</div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">배정할 반 *</label>
                        <select id="change-classroom-select"
                                class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                        </select>
                    </div>
                </div>
            `,
            focusConfirm: false,
            showCancelButton: true,
            cancelButtonText: '취소',
            confirmButtonText: '변경',
            customClass: {
                popup: 'rounded-xl',
                confirmButton: 'min-h-11 rounded-lg bg-primary-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-primary-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            },
            didOpen: () => {
                const select = document.getElementById('change-classroom-select');
                select.innerHTML = classroomsCache
                    .map(c => `<option value="${c.id}">${c.name}</option>`)
                    .join('');
                if (currentClassroomId) {
                    select.value = String(currentClassroomId);
                }
            },
            preConfirm: () => {
                const selectedId = document.getElementById('change-classroom-select').value;
                if (!selectedId) {
                    UI.error('반을 선택해주세요.');
                    return false;
                }
                return { classroomId: selectedId };
            }
        });

        if (!result.isConfirmed || !result.value) return;

        const response = await fetch(`/api/v1/kids/${kidId}/classroom`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ classroomId: Number(result.value.classroomId) })
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '반 변경에 실패했습니다.');
            return;
        }

        await UI.success('반이 변경되었습니다.');
        classroomCounts = {};
        await loadKids({ refreshCounts: true, page: currentPage });
    }

    async function showCreateKidModal() {
        const id = await ensureKindergartenId();
        if (!id) {
            await UI.alert({
                title: '소속 유치원이 필요해요',
                text: '원생을 추가하려면 먼저 유치원 소속이 필요합니다.',
                icon: 'info'
            });
            return;
        }

        const result = await window.Swal.fire({
            title: '원생 추가',
            html: `
                <form id="create-kid-form" class="text-left space-y-4 mt-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">이름 *</label>
                        <input id="kid-name" required
                               class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                               placeholder="이름을 입력하세요" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">생년월일 *</label>
                        <input id="kid-birthdate" type="date" required
                               class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">성별 *</label>
                        <select id="kid-gender" required
                                class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                            <option value="">선택</option>
                            <option value="MALE">남</option>
                            <option value="FEMALE">여</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">입소일 *</label>
                        <input id="kid-admission-date" type="date" required
                               class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">반 *</label>
                        <select id="kid-classroom" required
                                class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                            <option value="">선택</option>
                        </select>
                    </div>
                </form>
            `,
            focusConfirm: false,
            showCancelButton: true,
            cancelButtonText: '취소',
            confirmButtonText: '추가',
            customClass: {
                popup: 'rounded-xl',
                confirmButton: 'min-h-11 rounded-lg bg-primary-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-primary-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            },
            didOpen: async () => {
                const classroomSelect = document.getElementById('kid-classroom');
                const response = await fetch(`/api/v1/classrooms?kindergartenId=${id}`, { credentials: 'same-origin' });
                if (response.ok) {
                    const payload = await response.json().catch(() => ({}));
                    const classrooms = payload.data || [];
                    classroomSelect.innerHTML = '<option value="">선택</option>' + classrooms
                        .map(c => `<option value="${c.id}">${c.name}</option>`)
                        .join('');
                }

                document.getElementById('kid-admission-date').value = window.AppTime.todayInputValue();
            },
            preConfirm: () => {
                const name = document.getElementById('kid-name').value;
                const birthDate = document.getElementById('kid-birthdate').value;
                const gender = document.getElementById('kid-gender').value;
                const admissionDate = document.getElementById('kid-admission-date').value;
                const classroomId = document.getElementById('kid-classroom').value;

                if (!name || !birthDate || !gender || !admissionDate || !classroomId) {
                    UI.error('필수 값을 모두 입력해 주세요.');
                    return false;
                }

                return { name, birthDate, gender, admissionDate, classroomId };
            }
        });

        if (!result.isConfirmed) {
            return;
        }

        const formValues = result.value;
        if (!formValues) {
            return;
        }

        const response = await fetch('/api/v1/kids', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: formValues.name,
                birthDate: formValues.birthDate,
                gender: formValues.gender,
                admissionDate: formValues.admissionDate,
                classroomId: Number(formValues.classroomId)
            })
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '원생 추가에 실패했습니다.');
            return;
        }

        await UI.success('원생이 추가되었습니다.');
        classroomCounts = {};
        currentPage = 1;
        await loadKids({ refreshCounts: true, page: 1 });
        await loadClassrooms();
    }

    async function showEditKidModal(kidId) {
        const result = await window.Swal.fire({
            title: '원생 정보 수정',
            html: `
                <form id="edit-kid-form" class="text-left space-y-4 mt-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">이름 *</label>
                        <input id="edit-kid-name" required
                               class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                               placeholder="이름을 입력하세요" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">생년월일 *</label>
                        <input id="edit-kid-birthdate" type="date" required
                               class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">성별 *</label>
                        <select id="edit-kid-gender" required
                                class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                            <option value="">선택</option>
                            <option value="MALE">남</option>
                            <option value="FEMALE">여</option>
                        </select>
                    </div>
                </form>
            `,
            focusConfirm: false,
            showCancelButton: true,
            cancelButtonText: '취소',
            confirmButtonText: '수정',
            customClass: {
                popup: 'rounded-xl',
                confirmButton: 'min-h-11 rounded-lg bg-primary-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-primary-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            },
            didOpen: async () => {
                const response = await fetch(`/api/v1/kids/${kidId}`, { credentials: 'same-origin' });
                if (!response.ok) return;

                const payload = await response.json().catch(() => ({}));
                const kid = payload.data;

                if (kid) {
                    document.getElementById('edit-kid-name').value = kid.name;
                    document.getElementById('edit-kid-birthdate').value = kid.birthDate;
                    document.getElementById('edit-kid-gender').value = kid.gender;
                }
            },
            preConfirm: () => {
                const name = document.getElementById('edit-kid-name').value;
                const birthDate = document.getElementById('edit-kid-birthdate').value;
                const gender = document.getElementById('edit-kid-gender').value;

                if (!name || !birthDate || !gender) {
                    UI.error('필수 값을 모두 입력해 주세요.');
                    return false;
                }

                return { name, birthDate, gender };
            }
        });

        if (!result.isConfirmed) {
            return;
        }

        const formValues = result.value;
        if (!formValues) {
            return;
        }

        const response = await fetch(`/api/v1/kids/${kidId}`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: formValues.name,
                birthDate: formValues.birthDate,
                gender: formValues.gender
            })
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '원생 정보 수정에 실패했습니다.');
            return;
        }

        await UI.success('원생 정보가 수정되었습니다.');
        await loadKids({ page: currentPage });
    }

    async function deleteKid(kidId, kidName) {
        const confirmed = await UI.confirm({
            title: '원생 삭제',
            text: `${kidName} 원생을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`,
            icon: 'warning',
            confirmButtonText: '삭제',
            cancelButtonText: '취소',
            customClass: {
                confirmButton: 'min-h-11 rounded-lg bg-red-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-red-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            }
        });

        if (confirmed) {
            const response = await fetch(`/api/v1/kids/${kidId}`, {
                method: 'DELETE',
                credentials: 'same-origin'
            });

            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                await UI.error(payload.message || '원생 삭제에 실패했습니다.');
                return;
            }

            await UI.success('원생이 삭제되었습니다.');
            classroomCounts = {};
            await loadKids({ refreshCounts: true, page: currentPage });
        }
    }

    async function fetchAvailableParents(currentParents) {
        const currentIds = (currentParents || []).map(parent => parent.parentId);
        const response = await fetch('/api/v1/members/parents', { credentials: 'same-origin' });
        if (!response.ok) return [];
        const payload = await response.json().catch(() => ({}));
        const parents = payload.data || [];
        return parents.filter(parent => !currentIds.includes(parent.id));
    }

    async function showParentManageModal(kidId) {
        const response = await fetch(`/api/v1/kids/${kidId}`, { credentials: 'same-origin' });

        if (!response.ok) {
            await UI.error('원생 정보를 불러오지 못했습니다.');
            return;
        }

        const result = await response.json().catch(() => ({}));
        const kidDetail = result.data;

        if (!kidDetail) {
            await UI.error('원생 정보를 찾을 수 없습니다.');
            return;
        }

        const availableParents = await fetchAvailableParents(kidDetail.parents || []);

        const parentsHtml = kidDetail.parents && kidDetail.parents.length > 0
            ? kidDetail.parents.map(p => `
                <div class="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
                    <div>
                        <span class="font-medium text-gray-900">${escapeHtml(p.parentName)}</span>
                        <span class="ml-2 text-sm text-gray-600">(${escapeHtml(p.relationship)})</span>
                    </div>
                    <button type="button" data-action="removeParent" data-kid-id="${kidId}" data-id="${p.parentId}" data-name="${escapeAttribute(p.parentName)}" class="min-h-11 rounded-lg px-3 py-2 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50">
                        연결 해제
                    </button>
                </div>
            `).join('')
            : '<div class="text-gray-500 text-center py-4">연결된 학부모가 없습니다.</div>';

        const parentOptions = availableParents.length > 0
            ? availableParents.map(parent => `<option value="${parent.id}">${escapeHtml(parent.name)}</option>`).join('')
            : '<option value="">추가 가능한 학부모가 없습니다</option>';

        window.Swal.fire({
            title: '학부모 관리',
            html: `
                <div class="text-left space-y-4">
                    <div>
                        <div class="font-semibold text-gray-900 mb-2">${escapeHtml(kidDetail.name)} 원생의 학부모</div>
                        <div id="parent-list">
                            ${parentsHtml}
                        </div>
                    </div>
                    <div class="pt-4 border-t border-gray-200">
                        <div class="font-medium text-gray-900 mb-2">학부모 추가</div>
                        <div class="space-y-2">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">학부모 선택 *</label>
                                <select id="parent-select" required
                                        class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                                    <option value="">선택</option>
                                    ${parentOptions}
                                </select>
                                <p class="mt-1 text-xs text-gray-500">학부모 계정만 목록에 표시됩니다.</p>
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">관계 *</label>
                                <select id="relationship-select" required
                                        class="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
                                    <option value="">선택</option>
                                    <option value="FATHER">아버지</option>
                                    <option value="MOTHER">어머니</option>
                                    <option value="GRANDFATHER">할아버지</option>
                                    <option value="GRANDMOTHER">할머니</option>
                                    <option value="GUARDIAN">보호자</option>
                                </select>
                            </div>
                            <button type="button" data-action="assignParent" data-id="${kidId}" class="min-h-11 w-full rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-primary-700">
                                학부모 추가
                            </button>
                        </div>
                    </div>
                </div>
            `,
            showConfirmButton: false,
            showCancelButton: true,
            cancelButtonText: '닫기',
            customClass: {
                popup: 'rounded-xl',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            }
        });
    }

    async function assignParent(kidId) {
        const parentId = document.getElementById('parent-select').value;
        const relationship = document.getElementById('relationship-select').value;

        if (!parentId || !relationship) {
            UI.error('학부모와 관계를 선택해주세요.');
            return;
        }

        const parentSelect = document.getElementById('parent-select');
        const relationshipSelect = document.getElementById('relationship-select');

        const response = await fetch(`/api/v1/kids/${kidId}/parents`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                parentId: Number(parentId),
                relationship: relationship
            })
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            await UI.error(payload.message || '학부모 연결에 실패했습니다.');
            return;
        }

        await UI.success('학부모가 연결되었습니다.');
        if (parentSelect) parentSelect.value = '';
        if (relationshipSelect) relationshipSelect.value = '';
        await showParentManageModal(kidId);
    }

    async function removeParent(kidId, parentId, parentName) {
        const confirmed = await UI.confirm({
            title: '학부모 연결 해제',
            text: `${parentName}님의 연결을 해제하시겠습니까?`,
            icon: 'warning',
            confirmButtonText: '해제',
            cancelButtonText: '취소',
            customClass: {
                confirmButton: 'min-h-11 rounded-lg bg-red-600 px-5 py-2.5 font-semibold text-white transition-colors hover:bg-red-700',
                cancelButton: 'min-h-11 rounded-lg border border-gray-300 px-5 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-50'
            }
        });

        if (confirmed) {
            const response = await fetch(`/api/v1/kids/${kidId}/parents/${parentId}`, {
                method: 'DELETE',
                credentials: 'same-origin'
            });

            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                await UI.error(payload.message || '학부모 연결 해제에 실패했습니다.');
                return;
            }

            await UI.success('학부모 연결이 해제되었습니다.');
            await showParentManageModal(kidId);
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const params = new URLSearchParams(window.location.search);
        pendingAction = params.get('action');
        pendingKidId = params.get('kidId');
        await loadClassrooms();
        await loadKids({ page: 1 });
    });
