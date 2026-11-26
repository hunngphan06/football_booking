import {api, find, money, dateTime, option, message, eventCover, loginUrl} from './shared.js';
import {showAccountMenu} from './navigation.js';

const reserve = find('#reserve');
const id = location.pathname.match(/^\/events\/(\d+)/)?.[1];
const page = '/events/' + id;
const notice = find('#booking-message');
let account = null;
let selectedEvent = null;

async function loadDetails() {
    try {
        const [event, session] = await Promise.all([api('/api/events/' + id), api('/api/auth/me')]);
        selectedEvent = event;
        account = session.account;
        showAccountMenu(account, page);
        document.title = event.title + ' · Event Booking';
        find('#detail-title').textContent = event.title;
        find('#detail-cover').append(eventCover(event));
        find('#detail-when').textContent = event.venue + ' · ' + dateTime.format(new Date(event.startTime));
        find('#detail-description').textContent = event.description || '';
        const tickets = event.ticketTypes.filter(ticket => ticket.availableQuantity > 0);
        reserve.hidden = !event.saleOpen || !tickets.length;
        reserve.elements.ticket.replaceChildren(...tickets.map(ticket =>
            option(ticket.name + ' · ' + money.format(ticket.price) + ' · còn ' + ticket.availableQuantity, ticket.id)));
        if (reserve.hidden) {
            message(notice, event.saleOpen ? 'Sự kiện đã hết vé.' : 'Sự kiện đã đóng bán vé.');
        } else {
            quote();
        }
        find('#event-detail').hidden = false;
    } catch (error) {
        message(find('#detail-message'), error.message, true);
    }
}

function quote() {
    const ticket = selectedEvent?.ticketTypes.find(item => item.id === Number(reserve.elements.ticket.value));
    if (!ticket) return;
    reserve.elements.quantity.max = ticket.availableQuantity;
    find('#total').textContent = 'Tổng tiền dự kiến ' + money.format(ticket.price * Number(reserve.elements.quantity.value));
}

reserve.addEventListener('input', quote);
reserve.addEventListener('submit', async event => {
    event.preventDefault();
    if (account?.role !== 'USER') {
        location.assign(loginUrl(page));
        return;
    }
    const control = find('button[type=submit]', reserve);
    const ticketTypeId = Number(reserve.elements.ticket.value);
    const quantity = Number(reserve.elements.quantity.value);
    control.disabled = true;
    message(notice);
    try {
        const order = await api('/api/bookings', 'POST', {
            eventId:selectedEvent.id, items:[{ticketTypeId, quantity}]
        });
        location.assign('/my-bookings?created=' + order.id);
    } catch (error) {
        if (error.status === 401) location.assign(loginUrl(page));
        else message(notice, error.message, true);
    } finally {
        control.disabled = false;
    }
});

find('#assistant-ask').addEventListener('submit', async event => {
    event.preventDefault();
    const answer = find('#ai-answer');
    try {
        const result = await api('/api/events/' + id + '/ask', 'POST',
            Object.fromEntries(new FormData(event.currentTarget)));
        message(answer, result.answer);
    } catch (error) {
        message(answer, error.message, true);
    }
});

if (id) loadDetails();
