(() => {
    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#39;');

    document.addEventListener('DOMContentLoaded', async function() {
            const role = document.querySelector('main[data-role]')?.dataset.role || null;
            const createButton = document.getElementById('createNotepadBtn');
            if (role === 'PRINCIPAL' || role === 'TEACHER') {
                createButton.classList.remove('hidden');
                createButton.classList.add('flex');
            }

            const isParent = role === 'PARENT';

            // Get kindergartenId from current member (fallback: /api/v1/members/me)
            let kindergartenId = null;

            const filterClassroomSelect = document.getElementById('filterClassroom');
            const filterKidSelect = document.getElementById('filterKid');
            const notepadStatus = document.getElementById('notepadStatus');

            const setNotepadStatus = (message, isError = false) => {
                if (!notepadStatus) return;
                notepadStatus.textContent = message;
                notepadStatus.classList.toggle('text-red-600', isError);
                notepadStatus.classList.toggle('text-gray-500', !isError);
            };

            const triggerListReload = () => {
                setNotepadStatus('필터를 적용하는 중입니다.');
                if (window.htmx) {
                    htmx.trigger(document.body, 'filters-changed');
                }
                [300, 1000, 2000].forEach(delay => window.setTimeout(updateListStatus, delay));
            };

            const setOptions = (select, placeholderLabel, items) => {
                select.innerHTML = '';

                const placeholder = document.createElement('option');
                placeholder.value = '';
                placeholder.textContent = placeholderLabel;
                select.appendChild(placeholder);

                (items || []).forEach(item => {
                    const option = document.createElement('option');
                    option.value = item.id;
                    option.textContent = item.name;
                    select.appendChild(option);
                });
            };

            const fetchList = async (url) => {
                const response = await fetch(url, { credentials: 'same-origin' });
                if (!response.ok) {
                    return [];
                }
                const result = await response.json().catch(() => ({}));
                return result.data || [];
            };

            const fetchMe = async () => {
                const response = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
                if (!response.ok) return null;
                const result = await response.json().catch(() => ({}));
                return result.data || null;
            };

            const ensureKindergartenId = async () => {
                if (kindergartenId) return kindergartenId;

                // principal/teacher만 필요한 값이라, 없으면 한번 더 조회
                const me = await fetchMe();
                if (me && me.kindergartenId) {
                    kindergartenId = me.kindergartenId;
                }

                return kindergartenId;
            };

            const loadClassrooms = async () => {
                const id = await ensureKindergartenId();
                if (!id) return [];
                return fetchList(`/api/v1/classrooms?kindergartenId=${id}`);
            };

            const loadKidsByClassroom = async (classroomId) => {
                if (!classroomId) return [];
                return fetchList(`/api/v1/kids?classroomId=${classroomId}`);
            };

            const loadMyKids = async () => {
                return fetchList('/api/v1/kids/my-kids');
            };

            // Load classrooms (filters)
            try {
                const classrooms = await loadClassrooms();
                setOptions(filterClassroomSelect, classrooms.length ? '전체 반' : '등록된 반이 없습니다', classrooms);

                // 원장/교사: 반 선택 전에는 원생 필터 비활성화
                if (!classrooms.length) {
                    filterKidSelect.disabled = true;
                }
            } catch (error) {
                setNotepadStatus('반 목록을 불러오지 못했습니다. 새로고침 후 다시 시도하세요.', true);
            }

            // Filter: kids depend on classroom (PRINCIPAL/TEACHER)
            filterClassroomSelect.addEventListener('change', async () => {
                filterKidSelect.value = '';
                setOptions(filterKidSelect, '전체 원생', []);

                if (isParent) {
                    triggerListReload();
                    return;
                }

                try {
                    const kids = await loadKidsByClassroom(filterClassroomSelect.value);
                    setOptions(filterKidSelect, '전체 원생', kids);
                    filterKidSelect.disabled = !filterClassroomSelect.value;
                } catch (error) {
                    setNotepadStatus('원생 목록을 불러오지 못했습니다. 다시 시도하세요.', true);
                }

                triggerListReload();
            });

            filterKidSelect.addEventListener('change', triggerListReload);

            const updateListStatus = () => {
                const list = document.getElementById('notepad-list');
                if (!list) return;
                if (list.querySelector('.animate-spin')) return;
                const itemCount = list.querySelectorAll('a[href^="/notepad/"]').length;
                setNotepadStatus(itemCount > 0 ? `${itemCount}개의 알림장을 표시하고 있습니다.` : '조건에 맞는 알림장이 없습니다.');
            };

            document.body.addEventListener('htmx:afterSettle', (event) => {
                if (event.detail?.elt?.id === 'notepad-list' || document.getElementById('notepad-list')) {
                    updateListStatus();
                }
            });

            // Parent: load my kids into filter
            if (isParent) {
                try {
                    const kids = await loadMyKids();
                    setOptions(filterKidSelect, '전체 원생', kids);
                    filterKidSelect.disabled = kids.length === 0;

                    if (kids.length > 0) {
                        filterKidSelect.value = kids[0].id;
                    }
                } catch (error) {
                    setNotepadStatus('내 원생 목록을 불러오지 못했습니다. 다시 시도하세요.', true);
                }

                triggerListReload();
            }

            document.body.addEventListener('htmx:responseError', (event) => {
                if (event.detail?.path?.includes('/notepad')) {
                    setNotepadStatus('알림장을 불러오지 못했습니다. 네트워크 상태를 확인하세요.', true);
                }
            });

            // 알림장 작성 (SweetAlert2)
            const createBtn = document.getElementById('createNotepadBtn');
            createBtn.addEventListener('click', async () => {
                try {
                    const classrooms = await loadClassrooms();
                    if (!classrooms.length) {
                        await UI.alert({
                            title: '반이 없습니다',
                            text: '알림장을 작성하려면 먼저 반을 생성해 주세요.',
                            icon: 'info'
                        });
                        return;
                    }

                    const classroomOptions = {};
                    classrooms.forEach(c => {
                        classroomOptions[String(c.id)] = c.ageGroup ? `${c.name} (${c.ageGroup})` : c.name;
                    });

                    const formId = `notepad-form-${Date.now()}`;

                    const result = await Swal.fire({
                        title: '알림장 작성',
                        html: `
                            <form id="${formId}" class="text-left space-y-3">
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">반 선택 *</label>
                                    <select id="swal-classroom" class="w-full h-11 px-3 border border-gray-200 rounded-lg">
                                        ${Object.entries(classroomOptions).map(([id, label]) => `<option value="${escapeHtml(id)}">${escapeHtml(label)}</option>`).join('')}
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">대상 원생 (선택)</label>
                                    <select id="swal-kid" class="w-full h-11 px-3 border border-gray-200 rounded-lg">
                                        <option value="">전체</option>
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">제목 *</label>
                                    <input id="swal-title" class="w-full h-11 px-3 border border-gray-200 rounded-lg" placeholder="예) 오늘의 활동 안내" />
                                </div>
                                <div>
                                    <label class="block text-sm font-medium text-gray-700 mb-1">내용 *</label>
                                    <textarea id="swal-content" rows="6" class="w-full px-3 py-3 border border-gray-200 rounded-lg" placeholder="오늘 아이들이 어떤 활동을 했는지 적어 주세요."></textarea>
                                </div>
                            </form>
                        `,
                        focusConfirm: false,
                        showCancelButton: true,
                        confirmButtonText: '작성',
                        cancelButtonText: '취소',
                        customClass: {
                            popup: 'rounded-xl',
                            confirmButton: 'min-h-11 px-4 rounded-lg bg-primary-600 text-white font-semibold',
                            cancelButton: 'min-h-11 px-4 rounded-lg bg-gray-100 text-gray-700 font-semibold',
                        },
                        buttonsStyling: false,
                        didOpen: async () => {
                            const classroomEl = document.getElementById('swal-classroom');
                            const kidEl = document.getElementById('swal-kid');

                            const loadKids = async () => {
                                const kids = await loadKidsByClassroom(classroomEl.value);
                                kidEl.innerHTML = '<option value="">전체</option>';
                                kids.forEach(k => {
                                    const opt = document.createElement('option');
                                    opt.value = k.id;
                                    opt.textContent = k.name;
                                    kidEl.appendChild(opt);
                                });
                            };

                            classroomEl.addEventListener('change', loadKids);
                            await loadKids();
                        },
                        preConfirm: () => {
                            const classroomIdValue = document.getElementById('swal-classroom').value;
                            const kidIdValue = document.getElementById('swal-kid').value;
                            const title = document.getElementById('swal-title').value;
                            const content = document.getElementById('swal-content').value;

                            if (!classroomIdValue) {
                                Swal.showValidationMessage('반을 선택해 주세요.');
                                return false;
                            }
                            if (!title || title.trim() === '') {
                                Swal.showValidationMessage('제목을 입력해 주세요.');
                                return false;
                            }
                            if (!content || content.trim() === '') {
                                Swal.showValidationMessage('내용을 입력해 주세요.');
                                return false;
                            }

                            return {
                                classroomId: Number(classroomIdValue),
                                kidId: kidIdValue ? Number(kidIdValue) : null,
                                title: title.trim(),
                                content: content.trim()
                            };
                        }
                    });

                    if (!result.isConfirmed) return;

                    const response = await fetch('/api/v1/notepads', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(result.value)
                    });

                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok) {
                        throw new Error(payload.message || '작성 중 오류가 발생했습니다.');
                    }

                    await UI.success('알림장을 작성했습니다.');
                    triggerListReload();
                } catch (e) {
                    await UI.error(e.message);
                }
            });
        });

})();
