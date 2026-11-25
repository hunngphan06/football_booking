import {api, find, money, dateTime, element, option, button, message, startAccount} from './shared.js';

const dialog = find('#details');
const reserve = find('#reserve');
const categoriesFilter = find('#category-filter');
const search = find('#search');
const orderMessage = find('#order-message');
const scenario = find('#scenario');
let events = [];
let categories = [];
let assistantResults = null;
let account = null;
let demo = false;
let selectedEvent = null;

async function refreshEvents() {
    try {
        [events, categories] = await Promise.all([api('/api/events'), api('/api/event-categories')]);
        const selected = categoriesFilter.value;
        const availableCodes = new Set(events.filter(event => event.saleOpen).map(event => event.category));
        categoriesFilter.replaceChildren(option('Tất cả', ''), ...categories
            .filter(category => availableCodes.has(category.code)).map(category => option(category.name, category.code)));
        categoriesFilter.value = [...categoriesFilter.options].some(item => item.value === selected) ? selected : '';
        renderEvents();
    } catch (error) {
        message(find('#event-message'), error.message, true);
    }
}

function renderEvents() {
    const term = search.value.trim().toLocaleLowerCase('vi');
    const visible = events.filter(event => event.saleOpen && (!assistantResults || assistantResults.has(event.id))
        && (!categoriesFilter.value || event.category === categoriesFilter.value)
        && (event.title + ' ' + event.venue).toLocaleLowerCase('vi').includes(term));
    find('#event-cards').replaceChildren(...visible.map(eventCard));
    message(find('#event-message'), visible.length ? '' : 'Không tìm thấy sự kiện phù hợp.');
}

function eventCard(event) {
    const card = element('article', '', 'card');
    const category = categories.find(entry => entry.code === event.category)?.name || event.category;
    const available = event.ticketTypes.filter(ticket => ticket.availableQuantity > 0);
    const quantity = available.reduce((sum, ticket) => sum + ticket.availableQuantity, 0);
    const price = available.length ? 'Từ ' + money.format(Math.min(...available.map(ticket => ticket.price))) : 'Hết vé';
    card.append(element('span', category, 'chip'), element('h3', event.title),
        element('p', event.venue, 'muted'), element('p', dateTime.format(new Date(event.startTime)), 'muted'),
        element('strong', price + (quantity ? ' · còn ' + quantity + ' vé' : '')),
        button('Xem và chọn vé', () => openDetails(event.id), 'primary'));
    return card;
}

async function openDetails(id) {
    try {
        selectedEvent = await api('/api/events/' + id);
        find('#detail-title').textContent = selectedEvent.title;
        find('#detail-when').textContent = selectedEvent.venue + ' · ' + dateTime.format(new Date(selectedEvent.startTime));
        find('#detail-description').textContent = selectedEvent.description || '';
        find('#login-needed').hidden = Boolean(account);
        const tickets = selectedEvent.ticketTypes.filter(ticket => ticket.availableQuantity > 0);
        reserve.hidden = !account || !selectedEvent.saleOpen || !tickets.length;
        reserve.elements.quantity.value = 1;
        reserve.elements.ticket.replaceChildren(...tickets.map(ticket =>
            option(ticket.name + ' · ' + money.format(ticket.price) + ' · còn ' + ticket.availableQuantity, ticket.id)));
        if (!selectedEvent.saleOpen || !tickets.length) {
            message(find('#booking-message'), selectedEvent.saleOpen ? 'Sự kiện đã hết vé.' : 'Sự kiện đã đóng bán vé.');
        } else {
            message(find('#booking-message'));
        }
        find('#assistant-ask').reset();
        message(find('#ai-answer'));
        quote();
        dialog.showModal();
    } catch (error) {
        message(find('#event-message'), error.message, true);
    }
}

function quote() {
    const ticket = selectedEvent?.ticketTypes.find(item => item.id === Number(reserve.elements.ticket.value));
    if (!ticket) return;
    reserve.elements.quantity.max = ticket.availableQuantity;
    find('#total').textContent = 'Tổng tiền dự kiến ' + money.format(ticket.price * Number(reserve.elements.quantity.value));
}

reserve.addEventListener('input', quote);
reserve.addEventListener('submit', async formEvent => {
    formEvent.preventDefault();
    const submit = reserve.querySelector('button');
    const ticketTypeId = Number(reserve.elements.ticket.value);
    const quantity = Number(reserve.elements.quantity.value);
    submit.disabled = true;
    try {
        const order = await api('/api/bookings', 'POST', {eventId:selectedEvent.id, items:[{ticketTypeId, quantity}]});
        dialog.close();
        await refreshOrders();
        await refreshEvents();
        message(orderMessage, 'Đã giữ vé cho đơn #' + order.id + '.');
        find('#orders-heading').scrollIntoView({block:'nearest'});
    } catch (error) {
        message(find('#booking-message'), error.message, true);
    } finally {
        submit.disabled = false;
    }
});

async function refreshOrders() {
    if (!account) return;
    try {
        const orders = await api('/api/me/bookings');
        find('#order-list').replaceChildren(...(orders.length ? orders.map(orderRow) : [element('p', 'Bạn chưa có đơn đặt vé.', 'muted')]));
    } catch (error) {
        message(orderMessage, error.message, true);
    }
}

function orderRow(order) {
    const row = element('article', '', 'order');
    const title = events.find(event => event.id === order.eventId)?.title || 'Sự kiện #' + order.eventId;
    const quantity = order.items.reduce((sum, item) => sum + item.quantity, 0);
    const statuses = {PENDING:'Chờ thanh toán', PAID:'Chờ xác nhận', CONFIRMED:'Đã xác nhận', CANCELLED:'Đã hủy'};
    const payments = {PENDING:'Chưa rõ kết quả', FAILED:'Chưa trừ tiền', SUCCEEDED:'Đã trừ tiền',
        REFUND_PENDING:'Chờ hoàn tiền', REFUNDED:'Đã hoàn tiền'};
    row.append(element('h3', 'Đơn #' + order.id + ' · ' + title),
        element('p', (statuses[order.status] || order.status) + ' · ' + quantity + ' vé · ' + money.format(order.totalAmount)));
    if (order.paymentStatus) row.append(element('p', 'Thanh toán · ' + payments[order.paymentStatus], 'muted'));
    const actions = element('div', '', 'row');
    if (order.status === 'PENDING' || order.status === 'PAID') {
        if (demo && ['PENDING','SUCCEEDED'].includes(order.paymentStatus)) {
            actions.append(button('Đối soát', () => changeOrder(order, 'payment/reconcile', actions), 'primary'));
        } else {
            actions.append(button('Thanh toán giả lập', () => changeOrder(order, 'pay', actions), 'primary'));
        }
    }
    if (order.status === 'CONFIRMED') {
        const pdf = element('a', 'Tải PDF', 'secondary');
        pdf.href = '/api/bookings/' + order.id + '/confirmation';
        actions.append(pdf);
    }
    if (order.status !== 'CANCELLED' && order.paymentStatus !== 'PENDING') {
        actions.append(button('Hủy đơn', () => {
            if (confirm('Hủy đơn #' + order.id + '?')) changeOrder(order, 'cancel', actions);
        }, 'danger'));
    }
    if (demo && order.paymentStatus === 'REFUND_PENDING') {
        actions.append(button('Xác nhận hoàn tiền', () => changeOrder(order, 'refund', actions)));
    }
    row.append(actions);
    return row;
}

async function changeOrder(order, action, actions) {
    const controls = actions.querySelectorAll('button');
    controls.forEach(control => { control.disabled = true; });
    message(orderMessage);
    try {
        const body = action === 'pay' && demo ? {scenario:scenario.value} : undefined;
        await api('/api/bookings/' + order.id + '/' + action, 'POST', body);
        message(orderMessage, 'Đã cập nhật đơn #' + order.id + '.');
    } catch (error) {
        message(orderMessage, error.message, true);
    } finally {
        await refreshOrders();
        await refreshEvents();
        controls.forEach(control => { control.disabled = false; });
    }
}

find('#assistant-search').addEventListener('submit', async event => {
    event.preventDefault();
    const notice = find('#assistant-message');
    try {
        const result = await api('/api/assistant/search', 'POST', Object.fromEntries(new FormData(event.currentTarget)));
        assistantResults = new Set(result.content.map(item => item.id));
        search.value = '';
        categoriesFilter.value = '';
        renderEvents();
        message(notice, 'Đang hiển thị kết quả của trợ lý. Bấm Tải lại sự kiện để xem danh sách đầy đủ.');
    } catch (error) {
        message(notice, error.message, true);
    }
});

find('#assistant-ask').addEventListener('submit', async event => {
    event.preventDefault();
    const notice = find('#ai-answer');
    try {
        const result = await api('/api/events/' + selectedEvent.id + '/ask', 'POST',
            Object.fromEntries(new FormData(event.currentTarget)));
        message(notice, result.answer);
    } catch (error) {
        message(notice, error.message, true);
    }
});

find('#close-details').addEventListener('click', () => dialog.close());
find('#reload').addEventListener('click', () => {
    assistantResults = null;
    message(find('#assistant-message'));
    refreshEvents();
});
find('#reload-orders').addEventListener('click', refreshOrders);
search.addEventListener('input', renderEvents);
categoriesFilter.addEventListener('change', renderEvents);
startAccount('USER', async (user, demoMode) => {
    account = user;
    demo = demoMode;
    find('#orders').hidden = !account;
    find('#scenario-label').hidden = !demo;
    if (!account) find('#order-list').replaceChildren();
    await refreshEvents();
    await refreshOrders();
}).catch(error => message(find('#auth-message'), error.message, true));
