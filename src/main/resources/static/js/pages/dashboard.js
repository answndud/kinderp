(() => {
    function clampPercent(value) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) return 0;
        return Math.min(100, Math.max(0, numeric));
    }

    function renderDonut(elementId, rawPercent) {
        const percent = clampPercent(rawPercent);
        const donut = document.getElementById(elementId);
        if (donut) {
            donut.style.setProperty('--donut-progress', `${percent * 3.6}deg`);
        }
        return percent;
    }

    function setText(id, value) {
        const element = document.getElementById(id);
        if (element) element.textContent = value ?? '-';
    }

    async function loadStatistics() {
        try {
            const response = await fetch('/api/v1/dashboard/statistics', { credentials: 'same-origin' });
            const data = await response.json().catch(() => ({}));

            if (!response.ok || !data.success || !data.data) {
                setText('dashboardStatus', '지표를 불러오지 못했습니다. 새로고침 후 다시 시도하세요.');
                return;
            }

            const stats = data.data;
            setText('totalKids', stats.totalKids);
            setText('totalTeachers', stats.totalTeachers);
            setText('totalParents', stats.totalParents);
            setText('todayAttendanceCount', stats.todayAttendanceCount);

            const rate7Days = renderDonut('donut7Days', stats.attendanceRate7Days).toFixed(1);
            const rate30Days = renderDonut('donut30Days', stats.attendanceRate30Days).toFixed(1);
            const announcementRate = renderDonut('donutAnnouncement', stats.announcementReadRate).toFixed(1);
            setText('attendanceRate7Days', rate7Days);
            setText('attendanceRate30Days', rate30Days);
            setText('announcementReadRate', announcementRate);
            setText('totalAnnouncements', stats.totalAnnouncements);

            [['donut7Days', 'attendanceRate7Days'], ['donut30Days', 'attendanceRate30Days'], ['donutAnnouncement', 'announcementReadRate']]
                .forEach(([donutId, valueId]) => {
                    const donut = document.getElementById(donutId);
                    const value = document.getElementById(valueId)?.textContent;
                    if (donut && value) donut.setAttribute('aria-label', `${donut.getAttribute('aria-label')} ${value}%`);
                });
            setText('dashboardStatus', `마지막 갱신 ${new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`);
        } catch {
            setText('dashboardStatus', '지표를 불러오지 못했습니다. 네트워크 상태를 확인하세요.');
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', loadStatistics, { once: true });
    } else {
        loadStatistics();
    }
})();
