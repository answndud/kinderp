(() => {
    const page = document.getElementById('profile-page');
    if (!page) return;

    function escapeAttribute(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('"', '&quot;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;');
    }

    async function editProfile() {
        const currentName = page.dataset.memberName || '';
        const currentPhone = page.dataset.memberPhone || '';
        const result = await Swal.fire({
            title: '프로필 수정',
            html: `
                <div class="text-left space-y-3">
                    <div>
                        <label for="swal-name" class="block text-sm font-medium text-gray-700 mb-1">이름 *</label>
                        <input id="swal-name" autocomplete="name" class="h-11 w-full px-3 border border-gray-200 rounded-lg" value="${escapeAttribute(currentName)}" />
                    </div>
                    <div>
                        <label for="swal-phone" class="block text-sm font-medium text-gray-700 mb-1">전화번호 (선택)</label>
                        <input id="swal-phone" autocomplete="tel" class="h-11 w-full px-3 border border-gray-200 rounded-lg" value="${escapeAttribute(currentPhone)}" placeholder="예) 01012345678" />
                    </div>
                </div>
            `,
            focusConfirm: false,
            showCancelButton: true,
            confirmButtonText: '저장',
            cancelButtonText: '취소',
            customClass: {
                popup: 'rounded-xl',
                confirmButton: 'min-h-10 px-4 rounded-lg bg-primary-600 text-white font-medium',
                cancelButton: 'min-h-10 px-4 rounded-lg bg-gray-100 text-gray-700 font-medium'
            },
            buttonsStyling: false,
            preConfirm: () => {
                const name = document.getElementById('swal-name').value.trim();
                const phone = document.getElementById('swal-phone').value.trim();
                if (!name) {
                    Swal.showValidationMessage('이름을 입력해 주세요.');
                    return false;
                }
                return { name, phone: phone || null };
            }
        });
        if (!result.isConfirmed) return;

        try {
            const response = await fetch('/api/v1/members/profile', {
                method: 'PATCH',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(result.value)
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(payload.message || '수정 중 오류가 발생했습니다.');
            await UI.success('프로필을 수정했습니다.');
            window.location.reload();
        } catch (error) {
            await UI.error(error.message || '프로필 수정에 실패했습니다.');
        }
    }

    window.editProfile = editProfile;
})();
