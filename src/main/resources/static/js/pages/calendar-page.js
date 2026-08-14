(() => {
    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#39;');

    document.addEventListener('DOMContentLoaded', async () => {
        const calendarPage = document.getElementById('calendar-page');
        const role = calendarPage?.dataset.role || null;
        const memberId = calendarPage?.dataset.memberId || null;
        let kindergartenId = calendarPage?.dataset.kindergartenId || null;

        const startDateInput = document.getElementById('startDate');
        const endDateInput = document.getElementById('endDate');
        const scopeSelect = document.getElementById('scopeType');
        const classroomSelect = document.getElementById('classroomId');
        const todayBtn = document.getElementById('todayBtn');
        const createEventBtn = document.getElementById('createEventBtn');

        const toDateString = (date) => new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit'
        }).format(date);

        const setDefaultRange = () => {
            const now = new Date();
            const parts = new Intl.DateTimeFormat('en-US', {
                timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric'
            }).formatToParts(now).reduce((result, part) => {
                result[part.type] = Number(part.value);
                return result;
            }, {});
            const start = new Date(Date.UTC(parts.year, parts.month - 1, 1));
            const end = new Date(Date.UTC(parts.year, parts.month, 0));
            startDateInput.value = toDateString(start);
            endDateInput.value = toDateString(end);
        };

        const triggerReload = () => {
            if (window.htmx) {
                htmx.trigger(document.body, 'calendar-filters-changed');
            }
        };

        const fetchJson = async (url) => {
            const response = await fetch(url, { credentials: 'same-origin' });
            if (!response.ok) return null;
            return response.json().catch(() => null);
        };

        const ensureKindergartenId = async () => {
            if (kindergartenId) return kindergartenId;
            const me = await fetchJson('/api/v1/members/me');
            if (me && me.data && me.data.kindergartenId) {
                kindergartenId = me.data.kindergartenId;
            }
            return kindergartenId;
        };

        const loadClassrooms = async () => {
            if (role === 'PARENT') {
                const res = await fetchJson('/api/v1/kids/my-kids');
                const kids = res && res.data ? res.data : [];
                const unique = new Map();
                kids.forEach(kid => {
                    if (kid.classroomId && !unique.has(kid.classroomId)) {
                        unique.set(kid.classroomId, { id: kid.classroomId, name: kid.classroomName });
                    }
                });
                return Array.from(unique.values());
            }

            const id = await ensureKindergartenId();
            if (!id) return [];
            const res = await fetchJson(`/api/v1/classrooms?kindergartenId=${id}`);
            const classrooms = res && res.data ? res.data : [];

            if (role === 'TEACHER' && memberId) {
                return classrooms.filter(c => c.teacherId === memberId);
            }
            return classrooms;
        };

        const populateClassrooms = async () => {
            const classrooms = await loadClassrooms();
            classroomSelect.innerHTML = '<option value="">전체</option>';
            classrooms.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.name;
                classroomSelect.appendChild(opt);
            });
            classroomSelect.disabled = scopeSelect.value !== 'CLASSROOM';
        };

        const updateScopeOptions = () => {
            const options = Array.from(scopeSelect.options);
            options.forEach(option => {
                if (option.value === 'KINDERGARTEN' && role === 'PARENT') {
                    option.remove();
                }
            });
        };

        const normalizeScopeForCreate = (scope) => {
            if (role === 'PARENT') {
                return 'PERSONAL';
            }
            if (role === 'TEACHER' && scope === 'KINDERGARTEN') {
                return 'CLASSROOM';
            }
            return scope;
        };

        setDefaultRange();
        updateScopeOptions();
        await populateClassrooms();
        triggerReload();

        scopeSelect.addEventListener('change', async () => {
            classroomSelect.disabled = scopeSelect.value !== 'CLASSROOM';
            if (scopeSelect.value !== 'CLASSROOM') {
                classroomSelect.value = '';
            }
            triggerReload();
        });

        startDateInput.addEventListener('change', triggerReload);
        endDateInput.addEventListener('change', triggerReload);
        classroomSelect.addEventListener('change', triggerReload);

        todayBtn.addEventListener('click', () => {
            const today = new Date();
            startDateInput.value = toDateString(today);
            endDateInput.value = toDateString(today);
            triggerReload();
        });

        createEventBtn.addEventListener('click', async () => {
            const classrooms = await loadClassrooms();
            const scopeOptions = [];

            if (role === 'PRINCIPAL') {
                scopeOptions.push({ value: 'KINDERGARTEN', label: '유치원 전체' });
                scopeOptions.push({ value: 'PERSONAL', label: '개인' });
            } else if (role === 'TEACHER') {
                scopeOptions.push({ value: 'CLASSROOM', label: '반' });
                scopeOptions.push({ value: 'PERSONAL', label: '개인' });
            } else {
                scopeOptions.push({ value: 'PERSONAL', label: '개인' });
            }

            const eventTypeOptions = [
                { value: 'LESSON', label: '수업' },
                { value: 'EVENT', label: '행사' },
                { value: 'HOLIDAY', label: '휴일' },
                { value: 'MEETING', label: '회의' },
                { value: 'EXAM', label: '평가' },
                { value: 'FIELD_TRIP', label: '현장학습' },
                { value: 'ETC', label: '기타' }
            ];

            const repeatOptions = [
                { value: 'NONE', label: '반복 없음' },
                { value: 'DAILY', label: '매일' },
                { value: 'WEEKLY', label: '매주' },
                { value: 'MONTHLY', label: '매월' }
            ];

            const classroomOptionsHtml = classrooms.map(c => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.name)}</option>`).join('');
            const scopeOptionsHtml = scopeOptions.map(o => `<option value="${o.value}">${o.label}</option>`).join('');
            const eventTypeHtml = eventTypeOptions.map(o => `<option value="${o.value}">${o.label}</option>`).join('');
            const repeatTypeHtml = repeatOptions.map(o => `<option value="${o.value}">${o.label}</option>`).join('');

            const result = await Swal.fire({
                title: '새 일정 등록',
                html: `
                    <div class="text-left space-y-3">
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">범위</label>
                            <select id="swal-scope" class="w-full h-11 px-3 border border-gray-200 rounded-lg">${scopeOptionsHtml}</select>
                        </div>
                        <div id="swal-classroom-wrap" class="hidden">
                            <label class="block text-xs font-semibold text-gray-600 mb-1">반 선택</label>
                            <select id="swal-classroom" class="w-full h-11 px-3 border border-gray-200 rounded-lg">${classroomOptionsHtml}</select>
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">일정 유형</label>
                            <select id="swal-event-type" class="w-full h-11 px-3 border border-gray-200 rounded-lg">${eventTypeHtml}</select>
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">제목</label>
                            <input id="swal-title" class="w-full h-11 px-3 border border-gray-200 rounded-lg" placeholder="예) 봄소풍" />
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">시작</label>
                            <input id="swal-start" type="datetime-local" class="w-full h-11 px-3 border border-gray-200 rounded-lg" />
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">종료</label>
                            <input id="swal-end" type="datetime-local" class="w-full h-11 px-3 border border-gray-200 rounded-lg" />
                        </div>
                        <div class="flex items-center gap-2">
                            <input id="swal-all-day" type="checkbox" class="rounded" />
                            <label for="swal-all-day" class="text-sm text-gray-700">종일 일정</label>
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">장소</label>
                            <input id="swal-location" class="w-full h-11 px-3 border border-gray-200 rounded-lg" placeholder="예) 운동장" />
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">반복</label>
                            <select id="swal-repeat" class="w-full h-11 px-3 border border-gray-200 rounded-lg">${repeatTypeHtml}</select>
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">반복 종료일</label>
                            <input id="swal-repeat-end" type="date" class="w-full h-11 px-3 border border-gray-200 rounded-lg" />
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-gray-600 mb-1">설명</label>
                            <textarea id="swal-description" rows="4" class="w-full px-3 py-3 border border-gray-200 rounded-lg" placeholder="일정 설명을 입력하세요."></textarea>
                        </div>
                    </div>
                `,
                showCancelButton: true,
                confirmButtonText: '등록',
                cancelButtonText: '취소',
                customClass: {
                    popup: 'rounded-xl',
                    confirmButton: 'min-h-11 px-4 rounded-lg bg-primary-600 text-white font-semibold',
                    cancelButton: 'min-h-11 px-4 rounded-lg bg-gray-100 text-gray-700 font-semibold'
                },
                buttonsStyling: false,
                didOpen: () => {
                    const scopeEl = document.getElementById('swal-scope');
                    const classroomWrap = document.getElementById('swal-classroom-wrap');
                    const toggleClassroom = () => {
                        classroomWrap.classList.toggle('hidden', scopeEl.value !== 'CLASSROOM');
                    };
                    scopeEl.addEventListener('change', toggleClassroom);
                    toggleClassroom();
                },
                preConfirm: () => {
                    const scope = normalizeScopeForCreate(document.getElementById('swal-scope').value);
                    const classroomIdValue = document.getElementById('swal-classroom').value;
                    const title = document.getElementById('swal-title').value;
                    const start = document.getElementById('swal-start').value;
                    const end = document.getElementById('swal-end').value;
                    const eventType = document.getElementById('swal-event-type').value;
                    const isAllDay = document.getElementById('swal-all-day').checked;
                    const location = document.getElementById('swal-location').value;
                    const repeatType = document.getElementById('swal-repeat').value;
                    const repeatEndDate = document.getElementById('swal-repeat-end').value;
                    const description = document.getElementById('swal-description').value;

                    if (!title || title.trim() === '') {
                        Swal.showValidationMessage('제목을 입력해 주세요.');
                        return false;
                    }
                    if (!start || !end) {
                        Swal.showValidationMessage('시작/종료 일시는 필수입니다.');
                        return false;
                    }
                    if (scope === 'CLASSROOM' && !classroomIdValue) {
                        Swal.showValidationMessage('반을 선택해 주세요.');
                        return false;
                    }

                    return {
                        scopeType: scope,
                        classroomId: scope === 'CLASSROOM' ? Number(classroomIdValue) : null,
                        title: title.trim(),
                        description: description ? description.trim() : null,
                        startDateTime: start,
                        endDateTime: end,
                        eventType: eventType,
                        isAllDay: isAllDay,
                        location: location ? location.trim() : null,
                        repeatType: repeatType,
                        repeatEndDate: repeatEndDate || null
                    };
                }
            });

            if (!result.isConfirmed) return;

            try {
                const response = await fetch('/api/v1/calendar/events', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify(result.value)
                });
                const payload = await response.json().catch(() => ({}));
                if (!response.ok) {
                    throw new Error(payload.message || '일정 등록에 실패했습니다.');
                }
                await UI.success('일정을 등록했습니다.');
                triggerReload();
            } catch (error) {
                await UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
            }
        });
    });

})();
