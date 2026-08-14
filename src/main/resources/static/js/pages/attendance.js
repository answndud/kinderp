document.addEventListener('alpine:init', () => {
            Alpine.data('classroomLoader', () => ({
                classrooms: [],
                async init() {
                    try {
                        const res = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
                        if (!res.ok) return;
                        const me = await res.json().catch(() => ({}));
                        if (!me.success || !me.data) return;
                        const kindergartenId = me.data.kindergartenId;
                        if (!kindergartenId) return;

                        const classroomsRes = await fetch(`/api/v1/classrooms?kindergartenId=${kindergartenId}`, { credentials: 'same-origin' });
                        if (classroomsRes.ok) {
                            const d = await classroomsRes.json().catch(() => ({}));
                            this.classrooms = Array.isArray(d.data) ? d.data : (d.data?.content || []);

                            if (this.classrooms.length > 0) {
                                requestAnimationFrame(() => {
                                    const select = document.getElementById('classroomFilter');
                                    if (select && !select.value) {
                                        select.value = String(this.classrooms[0].id);
                                        if (typeof loadAttendance === 'function') {
                                            loadAttendance();
                                        }
                                    }
                                });
                            }
                            setAttendanceStatus(`${this.classrooms.length}개 반을 불러왔습니다. 날짜와 반을 선택하세요.`);
                        }
                    } catch (e) {
                        setAttendanceStatus('반 목록을 불러오지 못했습니다. 새로고침 후 다시 시도하세요.', true);
                    }
                }
            }));
        });

        let loadAttendance = null;

        document.addEventListener('DOMContentLoaded', function() {
            // Show quick actions for Principal/Teacher
            const userRole = document.getElementById('attendance-page')?.dataset.role || '';
            if (userRole === 'PRINCIPAL' || userRole === 'TEACHER') {
                document.getElementById('quickActions').classList.remove('hidden');
            }

            // Keep the date aligned with the product timezone instead of UTC.
            const today = window.AppTime.todayInputValue();
            document.getElementById('selectedDate').value = today;

            const shiftDate = (value, days) => {
                const [year, month, day] = value.split('-').map(Number);
                const date = new Date(Date.UTC(year, month - 1, day + days));
                return date.toISOString().slice(0, 10);
            };

            // Date navigation
            document.getElementById('prevDay').addEventListener('click', function() {
                const dateInput = document.getElementById('selectedDate');
                dateInput.value = shiftDate(dateInput.value, -1);
                loadAttendance();
            });

            document.getElementById('nextDay').addEventListener('click', function() {
                const dateInput = document.getElementById('selectedDate');
                dateInput.value = shiftDate(dateInput.value, 1);
                loadAttendance();
            });

            document.getElementById('selectedDate').addEventListener('change', function() {
                loadAttendance();
            });

            document.getElementById('classroomFilter').addEventListener('change', function() {
                loadAttendance();
            });

            // Load attendance via API
            loadAttendance = async function() {
                const date = document.getElementById('selectedDate').value;
                const classroomId = document.getElementById('classroomFilter').value;
                const tbody = document.getElementById('attendanceBody');
                const mobileTbody = document.getElementById('attendanceMobile');

                if (!classroomId) {
                    setAttendanceStatus('반을 선택하면 원생별 출결을 확인할 수 있습니다.');
                    tbody.innerHTML = '<tr><td colspan="6" class="px-6 py-8 text-center text-gray-500">반을 선택하면 출결 목록이 표시됩니다.</td></tr>';
                    if (mobileTbody) {
                        mobileTbody.innerHTML = '<div class="rounded-xl border border-gray-200 bg-white px-5 py-8 text-center text-sm text-gray-500">반을 선택하면 오늘 출결을 바로 기록할 수 있습니다.</div>';
                    }
                    return;
                }

                let rows = [];

                try {
                    const response = await fetch(`/api/v1/attendance/daily?date=${date}&classroomId=${classroomId}`, {
                        credentials: 'same-origin'
                    });
                    const data = await response.json().catch(() => ({}));
                    rows = Array.isArray(data.data) ? data.data : [];

                    if (!data.success || rows.length === 0) {
                        setAttendanceStatus('선택한 반에 표시할 출결 대상이 없습니다.');
                        tbody.innerHTML = '<tr><td colspan="6" class="px-6 py-8 text-center text-gray-500">선택한 반에 표시할 출결 대상이 없습니다.</td></tr>';
                        if (mobileTbody) {
                            mobileTbody.innerHTML = '<div class="rounded-xl border border-gray-200 bg-white px-5 py-8 text-center text-sm text-gray-500">선택한 반에 표시할 출결 대상이 없습니다.</div>';
                        }
                        return;
                    }

                    const statusCount = rows.reduce((count, row) => {
                        const key = row.status || 'PRESENT';
                        count[key] = (count[key] || 0) + 1;
                        return count;
                    }, {});
                    const presentCount = (statusCount.PRESENT || 0) + (statusCount.LATE || 0);
                    setAttendanceStatus(`${rows.length}명 · 출석/지각 ${presentCount}명 · 마지막 갱신 ${window.AppTime.formatTime(new Date(), { hour: '2-digit', minute: '2-digit' })}`);

                    tbody.innerHTML = rows.map(row => {
                        const statusMap = {
                            PRESENT: '출석',
                            ABSENT: '결석',
                            LATE: '지각',
                            EARLY_LEAVE: '조퇴',
                            SICK_LEAVE: '병결'
                        };
                        const selectedStatus = row.status || 'PRESENT';
                        const statusOptions = Object.entries(statusMap)
                            .map(([value, label]) => `<option value="${value}" ${selectedStatus === value ? 'selected' : ''}>${label}</option>`)
                            .join('');

                        const dropOffTime = row.dropOffTime || '';
                        const pickUpTime = row.pickUpTime || '';
                        const note = row.note || '';
                        const safeKidName = escapeHtml(row.kidName || '이름 없음');
                        const safeNote = escapeHtml(note);
                        const kidInitial = safeKidName ? safeKidName.charAt(0) : '?';

                        return `
                            <tr class="hover:bg-gray-50 lg:table-row" data-attendance-row="${row.kidId}">
                                <td class="px-6 py-4">
                                    <div class="flex items-center gap-3">
                                        <div class="w-10 h-10 bg-primary-100 rounded-full flex items-center justify-center">
                                            <span class="text-sm font-semibold text-primary-700">${kidInitial}</span>
                                        </div>
                                        <div>
                                            <p class="font-medium text-gray-900">${safeKidName}</p>
                                        </div>
                                    </div>
                                </td>
                                <td class="px-6 py-4 text-center">
                                    <select aria-label="${safeKidName} 출결 상태" class="attendance-status h-10 px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}">
                                        ${statusOptions}
                                    </select>
                                </td>
                                <td class="px-6 py-4 text-center">
                                        <input type="time" aria-label="${safeKidName} 등원 시간" class="drop-off-time h-10 px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" value="${escapeHtml(dropOffTime)}">
                                </td>
                                <td class="px-6 py-4 text-center">
                                        <input type="time" aria-label="${safeKidName} 하원 시간" class="pick-up-time h-10 px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" value="${escapeHtml(pickUpTime)}">
                                </td>
                                <td class="px-6 py-4 text-center">
                                    <input type="text" aria-label="${safeKidName} 출결 메모" class="attendance-note h-10 w-44 px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" placeholder="메모" value="${safeNote}">
                                </td>
                                <td class="px-6 py-4 text-center">
                                    <button type="button" data-action="saveAttendance" data-kid-id="${row.kidId}" class="min-h-10 px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-semibold hover:bg-primary-700 transition-colors">
                                        저장
                                    </button>
                                </td>
                            </tr>
                        `;
                    }).join('');
                } catch (error) {
                    setAttendanceStatus('출결 목록을 불러오지 못했습니다. 네트워크 상태를 확인하세요.', true);
                    tbody.innerHTML = '<tr><td colspan="6" class="px-6 py-8 text-center text-red-600">출결 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</td></tr>';
                    if (mobileTbody) {
                        mobileTbody.innerHTML = '<div class="rounded-xl border border-red-200 bg-red-50 px-5 py-8 text-center text-sm text-red-700">출결 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</div>';
                    }
                    return;
                }

                if (mobileTbody) {
                    mobileTbody.innerHTML = rows.map(row => {
                        const statusMap = {
                            PRESENT: '출석',
                            ABSENT: '결석',
                            LATE: '지각',
                            EARLY_LEAVE: '조퇴',
                            SICK_LEAVE: '병결'
                        };
                        const selectedStatus = row.status || 'PRESENT';
                        const statusOptions = Object.entries(statusMap)
                            .map(([value, label]) => `<option value="${value}" ${selectedStatus === value ? 'selected' : ''}>${label}</option>`)
                            .join('');

                        const dropOffTime = row.dropOffTime || '';
                        const pickUpTime = row.pickUpTime || '';
                        const note = row.note || '';
                        const safeKidName = escapeHtml(row.kidName || '이름 없음');
                        const safeNote = escapeHtml(note);
                        const kidInitial = safeKidName ? safeKidName.charAt(0) : '?';

                        return `
                            <div class="rounded-xl border border-gray-200 bg-white p-4" data-attendance-row="${row.kidId}">
                                <div class="flex items-center gap-3 mb-4">
                                    <div class="w-12 h-12 bg-primary-100 rounded-full flex items-center justify-center">
                                        <span class="text-sm font-semibold text-primary-700">${kidInitial}</span>
                                    </div>
                                    <div>
                                        <p class="font-semibold text-gray-900">${safeKidName}</p>
                                        <p class="text-sm text-gray-500">오늘 출결 상태를 확인해 주세요.</p>
                                    </div>
                                </div>
                                <div class="grid gap-3 sm:grid-cols-2">
                                    <div>
                                        <label class="block text-xs font-semibold text-gray-700 mb-2">상태</label>
                                        <select aria-label="${safeKidName} 출결 상태" class="attendance-status h-11 w-full px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}">
                                            ${statusOptions}
                                        </select>
                                    </div>
                                    <div>
                                        <label class="block text-xs font-semibold text-gray-700 mb-2">등원 시간</label>
                                            <input type="time" aria-label="${safeKidName} 등원 시간" class="drop-off-time h-11 w-full px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" value="${escapeHtml(dropOffTime)}">
                                    </div>
                                    <div>
                                        <label class="block text-xs font-semibold text-gray-700 mb-2">하원 시간</label>
                                            <input type="time" aria-label="${safeKidName} 하원 시간" class="pick-up-time h-11 w-full px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" value="${escapeHtml(pickUpTime)}">
                                    </div>
                                    <div>
                                        <label class="block text-xs font-semibold text-gray-700 mb-2">메모</label>
                                        <input type="text" aria-label="${safeKidName} 출결 메모" class="attendance-note h-11 w-full px-3 border border-gray-200 rounded-lg text-sm" data-kid-id="${row.kidId}" placeholder="결석/지각 사유" value="${safeNote}">
                                    </div>
                                </div>
                                <button type="button" data-action="saveAttendance" data-kid-id="${row.kidId}" class="mt-4 min-h-11 w-full px-4 py-3 bg-primary-600 text-white rounded-lg text-sm font-semibold hover:bg-primary-700 transition-colors">
                                    저장
                                </button>
                            </div>
                        `;
                    }).join('');
                }
            };
        });

        async function showBulkModal(type) {
            const date = document.getElementById('selectedDate').value;
            const classroomId = document.getElementById('classroomFilter').value;

            if (!classroomId) {
                await UI.alert({
                    title: '반 선택 필요',
                    text: '일괄 변경하려면 먼저 반을 선택해 주세요.',
                    icon: 'warning'
                });
                return;
            }

            const typeLabels = {
                present: '전체 출석',
                absent: '전체 결석'
            };
            const statusMap = {
                present: 'PRESENT',
                absent: 'ABSENT'
            };

            let note = '';
            if (type === 'absent') {
                const result = await UI.promptTextarea({
                    title: '결석 사유',
                    label: '결석 사유를 입력해 주세요',
                    placeholder: '예) 개인 사정, 병결 등',
                    confirmText: '적용',
                    cancelText: '취소'
                });
                if (!result.isConfirmed) return;
                note = result.value || '';
            } else {
                const ok = await UI.confirm({
                    title: typeLabels[type],
                    text: `${date}에 모든 원생의 출석을 ${typeLabels[type].replace('전체 ', '')} 처리하시겠습니까?`,
                    confirmText: '확인',
                    cancelText: '취소',
                    icon: 'question'
                });
                if (!ok) return;
            }

            try {
                const status = statusMap[type];
                const response = await fetch('/api/v1/attendance/bulk', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({
                        classroomId: Number(classroomId),
                        date: date,
                        status: status,
                        note: note || null
                    })
                });

                if (!response.ok) {
                    const error = await response.json().catch(() => ({}));
                    await UI.error(error.message || '일괄 처리에 실패했습니다.');
                    return;
                }

                await UI.success(`${typeLabels[type]}이 완료되었습니다.`);
                loadAttendance();
            } catch (error) {
                await UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
            }
        }

        function escapeHtml(value) {
            return String(value ?? '')
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#39;');
        }

        function setAttendanceStatus(message, isError = false) {
            const status = document.getElementById('attendanceStatus');
            if (!status) return;
            status.textContent = message;
            status.classList.toggle('text-red-600', isError);
            status.classList.toggle('text-gray-500', !isError);
        }

        async function saveAttendance(kidId, trigger) {
            const date = document.getElementById('selectedDate').value;
            const scope = trigger?.closest('[data-attendance-row]') || document.querySelector(`[data-attendance-row="${kidId}"]`);
            if (!scope) {
                await UI.error('저장할 출결 행을 찾지 못했습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
                return;
            }

            const status = scope.querySelector('.attendance-status').value;
            const dropOffTime = scope.querySelector('.drop-off-time').value;
            const pickUpTime = scope.querySelector('.pick-up-time').value;
            const note = scope.querySelector('.attendance-note').value;

            const data = {
                kidId: kidId,
                date: date,
                status: status,
                dropOffTime: dropOffTime || null,
                pickUpTime: pickUpTime || null,
                note: note || null
            };

            try {
                const response = await fetch('/api/v1/attendance/upsert', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(data)
                });

                if (response.ok) {
                    await UI.success('출결 정보를 저장했습니다.');
                } else {
                    const error = await response.json().catch(() => ({}));
                    await UI.error(error.message || '저장에 실패했습니다.');
                }
            } catch (error) {
                await UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
            }
        }
