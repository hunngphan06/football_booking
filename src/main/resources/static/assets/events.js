import {api, find, money, dateTime, element, button, message, eventCover, catalogEvents} from './shared.js';
import {showAccountMenu} from './navigation.js';

const categoryButtons = find('#category-filter');
const search = find('#search');
const cards = find('#event-cards');
const notice = find('#event-message');
const assistantNotice = find('#assistant-message');
const resetFilters = find('#reset-filters');
const reload = find('#reload');
let events = [];
let categories = [];
let selectedCategory = '';
let assistantResults = null;
let assistantCriteria = null;

function categoryName(code) {
    return categories.find(category => category.code === code)?.name || code;
}

function availableTickets(event) {
    return event.ticketTypes.filter(ticket => ticket.availableQuantity > 0);
}

function matchesAssistantPrice(ticket) {
    const {minPrice, minPriceExclusive, maxPrice, maxPriceExclusive} = assistantCriteria;
    return (minPrice == null || (minPriceExclusive ? ticket.price > minPrice : ticket.price >= minPrice))
        && (maxPrice == null || (maxPriceExclusive ? ticket.price < maxPrice : ticket.price <= maxPrice));
}

async function refreshEvents() {
    reload.disabled = true;
    cards.setAttribute('aria-busy', 'true');
    try {
        const [eventList, categoryList] = await Promise.all([api('/api/events'), api('/api/event-categories')]);
        const catalog = catalogEvents(eventList);
        events = catalog.events;
        categories = categoryList;
        renderFeatured(catalog.featured);
        renderCategories();
        renderEvents();
        reload.hidden = true;
    } catch (error) {
        cards.replaceChildren();
        find('#event-count').textContent = '';
        message(notice, 'Không tải được sự kiện. ' + error.message, true);
        reload.hidden = false;
    } finally {
        cards.setAttribute('aria-busy', 'false');
        reload.disabled = false;
    }
}

function renderFeatured(event) {
    if (!event) return;
    find('#feature-media').replaceChildren(eventCover(event, {eager:true}));
    find('#feature-label').hidden = false;
    find('#feature-title').textContent = event.title;
    const meta = find('#feature-meta');
    meta.textContent = dateTime.format(new Date(event.startTime)) + ' · ' + event.venue;
    meta.hidden = false;
    const link = find('#feature-link');
    link.href = '/events/' + event.id;
    link.textContent = availableTickets(event).length ? 'Xem vé' : 'Xem chi tiết';
}

function renderCategories() {
    const codes = new Set(events.map(event => event.category));
    if (!codes.has(selectedCategory)) selectedCategory = '';
    const choices = [{code:'', name:'Tất cả'}, ...categories.filter(category => codes.has(category.code))];
    categoryButtons.replaceChildren(...choices.map(category => {
        const control = button(category.name, () => {
            selectedCategory = category.code;
            renderEvents();
        }, 'category-button');
        control.dataset.category = category.code;
        return control;
    }));
}

function searchable(value) {
    return value.trim().toLocaleLowerCase('vi').normalize('NFD').replace(/\p{Mark}/gu, '').replace(/đ/g, 'd');
}

function renderEvents() {
    const term = searchable(search.value);
    const visible = events.filter(event => (!assistantResults || assistantResults.has(event.id))
        && (!selectedCategory || event.category === selectedCategory)
        && searchable(event.title + ' ' + event.venue).includes(term));
    for (const control of categoryButtons.querySelectorAll('button')) {
        control.setAttribute('aria-pressed', String(control.dataset.category === selectedCategory));
    }
    const filtering = Boolean(term || selectedCategory || assistantResults);
    resetFilters.hidden = !filtering;
    const firstByCategory = new Map();
    for (const event of visible) {
        if (!firstByCategory.has(event.category)) firstByCategory.set(event.category, event);
    }
    // On the unfiltered homepage, show one of each category before the other dates.
    const ordered = filtering ? visible : [...firstByCategory.values(),
        ...visible.filter(event => firstByCategory.get(event.category) !== event)];
    cards.replaceChildren(...ordered.map(eventCard));
    find('#event-count').textContent = visible.length ? visible.length + ' sự kiện' : '';
    message(notice, visible.length ? '' : 'Không tìm thấy sự kiện phù hợp.');
}

function eventCard(event) {
    const card = element('article', '', 'card catalog-card');
    const media = element('div', '', 'catalog-card-media');
    media.append(eventCover(event), element('span', categoryName(event.category), 'chip'));
    const body = element('div', '', 'catalog-card-body');
    const meta = element('div', '', 'card-meta');
    const time = element('time', dateTime.format(new Date(event.startTime)));
    time.dateTime = event.startTime;
    meta.append(time, element('span', event.venue));

    const priceFiltered = assistantCriteria?.minPrice != null || assistantCriteria?.maxPrice != null;
    const available = availableTickets(event);
    const tickets = priceFiltered ? available.filter(matchesAssistantPrice) : available;
    const remaining = tickets.reduce((sum, ticket) => sum + ticket.availableQuantity, 0);
    const price = element('div', '', 'card-price');
    price.append(element('strong', remaining ? 'Từ ' + money.format(Math.min(...tickets.map(ticket => ticket.price))) : 'Hết vé'));
    if (remaining) price.append(element('small', 'Còn ' + remaining + (priceFiltered ? ' vé phù hợp' : ' vé')));
    const details = element('a', remaining ? 'Xem vé' : 'Xem chi tiết', 'secondary card-link');
    details.href = '/events/' + event.id;
    details.setAttribute('aria-label', details.textContent + ' · ' + event.title + ' · ' + time.textContent);
    const footer = element('div', '', 'row card-footer');
    footer.append(price, details);
    body.append(element('h3', event.title), meta, footer);
    card.append(media, body);
    return card;
}

function openAssistant() {
    find('#catalog-assistant').open = true;
    find('#assistant-query').focus({preventScroll:true});
}

function followSectionLink() {
    if (location.hash === '#catalog-assistant') openAssistant();
}

find('#nav-assistant').addEventListener('click', openAssistant);
window.addEventListener('hashchange', followSectionLink);
followSectionLink();

find('#assistant-search').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = find('button[type="submit"]', form);
    submit.disabled = true;
    try {
        const result = await api('/api/assistant/search', 'POST', Object.fromEntries(new FormData(form)));
        assistantResults = new Set(result.content.map(item => item.id));
        assistantCriteria = result.criteria;
        search.value = '';
        selectedCategory = '';
        find('#catalog-assistant').open = false;
        renderEvents();
        message(assistantNotice, 'Đang hiển thị kết quả từ trợ lý.');
    } catch (error) {
        message(assistantNotice, error.message, true);
    } finally {
        submit.disabled = false;
    }
});

resetFilters.addEventListener('click', () => {
    assistantResults = null;
    assistantCriteria = null;
    selectedCategory = '';
    search.value = '';
    message(assistantNotice);
    renderEvents();
    search.focus();
});
reload.addEventListener('click', refreshEvents);
search.addEventListener('input', renderEvents);
api('/api/auth/me').then(session => showAccountMenu(session.account))
    .catch(error => message(find('#nav-message'), error.message, true));
refreshEvents();
