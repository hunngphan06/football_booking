import {api, find, money, dateTime, element, option, button, message, startAccount} from './shared.js';

const eventDialog = find('#event-dialog');
const categoryDialog = find('#category-dialog');
const ticketDialog = find('#ticket-dialog');
const eventForm = find('#event-form');
const categoryForm = find('#category-form');
const ticketForm = find('#ticket-form');
const adminMessage = find('#admin-message');
let categories = [];
let editingEvent = null;
let editingCategory = null;
let ticketEventId = null;
let editingTicket = null;

async function refresh() {
    try {
        const [events, entries] = await Promise.all([api('/api/events'), api('/api/event-categories')]);
        categories = entries;
        find('#event-rows').replaceChildren(...events.map(eventRow));
        find('#category-rows').replaceChildren(...categories.map(categoryRow));
    } catch (error) {
        message(adminMessage, error.message, true);
    }
}

function eventRow(event) {
    const row = element('tr');
    const details = element('td', event.title);
    details.append(element('span', event.venue + ' · ' + dateTime.format(new Date(event.startTime)), 'muted'));
    const stock = event.ticketTypes.reduce((count, ticket) => count + ticket.availableQuantity, 0);
    const actions = element('td');
    const group = element('div', '', 'row');
    group.append(button('Sửa', () => editEvent(event)), button('Loại vé', () => openTickets(event)),
        button('Xóa', () => remove('/api/events/' + event.id, event.title), 'danger'));
    actions.append(group);
    row.append(details, element('td', categories.find(entry => entry.code === event.category)?.name || event.category),
        element('td', String(stock)), element('td', event.saleOpen ? 'Đang mở' : 'Đã đóng'), actions);
    return row;
}

function categoryRow(category) {
    const row = element('tr');
    const cell = element('td');
    const group = element('div', '', 'row');
    group.append(button('Sửa', () => editCategory(category)),
        button('Xóa', () => remove('/api/event-categories/' + category.code, category.name), 'danger'));
    cell.append(group);
    row.append(element('td', category.name), element('td', category.code),
        element('td', category.active ? 'Đang dùng' : 'Ngừng dùng'), cell);
    return row;
}

async function remove(path, label) {
    if (!confirm('Xóa ' + label + '?')) return;
    try {
        await api(path, 'DELETE');
        message(adminMessage, 'Đã xóa ' + label + '.');
        await refresh();
    } catch (error) {
        message(adminMessage, error.message, true);
    }
}

function editEvent(event) {
    editingEvent = event;
    eventForm.reset();
    const fields = eventForm.elements;
    fields.category.replaceChildren(...categories.filter(category => category.active || category.code === event?.category)
        .map(category => option(category.name, category.code)));
    find('#event-heading').textContent = event ? 'Sửa sự kiện' : 'Thêm sự kiện';
    find('#initial-ticket').hidden = Boolean(event);
    find('#initial-ticket').disabled = Boolean(event);
    if (event) {
        for (const key of ['title', 'venue', 'category', 'organizer', 'description']) {
            fields.namedItem(key).value = event[key] ?? '';
        }
        const vietnamTime = new Date(Date.parse(event.startTime) + 7 * 3600000);
        fields.startTime.value = vietnamTime.toISOString().slice(0, 16);
        fields.saleOpen.value = String(event.saleOpen);
    }
    message(find('#event-message'));
    eventDialog.showModal();
}

eventForm.addEventListener('submit', async submit => {
    submit.preventDefault();
    const values = Object.fromEntries(new FormData(eventForm));
    const data = {title:values.title, venue:values.venue, category:values.category, organizer:values.organizer,
        startTime:new Date(values.startTime + '+07:00').toISOString(), description:values.description,
        saleOpen:values.saleOpen === 'true'};
    if (!editingEvent) {
        data.ticketTypes = [{name:values.ticketName, price:Number(values.price), totalQuantity:Number(values.quantity)}];
    } else if (editingEvent.category === 'SPORTS' && data.category === 'SPORTS') {
        data.homeTeam = editingEvent.homeTeam;
        data.awayTeam = editingEvent.awayTeam;
    }
    await save(eventForm, find('#event-message'), editingEvent ? '/api/events/' + editingEvent.id : '/api/events',
        editingEvent ? 'PUT' : 'POST', data, async () => {
            eventDialog.close();
            message(adminMessage, 'Đã lưu sự kiện.');
            await refresh();
        });
});

function editCategory(category) {
    editingCategory = category;
    categoryForm.reset();
    const code = categoryForm.elements.code;
    code.readOnly = Boolean(category);
    find('#category-heading').textContent = category ? 'Sửa danh mục' : 'Thêm danh mục';
    if (category) {
        code.value = category.code;
        categoryForm.elements.namedItem('name').value = category.name;
        categoryForm.elements.active.value = String(category.active);
    }
    message(find('#category-message'));
    categoryDialog.showModal();
}

categoryForm.addEventListener('submit', async submit => {
    submit.preventDefault();
    const values = Object.fromEntries(new FormData(categoryForm));
    const base = '/api/event-categories';
    await save(categoryForm, find('#category-message'), editingCategory ? base + '/' + editingCategory.code : base,
        editingCategory ? 'PUT' : 'POST', {...values, active:values.active === 'true'}, async () => {
            categoryDialog.close();
            message(adminMessage, 'Đã lưu danh mục.');
            await refresh();
        });
});

function openTickets(event) {
    ticketEventId = event.id;
    find('#ticket-heading').textContent = 'Loại vé · ' + event.title;
    renderTickets(event.ticketTypes);
    editTicket(null);
    ticketDialog.showModal();
}

function renderTickets(tickets) {
    const rows = tickets.map(ticket => {
        const row = element('div', '', 'ticket-row');
        row.append(element('span', ticket.name + ' · ' + money.format(ticket.price)
            + ' · còn ' + ticket.availableQuantity + '/' + ticket.totalQuantity),
            button('Sửa vé', () => editTicket(ticket)));
        return row;
    });
    find('#ticket-list').replaceChildren(...rows);
}

function editTicket(ticket) {
    editingTicket = ticket;
    ticketForm.reset();
    find('#new-ticket').hidden = !ticket;
    find('#ticket-form-heading').textContent = ticket ? 'Sửa loại vé' : 'Thêm loại vé';
    if (ticket) {
        for (const field of ['name','price','totalQuantity']) {
            ticketForm.elements.namedItem(field).value = ticket[field];
        }
    }
    message(find('#ticket-message'));
}

ticketForm.addEventListener('submit', async submit => {
    submit.preventDefault();
    const values = Object.fromEntries(new FormData(ticketForm));
    const base = '/api/events/' + ticketEventId;
    const path = base + '/ticket-types' + (editingTicket ? '/' + editingTicket.id : '');
    const data = {name:values.name, price:Number(values.price), totalQuantity:Number(values.totalQuantity)};
    await save(ticketForm, find('#ticket-message'), path, editingTicket ? 'PUT' : 'POST', data, async () => {
        renderTickets((await api(base)).ticketTypes);
        editTicket(null);
        message(find('#ticket-message'), 'Đã lưu loại vé.');
        await refresh();
    });
});

async function save(form, notice, path, method, data, onSuccess) {
    const submit = form.querySelector('button[type=submit]');
    submit.disabled = true;
    try {
        await api(path, method, data);
        await onSuccess();
    } catch (error) {
        message(notice, error.message, true);
    } finally {
        submit.disabled = false;
    }
}

document.querySelectorAll('[data-close]').forEach(control =>
    control.addEventListener('click', () => control.closest('dialog').close()));
find('#add-event').addEventListener('click', () => editEvent(null));
find('#add-category').addEventListener('click', () => editCategory(null));
find('#new-ticket').addEventListener('click', () => editTicket(null));
find('#reload').addEventListener('click', refresh);
startAccount('ADMIN', async account => {
    find('#workspace').hidden = !account;
    if (account) await refresh();
}).catch(error => message(find('#auth-message'), error.message, true));
