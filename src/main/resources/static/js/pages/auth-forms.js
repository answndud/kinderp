document.addEventListener('alpine:init', () => {
    Alpine.data('loginForm', () => ({
        email: '',
        password: '',
        isLoading: false,
        error: '',
        showPassword: false,
        async handleSubmit() {
            this.isLoading = true;
            this.error = '';
            try {
                const response = await fetch('/api/v1/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({ email: this.email, password: this.password })
                });
                const data = await response.json().catch(() => ({}));
                if (response.ok && data.success) {
                    window.location.href = '/';
                } else {
                    this.error = data.message || '로그인에 실패했습니다.';
                }
            } catch {
                this.error = '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
            } finally {
                this.isLoading = false;
            }
        }
    }));

    Alpine.data('signupForm', () => ({
        email: '',
        password: '',
        passwordConfirm: '',
        name: '',
        phone: '',
        role: 'PARENT',
        isLoading: false,
        error: '',
        errors: {},
        passwordsMatch() {
            return this.password === this.passwordConfirm && this.password.length > 0;
        },
        async handleSubmit() {
            this.isLoading = true;
            this.error = '';
            this.errors = {};
            try {
                const response = await fetch('/api/v1/auth/signup', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({
                        email: this.email,
                        password: this.password,
                        passwordConfirm: this.passwordConfirm,
                        name: this.name,
                        phone: this.phone,
                        role: this.role
                    })
                });
                const data = await response.json().catch(() => ({}));
                if (response.ok && data.success) {
                    await UI.success('회원가입이 완료되었습니다. 로그인 페이지로 이동합니다.');
                    window.location.href = '/login';
                } else if (data.data && typeof data.data === 'object') {
                    this.errors = data.data;
                } else {
                    this.error = data.message || '회원가입에 실패했습니다.';
                }
            } catch {
                this.error = '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
            } finally {
                this.isLoading = false;
            }
        }
    }));
});
