export const find = (selector, root = document) => root.querySelector(selector);
export const money = new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'});
export const dateTime = new Intl.DateTimeFormat('vi-VN', {
    dateStyle:'medium', timeStyle:'short', timeZone:'Asia/Ho_Chi_Minh'
});

export async function api(path, method = 'GET', body) {
    const headers = {};
    if (method !== 'GET') {
        const tokenResponse = await fetch('/api/auth/csrf', {cache:'no-store'});
        if (!tokenResponse.ok) throw new Error('Không lấy được phiên làm việc.');
        const token = await tokenResponse.json();
        headers[token.headerName] = token.token;
    }
    if (body !== undefined) {
        const isForm = body instanceof URLSearchParams;
        headers['Content-Type'] = isForm ? 'application/x-www-form-urlencoded' : 'application/json';
        body = isForm ? body : JSON.stringify(body);
    }
    const response = await fetch(path, {method, headers, body, cache:'no-store'});
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'API trả mã lỗi ' + response.status);
    return data;
}

export function element(tag, content = '', className = '') {
    const node = document.createElement(tag);
    node.textContent = content;
    if (className) node.className = className;
    return node;
}

export function option(label, value) {
    const node = element('option', label);
    node.value = value;
    return node;
}

export function button(label, action, className = 'secondary') {
    const node = element('button', label, className);
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
}

export function message(node, content = '', error = false) {
    node.textContent = content;
    node.classList.toggle('error', error);
}

export async function startAccount(role, onChange) {
    const login = find('#login');
    const registration = find('#registration');
    const accountBar = find('#account-bar');
    const authMessage = find('#auth-message');
    const demoLogin = find('#demo-login');
    const logout = find('#logout');
    const demoCredentials = role === 'ADMIN'
        ? {email:'admin@demo.example', password:'AdminDemo2026!'}
        : {email:'khach@demo.example', password:'KhachDemo2026!'};

    async function refresh() {
        const session = await api('/api/auth/me');
        const active = session.account?.role === role ? session.account : null;
        login.hidden = Boolean(session.account);
        if (registration) registration.hidden = Boolean(session.account);
        demoLogin.hidden = !session.demo || Boolean(session.account);
        accountBar.hidden = !session.account;
        find('#account-name').textContent = active?.name || 'Bạn đang dùng tài khoản của trang kia.';
        await onChange(active, session.demo);
    }

    async function signIn(credentials) {
        await api('/api/auth/login', 'POST', new URLSearchParams(credentials));
        message(authMessage);
        await refresh();
    }

    login.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = login.querySelector('button[type=submit]');
        submit.disabled = true;
        try {
            await signIn(new FormData(login));
            login.reset();
        } catch (error) {
            message(authMessage, error.message, true);
        } finally {
            submit.disabled = false;
        }
    });
    demoLogin.addEventListener('click', async () => {
        demoLogin.disabled = true;
        try {
            await signIn(demoCredentials);
        } catch (error) {
            message(authMessage, error.message, true);
        } finally {
            demoLogin.disabled = false;
        }
    });
    logout.addEventListener('click', async () => {
        try {
            await api('/api/auth/logout', 'POST');
            message(authMessage);
            await refresh();
        } catch (error) {
            message(authMessage, error.message, true);
        }
    });
    registration?.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = registration.querySelector('button[type=submit]');
        submit.disabled = true;
        try {
            const details = Object.fromEntries(new FormData(registration));
            await api('/api/auth/register', 'POST', details);
            await signIn({email:details.email, password:details.password});
            registration.reset();
        } catch (error) {
            message(authMessage, error.message, true);
        } finally {
            submit.disabled = false;
        }
    });
    await refresh();
}
