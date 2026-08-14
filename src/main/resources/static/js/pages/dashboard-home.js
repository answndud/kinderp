(() => {
    document.addEventListener('alpine:init', () => {
        Alpine.data('dashboardStats', () => ({
            period: 'week',
            loading: false,
            stats: {
                notepads: 0,
                attendanceRate: 0,
                applicationsPending: 0,
                announcements: 0
            },
            async init() {
                await this.loadStats();
                document.body.addEventListener('dashboard-stats-changed', () => {
                    this.loadStats();
                });
            },
            async setPeriod(value) {
                if (this.period === value || this.loading) return;
                this.period = value;
                await this.loadStats();
            },
            getRangeDays() {
                return this.period === 'month' ? 30 : 7;
            },
            getKstBaseDate(date = new Date()) {
                const formatter = new Intl.DateTimeFormat('en-CA', {
                    timeZone: 'Asia/Seoul',
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit'
                });
                const [year, month, day] = formatter.format(date).split('-').map(Number);
                return new Date(Date.UTC(year, month - 1, day));
            },
            toDateString(date) {
                return date.toISOString().slice(0, 10);
            },
            async loadPrincipalAttendanceRate() {
                const response = await fetch('/api/v1/dashboard/statistics', { credentials: 'same-origin' });
                if (!response.ok) return false;

                const payload = await response.json().catch(() => ({}));
                const data = payload.data || {};
                const attendanceRate = this.period === 'month'
                    ? data.attendanceRate30Days
                    : data.attendanceRate7Days;

                if (!Number.isFinite(attendanceRate)) return false;
                this.stats.attendanceRate = Math.round(attendanceRate);
                return true;
            },
            async loadStats() {
                if (this.loading) return;
                this.loading = true;
                try {
                    const meRes = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
                    if (!meRes.ok) return;
                    const me = await meRes.json().catch(() => ({}));
                    if (!me.success || !me.data || !me.data.kindergartenId) return;

                    const kindergartenId = me.data.kindergartenId;
                    const nowKst = this.getKstBaseDate();
                    const endDate = this.toDateString(nowKst);
                    const rangeDays = this.getRangeDays();
                    const startDateObj = new Date(nowKst);
                    startDateObj.setDate(startDateObj.getDate() - (rangeDays - 1));
                    const startDate = this.toDateString(startDateObj);

                    const notepadsRes = await fetch('/api/v1/notepads?page=0&size=200', { credentials: 'same-origin' });
                    if (notepadsRes.ok) {
                        const payload = await notepadsRes.json().catch(() => ({}));
                        this.stats.notepads = (payload.data?.content || []).filter(item => {
                            return item.createdAt && item.createdAt.slice(0, 10) >= startDate && item.createdAt.slice(0, 10) <= endDate;
                        }).length;
                    }

                    let attendanceLoaded = false;
                    if (me.data.role === 'PRINCIPAL') {
                        attendanceLoaded = await this.loadPrincipalAttendanceRate();
                    }

                    if (!attendanceLoaded) {
                        const classroomsRes = await fetch(`/api/v1/classrooms?kindergartenId=${kindergartenId}`, { credentials: 'same-origin' });
                        if (classroomsRes.ok) {
                            const classroomsData = await classroomsRes.json().catch(() => ({}));
                            const classrooms = classroomsData.data || [];
                            if (classrooms.length > 0) {
                                let totalPresent = 0;
                                let totalCount = 0;

                                for (let dayOffset = 0; dayOffset < rangeDays; dayOffset += 1) {
                                    const date = new Date(startDateObj);
                                    date.setDate(startDateObj.getDate() + dayOffset);
                                    const dateStr = this.toDateString(date);

                                    const responses = await Promise.all(
                                        classrooms.map(c =>
                                            fetch(`/api/v1/attendance/daily?date=${dateStr}&classroomId=${c.id}`, { credentials: 'same-origin' })
                                                .then(resp => resp.ok ? resp.json().catch(() => ({})) : null)
                                        )
                                    );

                                    responses.forEach((payload) => {
                                        if (!payload) return;
                                        const list = payload.data || [];
                                        if (list.length === 0) return;
                                        const present = list.filter(row => row.status === 'PRESENT' || row.status === 'LATE').length;
                                        totalPresent += present;
                                        totalCount += list.length;
                                    });
                                }

                                this.stats.attendanceRate = totalCount > 0 ? Math.round((totalPresent / totalCount) * 100) : 0;
                            }
                        }
                    }

                    this.stats.applicationsPending = 0;
                    if (me.data.role === 'PRINCIPAL' || me.data.role === 'TEACHER') {
                        const kidRes = await fetch(`/api/v1/kid-applications/pending?kindergartenId=${kindergartenId}`, { credentials: 'same-origin' });
                        if (kidRes.ok) {
                            const kidData = await kidRes.json().catch(() => ({}));
                            this.stats.applicationsPending = (kidData.data || []).length;
                        }
                    }

                    if (me.data.role === 'PRINCIPAL') {
                        const teacherRes = await fetch(`/api/v1/kindergarten-applications/pending?kindergartenId=${kindergartenId}`, { credentials: 'same-origin' });
                        if (teacherRes.ok) {
                            const teacherData = await teacherRes.json().catch(() => ({}));
                            this.stats.applicationsPending += (teacherData.data || []).length;
                        }
                    }

                    const annRes = await fetch(`/api/v1/announcements?kindergartenId=${kindergartenId}&page=0&size=200`, { credentials: 'same-origin' });
                    if (annRes.ok) {
                        const payload = await annRes.json().catch(() => ({}));
                        this.stats.announcements = (payload.data?.content || []).filter(item => {
                            return item.createdAt && item.createdAt.slice(0, 10) >= startDate && item.createdAt.slice(0, 10) <= endDate;
                        }).length;
                    }
                } catch (error) {
                    console.error('대시보드 통계 로드 실패:', error);
                } finally {
                    this.loading = false;
                }
            }
        }));
    });

})();
