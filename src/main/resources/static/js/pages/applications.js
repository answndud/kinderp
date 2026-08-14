    document.addEventListener('DOMContentLoaded', () => {
        const pageRoot = document.getElementById('applications-page');
        const currentKindergartenId = Number(pageRoot?.dataset.kindergartenId) || null;
        const currentRole = pageRoot?.dataset.role || null;
        const currentStatus = pageRoot?.dataset.status || null;

        const refreshApplications = () => {
            if (window.htmx) htmx.trigger(document.body, 'applications-changed');
        };

        const notifyRefresh = () => {
            if (window.htmx) htmx.trigger(document.body, 'notifications-changed');
        };

        const requestJson = async (url, method, body) => {
            const response = await fetch(url, {
                method,
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: body ? JSON.stringify(body) : undefined
            });

            const payload = await response.json().catch(() => ({}));
                if (!response.ok) {
                    throw new Error(payload.message || '요청 중 오류가 발생했습니다.');
                }
            return payload;
        };

        const fetchList = async (url) => {
            const response = await fetch(url, { credentials: 'same-origin' });
            if (!response.ok) return [];
            const payload = await response.json().catch(() => ({}));
            if (Array.isArray(payload.data)) {
                return payload.data;
            }
            return payload.data?.content || [];
        };

        const setOptions = (selectEl, placeholderText, items, labelFn) => {
            if (!selectEl) return;
            selectEl.innerHTML = '';

            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = placeholderText;
            selectEl.appendChild(placeholder);

            (items || []).forEach(item => {
                const option = document.createElement('option');
                option.value = item.id;
                option.textContent = labelFn ? labelFn(item) : item.name;
                selectEl.appendChild(option);
            });
        };

        // 페이지별 업무 액션이 공통 data-action 이벤트 위임과 연결될 수 있도록 공개한다.
        window.Applications = {
            refresh: refreshApplications,
            notifyRefresh,

            async approveTeacher(applicationId) {
                await requestJson(`/api/v1/kindergarten-applications/${applicationId}/approve`, 'PUT');
                refreshApplications();
                notifyRefresh();
                if (window.htmx) htmx.trigger(document.body, 'dashboard-stats-changed');
            },

            async applyTeacher(kindergartenId, message) {
                await requestJson('/api/v1/kindergarten-applications', 'POST', {
                    kindergartenId: Number(kindergartenId),
                    message: message && message.trim() !== '' ? message : null
                });
                refreshApplications();
                notifyRefresh();
                if (window.htmx) htmx.trigger(document.body, 'dashboard-stats-changed');
            },

            async applyParent(payload) {
                await requestJson('/api/v1/kid-applications', 'POST', payload);
                refreshApplications();
                notifyRefresh();
                if (window.htmx) htmx.trigger(document.body, 'dashboard-stats-changed');
            },

            async loadKindergartens(selectEl) {
                const kindergartens = await fetchList('/api/v1/kindergartens');
                setOptions(selectEl, '유치원을 선택하세요', kindergartens, (item) => `${item.name} · ${item.address || '주소 미등록'}`);
            },

            async loadClassroomsByKindergarten(selectEl, kindergartenId) {
                if (!kindergartenId) {
                    setOptions(selectEl, '유치원을 먼저 선택하세요', [], null);
                    return;
                }

                const classrooms = await fetchList(`/api/v1/classrooms?kindergartenId=${kindergartenId}`);
                setOptions(selectEl, '희망 반을 선택하세요', classrooms, (item) => item.name);
            },

            async loadCurrentClassroomOptions() {
                if (!currentKindergartenId) {
                    return {};
                }
                const classrooms = await fetchList(`/api/v1/classrooms?kindergartenId=${currentKindergartenId}`);
                return classrooms.reduce((options, classroom) => {
                    options[classroom.id] = classroom.name;
                    return options;
                }, {});
            },

            async initPendingForms() {
                // Teacher apply form
                const teacherForm = document.getElementById('teacher-apply-form');
                if (teacherForm && !teacherForm.dataset.bound) {
                    teacherForm.dataset.bound = 'true';

                    const teacherSelect = document.getElementById('teacher-kindergarten-select');
                    await this.loadKindergartens(teacherSelect);

                    teacherForm.addEventListener('submit', async (e) => {
                        e.preventDefault();
                        try {
                            const kid = teacherSelect.value;
                            const message = document.getElementById('teacher-apply-message')?.value || '';
                            if (!kid) {
                                await UI.alert({ title: '유치원 선택', text: '지원할 유치원을 선택해 주세요.', icon: 'info' });
                                return;
                            }
                            await this.applyTeacher(kid, message);
                            await UI.success('유치원 지원 신청이 접수되었습니다.');
                            teacherForm.reset();
                        } catch (err) {
                            await UI.error(err.message);
                        }
                    });
                }

                // Parent apply form
                const parentForm = document.getElementById('parent-apply-form');
                if (parentForm && !parentForm.dataset.bound) {
                    parentForm.dataset.bound = 'true';

                    const parentKindergartenSelect = document.getElementById('parent-kindergarten-select');
                    const preferredClassroomSelect = document.getElementById('parent-preferred-classroom');

                    await this.loadKindergartens(parentKindergartenSelect);
                    await this.loadClassroomsByKindergarten(preferredClassroomSelect, parentKindergartenSelect.value);

                    parentKindergartenSelect.addEventListener('change', async () => {
                        await this.loadClassroomsByKindergarten(preferredClassroomSelect, parentKindergartenSelect.value);
                    });

                    parentForm.addEventListener('submit', async (e) => {
                        e.preventDefault();
                        try {
                            const kindergartenId = parentKindergartenSelect.value;
                            const kidName = document.getElementById('parent-kid-name')?.value || '';
                            const birthDate = document.getElementById('parent-birth-date')?.value || '';
                            const gender = document.getElementById('parent-gender')?.value || '';
                            const preferredClassroomId = preferredClassroomSelect.value;
                            const notes = document.getElementById('parent-notes')?.value || '';

                            if (!kindergartenId) {
                                await UI.alert({ title: '유치원 선택', text: '신청할 유치원을 선택해 주세요.', icon: 'info' });
                                return;
                            }

                            await this.applyParent({
                                kindergartenId: Number(kindergartenId),
                                kidName: kidName,
                                birthDate: birthDate,
                                gender: gender,
                                preferredClassroomId: preferredClassroomId ? Number(preferredClassroomId) : null,
                                notes: notes && notes.trim() !== '' ? notes : null
                            });

                            await UI.success('입학 신청이 접수되었습니다.');
                            parentForm.reset();
                            await this.loadClassroomsByKindergarten(preferredClassroomSelect, parentKindergartenSelect.value);
                        } catch (err) {
                            await UI.error(err.message);
                        }
                    });
                }
            },

            async uiApproveTeacher(applicationId, teacherName) {
                const ok = await UI.confirm({
                    title: '교사 지원 승인',
                    text: `${teacherName} 선생님의 지원을 승인할까요? 승인 후 소속 교사로 전환됩니다.`,
                    confirmText: '승인',
                    cancelText: '취소',
                    icon: 'question',
                    showCancelButton: true,
                    customClass: {
                        popup: 'rounded-xl',
                        confirmButton: 'px-4 py-2 rounded-lg bg-primary-600 text-white font-semibold',
                        cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-semibold'
                    },
                    buttonsStyling: false
                });
                if (!ok) return;

                try {
                    await requestJson(`/api/v1/kindergarten-applications/${applicationId}/approve`, 'PUT');
                    await UI.success('교사 지원을 승인했습니다.');
                    refreshApplications();
                    notifyRefresh();
                    if (window.htmx) htmx.trigger(document.body, 'dashboard-stats-changed');
                } catch (err) {
                    await UI.error(err.message);
                }
            },

            async uiRejectTeacher(applicationId, teacherName) {
                const reason = await UI.promptTextarea({
                    title: '교사 지원 거절',
                    label: `${teacherName} 선생님에게 남길 거절 사유`,
                    placeholder: '예) 이번 학기 정원이 마감되었습니다.',
                    confirmText: '거절',
                    required: true
                });
                if (!reason?.isConfirmed) return;

                try {
                    await requestJson(`/api/v1/kindergarten-applications/${applicationId}/reject`, 'PUT', {
                        reason: reason.value.trim()
                    });
                    await UI.success('교사 지원을 거절했습니다.');
                    refreshApplications();
                    notifyRefresh();
                } catch (err) {
                    await UI.error(err.message);
                }
            },

            async uiCancelTeacher(applicationId, kindergartenName) {
                const ok = await UI.confirm({
                    title: '지원 신청 취소',
                    text: `${kindergartenName} 지원 신청을 취소할까요?`,
                    confirmText: '신청 취소',
                    cancelText: '유지',
                    icon: 'warning',
                    showCancelButton: true,
                    customClass: {
                        popup: 'rounded-xl',
                        confirmButton: 'px-4 py-2 rounded-lg bg-red-600 text-white font-semibold',
                        cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-semibold'
                    },
                    buttonsStyling: false
                });
                if (!ok) return;

                try {
                    await requestJson(`/api/v1/kindergarten-applications/${applicationId}/cancel`, 'PUT');
                    await UI.success('지원 신청을 취소했습니다.');
                    refreshApplications();
                    notifyRefresh();
                } catch (err) {
                    await UI.error(err.message);
                }
            },

            async uiApproveKid(applicationId, kidName) {
                const classroomOptions = await this.loadCurrentClassroomOptions();
                if (Object.keys(classroomOptions).length === 0) {
                    await UI.error('배정 가능한 반을 먼저 등록해 주세요.');
                    return;
                }

                const result = await UI.promptSelect({
                    title: '입학 신청 승인',
                    label: `${kidName} 원아를 배정할 반`,
                    options: classroomOptions,
                    placeholder: '반을 선택하세요',
                    confirmText: '승인',
                    required: true
                });
                if (!result?.isConfirmed) return;

                try {
                    await requestJson(`/api/v1/kid-applications/${applicationId}/approve`, 'PUT', {
                        classroomId: Number(result.value)
                    });
                    await UI.success('입학 신청을 승인했습니다.');
                    refreshApplications();
                    notifyRefresh();
                    if (window.htmx) htmx.trigger(document.body, 'dashboard-stats-changed');
                } catch (err) {
                    await UI.error(err.message);
                }
            },

            async uiRejectKid(applicationId, kidName) {
                const reason = await UI.promptTextarea({
                    title: '입학 신청 거절',
                    label: `${kidName} 원아 신청의 거절 사유`,
                    placeholder: '예) 현재 희망 반 정원이 마감되었습니다.',
                    confirmText: '거절',
                    required: true
                });
                if (!reason?.isConfirmed) return;

                try {
                    await requestJson(`/api/v1/kid-applications/${applicationId}/reject`, 'PUT', {
                        reason: reason.value.trim()
                    });
                    await UI.success('입학 신청을 거절했습니다.');
                    refreshApplications();
                    notifyRefresh();
                } catch (err) {
                    await UI.error(err.message);
                }
            },

            async uiCancelKid(applicationId, kidName) {
                const ok = await UI.confirm({
                    title: '입학 신청 취소',
                    text: `${kidName} 원아의 입학 신청을 취소할까요?`,
                    confirmText: '신청 취소',
                    cancelText: '유지',
                    icon: 'warning',
                    showCancelButton: true,
                    customClass: {
                        popup: 'rounded-xl',
                        confirmButton: 'px-4 py-2 rounded-lg bg-red-600 text-white font-semibold',
                        cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-semibold'
                    },
                    buttonsStyling: false
                });
                if (!ok) return;

                try {
                    await requestJson(`/api/v1/kid-applications/${applicationId}/cancel`, 'PUT');
                    await UI.success('입학 신청을 취소했습니다.');
                    refreshApplications();
                    notifyRefresh();
                } catch (err) {
                    await UI.error(err.message);
                }
            }
        };

        // 알림 프래그먼트에서 사용하는 공통 네임스페이스
        window.Notifications = {
            refresh: notifyRefresh,
            open: (notificationId, linkUrl) => window.Applications.openNotification(notificationId, linkUrl),
            markRead: async (notificationId) => {
                if (!notificationId) return;
                await requestJson(`/api/v1/notifications/${notificationId}/read`, 'PUT');
                notifyRefresh();
            },
            markAllRead: () => window.Applications.markAllNotificationsRead(),
            remove: async (notificationId) => {
                if (!notificationId) return;
                const ok = await UI.confirm({
                    title: '알림 삭제',
                    text: '알림을 삭제할까요?',
                    confirmText: '삭제',
                    cancelText: '취소',
                    icon: 'warning'
                });
                if (!ok) return;

                await requestJson(`/api/v1/notifications/${notificationId}`, 'DELETE');
                notifyRefresh();
            }
        };

        // HTMX로 조각이 바뀌면 폼 바인딩 재시도
        document.body.addEventListener('htmx:afterSwap', async (evt) => {
            const target = evt.detail?.target;
            if (target && target.id === 'pending-content') {
                await window.Applications.initPendingForms();
            }
        });

        // 최초 1회
        window.Applications.initPendingForms().catch(() => {});

        // 승인 완료 시 자동 이동
        const shouldPoll = (currentRole === 'TEACHER' || currentRole === 'PARENT') && (currentStatus === 'PENDING' || !currentKindergartenId);
        if (shouldPoll) {
            setInterval(async () => {
                try {
                    const me = await requestJson('/api/v1/members/me', 'GET');
                    const status = me?.data?.status;
                    const kindergartenId = me?.data?.kindergartenId;
                    if (status === 'ACTIVE' && kindergartenId) {
                        window.location.href = '/';
                    }
                } catch (e) {
                    // ignore
                }
            }, 10000);
        }
    });
