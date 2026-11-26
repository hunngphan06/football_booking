import {api, find, loginUrl, message} from './shared.js';

const header = find('#site-header');
header.innerHTML = `
    <nav class="site-nav" aria-label="Điều hướng chính">
        <a class="site-brand" href="/events">
            <span class="site-ticket" aria-hidden="true"></span>Event Booking
        </a>
        <div class="site-public">
            <a href="/events#events-heading">Khám phá</a>
            <a href="/events#category-filter">Danh mục</a>
            <a id="nav-assistant" href="/events#catalog-assistant">Trợ lý tìm vé</a>
        </div>
        <div class="site-actions">
            <a id="nav-orders" href="/my-bookings" hidden>Đơn của tôi</a>
            <a id="nav-admin" href="/admin" hidden>Quản trị</a>
            <div class="site-auth" id="nav-auth">
                <a class="site-login" id="sign-in" href="/login">Đăng nhập</a>
                <a class="site-signup" id="sign-up" href="/register">Đăng ký</a>
            </div>
            <details class="site-account" id="account-menu" hidden>
                <summary id="account-trigger">
                    <span class="site-avatar" id="account-initials" aria-hidden="true"></span>
                    <span class="site-account-name" id="account-name"></span>
                    <span class="site-chevron" aria-hidden="true"></span>
                </summary>
                <div class="site-account-options">
                    <button type="button" id="open-profile">Hồ sơ</button>
                    <button type="button" id="logout">Đăng xuất</button>
                </div>
            </details>
        </div>
    </nav>
    <p class="nav-message" id="nav-message" role="status"></p>
    <dialog class="site-profile" id="profile-dialog" aria-labelledby="profile-heading">
        <h2 id="profile-heading">Hồ sơ</h2>
        <dl>
            <div><dt>Tên tài khoản</dt><dd id="profile-name"></dd></div>
            <div><dt>Vai trò</dt><dd id="profile-role"></dd></div>
        </dl>
        <form method="dialog"><button class="secondary" type="submit">Đóng</button></form>
    </dialog>
`;

const menu = find('#account-menu', header);
const trigger = find('#account-trigger', header);
const profile = find('#profile-dialog', header);
let returnPage = location.pathname;

function setReturnLinks(next) {
    returnPage = next;
    const destination = next === '/admin' ? '/events' : next;
    find('#sign-in', header).href = loginUrl(destination);
    find('#sign-up', header).href = '/register?next=' + encodeURIComponent(destination);
}

setReturnLinks(returnPage);
for (const link of header.querySelectorAll('a[href="/my-bookings"], a[href="/admin"]')) {
    if (link.getAttribute('href') === location.pathname) link.setAttribute('aria-current', 'page');
}

export function showAccountMenu(account, next = location.pathname) {
    setReturnLinks(next);
    find('#nav-auth', header).hidden = Boolean(account);
    menu.hidden = !account;
    find('#nav-orders', header).hidden = !account;
    find('#nav-admin', header).hidden = account?.role !== 'ADMIN';
    if (!account) return;

    const words = account.name.trim().split(/\s+/).filter(Boolean);
    const initials = words.length ? [words[0], ...(words.length > 1 ? [words.at(-1)] : [])]
        .map(word => [...word][0]).join('').toLocaleUpperCase('vi') : '?';
    find('#account-initials', header).textContent = initials;
    find('#account-name', header).textContent = account.name;
    trigger.setAttribute('aria-label', 'Tài khoản ' + account.name);
    find('#profile-name', header).textContent = account.name;
    find('#profile-role', header).textContent = account.role === 'ADMIN' ? 'Quản trị viên' : 'Khách hàng';
}

document.addEventListener('pointerdown', event => {
    if (menu.open && !menu.contains(event.target)) menu.open = false;
});

menu.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menu.open) {
        menu.open = false;
        trigger.focus();
    }
});

find('#open-profile', header).addEventListener('click', () => {
    menu.open = false;
    profile.showModal();
});

profile.addEventListener('close', () => trigger.focus());

find('#logout', header).addEventListener('click', async event => {
    const control = event.currentTarget;
    control.disabled = true;
    try {
        await api('/api/auth/logout', 'POST');
        location.assign(returnPage === '/admin' ? '/login' : '/events');
    } catch (error) {
        message(find('#nav-message', header), error.message, true);
        control.disabled = false;
    }
});
