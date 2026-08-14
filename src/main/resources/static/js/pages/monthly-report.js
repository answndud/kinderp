(() => {
    const page = document.getElementById('monthly-report-page');
    if (!page) return;

    const monthPicker = document.getElementById('monthPicker');
    const classroomSelect = document.getElementById('classroomSelect');
    const body = document.getElementById('monthlyReportBody');
    const cards = document.getElementById('monthlyReportCards');
    const title = document.getElementById('reportTitle');
    const subtitle = document.getElementById('reportSubtitle');

    const currentSeoulMonth = () => {
        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit'
        }).formatToParts(new Date()).reduce((result, part) => {
            result[part.type] = part.value;
            return result;
        }, {});
        return `${parts.year}-${parts.month}`;
    };

    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const setBusy = busy => {
        cards.setAttribute('aria-busy', String(busy));
        body.setAttribute('aria-busy', String(busy));
    };

    const setEmptyState = message => {
        body.innerHTML = `<tr><td colspan="7" class="px-6 py-8 text-center text-gray-500">${escapeHtml(message)}</td></tr>`;
        cards.innerHTML = `<div class="px-2 py-8 text-center text-sm leading-6 text-gray-500">${escapeHtml(message)}</div>`;
    };

    const setErrorState = message => {
        body.innerHTML = `<tr><td colspan="7" class="px-6 py-8 text-center text-red-600">${escapeHtml(message)}</td></tr>`;
        cards.innerHTML = `<div class="rounded-lg border border-red-200 bg-red-50 px-5 py-6 text-center text-sm leading-6 text-red-700">${escapeHtml(message)}</div>`;
    };

    const renderMonthlyCard = kid => {
        const total = Number(kid.totalDays || 0);
        const attended = Number(kid.presentDays || 0) + Number(kid.lateDays || 0);
        const rate = total > 0 ? Math.round((attended / total) * 100) : 0;
        return `
            <article class="rounded-xl border border-gray-200 bg-white p-4">
                <div class="flex items-start justify-between gap-3">
                    <div>
                        <h3 class="font-semibold text-gray-900">${escapeHtml(kid.kidName)}</h3>
                        <p class="mt-1 text-sm text-gray-500">출석 기준 ${rate}% · 총 ${total}건</p>
                    </div>
                    <span class="rounded-full bg-primary-50 px-3 py-1 text-xs font-semibold text-primary-700">${rate}%</span>
                </div>
                <dl class="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">출석</dt><dd class="mt-1 font-semibold text-gray-900">${kid.presentDays}</dd></div>
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">결석</dt><dd class="mt-1 font-semibold text-gray-900">${kid.absentDays}</dd></div>
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">지각</dt><dd class="mt-1 font-semibold text-gray-900">${kid.lateDays}</dd></div>
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">조퇴</dt><dd class="mt-1 font-semibold text-gray-900">${kid.earlyLeaveDays}</dd></div>
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">병결</dt><dd class="mt-1 font-semibold text-gray-900">${kid.sickLeaveDays}</dd></div>
                    <div class="rounded-lg bg-gray-50 px-2 py-3"><dt class="text-xs font-semibold text-gray-500">총 기록</dt><dd class="mt-1 font-semibold text-gray-900">${kid.totalDays}</dd></div>
                </dl>
            </article>`;
    };

    const renderTableRow = kid => `
        <tr class="hover:bg-gray-50">
            <td class="px-6 py-4 font-medium text-gray-900">${escapeHtml(kid.kidName)}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.presentDays}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.absentDays}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.lateDays}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.earlyLeaveDays}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.sickLeaveDays}</td>
            <td class="px-6 py-4 text-center text-gray-700">${kid.totalDays}</td>
        </tr>`;

    const loadMonthlyReport = async () => {
        const selectedMonth = monthPicker.value;
        const classroomId = classroomSelect.value;
        if (!selectedMonth || !classroomId) {
            setEmptyState('반과 월을 선택하면 원생별 출결 요약이 표시됩니다.');
            setBusy(false);
            return;
        }

        setBusy(true);
        const [year, month] = selectedMonth.split('-').map(Number);
        try {
            const response = await fetch(`/api/v1/attendance/classroom/${encodeURIComponent(classroomId)}/monthly-report?year=${year}&month=${month}`, {
                credentials: 'same-origin'
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok || !data.success || !data.data) {
                setErrorState('월간 리포트를 불러오지 못했습니다. 잠시 후 다시 조회해 주세요.');
                return;
            }

            const report = data.data;
            title.textContent = `${report.classroomName} 출석 리포트`;
            subtitle.textContent = `${report.year}년 ${String(report.month).padStart(2, '0')}월 기준`;
            if (!report.kids?.length) {
                setEmptyState('선택한 조건에 표시할 원생 출결 기록이 없습니다.');
                return;
            }
            body.innerHTML = report.kids.map(renderTableRow).join('');
            cards.innerHTML = report.kids.map(renderMonthlyCard).join('');
        } catch (error) {
            window.AppLog?.error('월간 리포트 조회 실패:', error);
            setErrorState('월간 리포트를 불러오지 못했습니다. 잠시 후 다시 조회해 주세요.');
        } finally {
            setBusy(false);
        }
    };

    const loadClassrooms = async () => {
        try {
            const meResponse = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
            if (!meResponse.ok) return [];
            const me = await meResponse.json().catch(() => ({}));
            const kindergartenId = me.data?.kindergartenId;
            if (!kindergartenId) return [];
            const response = await fetch(`/api/v1/classrooms?kindergartenId=${encodeURIComponent(kindergartenId)}`, { credentials: 'same-origin' });
            if (!response.ok) return [];
            const data = await response.json().catch(() => ({}));
            return Array.isArray(data.data) ? data.data : (data.data?.content || []);
        } catch (error) {
            window.AppLog?.error('반 목록 로드 실패:', error);
            return [];
        }
    };

    document.addEventListener('alpine:init', () => {
        Alpine.data('monthlyReport', () => ({
            classrooms: [],
            async init() {
                monthPicker.value = currentSeoulMonth();
                this.classrooms = await loadClassrooms();
                if (this.classrooms.length) {
                    requestAnimationFrame(() => {
                        if (!classroomSelect.value) classroomSelect.value = String(this.classrooms[0].id);
                    });
                }
            }
        }));
    });

    window.loadMonthlyReport = loadMonthlyReport;
})();
