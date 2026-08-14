(() => {
    const page = document.getElementById('kid-form-page');
    const form = document.getElementById('kid-form');
    if (!page || !form) return;

    const formMode = page.dataset.formMode || 'create';
    const kidId = page.dataset.kidId || '';
    const classroomId = page.dataset.kidClassroomId || '';
    let kindergartenId = page.dataset.kindergartenId || null;
    const submitButton = form.querySelector('button[type="submit"]');

    const ensureKindergartenId = async () => {
        if (kindergartenId) return kindergartenId;
        const response = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
        if (!response.ok) return null;
        const payload = await response.json().catch(() => ({}));
        kindergartenId = payload?.data?.kindergartenId || null;
        return kindergartenId;
    };

    const setClassroomOptions = (select, classrooms) => {
        select.replaceChildren(new Option('반을 선택하세요', ''));
        classrooms.forEach(classroom => select.add(new Option(classroom.name, classroom.id)));
        if (classroomId) select.value = classroomId;
    };

    const loadClassrooms = async () => {
        const select = document.getElementById('classroomId');
        const id = await ensureKindergartenId();
        if (!id) {
            setClassroomOptions(select, []);
            select.options[0].textContent = '소속 유치원이 없습니다';
            return;
        }

        const response = await fetch(`/api/v1/classrooms?kindergartenId=${encodeURIComponent(id)}`, {
            credentials: 'same-origin'
        });
        if (!response.ok) throw new Error('반 목록을 불러오지 못했습니다.');
        const payload = await response.json().catch(() => ({}));
        setClassroomOptions(select, payload.data || []);
    };

    const collectFormData = () => {
        const data = new FormData(form);
        const name = data.get('name')?.trim();
        const birthDate = data.get('birthDate');
        const admissionDate = data.get('admissionDate');
        const gender = data.get('gender');
        const selectedClassroomId = data.get('classroomId');
        if (!name || !birthDate || !admissionDate || !gender || !selectedClassroomId) {
            throw new Error('모든 필수 값을 입력해 주세요.');
        }
        return { name, birthDate, admissionDate, gender, classroomId: Number(selectedClassroomId) };
    };

    const setSubmitting = (submitting) => {
        form.setAttribute('aria-busy', String(submitting));
        if (submitButton) {
            submitButton.disabled = submitting;
            submitButton.textContent = submitting ? '저장 중…' : (formMode === 'edit' ? '정보 수정' : '원생 등록');
        }
    };

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (form.dataset.submitting === 'true') return;
        form.dataset.submitting = 'true';
        setSubmitting(true);
        try {
            const body = collectFormData();
            const url = formMode === 'edit' ? `/api/v1/kids/${kidId}` : '/api/v1/kids';
            const response = await fetch(url, {
                method: formMode === 'edit' ? 'PUT' : 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || payload.success === false) {
                throw new Error(payload.message || '요청 처리에 실패했습니다.');
            }
            await UI.success(formMode === 'edit' ? '원생 정보가 수정되었습니다.' : '원생이 등록되었습니다.');
            window.location.href = formMode === 'edit' ? `/kids/${kidId}` : '/kids';
        } catch (error) {
            await UI.error(error.message || '요청 처리 중 오류가 발생했습니다.');
            form.dataset.submitting = 'false';
            setSubmitting(false);
        }
    });

    const initialize = async () => {
        try {
            await loadClassrooms();
        } catch (error) {
            await UI.error(error.message || '반 목록을 불러오지 못했습니다.');
        }
        if (formMode === 'create') {
            const admission = document.getElementById('admissionDate');
            if (admission && !admission.value) admission.valueAsDate = new Date();
        }
    };

    initialize();
})();
