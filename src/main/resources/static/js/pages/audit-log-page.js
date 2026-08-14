(() => {
    const page = document.querySelector('[data-audit-kind]');
    if (!page) return;

    const kind = page.dataset.auditKind;
    const isAuth = kind === 'auth';
    const config = isAuth
        ? {
            endpoint: '/api/v1/auth/audit-logs',
            exportEndpoint: '/api/v1/auth/audit-logs/export',
            emptyTitle: '조건에 맞는 인증 이벤트가 없습니다.',
            errorLabel: '인증 감사 로그'
        }
        : {
            endpoint: '/api/v1/domain-audit-logs',
            exportEndpoint: '/api/v1/domain-audit-logs/export',
            emptyTitle: '조건에 맞는 업무 감사 로그가 없습니다.',
            errorLabel: '업무 감사 로그'
        };

    const form = document.getElementById('auditFilterForm');
    const tableBody = document.getElementById('auditTableBody');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const errorBanner = document.getElementById('errorBanner');
    const prevPageButton = document.getElementById('prevPageButton');
    const nextPageButton = document.getElementById('nextPageButton');
    const totalLogsValue = document.getElementById('totalLogsValue');
    const pageValue = document.getElementById('pageValue');
    const activeFilterSummary = document.getElementById('activeFilterSummary');
    const paginationSummary = document.getElementById('paginationSummary');
    const exportCsvButton = document.getElementById('exportCsvButton');
    const status = document.getElementById('auditStatus');
    if (!form || !tableBody) return;

    let currentPage = 0;
    let currentTotalPages = 0;

    const escapeHtml = value => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const formatDateTime = value => {
        if (!value) return '-';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value).replace('T', ' ').slice(0, 16) : date.toLocaleString('ko-KR');
    };

    const buildQueryParams = (pageNumber, includePagination = true) => {
        const params = new URLSearchParams();
        new FormData(form).forEach((value, key) => {
            const normalized = String(value).trim();
            if (normalized) params.set(key, normalized);
        });
        if (includePagination) params.set('page', String(pageNumber));
        return params;
    };

    const updateLocation = params => {
        const query = params.toString();
        window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
    };

    const initializeFormFromQuery = () => {
        const params = new URLSearchParams(window.location.search);
        Array.from(form.elements).forEach(element => {
            if (element.name && params.has(element.name)) element.value = params.get(element.name);
        });
        if (!params.has('size')) document.getElementById('size').value = '20';
    };

    const getInitialPage = () => {
        const pageNumber = Number(new URLSearchParams(window.location.search).get('page') || '0');
        return Number.isNaN(pageNumber) || pageNumber < 0 ? 0 : pageNumber;
    };

    const buildFilterSummary = () => {
        const fields = isAuth
            ? [['eventType', '이벤트'], ['result', '결과'], ['provider', 'Provider'], ['email', '이메일']]
            : [['action', '액션'], ['targetType', '대상'], ['actorName', '행위자'], ['summary', '요약']];
        const parts = fields
            .map(([id, label]) => [label, document.getElementById(id)?.value?.trim()])
            .filter(([, value]) => value)
            .map(([label, value]) => `${label} ${value}`);
        const from = document.getElementById('from')?.value;
        const to = document.getElementById('to')?.value;
        if (from || to) parts.push(`기간 ${from || '시작'} ~ ${to || '현재'}`);
        return parts.length ? parts.join(' · ') : '전체 로그';
    };

    const emptyRow = message => `<tr><td colspan="${isAuth ? 7 : 6}" class="px-6 py-12 text-center text-sm text-gray-500">${escapeHtml(message)}</td></tr>`;

    const renderAuthRow = log => `
        <tr class="hover:bg-gray-50">
            <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${formatDateTime(log.createdAt)}</td>
            <td class="px-6 py-4"><span class="inline-flex whitespace-nowrap rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">${escapeHtml({LOGIN: '로그인', REFRESH: '토큰 갱신', SOCIAL_LINK: '소셜 연결', SOCIAL_UNLINK: '소셜 해제'}[log.eventType] || log.eventType)}</span></td>
            <td class="px-6 py-4"><span class="inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${log.result === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}">${escapeHtml(log.result)}</span></td>
            <td class="px-6 py-4 text-sm text-gray-700"><div class="font-medium text-gray-900">${escapeHtml(log.email || '-')}</div><div class="text-xs text-gray-500">memberId: ${escapeHtml(log.memberId ?? '-')}</div></td>
            <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${escapeHtml(log.provider || '-')}</td>
            <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${escapeHtml(log.reason || '-')}</td>
            <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${escapeHtml(log.clientIp || '-')}</td>
        </tr>`;

    const renderDomainRow = log => `
        <tr>
            <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-600">${formatDateTime(log.createdAt)}</td>
            <td class="px-6 py-4"><div class="text-sm font-semibold text-gray-900">${escapeHtml(log.actorName || 'SYSTEM')}</div><div class="text-xs text-gray-500">${escapeHtml(log.actorRole || '-')}</div></td>
            <td class="px-6 py-4 text-sm text-gray-700">${escapeHtml(log.action)}</td>
            <td class="px-6 py-4"><div class="text-sm text-gray-900">${escapeHtml(log.targetType)}</div><div class="text-xs text-gray-500">ID ${escapeHtml(log.targetId ?? '-')}</div></td>
            <td class="min-w-[190px] max-w-[260px] whitespace-normal px-6 py-4 text-sm text-gray-700">${escapeHtml(log.summary)}</td>
            <td class="max-w-[240px] px-6 py-4 text-xs text-gray-500">${log.metadataJson ? `<details class="audit-metadata"><summary class="cursor-pointer font-semibold text-primary-700">메타데이터 보기</summary><pre class="mt-2 max-h-32 overflow-auto whitespace-pre-wrap break-words font-mono text-[11px] leading-5">${escapeHtml(log.metadataJson)}</pre></details>` : '<span>-</span>'}</td>
        </tr>`;

    const renderPage = pageData => {
        currentPage = pageData.number || 0;
        currentTotalPages = pageData.totalPages || 0;
        totalLogsValue.textContent = Number(pageData.totalElements || 0).toLocaleString();
        pageValue.textContent = `${currentPage + 1} / ${Math.max(currentTotalPages, 1)}`;
        activeFilterSummary.textContent = buildFilterSummary();
        paginationSummary.textContent = pageData.totalElements === 0
            ? '조건에 맞는 로그가 없습니다.'
            : `${Number(pageData.totalElements).toLocaleString()}개 중 ${currentPage * pageData.size + 1}-${currentPage * pageData.size + pageData.numberOfElements}번째 로그`;
        prevPageButton.disabled = currentPage <= 0;
        nextPageButton.disabled = currentPage + 1 >= currentTotalPages;
        if (!pageData.content?.length) {
            tableBody.innerHTML = emptyRow(config.emptyTitle);
            return;
        }
        tableBody.innerHTML = pageData.content.map(isAuth ? renderAuthRow : renderDomainRow).join('');
    };

    const renderError = message => {
        tableBody.innerHTML = emptyRow(`${config.errorLabel}를 불러오지 못했습니다.`);
        errorBanner.textContent = message;
        errorBanner.classList.remove('hidden');
        totalLogsValue.textContent = '-';
        pageValue.textContent = '-';
        paginationSummary.textContent = '오류가 발생했습니다.';
        prevPageButton.disabled = true;
        nextPageButton.disabled = true;
        if (status) status.textContent = '로그를 불러오지 못했습니다.';
    };

    const setLoading = loading => {
        loadingIndicator.classList.toggle('hidden', !loading);
        form.setAttribute('aria-busy', String(loading));
        tableBody.setAttribute('aria-busy', String(loading));
    };

    const loadAuditLogs = async pageNumber => {
        setLoading(true);
        errorBanner.classList.add('hidden');
        const params = buildQueryParams(pageNumber);
        updateLocation(params);
        try {
            const response = await fetch(`${config.endpoint}?${params.toString()}`, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || !payload.success) throw new Error(payload.message || `${config.errorLabel} 조회에 실패했습니다.`);
            renderPage(payload.data);
            if (status) status.textContent = `${payload.data.totalElements || 0}건 · ${buildFilterSummary()} · 마지막 갱신 ${new Date().toLocaleTimeString('ko-KR', {hour: '2-digit', minute: '2-digit'})}`;
        } catch (error) {
            renderError(error.message || `${config.errorLabel}를 불러오지 못했습니다.`);
        } finally {
            setLoading(false);
        }
    };

    document.addEventListener('DOMContentLoaded', () => {
        initializeFormFromQuery();
        form.addEventListener('submit', event => { event.preventDefault(); loadAuditLogs(0); });
        document.getElementById('resetFiltersButton').addEventListener('click', () => { form.reset(); document.getElementById('size').value = '20'; loadAuditLogs(0); });
        prevPageButton.addEventListener('click', () => { if (currentPage > 0) loadAuditLogs(currentPage - 1); });
        nextPageButton.addEventListener('click', () => { if (currentPage + 1 < currentTotalPages) loadAuditLogs(currentPage + 1); });
        exportCsvButton.addEventListener('click', () => { window.location.href = `${config.exportEndpoint}?${buildQueryParams(0, false).toString()}`; });
        loadAuditLogs(getInitialPage());
    });
})();
