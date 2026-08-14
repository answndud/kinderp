/**
 * 유치원 ERP - 공통 JavaScript
 * HTMX 및 Alpine.js 전역 설정
 */

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const CSRF_UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

window.AppLog = window.AppLog || Object.freeze({
    error(...args) {
        if (document.documentElement?.dataset.debug === 'true') {
            console.error(...args);
        }
    },
    warn(...args) {
        if (document.documentElement?.dataset.debug === 'true') {
            console.warn(...args);
        }
    }
});

function getCookieValue(name) {
    const cookiePrefix = `${name}=`;
    const cookies = document.cookie ? document.cookie.split(';') : [];

    for (const rawCookie of cookies) {
        const cookie = rawCookie.trim();
        if (cookie.startsWith(cookiePrefix)) {
            return decodeURIComponent(cookie.substring(cookiePrefix.length));
        }
    }

    return null;
}

function isSameOrigin(url) {
    try {
        const target = new URL(url, window.location.origin);
        return target.origin === window.location.origin;
    } catch (e) {
        return false;
    }
}

function shouldAttachCsrfToken(method, url) {
    return CSRF_UNSAFE_METHODS.has(method) && isSameOrigin(url);
}

// HTMX 설정
document.addEventListener('htmx:configRequest', function (evt) {
    const method = (evt.detail.verb || 'GET').toUpperCase();
    const url = evt.detail.path || window.location.href;

    if (!shouldAttachCsrfToken(method, url)) {
        return;
    }

    const csrfToken = getCookieValue(CSRF_COOKIE_NAME);
    if (csrfToken) {
        evt.detail.headers[CSRF_HEADER_NAME] = csrfToken;
    }
});
const originalFetch = window.fetch.bind(window);
window.fetch = function (input, init = {}) {
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    const url = input instanceof Request ? input.url : String(input);

    if (!shouldAttachCsrfToken(method, url)) {
        return originalFetch(input, init);
    }

    const csrfToken = getCookieValue(CSRF_COOKIE_NAME);
    if (!csrfToken) {
        return originalFetch(input, init);
    }

    if (input instanceof Request) {
        const headers = new Headers(input.headers);
        if (!headers.has(CSRF_HEADER_NAME)) {
            headers.set(CSRF_HEADER_NAME, csrfToken);
        }
        const requestWithCsrf = new Request(input, {
            ...init,
            headers
        });
        return originalFetch(requestWithCsrf);
    }

    const headers = new Headers(init.headers || {});
    if (!headers.has(CSRF_HEADER_NAME)) {
        headers.set(CSRF_HEADER_NAME, csrfToken);
    }

    return originalFetch(input, {
        ...init,
        headers
    });
};

function normalizePath(path) {
    if (!path) {
        return '/';
    }
    if (path.length > 1 && path.endsWith('/')) {
        return path.slice(0, -1);
    }
    return path;
}

function isMatchingNavPath(currentPath, pattern) {
    if (!pattern) {
        return false;
    }
    const normalizedPattern = normalizePath(pattern);
    if (normalizedPattern === '/') {
        return currentPath === '/';
    }
    return currentPath === normalizedPattern || currentPath.startsWith(`${normalizedPattern}/`);
}

function applyActiveNavLinks() {
    const currentPath = normalizePath(window.location.pathname);
    const links = document.querySelectorAll('.js-nav-link[data-nav-pattern]');

    links.forEach((link) => {
        const rawPatterns = (link.getAttribute('data-nav-pattern') || '')
            .split(',')
            .map((pattern) => pattern.trim())
            .filter(Boolean);

        const active = rawPatterns.some((pattern) => isMatchingNavPath(currentPath, pattern));
        link.classList.toggle('active', active);
    });
}

document.addEventListener('DOMContentLoaded', applyActiveNavLinks);
document.addEventListener('htmx:afterSwap', applyActiveNavLinks);

document.addEventListener('htmx:responseError', function (evt) {
    window.AppLog.error('HTMX Error:', evt.detail.xhr);
    // 에러 처리 (예: 토스트 메시지 표시)
});

function resolveAction(action) {
    const parts = (action || '').split('.').filter(Boolean);
    let context = window;
    for (const part of parts) {
        if (context == null || !(part in context)) {
            return null;
        }
        if (part === parts.at(-1)) {
            return { context, handler: context[part] };
        }
        context = context[part];
    }
    return null;
}

function actionArguments(element) {
    const action = element.dataset.action;
    const owner = element.closest('[data-id]');
    const id = element.dataset.id || owner?.dataset.id;
    const name = element.dataset.name;

    if (action === 'loadKids') {
        return [{ page: Number(element.dataset.page || window.currentPage || 1) }];
    }
    if (action === 'changePage') {
        return [Number(element.dataset.page)];
    }
    if (action === 'openChangeClassroom' || action === 'openDeleteKid') {
        return [element];
    }
    if (action === 'saveAttendance') {
        return [Number(element.dataset.kidId), element];
    }
    if (action === 'showParentManageModal' || action === 'assignParent' || action === 'selectKindergarten') {
        return [Number(id)];
    }
    if (action === 'removeParent') {
        return [Number(element.dataset.kidId), Number(id), name];
    }
    if (action === 'showBulkModal') {
        return [element.dataset.type];
    }
    if (action?.startsWith('Applications.')) {
        return [id, name];
    }
    if (action === 'Notifications.open') {
        return [id, element.dataset.link || owner?.dataset.link];
    }
    if (action === 'Notifications.markRead' || action === 'Notifications.remove') {
        return [id];
    }
    return [];
}

async function dispatchDataAction(element, event) {
    if (element.dataset.stopPropagation === 'true') {
        event.stopPropagation();
    }

    const action = element.dataset.action;
    if (action === 'navigate') {
        window.location.assign(element.dataset.href);
        return;
    }
    if (action === 'historyBack') {
        window.history.back();
        return;
    }

    const resolved = resolveAction(action);
    if (!resolved || typeof resolved.handler !== 'function') {
        window.AppLog.warn(`Unknown data-action: ${action}`);
        return;
    }

    try {
        await resolved.handler.apply(resolved.context, actionArguments(element));
    } catch (error) {
        if (window.UI?.error) {
            await window.UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
        } else {
            window.AppLog.error(error);
        }
    }
}

document.addEventListener('click', (event) => {
    const actionElement = event.target.closest('[data-action]');
    if (actionElement) {
        dispatchDataAction(actionElement, event);
    }
});

document.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') {
        return;
    }
    const actionElement = event.target.closest('[data-key-action]');
    if (!actionElement) {
        return;
    }
    event.preventDefault();
    if (!actionElement.dataset.action) {
        actionElement.dataset.action = actionElement.dataset.keyAction;
    }
    dispatchDataAction(actionElement, event);
});

// Alpine.js 전역 데이터
document.addEventListener('alpine:init', () => {
    // 전역 유틸리티 함수
    Alpine.store('utils', {
        formatDate(date) {
            if (!date) return '';
            return new Date(date).toLocaleDateString('ko-KR');
        },
        formatDateTime(date) {
            if (!date) return '';
            return new Date(date).toLocaleString('ko-KR');
        }
    });
});

// 공통 함수
const API = {
    // 에러 메시지 표시 (HTMX + Alpine)
    showError(message) {
        Alpine.store('toast').show(message, 'error');
    },

    // 성공 메시지 표시
    showSuccess(message) {
        Alpine.store('toast').show(message, 'success');
    }
};

// SweetAlert2 기반 공통 UI
window.UI = window.UI || {
    hasSwal() {
        return typeof window.Swal !== 'undefined';
    },

    async alert({ title = '알림', text = '', icon = 'info' } = {}) {
        if (this.hasSwal()) {
            await window.Swal.fire({
                title,
                text,
                icon,
                confirmButtonText: '확인',
                customClass: {
                    popup: 'rounded-2xl',
                    confirmButton: 'px-4 py-2 rounded-lg bg-primary-600 text-white font-medium',
                },
                buttonsStyling: false,
            });
            return;
        }

        window.alert(text ? `${title}\n\n${text}` : title);
    },

    async success(message, title = '완료') {
        return this.alert({ title, text: message, icon: 'success' });
    },

    async error(message, title = '오류') {
        return this.alert({ title, text: message, icon: 'error' });
    },

    async confirm(options) {
        if (this.hasSwal()) {
            const result = await window.Swal.fire(options);
            return result.isConfirmed === true;
        }

        return window.confirm(options.text || options);
    },

    async promptTextarea({
        title,
        label,
        placeholder = '',
        confirmText = '확인',
        cancelText = '취소',
        required = false,
    } = {}) {
        if (this.hasSwal()) {
            const result = await window.Swal.fire({
                title,
                input: 'textarea',
                inputLabel: label,
                inputPlaceholder: placeholder,
                inputAttributes: {
                    autocapitalize: 'off'
                },
                showCancelButton: true,
                confirmButtonText: confirmText,
                cancelButtonText: cancelText,
                inputValidator: (value) => {
                    if (required && (!value || value.trim() === '')) {
                        return '내용을 입력해 주세요.';
                    }
                    return undefined;
                },
                customClass: {
                    popup: 'rounded-2xl',
                    confirmButton: 'px-4 py-2 rounded-lg bg-primary-600 text-white font-medium',
                    cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-medium',
                },
                buttonsStyling: false,
            });

            return result;
        }

        return { isConfirmed: false, value: null };
    },

    async promptSelect({

        title,
        label,
        options,
        placeholder = '선택해 주세요',
        confirmText = '확인',
        cancelText = '취소',
        required = true,
    } = {}) {
        if (this.hasSwal()) {
            const result = await window.Swal.fire({
                title,
                input: 'select',
                inputLabel: label,
                inputOptions: options,
                inputPlaceholder: placeholder,
                showCancelButton: true,
                confirmButtonText: confirmText,
                cancelButtonText: cancelText,
                inputValidator: (value) => {
                    if (required && (!value || value === '')) {
                        return '항목을 선택해 주세요.';
                    }
                    return undefined;
                },
                customClass: {
                    popup: 'rounded-2xl',
                    confirmButton: 'px-4 py-2 rounded-lg bg-primary-600 text-white font-medium',
                    cancelButton: 'px-4 py-2 rounded-lg bg-gray-100 text-gray-700 font-medium',
                },
                buttonsStyling: false,
            });

            return result;
        }

        return { isConfirmed: false, value: null };
    }
};


document.addEventListener('submit', async (e) => {
    const form = e.target;
    if (!(form instanceof HTMLFormElement)) return;

    const confirmMessage = form.dataset.uiConfirm;
    if (!confirmMessage) return;

    if (form.dataset.uiConfirmed === 'true') return;

    e.preventDefault();

    const ok = await window.UI.confirm({
        title: '확인',
        text: confirmMessage,
        confirmText: '계속',
        cancelText: '취소',
        icon: 'warning'
    });

    if (!ok) return;

    form.dataset.uiConfirmed = 'true';
    form.submit();
});
