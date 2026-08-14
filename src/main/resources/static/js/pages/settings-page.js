(() => {
    const page = document.getElementById('settings-page');
    if (!page) return;

    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#39;');

    const parseResponse = async response => {
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload.message || '요청 처리에 실패했습니다.');
        return payload;
    };

    const setFormBusy = (form, busy, idleLabel) => {
        form.setAttribute('aria-busy', String(busy));
        const button = form.querySelector('button[type="submit"]');
        if (!button) return;
        button.disabled = busy;
        button.textContent = busy ? '처리 중…' : idleLabel;
    };

    const validatePasswords = (password, confirmation) => {
        if (password !== confirmation) {
            UI.alert({ title: '입력값을 확인해 주세요', text: '새 비밀번호와 확인 비밀번호가 일치하지 않습니다.', icon: 'warning' });
            return false;
        }
        if (password.length < 8) {
            UI.alert({ title: '비밀번호 규칙', text: '비밀번호는 최소 8자 이상이어야 합니다.', icon: 'warning' });
            return false;
        }
        return true;
    };

    const bindPasswordForms = () => {
        const passwordForm = document.getElementById('passwordForm');
        const bootstrapForm = document.getElementById('bootstrapPasswordForm');

        passwordForm?.addEventListener('submit', async event => {
            event.preventDefault();
            const currentPassword = document.getElementById('currentPassword').value;
            const newPassword = document.getElementById('newPassword').value;
            const confirmPassword = document.getElementById('confirmPassword').value;
            if (!validatePasswords(newPassword, confirmPassword)) return;
            setFormBusy(passwordForm, true, '비밀번호 변경');
            try {
                await parseResponse(await fetch('/api/v1/members/password', {
                    method: 'PATCH', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ currentPassword, newPassword })
                }));
                await UI.success('비밀번호가 변경되었습니다. 다시 로그인해 주세요.');
                document.getElementById('logoutAfterPasswordChange').submit();
            } catch (error) {
                await UI.error(error.message || '비밀번호 변경에 실패했습니다.');
                setFormBusy(passwordForm, false, '비밀번호 변경');
            }
        });

        bootstrapForm?.addEventListener('submit', async event => {
            event.preventDefault();
            const newPassword = document.getElementById('bootstrapNewPassword').value;
            const confirmPassword = document.getElementById('bootstrapConfirmPassword').value;
            if (!validatePasswords(newPassword, confirmPassword)) return;
            setFormBusy(bootstrapForm, true, '로컬 비밀번호 설정');
            try {
                await parseResponse(await fetch('/api/v1/members/password/bootstrap', {
                    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ newPassword })
                }));
                await UI.success('로컬 비밀번호가 설정되었습니다. 이제 이메일/비밀번호 로그인도 사용할 수 있습니다.');
                window.location.reload();
            } catch (error) {
                await UI.error(error.message || '로컬 비밀번호 설정에 실패했습니다.');
                setFormBusy(bootstrapForm, false, '로컬 비밀번호 설정');
            }
        });
    };

    const sessionList = document.getElementById('sessionList');
    const sessionError = document.getElementById('sessionErrorBanner');
    const revokeOthersButton = document.getElementById('revokeOtherSessionsButton');

    const setSessionError = message => {
        sessionError.textContent = message || '';
        sessionError.classList.toggle('hidden', !message);
    };

    const setBulkState = enabled => {
        revokeOthersButton.disabled = !enabled;
        revokeOthersButton.classList.toggle('border-red-200', enabled);
        revokeOthersButton.classList.toggle('bg-white', enabled);
        revokeOthersButton.classList.toggle('text-red-700', enabled);
        revokeOthersButton.classList.toggle('hover:bg-red-50', enabled);
        revokeOthersButton.classList.toggle('border-gray-200', !enabled);
        revokeOthersButton.classList.toggle('bg-gray-100', !enabled);
        revokeOthersButton.classList.toggle('text-gray-400', !enabled);
    };

    const formatDateTime = value => {
        if (!value) return '-';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '-' : new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
        }).format(date);
    };

    const renderEmptySessions = message => {
        sessionList.innerHTML = `<div class="bg-gray-50 px-4 py-5 text-sm text-gray-500">${escapeHtml(message)}</div>`;
        sessionList.setAttribute('aria-busy', 'false');
    };

    const loadSessions = async () => {
        setSessionError('');
        sessionList.setAttribute('aria-busy', 'true');
        sessionList.innerHTML = '<div class="bg-gray-50 px-4 py-5 text-sm text-gray-500">활성 세션을 불러오는 중입니다.</div>';
        try {
            const payload = await parseResponse(await fetch('/api/v1/auth/sessions', { credentials: 'same-origin' }));
            renderSessions(Array.isArray(payload.data) ? payload.data : []);
        } catch (error) {
            setBulkState(false);
            setSessionError(error.message || '활성 세션 목록을 불러오지 못했습니다.');
            renderEmptySessions('활성 세션을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
    };

    const renderSessions = sessions => {
        const normalized = Array.isArray(sessions) ? sessions : [];
        setBulkState(normalized.some(session => !session.current));
        if (!normalized.length) {
            renderEmptySessions('현재 계정에 연결된 활성 세션이 없습니다. 다시 로그인하면 기기별 세션 정보가 표시됩니다.');
            return;
        }
        sessionList.innerHTML = normalized.map(session => {
            const deviceLabel = session.deviceLabel || '알 수 없는 기기';
            const current = Boolean(session.current);
            const badge = current
                ? '<span class="inline-flex items-center rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">현재 기기</span>'
                : '<span class="inline-flex items-center rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">다른 기기</span>';
            return `<div class="border-t border-gray-100 bg-white px-4 py-4 first:border-t-0">
                <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div class="min-w-0 space-y-2"><div class="flex flex-wrap items-center gap-2"><h3 class="text-base font-semibold text-gray-900">${escapeHtml(deviceLabel)}</h3>${badge}</div>
                    <div class="grid grid-cols-1 gap-x-5 gap-y-1 text-sm text-gray-600 md:grid-cols-3"><p><span class="font-medium text-gray-700">IP</span> ${escapeHtml(session.clientIp || 'unknown')}</p><p><span class="font-medium text-gray-700">세션 ID</span> ${escapeHtml((session.sessionId || '').slice(0, 8))}...</p><p><span class="font-medium text-gray-700">최초 로그인</span> ${escapeHtml(formatDateTime(session.createdAt))}</p><p><span class="font-medium text-gray-700">최근 활동</span> ${escapeHtml(formatDateTime(session.lastSeenAt))}</p><p><span class="font-medium text-gray-700">토큰 갱신</span> ${escapeHtml(formatDateTime(session.lastRefreshedAt))}</p><p><span class="font-medium text-gray-700">만료 예정</span> ${escapeHtml(formatDateTime(session.expiresAt))}</p></div>
                    <p class="break-all text-xs text-gray-500">${escapeHtml(session.userAgent || 'Unknown device')}</p></div>
                    <div class="flex shrink-0 items-center"><button type="button" class="js-revoke-session-button inline-flex min-h-10 items-center justify-center rounded-lg border border-red-200 bg-white px-4 text-sm font-medium text-red-700 transition-colors hover:bg-red-50" data-session-id="${escapeHtml(session.sessionId)}" data-current="${current}" data-device-label="${escapeHtml(deviceLabel)}">${current ? '이 기기 로그아웃' : '세션 종료'}</button></div>
                </div>
            </div>`;
        }).join('');
        sessionList.setAttribute('aria-busy', 'false');
    };

    const revokeSession = async button => {
        if (button.dataset.processing === 'true') return;
        button.dataset.processing = 'true';
        button.disabled = true;
        const current = button.dataset.current === 'true';
        const label = button.dataset.deviceLabel || '알 수 없는 기기';
        const confirmed = await UI.confirm({ title: current ? '이 기기에서 로그아웃할까요?' : `${label} 세션을 종료할까요?`, text: current ? '현재 기기의 access token과 refresh token이 모두 무효화됩니다.' : '해당 기기는 즉시 다시 로그인해야 합니다.', icon: 'warning', showCancelButton: true, confirmButtonText: current ? '이 기기 로그아웃' : '세션 종료', cancelButtonText: '취소', buttonsStyling: false });
        if (!confirmed) { button.disabled = false; delete button.dataset.processing; return; }
        try {
            await parseResponse(await fetch(`/api/v1/auth/sessions/${encodeURIComponent(button.dataset.sessionId)}`, { method: 'DELETE', credentials: 'same-origin' }));
            if (current) { await UI.success('현재 세션을 종료했습니다. 다시 로그인해 주세요.'); window.location.href = '/login'; return; }
            await UI.success('선택한 세션을 종료했습니다.');
            await loadSessions();
        } catch (error) {
            setSessionError(error.message || '세션 종료에 실패했습니다.');
            button.disabled = false; delete button.dataset.processing;
        }
    };

    sessionList?.addEventListener('click', event => {
        const button = event.target.closest('.js-revoke-session-button');
        if (button) revokeSession(button);
    });

    revokeOthersButton?.addEventListener('click', async () => {
        if (revokeOthersButton.disabled || revokeOthersButton.dataset.processing === 'true') return;
        const confirmed = await UI.confirm({ title: '다른 기기 세션을 모두 종료할까요?', text: '현재 기기를 제외한 모든 세션이 즉시 만료됩니다.', icon: 'warning', showCancelButton: true, confirmButtonText: '다른 기기 로그아웃', cancelButtonText: '취소', buttonsStyling: false });
        if (!confirmed) return;
        revokeOthersButton.dataset.processing = 'true'; revokeOthersButton.disabled = true;
        try {
            await parseResponse(await fetch('/api/v1/auth/sessions/others', { method: 'DELETE', credentials: 'same-origin' }));
            await UI.success('다른 기기 세션을 모두 종료했습니다.');
            await loadSessions();
        } catch (error) {
            setSessionError(error.message || '다른 기기 세션 종료에 실패했습니다.');
        } finally {
            delete revokeOthersButton.dataset.processing;
        }
    });

    document.querySelectorAll('.js-unlink-social-button').forEach(button => {
        button.addEventListener('click', async () => {
            if (button.dataset.processing === 'true') return;
            const provider = button.dataset.provider;
            const label = button.dataset.providerLabel;
            const confirmed = await UI.confirm({ title: `${label} 연결을 해제할까요?`, text: '해제 후에는 해당 소셜 로그인으로 더 이상 로그인할 수 없습니다.', icon: 'warning', showCancelButton: true, confirmButtonText: '연결 해제', cancelButtonText: '취소', buttonsStyling: false });
            if (!confirmed) return;
            button.dataset.processing = 'true'; button.disabled = true;
            try {
                await parseResponse(await fetch(`/api/v1/members/social-link/${encodeURIComponent(provider)}`, { method: 'DELETE', credentials: 'same-origin' }));
                window.location.href = `/settings?socialLinkStatus=success&reason=unlinked&provider=${encodeURIComponent(provider)}`;
            } catch (error) {
                await UI.error(error.message || '소셜 연결 해제에 실패했습니다.');
                button.disabled = false; delete button.dataset.processing;
            }
        });
    });

    bindPasswordForms();
    loadSessions();
})();
