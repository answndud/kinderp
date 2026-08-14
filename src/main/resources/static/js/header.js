        document.addEventListener('DOMContentLoaded', () => {
            const mobileBtn = document.getElementById('headerMobileBtn');
            const mobilePanel = document.getElementById('headerMobilePanel');
            const userBtn = document.getElementById('headerUserBtn');
            const userPanel = document.getElementById('headerUserPanel');
            const notificationBtn = document.getElementById('headerNotificationBtn');
            const notificationPanel = document.getElementById('headerNotificationPanel');

            const closePanel = (panel) => {
                if (panel) panel.classList.add('hidden');
            };

            const togglePanel = (panel) => {
                if (!panel) return;
                panel.classList.toggle('hidden');
            };

            if (mobileBtn) {
                mobileBtn.addEventListener('click', (event) => {
                    event.stopPropagation();
                    togglePanel(mobilePanel);
                });
            }

            if (userBtn) {
                userBtn.addEventListener('click', (event) => {
                    event.stopPropagation();
                    closePanel(notificationPanel);
                    togglePanel(userPanel);
                });
            }

            if (notificationBtn) {
                notificationBtn.addEventListener('click', (event) => {
                    event.stopPropagation();
                    closePanel(userPanel);
                    togglePanel(notificationPanel);
                    if (window.htmx && notificationPanel && !notificationPanel.classList.contains('hidden')) {
                        htmx.trigger(document.body, 'notifications-changed');
                    }
                });
            }

            document.addEventListener('click', (event) => {
                if (userPanel && userBtn && !userPanel.contains(event.target) && !userBtn.contains(event.target)) {
                    closePanel(userPanel);
                }

                if (notificationPanel && notificationBtn && !notificationPanel.contains(event.target) && !notificationBtn.contains(event.target)) {
                    closePanel(notificationPanel);
                }

                if (mobilePanel && mobileBtn && !mobilePanel.contains(event.target) && !mobileBtn.contains(event.target)) {
                    closePanel(mobilePanel);
                }
            });
        });
