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
    if (body !== undefined && !(body instanceof FormData)) {
        const isForm = body instanceof URLSearchParams;
        headers['Content-Type'] = isForm ? 'application/x-www-form-urlencoded' : 'application/json';
        body = isForm ? body : JSON.stringify(body);
    }
    const response = await fetch(path, {method, headers, body, cache:'no-store'});
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(data?.message || 'API trả mã lỗi ' + response.status);
        error.status = response.status;
        throw error;
    }
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

export function catalogEvents(eventList) {
    const events = eventList.filter(event => event.saleOpen)
        .sort((first, second) => Date.parse(first.startTime) - Date.parse(second.startTime));
    const featured = events.find(event => event.ticketTypes.some(ticket => ticket.availableQuantity > 0)) || events[0];
    return {events, featured};
}

const categoryArt = {
    SPORTS: '/assets/illustrations/football.jpg',
    CINEMA: '/assets/illustrations/cinema.jpg',
    MUSIC: '/assets/illustrations/music.jpg'
};

export function eventCover(event, {eager = false} = {}) {
    const cover = element('div', '', 'event-cover');
    cover.dataset.category = event.category;
    const fallback = Object.hasOwn(categoryArt, event.category) ? categoryArt[event.category] : null;
    if (event.coverUrl || fallback) {
        const image = element('img');
        image.alt = '';
        image.loading = eager ? 'eager' : 'lazy';
        image.decoding = 'async';
        if (eager) image.fetchPriority = 'high';
        image.addEventListener('error', () => {
            if (fallback && image.getAttribute('src') !== fallback) image.src = fallback;
            else image.remove();
        });
        image.src = event.coverUrl || fallback;
        cover.append(image);
    }
    return cover;
}

export function loginUrl(next = '/events') {
    return '/login?next=' + encodeURIComponent(next);
}
