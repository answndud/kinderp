(() => {
    const page = document.getElementById('notification-outbox-page');
    const summaryEndpoint = page?.dataset.summaryEndpoint || '/api/v1/notification-outbox/summary';
    const timelineEndpoint = page?.dataset.timelineEndpoint || '/api/v1/notification-outbox';

    document.addEventListener('DOMContentLoaded', () => {
        const statusSummary = document.getElementById('statusSummary');
        const channelSummary = document.getElementById('channelSummary');
        const tableBody = document.getElementById('deadLetterTableBody');
        const errorBanner = document.getElementById('errorBanner');
        const statusFilter = document.getElementById('statusFilter');
        const channelFilter = document.getElementById('channelFilter');
        const keywordFilter = document.getElementById('keywordFilter');
        const pageSize = document.getElementById('pageSize');
        const refreshButton = document.getElementById('refreshButton');
        const prevPageButton = document.getElementById('prevPageButton');
        const nextPageButton = document.getElementById('nextPageButton');
        const paginationSummary = document.getElementById('paginationSummary');
        const lastRefreshedAt = document.getElementById('lastRefreshedAt');

        let currentPage = 0;
        let currentTotalPages = 0;
        let refreshing = false;

        bindEvents();
        refresh();

        function bindEvents() {
            refreshButton.addEventListener('click', () => refresh(currentPage));
            statusFilter.addEventListener('change', () => refresh(0));
            channelFilter.addEventListener('change', () => refresh(0));
            keywordFilter.addEventListener('keydown', (event) => {
                if (event.key === 'Enter') refresh(0);
            });
            pageSize.addEventListener('change', () => refresh(0));
            prevPageButton.addEventListener('click', () => {
                if (currentPage > 0) refresh(currentPage - 1);
            });
            nextPageButton.addEventListener('click', () => {
                if (currentPage + 1 < currentTotalPages) refresh(currentPage + 1);
            });
        }

        async function refresh(page = 0) {
            if (refreshing) return;
            refreshing = true;
            refreshButton.disabled = true;
            refreshButton.textContent = '갱신 중...';
            hideError();
            try {
                await Promise.all([loadSummary(), loadTimeline(page)]);
                lastRefreshedAt.textContent = `마지막 갱신: ${window.AppTime.formatTime(new Date())}`;
            } finally {
                refreshing = false;
                refreshButton.disabled = false;
                refreshButton.textContent = '새로고침';
            }
        }

        async function loadSummary() {
            try {
                const payload = await fetchJson(summaryEndpoint);
                renderSummary(payload.data);
            } catch (error) {
                showError(error.message || 'outbox 요약을 불러오지 못했습니다.');
                return;
            }
        }

        async function loadTimeline(page) {
            currentPage = page;
            tableBody.innerHTML = '<tr><td colspan="9" class="px-6 py-12 text-center text-sm text-gray-500">outbox timeline을 불러오는 중입니다.</td></tr>';

            const params = new URLSearchParams({
                page: String(page),
                size: pageSize.value
            });
            if (statusFilter.value) {
                params.set('status', statusFilter.value);
            }
            if (channelFilter.value) {
                params.set('channel', channelFilter.value);
            }
            if (keywordFilter.value.trim()) {
                params.set('q', keywordFilter.value.trim());
            }
            try {
                const payload = await fetchJson(`${timelineEndpoint}?${params.toString()}`);
                renderDeadLetters(payload.data);
            } catch (error) {
                showError(error.message || 'outbox timeline을 불러오지 못했습니다.');
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="9" class="px-6 py-14 text-center">
                            <p class="text-sm font-medium text-red-700">outbox timeline을 불러오지 못했습니다.</p>
                            <p class="mt-2 text-sm text-gray-500">권한, 세션, 네트워크 상태를 확인한 뒤 새로고침하세요.</p>
                        </td>
                    </tr>
                `;
                return;
            }
        }

        function renderSummary(data) {
            const statusCounts = Object.entries(data.statusCounts || {});
            statusSummary.innerHTML = statusCounts.length === 0
                ? '<div class="col-span-2 rounded-lg bg-gray-50 p-4 text-sm text-gray-500">집계할 outbox 상태가 없습니다.</div>'
                : statusCounts
                .map(([status, count]) => `
                    <div class="rounded-lg border border-gray-200 bg-white p-4">
                        <p class="text-xs font-semibold uppercase tracking-wide text-gray-500">${escapeHtml(status)}</p>
                        <p class="mt-2 text-2xl font-bold text-gray-900">${Number(count).toLocaleString()}</p>
                    </div>
                `)
                .join('');

            const channelCounts = Object.entries(data.deadLetterCountsByChannel || {});
            channelSummary.innerHTML = channelCounts.length === 0
                ? '<div class="rounded-lg bg-gray-50 p-4 text-sm text-gray-500">현재 dead-letter 채널이 없습니다.</div>'
                : channelCounts
                .map(([channel, count]) => `
                    <div class="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-3">
                        <span class="text-sm font-semibold text-gray-700">${escapeHtml(channel)}</span>
                        <span class="text-sm font-bold text-red-700">${Number(count).toLocaleString()}</span>
                    </div>
                `)
                .join('');
        }

        function renderDeadLetters(pageData) {
            currentPage = pageData.number;
            currentTotalPages = pageData.totalPages;
            prevPageButton.disabled = pageData.first;
            nextPageButton.disabled = pageData.last || pageData.totalPages === 0;
            paginationSummary.textContent = pageData.totalElements === 0
                ? '현재 조건에 맞는 outbox가 없습니다.'
                : `${pageData.totalElements.toLocaleString()}개 중 ${pageData.number * pageData.size + 1}-${pageData.number * pageData.size + pageData.numberOfElements}번째`;

            if (!pageData.content || pageData.content.length === 0) {
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="9" class="px-6 py-14 text-center">
                            <p class="text-sm font-medium text-gray-700">조건에 맞는 outbox가 없습니다.</p>
                            <p class="mt-2 text-sm text-gray-500">상태, 채널, 검색어를 바꿔 다시 확인하세요.</p>
                        </td>
                    </tr>
                `;
                return;
            }

            tableBody.innerHTML = pageData.content.map((item) => `
                <tr class="hover:bg-gray-50">
                    <td class="whitespace-nowrap px-6 py-4 text-sm font-semibold text-gray-900">#${item.id}</td>
                    <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${escapeHtml(item.channel || '-')}</td>
                    <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${escapeHtml(item.receiverEmail || `member:${item.receiverMemberId || '-'}`)}</td>
                    <td class="min-w-[220px] px-6 py-4 text-sm text-gray-900">${escapeHtml(item.title || '-')}</td>
                    <td class="whitespace-nowrap px-6 py-4 text-sm font-semibold ${statusClass(item.status)}">${escapeHtml(item.status || '-')}</td>
                    <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${item.attemptCount} / ${item.maxAttempts}</td>
                    <td class="whitespace-nowrap px-6 py-4 text-sm text-gray-700">${formatDateTime(item.deadLetteredAt || item.lastAttemptAt || item.nextAttemptAt)}</td>
                    <td class="min-w-[240px] px-6 py-4 text-sm text-red-700">${escapeHtml(item.lastError || '-')}</td>
                    <td class="whitespace-nowrap px-6 py-4">
                        ${item.status === 'DEAD_LETTER' ? `
                            <button type="button"
                                    class="retry-button min-h-9 rounded-lg bg-primary-600 px-3 text-sm font-semibold text-white hover:bg-primary-700"
                                    data-outbox-id="${item.id}">
                                재시도
                            </button>
                        ` : '<span class="text-sm text-gray-400">-</span>'}
                    </td>
                </tr>
            `).join('');

            tableBody.querySelectorAll('.retry-button').forEach((button) => {
                button.addEventListener('click', () => retryOutbox(button.dataset.outboxId));
            });
        }

        function statusClass(status) {
            if (status === 'DEAD_LETTER') return 'text-red-700';
            if (status === 'DELIVERED') return 'text-primary-700';
            if (status === 'PROCESSING') return 'text-amber-700';
            return 'text-gray-700';
        }

        async function retryOutbox(outboxId) {
            const retryButton = tableBody.querySelector(`.retry-button[data-outbox-id="${outboxId}"]`);
            const confirmed = await window.UI.confirm({
                title: '재시도 예약',
                text: `Outbox #${outboxId}를 PENDING 상태로 되돌릴까요?`,
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: '재시도',
                cancelButtonText: '취소',
                customClass: {
                    popup: 'rounded-2xl',
                    confirmButton: 'px-4 py-2 rounded-lg bg-primary-600 text-white font-medium',
                    cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-medium',
                },
                buttonsStyling: false,
            });
            if (!confirmed) return;

            if (retryButton) {
                retryButton.disabled = true;
                retryButton.textContent = '처리 중...';
                retryButton.classList.add('opacity-60', 'cursor-not-allowed');
            }

            try {
                await fetchJson(`${timelineEndpoint}/${encodeURIComponent(outboxId)}/retry`, { method: 'POST' });
            } catch (error) {
                showError(error.message || '재시도 예약에 실패했습니다.');
                if (retryButton) {
                    retryButton.disabled = false;
                    retryButton.textContent = '재시도';
                    retryButton.classList.remove('opacity-60', 'cursor-not-allowed');
                }
                return;
            }
            await window.UI.success('재시도 대기 상태로 전환했습니다.');
            refresh(0);
        }

        async function fetchJson(url, options = {}) {
            const response = await fetch(url, {
                ...options,
                headers: {
                    'Accept': 'application/json',
                    ...(options.headers || {})
                }
            });
            const payload = await response.json();
            if (!response.ok || !payload.success) {
                throw new Error(payload.message || '요청 처리에 실패했습니다.');
            }
            return payload;
        }

        function showError(message) {
            errorBanner.textContent = message;
            errorBanner.classList.remove('hidden');
        }

        function hideError() {
            errorBanner.classList.add('hidden');
            errorBanner.textContent = '';
        }

        function formatDateTime(value) {
            if (!value) return '-';
            return window.AppTime.formatDateTime(value);
        }

        function escapeHtml(value) {
            return String(value ?? '')
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#039;');
        }
    });

})();
