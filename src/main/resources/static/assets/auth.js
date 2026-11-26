import {api, find, loginUrl, message, catalogEvents, eventCover} from './shared.js';

const login = find('#login');
const registration = find('#registration');
const notice = find('#auth-message');
const requested = new URLSearchParams(location.search).get('next');
const allowed = ['/events', '/my-bookings'];
const next = allowed.includes(requested) || /^\/events\/\d+$/.test(requested || '')
    ? requested : '/events';

async function signIn(credentials) {
    await api('/api/auth/login', 'POST', new URLSearchParams(credentials));
    const {account} = await api('/api/auth/me');
    if (account?.role === 'ADMIN') {
        location.replace('/admin');
    } else if (account?.role === 'USER') {
        location.replace(next);
    } else {
        throw new Error('Không mở được phiên đăng nhập. Hãy thử lại.');
    }
}

async function submit(control, action) {
    control.disabled = true;
    message(notice);
    try {
        await action();
    } catch (error) {
        message(notice, error.message, true);
    } finally {
        control.disabled = false;
    }
}

api('/api/events').then(eventList => {
    const {featured} = catalogEvents(eventList);
    if (featured) find('#login-background').replaceChildren(eventCover(featured, {eager:true}));
}).catch(() => { /* The stylesheet supplies the background if events are unavailable. */ });

if (login) {
    find('#register-link').href = '/register?next=' + encodeURIComponent(next);
    find('#forgot-password').addEventListener('click', () => find('#password-help-dialog').showModal());
    login.addEventListener('submit', event => {
        event.preventDefault();
        submit(find('button[type=submit]', login), () => signIn(new FormData(login)));
    });
    if (next === '/my-bookings') {
        api('/api/auth/me').then(({account}) => {
            if (account?.role === 'ADMIN' && !notice.textContent) {
                message(notice, 'Đăng nhập bằng tài khoản khách để xem đơn đặt vé.');
            }
        }).catch(() => {});
    }
}

if (registration) {
    find('#login-link').href = loginUrl(next);
    registration.addEventListener('submit', event => {
        event.preventDefault();
        submit(find('button[type=submit]', registration), async () => {
            const details = Object.fromEntries(new FormData(registration));
            await api('/api/auth/register', 'POST', details);
            try {
                await signIn({email:details.email, password:details.password});
            } catch {
                location.replace(find('#login-link').href);
            }
        });
    });
}
