import {api, find, money, element, button, message, loginUrl} from './shared.js';
import {showAccountMenu} from './navigation.js';

const orderMessage = find('#order-message');
let events = [];
let demo = false;

async function refreshOrders() {
    try {
        const orders = await api('/api/me/bookings');
        find('#order-list').replaceChildren(...(orders.length ? orders.map(orderRow) : [element('p', 'Bạn chưa có đơn đặt vé.', 'muted')]));
        return orders;
    } catch (error) {
        if (error.status === 401) location.replace(loginUrl('/my-bookings'));
        else message(orderMessage, error.message, true);
        return [];
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
        await api('/api/bookings/' + order.id + '/' + action, 'POST');
        message(orderMessage, 'Đã cập nhật đơn #' + order.id + '.');
    } catch (error) {
        message(orderMessage, error.message, true);
    } finally {
        await refreshOrders();
        controls.forEach(control => { control.disabled = false; });
    }
}

find('#reload-orders').addEventListener('click', refreshOrders);

async function loadPage() {
    try {
        const [session, eventList] = await Promise.all([api('/api/auth/me'), api('/api/events')]);
        if (session.account?.role !== 'USER') {
            location.replace(loginUrl('/my-bookings'));
            return;
        }
        showAccountMenu(session.account);
        events = eventList;
        demo = session.demo;
        const orders = await refreshOrders();
        const createdId = Number(new URLSearchParams(location.search).get('created'));
        if (orders.some(order => order.id === createdId)) {
            message(orderMessage, 'Đã giữ vé cho đơn #' + createdId + '.');
        }
    } catch (error) {
        message(orderMessage, error.message, true);
    }
}
loadPage();
