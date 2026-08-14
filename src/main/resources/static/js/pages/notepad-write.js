(() => {
    const page = document.getElementById('notepad-write-page');
    if (!page) return;

    const targetType = document.getElementById('targetType');
    const classroomSelect = document.getElementById('classroomId');
    const kidSelect = document.getElementById('kidId');
    const targetSummary = document.getElementById('targetSummary');
    let kindergartenId = page.dataset.kindergartenId || null;

    const fetchList = async (url) => {
        const response = await fetch(url, { credentials: 'same-origin' });
        if (!response.ok) return [];
        const result = await response.json().catch(() => ({}));
        return result.data || [];
    };

    const ensureKindergartenId = async () => {
        if (kindergartenId) return kindergartenId;
        const response = await fetch('/api/v1/members/me', { credentials: 'same-origin' });
        if (!response.ok) return null;
        const result = await response.json().catch(() => ({}));
        kindergartenId = result?.data?.kindergartenId || null;
        return kindergartenId;
    };

    const setOptions = (select, placeholderLabel, items) => {
        select.replaceChildren(new Option(placeholderLabel, ''));
        (items || []).forEach(item => select.add(new Option(item.name, item.id)));
    };

    const updateTargetSummary = () => {
        const type = targetType.value;
        if (type === 'GLOBAL') {
            targetSummary.textContent = '전체 학부모에게 알림이 발송됩니다.';
            return;
        }
        const select = type === 'CLASSROOM' ? classroomSelect : kidSelect;
        const selected = select.options[select.selectedIndex];
        const subject = type === 'CLASSROOM' ? '반 학부모' : '학부모';
        targetSummary.textContent = selected && selected.value
            ? `${selected.textContent} ${subject}에게 발송됩니다.`
            : `${type === 'CLASSROOM' ? '반' : '원생'}을 선택하면 해당 ${subject}에게 발송됩니다.`;
    };

    const updateTargetInputs = () => {
        const type = targetType.value;
        classroomSelect.disabled = type === 'GLOBAL';
        kidSelect.disabled = type !== 'KID';
        if (type === 'GLOBAL') {
            classroomSelect.value = '';
            kidSelect.value = '';
        }
        if (type === 'CLASSROOM') kidSelect.value = '';
        updateTargetSummary();
    };

    const loadClassrooms = async () => {
        const id = await ensureKindergartenId();
        return id ? fetchList(`/api/v1/classrooms?kindergartenId=${encodeURIComponent(id)}`) : [];
    };

    const loadKids = async () => classroomSelect.value
        ? fetchList(`/api/v1/kids?classroomId=${encodeURIComponent(classroomSelect.value)}`)
        : [];

    const initialize = async () => {
        try {
            const classrooms = await loadClassrooms();
            setOptions(classroomSelect, classrooms.length ? '반을 선택하세요' : '등록된 반이 없습니다', classrooms);
        } catch (error) {
            console.error('반 목록 로드 실패:', error);
            setOptions(classroomSelect, '반 목록을 불러오지 못했습니다', []);
        }
        updateTargetInputs();
    };

    targetType.addEventListener('change', updateTargetInputs);
    classroomSelect.addEventListener('change', async () => {
        try {
            const kids = await loadKids();
            setOptions(kidSelect, kids.length ? '원생을 선택하세요' : '등록된 원생이 없습니다', kids);
        } catch (error) {
            console.error('원생 목록 로드 실패:', error);
            setOptions(kidSelect, '원생 목록을 불러오지 못했습니다', []);
        }
        updateTargetInputs();
    });
    kidSelect.addEventListener('change', updateTargetSummary);
    initialize();
})();
