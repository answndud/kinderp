(() => {
    const page = document.querySelector('main[data-attendance-requests]');
    if (!page) return;

    const isParent = page.dataset.parent === 'true';
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const createForm = document.getElementById('createRequestForm');
    const reviewFilterForm = document.getElementById('reviewFilterForm');
    const tableBody = document.getElementById('requestTableBody');
    const mobileList = document.getElementById('requestMobileList');
    const loadingIndicator = document.getElementById('requestLoadingIndicator');
    const successBanner = document.getElementById('requestSuccessBanner');
    const errorBanner = document.getElementById('requestErrorBanner');
    const requestCount = document.getElementById('requestCountValue');
    const kidSelect = document.getElementById('kidId');
    const createSubmit = document.getElementById('createRequestSubmit');
    let pendingSubmission = null;

    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#39;');

    const showBanner = (element, message) => {
        element.textContent = message;
        element.classList.remove('hidden');
    };

    const hideBanner = element => {
        element.classList.add('hidden');
        element.textContent = '';
    };

    const apiRequest = async (url, options = {}) => {
        const headers = {
            Accept: 'application/json',
            ...(options.body ? { 'Content-Type': 'application/json' } : {}),
            ...(options.headers || {})
        };
        if (csrfToken && csrfHeader && options.method && options.method !== 'GET') headers[csrfHeader] = csrfToken;
        try {
            const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
            const payload = await response.json().catch(() => ({}));
            return response.ok ? payload : { success: false, message: payload.message || '요청 처리에 실패했습니다.' };
        } catch (error) {
            return { success: false, message: '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' };
        }
    };

    const setLoading = loading => {
        loadingIndicator.classList.toggle('hidden', !loading);
        mobileList.setAttribute('aria-busy', String(loading));
        tableBody.setAttribute('aria-busy', String(loading));
    };

    const emptyRow = message => `<tr><td colspan="6" class="px-6 py-12 text-center text-sm text-gray-500">${escapeHtml(message)}</td></tr>`;
    const emptyCard = message => `<div class="px-2 py-8 text-center text-sm leading-6 text-gray-500">${escapeHtml(message)}</div>`;

    const formatDateTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
    const statusLabel = status => ({ PRESENT: '출석', ABSENT: '결석', LATE: '지각', EARLY_LEAVE: '조퇴', SICK_LEAVE: '병결' }[status] || status);
    const statusTone = status => {
        const tones = {
            PENDING: 'bg-amber-50 text-amber-700',
            APPROVED: 'bg-emerald-50 text-emerald-700',
            REJECTED: 'bg-rose-50 text-rose-700',
            CANCELLED: 'bg-gray-100 text-gray-700'
        };
        return tones[status] || tones.CANCELLED;
    };
    const requestStatusLabel = status => ({ PENDING: '승인 대기', APPROVED: '승인됨', REJECTED: '거절됨', CANCELLED: '취소됨' }[status] || status);

    const renderActions = request => {
        if (isParent) {
            return request.status === 'PENDING'
                ? `<button type="button" data-action="cancel" data-id="${request.id}" class="min-h-11 rounded-lg border border-red-200 px-4 py-2 text-sm font-semibold text-red-700 hover:bg-red-50">요청 취소</button>`
                : '<span class="text-sm text-gray-400">처리 완료</span>';
        }
        return `<div class="grid grid-cols-2 gap-2 md:flex md:flex-wrap">
            <button type="button" data-action="approve" data-id="${request.id}" class="min-h-11 rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700">승인</button>
            <button type="button" data-action="reject" data-id="${request.id}" class="min-h-11 rounded-lg border border-amber-200 px-4 py-2 text-sm font-semibold text-amber-700 hover:bg-amber-50">거절</button>
        </div>`;
    };

    const renderRow = request => {
        const reviewer = request.reviewedBy
            ? `<div class="text-sm text-gray-700">${escapeHtml(request.reviewedBy.name)}</div>`
            : '<span class="text-sm text-gray-400">미처리</span>';
        return `<tr class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm text-gray-600">${formatDateTime(request.createdAt)}</td>
            <td class="px-6 py-4"><div class="text-sm font-semibold text-gray-900">${escapeHtml(request.kid.name)}</div><div class="text-xs text-gray-500">${escapeHtml(request.requester.name)}</div></td>
            <td class="px-6 py-4 text-sm text-gray-700">${escapeHtml(request.date)}</td>
            <td class="px-6 py-4"><span class="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold ${statusTone(request.status)}">${escapeHtml(statusLabel(request.requestedStatus))} · ${escapeHtml(requestStatusLabel(request.status))}</span></td>
            <td class="px-6 py-4"><div class="text-sm text-gray-700">${escapeHtml(request.note || '-')}</div><div class="mt-1 text-xs text-gray-500">${reviewer}</div>${request.rejectionReason ? `<div class="mt-2 text-xs text-red-600">거절 사유: ${escapeHtml(request.rejectionReason)}</div>` : ''}</td>
            <td class="px-6 py-4">${renderActions(request)}</td>
        </tr>`;
    };

    const renderCard = request => `<article class="rounded-xl border border-gray-200 bg-white p-4">
        <div class="flex items-start justify-between gap-3"><div><h3 class="font-semibold text-gray-900">${escapeHtml(request.kid.name)}</h3><p class="mt-1 text-sm text-gray-500">${escapeHtml(request.requester.name)} · ${escapeHtml(request.date)}</p></div><span class="inline-flex shrink-0 items-center rounded-full px-3 py-1 text-xs font-semibold ${statusTone(request.status)}">${escapeHtml(requestStatusLabel(request.status))}</span></div>
        <dl class="mt-4 grid grid-cols-2 gap-3 text-sm"><div><dt class="text-xs font-semibold text-gray-500">요청 상태</dt><dd class="mt-1 font-medium text-gray-900">${escapeHtml(statusLabel(request.requestedStatus))}</dd></div><div><dt class="text-xs font-semibold text-gray-500">생성 시각</dt><dd class="mt-1 font-medium text-gray-900">${formatDateTime(request.createdAt)}</dd></div></dl>
        <div class="mt-4 rounded-lg bg-gray-50 px-3 py-3"><p class="text-sm text-gray-700">${escapeHtml(request.note || '메모가 없습니다.')}</p><p class="mt-1 text-xs text-gray-500">${request.reviewedBy ? `처리자 ${escapeHtml(request.reviewedBy.name)}` : '아직 처리되지 않았습니다.'}</p>${request.rejectionReason ? `<p class="mt-2 text-sm text-red-700">거절 사유: ${escapeHtml(request.rejectionReason)}</p>` : ''}</div>
        <div class="mt-4">${renderActions(request)}</div>
    </article>`;

    const buildPendingUrl = () => {
        const params = new URLSearchParams();
        const date = document.getElementById('filterDate')?.value;
        const classroomId = document.getElementById('filterClassroomId')?.value;
        if (date) params.set('date', date);
        if (classroomId) params.set('classroomId', classroomId);
        const query = params.toString();
        return query ? `/api/v1/attendance-requests/pending?${query}` : '/api/v1/attendance-requests/pending';
    };

    const loadMyKids = async () => {
        const response = await apiRequest('/api/v1/kids/my-kids');
        if (!response.success) {
            showBanner(errorBanner, response.message || '원생 목록을 불러오지 못했습니다.');
            return;
        }
        kidSelect.replaceChildren(new Option('원생을 선택하세요', ''));
        response.data.forEach(kid => kidSelect.add(new Option(`${kid.name} (${kid.classroomName})`, kid.id)));
    };

    const loadRequests = async () => {
        setLoading(true);
        hideBanner(errorBanner);
        const response = await apiRequest(isParent ? '/api/v1/attendance-requests/my' : buildPendingUrl());
        setLoading(false);
        if (!response.success) {
            tableBody.innerHTML = emptyRow('요청을 불러오지 못했습니다.');
            mobileList.innerHTML = emptyCard('요청을 불러오지 못했습니다.');
            requestCount.textContent = '-';
            showBanner(errorBanner, response.message || '요청 조회에 실패했습니다.');
            return;
        }
        const requests = response.data ?? [];
        requestCount.textContent = String(requests.length);
        if (!requests.length) {
            const rowMessage = isParent ? '아직 요청 이력이 없습니다.' : '승인 대기 요청이 없습니다.';
            tableBody.innerHTML = emptyRow(rowMessage);
            mobileList.innerHTML = emptyCard(isParent ? '아직 요청 이력이 없습니다. 필요한 경우 왼쪽 양식에서 변경 요청을 제출하세요.' : '승인 대기 요청이 없습니다. 날짜나 반 필터를 조정해 다시 확인할 수 있습니다.');
            return;
        }
        tableBody.innerHTML = requests.map(renderRow).join('');
        mobileList.innerHTML = requests.map(renderCard).join('');
    };

    const createIdempotencyKey = () => window.crypto?.randomUUID?.() || `attendance-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;

    const setActionBusy = button => {
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        button.dataset.processing = 'true';
    };

    const handleActionResponse = async (response, message, button) => {
        if (!response.success) {
            showBanner(errorBanner, response.message || '처리에 실패했습니다.');
            button?.removeAttribute('disabled');
            button?.removeAttribute('aria-busy');
            delete button?.dataset.processing;
            return;
        }
        showBanner(successBanner, message);
        await loadRequests();
    };

    const handleRequestAction = async event => {
        const button = event.target.closest('button[data-action]');
        if (!button || button.dataset.processing === 'true') return;
        hideBanner(successBanner);
        hideBanner(errorBanner);
        setActionBusy(button);
        const requestId = button.dataset.id;
        const action = button.dataset.action;
        if (action === 'cancel') {
            await handleActionResponse(await apiRequest(`/api/v1/attendance-requests/${requestId}/cancel`, { method: 'POST' }), '요청이 취소되었습니다.', button);
            return;
        }
        if (action === 'approve') {
            await handleActionResponse(await apiRequest(`/api/v1/attendance-requests/${requestId}/approve`, { method: 'POST' }), '출결 변경 요청을 승인했습니다.', button);
            return;
        }
        if (action === 'reject') {
            button.removeAttribute('disabled');
            button.removeAttribute('aria-busy');
            delete button.dataset.processing;
            const promptResult = await UI.promptTextarea({ title: '출결 변경 요청 거절', label: '거절 사유', placeholder: '보호자가 확인할 수 있는 사유를 입력하세요.', confirmText: '거절 처리', cancelText: '취소', required: true });
            const reason = promptResult?.isConfirmed ? promptResult.value?.trim() : '';
            if (!reason) return;
            setActionBusy(button);
            await handleActionResponse(await apiRequest(`/api/v1/attendance-requests/${requestId}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }), '출결 변경 요청을 거절했습니다.', button);
        }
    };

    createForm?.addEventListener('submit', async event => {
        event.preventDefault();
        hideBanner(successBanner); hideBanner(errorBanner);
        createSubmit.disabled = true; createSubmit.setAttribute('aria-busy', 'true');
        const body = { kidId: Number(document.getElementById('kidId').value), date: document.getElementById('requestDate').value, status: document.getElementById('requestStatus').value, note: document.getElementById('requestNote').value || null };
        const fingerprint = JSON.stringify(body);
        if (!pendingSubmission || pendingSubmission.payloadFingerprint !== fingerprint) pendingSubmission = { payloadFingerprint: fingerprint, key: createIdempotencyKey() };
        const response = await apiRequest('/api/v1/attendance-requests', { method: 'POST', body: fingerprint, headers: { 'Idempotency-Key': pendingSubmission.key } });
        if (!response.success) {
            showBanner(errorBanner, response.message || '요청 생성에 실패했습니다.');
        } else {
            createForm.reset(); pendingSubmission = null; showBanner(successBanner, '출결 변경 요청이 등록되었습니다.'); await loadRequests();
        }
        createSubmit.disabled = false; createSubmit.removeAttribute('aria-busy');
    });

    reviewFilterForm?.addEventListener('submit', event => { event.preventDefault(); loadRequests(); });
    tableBody.addEventListener('click', handleRequestAction);
    mobileList.addEventListener('click', handleRequestAction);
    if (isParent) loadMyKids();
    loadRequests();
})();
