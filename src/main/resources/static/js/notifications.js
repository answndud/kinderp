// 알림 (공통)
window.Notifications = window.Notifications || {
    _pollTimerId: null,

    refresh() {
        if (window.htmx) {
            htmx.trigger(document.body, 'notifications-changed');
        }
    },

    startAutoRefresh(intervalMs = 30000) {
        if (this._pollTimerId) {
            return;
        }

        this._pollTimerId = window.setInterval(() => {
            if (document.visibilityState === 'visible') {
                this.refresh();
            }
        }, intervalMs);

        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                this.refresh();
            }
        });

        window.addEventListener('focus', () => this.refresh());
    },

    async requestJson(url, method, body) {
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
            throw new Error(payload.message || '요청이 실패했습니다');
        }
        return payload;
    },

    async open(notificationId, linkUrl) {
        if (notificationId) {
            await this.requestJson(`/api/v1/notifications/${notificationId}/read`, 'PUT');
            this.refresh();
        }

        if (linkUrl && linkUrl !== 'null' && linkUrl !== 'undefined' && linkUrl.trim() !== '') {
            window.location.href = linkUrl;
        }
    },

    async markRead(notificationId) {
        if (!notificationId) return;
        await this.requestJson(`/api/v1/notifications/${notificationId}/read`, 'PUT');
        this.refresh();
    },

    async markAllRead() {
        const ok = await window.UI.confirm({
            title: '전체 읽음 처리',
            text: '모든 알림을 읽음 처리할까요?',
            confirmText: '처리',
            cancelText: '취소',
            icon: 'warning'
        });
        if (!ok) return;

        await this.requestJson('/api/v1/notifications/read-all', 'PUT');
        this.refresh();
    },

    async remove(notificationId) {
        if (!notificationId) return;

        const ok = await window.UI.confirm({
            title: '알림 삭제',
            text: '알림을 삭제할까요?',
            confirmText: '삭제',
            cancelText: '취소',
            icon: 'warning'
        });
        if (!ok) return;

        await this.requestJson(`/api/v1/notifications/${notificationId}`, 'DELETE');
        this.refresh();
    }
};

// 페이지 로드 시 실행
document.addEventListener('DOMContentLoaded', function () {
    window.Notifications.startAutoRefresh();
});
