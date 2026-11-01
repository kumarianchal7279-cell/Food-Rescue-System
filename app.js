const SESSION_KEY = 'goodroots.session';
const categoryDetails = {
  produce: { label: 'Produce', emoji: '🥬', accent: 'produce-art' },
  bakery: { label: 'Bakery', emoji: '🥖', accent: 'bakery-art' },
  prepared: { label: 'Prepared meal', emoji: '🥗', accent: 'prepared-art' },
  dairy: { label: 'Dairy', emoji: '🥛', accent: 'produce-art' },
  other: { label: 'Other', emoji: '🍱', accent: 'prepared-art' }
};
const pageTitles = {
  home: 'Overview',
  discover: 'Find food',
  activity: 'My activity',
  impact: 'Our impact',
  assistant: 'Food guide'
};
let session = readSession();
let activeFilter = 'all';
let searchQuery = '';
let authMode = 'login';
let cachedListings = [];

function readSession() {
  const saved = sessionStorage.getItem(SESSION_KEY);
  if (!saved) return null;
  try {
    return JSON.parse(saved);
  } catch (error) {
    console.error('Could not restore the saved sign-in session.', error);
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set('Content-Type', 'application/json');
  if (session?.token) headers.set('Authorization', `Bearer ${session.token}`);
  const response = await fetch(path, { ...options, headers });
  const responseText = await response.text();
  let payload = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch (error) {
      throw new Error(`The server returned an invalid response (HTTP ${response.status}).`);
    }
  }
  if (!response.ok) {
    const message = payload?.message || `Request failed with HTTP ${response.status}.`;
    if (response.status === 401 && session) {
      session = null;
      sessionStorage.removeItem(SESSION_KEY);
      updateAccountUI();
    }
    throw new Error(message);
  }
  return payload;
}

class FoodRescueGateway {
  getListings(category = 'all') {
    const params = new URLSearchParams();
    if (category !== 'all') params.set('category', category);
    return apiRequest(`/api/donations?${params}`);
  }

  createListing(input) {
    return apiRequest('/api/donations', { method: 'POST', body: JSON.stringify(input) });
  }

  requestDonation(id, message) {
    return apiRequest(`/api/donations/${id}/requests`, {
      method: 'POST',
      body: JSON.stringify({ message })
    });
  }

  myRequests() {
    return apiRequest('/api/requests/mine');
  }

  myDonations() {
    return apiRequest('/api/donations/mine');
  }

  volunteers() {
    return apiRequest('/api/users/volunteers');
  }

  decideRequest(id, decision) {
    return apiRequest(`/api/requests/${id}/decision`, {
      method: 'PATCH',
      body: JSON.stringify({ decision })
    });
  }

  assignVolunteer(id, volunteerId) {
    return apiRequest(`/api/requests/${id}/volunteer`, {
      method: 'PATCH',
      body: JSON.stringify({ volunteerId })
    });
  }

  updateDelivery(id, status) {
    return apiRequest(`/api/requests/${id}/delivery-status`, {
      method: 'PATCH',
      body: JSON.stringify({ status })
    });
  }
}

const gateway = new FoodRescueGateway();

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function categoryInfo(value) {
  const key = String(value || '').toLowerCase();
  return categoryDetails[key] || categoryDetails.other;
}

function localDateTimeValue(date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function formatDate(value) {
  if (!value) return 'Not provided';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Not provided';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function updateAccountUI() {
  const authButton = document.getElementById('authOpen');
  const profileName = document.getElementById('profileName');
  const profileRole = document.getElementById('profileRole');
  const profileInitials = document.getElementById('profileInitials');
  const profileButton = document.getElementById('profileButton');
  const welcome = document.getElementById('welcomeCopy');
  const roleNames = { DONOR: 'Food donor', NGO: 'NGO / receiver', VOLUNTEER: 'Volunteer' };

  if (session) {
    authButton.textContent = 'Sign out';
    profileName.textContent = session.fullName;
    profileRole.textContent = roleNames[session.role] || session.role;
    profileInitials.textContent = session.fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
    profileButton.setAttribute('aria-label', `Account for ${session.fullName}; sign out`);
    welcome.textContent = `Welcome, ${session.fullName}. Find food and follow your community activity.`;
  } else {
    authButton.textContent = 'Sign in';
    profileName.textContent = 'Guest';
    profileRole.textContent = 'Sign in to get started';
    profileInitials.textContent = 'G';
    profileButton.setAttribute('aria-label', 'Open account sign in');
    welcome.textContent = 'Sign in to find available food and share with your community.';
  }
}

function pageError(title, message, action = 'auth') {
  const button = action === 'auth'
    ? '<button class="button button-outline" type="button" data-open-auth>Sign in or create an account</button>'
    : '';
  return `<div class="page-error"><strong>${escapeHtml(title)}</strong>${escapeHtml(message)}${button}</div>`;
}

function listingCard(listing) {
  const category = categoryInfo(listing.category);
  const isReceiver = session?.role === 'NGO';
  const actionLabel = !session ? 'Sign in to request' : isReceiver ? 'Request food' : 'NGO accounts request';
  const pickupDate = formatDate(listing.safeConsumptionDeadline);
  const donor = listing.organizationName || listing.donorName;
  const initials = (donor || '?').split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  return `<article class="listing-card" data-category="${escapeHtml(String(listing.category).toLowerCase())}">
    <div class="food-art ${category.accent}">
      <span class="fresh-badge">Check handling details</span>
      <span class="food-emoji" aria-hidden="true">${category.emoji}</span>
    </div>
    <div class="card-body">
      <div class="card-meta"><span class="card-category">${escapeHtml(category.label)}</span><span class="listing-distance">Collection deadline</span></div>
      <h3 title="${escapeHtml(listing.foodName)}">${escapeHtml(listing.foodName)}</h3>
      <p class="card-description details-copy">${escapeHtml(listing.description || 'Food details provided by the donor.')}</p>
      <details class="listing-safety"><summary>Storage, allergens &amp; handling</summary><div><p><strong>Storage:</strong> ${escapeHtml(listing.storageConditions || 'Ask the donor')}</p><p><strong>Allergens:</strong> ${escapeHtml(listing.allergens || 'Not provided')}</p><p><strong>Handling:</strong> ${escapeHtml(listing.handlingInformation || 'Ask the donor')}</p></div></details>
      <div class="card-meta" style="margin-top:7px"><span>${escapeHtml(listing.quantityKg)} kg</span><span>${escapeHtml(pickupDate)}</span></div>
      <div class="card-footer"><span class="donor-info"><span class="donor-avatar">${escapeHtml(initials)}</span><span>${escapeHtml(donor)}</span></span><button class="button ${isReceiver ? 'button-primary' : 'button-outline'}" type="button" data-request="${listing.id}" ${isReceiver ? '' : 'disabled'}>${escapeHtml(actionLabel)}</button></div>
    </div>
  </article>`;
}

function updateListingGrids(listings) {
  cachedListings = listings;
  const filtered = listings.filter((listing) => {
    const matchesCategory = activeFilter === 'all' || String(listing.category).toLowerCase() === activeFilter;
    const searchable = `${listing.foodName} ${listing.description || ''} ${listing.pickupAddress} ${listing.donorName} ${listing.organizationName || ''} ${listing.category}`.toLowerCase();
    return matchesCategory && searchable.includes(searchQuery.toLowerCase());
  });
  const markup = filtered.length
    ? filtered.map(listingCard).join('')
    : '<div class="empty-state"><strong>No available listings match.</strong>Try a different search or check back later.</div>';
  document.querySelectorAll('.listing-grid').forEach((grid) => { grid.innerHTML = markup; });
  const resultsCount = document.getElementById('resultsCount');
  if (resultsCount) resultsCount.textContent = `${filtered.length} available ${filtered.length === 1 ? 'donation' : 'donations'}`;
}

async function renderListings() {
  if (!session) {
    document.querySelectorAll('.listing-grid').forEach((grid) => {
      grid.innerHTML = pageError('Sign in to browse donations.', 'Available food is loaded from the project API; no sample listings are shown.');
    });
    document.getElementById('resultsCount').textContent = 'Sign in to view available donations';
    return;
  }
  try {
    const listings = await gateway.getListings(activeFilter);
    updateListingGrids(listings);
  } catch (error) {
    document.querySelectorAll('.listing-grid').forEach((grid) => {
      grid.innerHTML = pageError('Could not load donations.', error.message);
    });
    document.getElementById('resultsCount').textContent = 'Listings unavailable';
  }
}

function volunteerOptions(volunteers, selectedId) {
  return `<option value="">Select a volunteer</option>${volunteers.map((volunteer) => (
    `<option value="${volunteer.id}" ${volunteer.id === selectedId ? 'selected' : ''}>${escapeHtml(volunteer.fullName)}${volunteer.organizationName ? ` · ${escapeHtml(volunteer.organizationName)}` : ''}</option>`
  )).join('')}`;
}

function donationActivityRow(donation, request, volunteers) {
  let actions = '';
  if (session.role === 'DONOR' && donation.status === 'REQUESTED' && request) {
    actions = `<button class="button button-primary" type="button" data-decision="ACCEPT" data-request-id="${request.id}">Approve</button>
      <button class="button button-outline" type="button" data-decision="REJECT" data-request-id="${request.id}">Reject</button>`;
  } else if (session.role === 'DONOR' && donation.status === 'ACCEPTED' && request) {
    actions = `<select aria-label="Assign volunteer for ${escapeHtml(donation.foodName)}" data-volunteer="${request.id}">${volunteerOptions(volunteers)}</select>
      <button class="button button-primary" type="button" data-assign="${request.id}">Assign pickup</button>`;
  }
  const subtitle = request
    ? `Requested by ${escapeHtml(request.receiverName)} · ${escapeHtml(donation.pickupAddress)}`
    : escapeHtml(donation.pickupAddress);
  return `<article class="activity-entry activity-request">
    <span class="activity-entry-icon" aria-hidden="true">${categoryInfo(donation.category).emoji}</span>
    <div class="activity-entry-copy"><strong>${escapeHtml(donation.foodName)}</strong><span>${subtitle}</span><span class="status-pill status-${String(donation.status).toLowerCase()}">${escapeHtml(donation.status.replaceAll('_', ' '))}</span></div>
    <div class="activity-request-actions">${actions}</div>
  </article>`;
}

function requestActivityRow(request) {
  const donation = request.donation;
  let actions = '';
  if (session.role === 'VOLUNTEER' && donation.status === 'PICKUP_ASSIGNED') {
    actions = `<button class="button button-primary" type="button" data-delivery="COLLECTED" data-request-id="${request.id}">Mark collected</button>`;
  } else if (session.role === 'VOLUNTEER' && donation.status === 'COLLECTED') {
    actions = `<button class="button button-primary" type="button" data-delivery="DELIVERED" data-request-id="${request.id}">Mark delivered</button>`;
  }
  const otherParty = session.role === 'NGO' ? `Donor: ${request.donorName}` : `Receiver: ${request.receiverName}`;
  return `<article class="activity-entry activity-request">
    <span class="activity-entry-icon" aria-hidden="true">${categoryInfo(donation.category).emoji}</span>
    <div class="activity-entry-copy"><strong>${escapeHtml(donation.foodName)}</strong><span>${escapeHtml(otherParty)} · ${escapeHtml(donation.pickupAddress)}</span><span class="status-pill status-${String(donation.status).toLowerCase()}">${escapeHtml(donation.status.replaceAll('_', ' '))}</span></div>
    <div class="activity-request-actions">${actions}</div>
  </article>`;
}

function updateActivitySummary(donations, requests) {
  const completed = requests.filter((request) => request.donation.status === 'DELIVERED').length;
  const active = requests.filter((request) => !['DELIVERED', 'REJECTED', 'CANCELLED', 'EXPIRED'].includes(request.status)).length;
  const quantity = session.role === 'DONOR'
    ? donations.reduce((total, donation) => total + Number(donation.quantityKg), 0)
    : requests.reduce((total, request) => total + Number(request.donation.quantityKg), 0);
  document.getElementById('dashboardDonations').textContent = session.role === 'DONOR' ? String(donations.length) : '—';
  document.getElementById('dashboardRequests').textContent = String(requests.length);
  document.getElementById('dashboardDelivered').textContent = String(completed);
  document.getElementById('activityDelivered').textContent = String(completed);
  document.getElementById('activityRequests').textContent = String(active);
  document.getElementById('activityQuantity').innerHTML = `${quantity.toFixed(2)} <small>kg</small>`;
  document.getElementById('impactDonationCount').textContent = session.role === 'DONOR' ? String(donations.length) : '—';
  document.getElementById('impactQuantity').innerHTML = `${quantity.toFixed(2)} <small>kg</small>`;
  document.getElementById('impactDeliveryCount').textContent = String(completed);
  document.getElementById('impactSummary').textContent = session.role === 'DONOR'
    ? `You have ${donations.length} donation listing${donations.length === 1 ? '' : 's'} and ${completed} completed deliveries.`
    : `Your account has ${requests.length} request${requests.length === 1 ? '' : 's'} and ${completed} completed deliveries.`;
}

async function renderActivity() {
  const activityList = document.getElementById('activityList');
  if (!session) {
    activityList.innerHTML = pageError('Sign in to see your activity.', 'Requests, pickups, and donation status are linked to your account.');
    return;
  }
  try {
    const requests = await gateway.myRequests();
    let rows;
    if (session.role === 'DONOR') {
      const donations = await gateway.myDonations();
      const volunteers = donations.some((donation) => donation.status === 'ACCEPTED' && requests.some((request) => request.donation.id === donation.id))
        ? await gateway.volunteers() : [];
      updateActivitySummary(donations, requests);
      rows = donations.map((donation) => {
        const request = requests.find((entry) => entry.donation.id === donation.id && entry.status !== 'REJECTED');
        return donationActivityRow(donation, request, volunteers);
      });
    } else {
      updateActivitySummary([], requests);
      rows = requests.map(requestActivityRow);
    }
    activityList.innerHTML = rows.length ? rows.join('') : '<div class="empty-state"><strong>No activity yet.</strong>Your account activity will appear here as donations and requests move through the workflow.</div>';
  } catch (error) {
    activityList.innerHTML = pageError('Could not load your activity.', error.message);
  }
}

async function updateImpactPage() {
  if (!session) {
    document.getElementById('impactSummary').textContent = 'Sign in to see activity linked to your account.';
    return;
  }
  await renderActivity();
}

function navigateTo(page) {
  if (!pageTitles[page]) return;
  document.querySelectorAll('.page-section').forEach((section) => {
    section.classList.toggle('is-visible', section.id === `page-${page}`);
  });
  document.querySelectorAll('.nav-item').forEach((item) => {
    const isActive = item.dataset.page === page;
    item.classList.toggle('active', isActive);
    if (isActive) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  });
  document.getElementById('currentPageLabel').textContent = pageTitles[page];
  document.getElementById('sidebar').classList.remove('is-open');
  document.getElementById('menuToggle').setAttribute('aria-expanded', 'false');
  history.replaceState(null, '', `#${page}`);
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (page === 'activity' || page === 'impact') updateImpactPage();
  if (page === 'home' || page === 'discover') renderListings();
}

function showToast(message) {
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.setAttribute('role', 'status');
  toast.textContent = message;
  document.getElementById('toastRegion').append(toast);
  window.setTimeout(() => toast.remove(), 4500);
}

function setAuthMode(mode) {
  authMode = mode;
  const isRegister = mode === 'register';
  document.getElementById('authRegisterFields').hidden = !isRegister;
  document.getElementById('authName').required = isRegister;
  document.getElementById('authTitle').textContent = isRegister ? 'Create your account' : 'Welcome to Goodroots';
  document.getElementById('authDescription').textContent = isRegister
    ? 'Join as a food donor, NGO / receiver, or volunteer.'
    : 'Sign in to continue to your food rescue workspace.';
  document.getElementById('authSubmit').textContent = isRegister ? 'Create account' : 'Sign in';
  document.getElementById('authPassword').autocomplete = isRegister ? 'new-password' : 'current-password';
  document.querySelectorAll('[data-auth-mode]').forEach((button) => {
    button.classList.toggle('selected', button.dataset.authMode === mode);
  });
}

function assistantReply(message, listings) {
  const input = message.toLowerCase();
  const preferences = [
    { match: /produce|vegetable|veggie|fruit/, category: 'produce' },
    { match: /bread|bakery|bagel|pastry/, category: 'bakery' },
    { match: /dinner|meal|ready|soup/, category: 'prepared' }
  ];
  const preference = preferences.find((item) => item.match.test(input));
  const listing = (preference && listings.find((item) => item.category.toLowerCase() === preference.category)) || listings[0];
  if (!listing) return 'There are no available donations to recommend right now. Check back later for new listings.';
  if (/allergy|safe|ingredient/.test(input)) {
    return `Please confirm the allergens, storage, and handling details directly with ${listing.organizationName || listing.donorName}. The deadline is ${formatDate(listing.safeConsumptionDeadline)}; a deadline alone does not establish that food is safe.`;
  }
  return `A nearby listing to review: "${listing.foodName}" from ${listing.organizationName || listing.donorName}. It is ${listing.quantityKg} kg at ${listing.pickupAddress}; collection deadline ${formatDate(listing.safeConsumptionDeadline)}. Check the donor's handling details before requesting it.`;
}

function appendMessage(message, type) {
  const messages = document.getElementById('chatMessages');
  const item = document.createElement('div');
  item.className = `chat-message ${type === 'user' ? 'user-message' : 'assistant-message'}`;
  item.innerHTML = type === 'user'
    ? '<div><p></p><span class="message-time">Just now</span></div>'
    : '<span class="chat-avatar mini">✧</span><div><p></p><span class="message-time">Just now</span></div>';
  item.querySelector('p').textContent = message;
  messages.append(item);
  messages.scrollTop = messages.scrollHeight;
}

document.addEventListener('click', async (event) => {
  const target = event.target instanceof Element ? event.target : null;
  if (!target) return;
  try {
    const pageButton = target.closest('[data-page]');
    if (pageButton) {
      navigateTo(pageButton.dataset.page);
      return;
    }
    if (target.closest('#authOpen, #profileButton')) {
      if (session) {
        session = null;
        sessionStorage.removeItem(SESSION_KEY);
        updateAccountUI();
        await renderListings();
        await renderActivity();
        showToast('You have signed out.');
      } else {
        setAuthMode('login');
        document.getElementById('authModal').showModal();
      }
      return;
    }
    if (target.closest('[data-open-auth]')) {
      setAuthMode('login');
      document.getElementById('authModal').showModal();
      return;
    }
    const authModeButton = target.closest('[data-auth-mode]');
    if (authModeButton) {
      setAuthMode(authModeButton.dataset.authMode);
      return;
    }
    if (target.closest('[data-open-donation]')) {
      if (session?.role !== 'DONOR') {
        showToast(session ? 'Only donor accounts can post food donations.' : 'Sign in with a donor account to post food.');
        return;
      }
      const now = new Date();
      document.getElementById('foodPreparedAt').value = localDateTimeValue(new Date(now.getTime() - 60 * 60 * 1000));
      document.getElementById('foodDeadline').value = localDateTimeValue(new Date(now.getTime() + 4 * 60 * 60 * 1000));
      document.getElementById('donationModal').showModal();
      return;
    }
    if (target.closest('[data-close-modal]')) {
      document.getElementById('donationModal').close();
      return;
    }
    if (target.closest('[data-close-auth]')) {
      document.getElementById('authModal').close();
      return;
    }
    const filterButton = target.closest('[data-filter]');
    if (filterButton) {
      activeFilter = filterButton.dataset.filter;
      document.querySelectorAll('[data-filter]').forEach((button) => {
        button.classList.toggle('selected', button.dataset.filter === activeFilter);
      });
      await renderListings();
      return;
    }
    const requestButton = target.closest('[data-request]');
    if (requestButton) {
      if (session?.role !== 'NGO') {
        showToast(session ? 'Only NGO / receiver accounts can request donations.' : 'Sign in with an NGO / receiver account to request food.');
        return;
      }
      const request = await gateway.requestDonation(Number(requestButton.dataset.request), '');
      showToast(`Request ${request.id} sent. The donor can now review it.`);
      await renderListings();
      await renderActivity();
      return;
    }
    const decisionButton = target.closest('[data-decision]');
    if (decisionButton) {
      const request = await gateway.decideRequest(Number(decisionButton.dataset.requestId), decisionButton.dataset.decision);
      showToast(`Request ${request.id} ${request.status.toLowerCase()}.`);
      await renderListings();
      await renderActivity();
      return;
    }
    const assignButton = target.closest('[data-assign]');
    if (assignButton) {
      const select = document.querySelector(`[data-volunteer="${assignButton.dataset.assign}"]`);
      if (!select?.value) {
        showToast('Choose a registered volunteer before assigning the pickup.');
        return;
      }
      await gateway.assignVolunteer(Number(assignButton.dataset.assign), Number(select.value));
      showToast('Volunteer assigned to the pickup.');
      await renderActivity();
      return;
    }
    const deliveryButton = target.closest('[data-delivery]');
    if (deliveryButton) {
      const result = await gateway.updateDelivery(Number(deliveryButton.dataset.requestId), deliveryButton.dataset.delivery);
      showToast(`Donation status updated to ${result.donation.status.toLowerCase().replaceAll('_', ' ')}.`);
      await renderActivity();
      return;
    }
    if (target.closest('#menuToggle')) {
      const sidebar = document.getElementById('sidebar');
      const open = sidebar.classList.toggle('is-open');
      document.getElementById('menuToggle').setAttribute('aria-expanded', String(open));
      return;
    }
    if (target.closest('#locationButton')) {
      showToast('Search by neighborhood or address in Find food.');
      return;
    }
    if (target.closest('#distanceButton')) {
      showToast('Search by neighborhood or address to narrow available food.');
      return;
    }
    if (target.closest('#sortButton')) {
      showToast('Donations are ordered by collection deadline.');
      return;
    }
    const suggestion = target.closest('.suggestion-chip');
    if (suggestion) {
      document.getElementById('chatInput').value = suggestion.textContent;
      document.getElementById('chatForm').requestSubmit();
    }
  } catch (error) {
    showToast(error.message || 'The action could not be completed. Please try again.');
  }
});

document.getElementById('searchInput').addEventListener('input', (event) => {
  searchQuery = event.target.value.trim();
  updateListingGrids(cachedListings);
});

document.getElementById('donationForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const data = new FormData(form);
  const optional = (name) => String(data.get(name) || '').trim() || null;
  const donation = {
    foodName: optional('foodName'),
    category: data.get('foodCategory'),
    description: optional('description'),
    quantityKg: Number(data.get('quantityKg')),
    preparationTime: data.get('preparationTime'),
    safeConsumptionDeadline: data.get('safeConsumptionDeadline'),
    pickupAddress: optional('pickupAddress'),
    imageUrl: optional('imageUrl'),
    storageConditions: optional('storageConditions'),
    allergens: optional('allergens'),
    handlingInformation: optional('handlingInformation'),
    foodSafetyChecklistConfirmed: data.get('foodSafetyChecklistConfirmed') === 'on'
  };
  try {
    await gateway.createListing(donation);
    document.getElementById('donationModal').close();
    form.reset();
    selectHomeFilter();
    navigateTo('discover');
    showToast('Your donation is listed for registered NGO receivers.');
  } catch (error) {
    showToast(error.message);
  }
});

function selectHomeFilter() {
  activeFilter = 'all';
  document.querySelectorAll('[data-filter]').forEach((button) => {
    button.classList.toggle('selected', button.dataset.filter === 'all');
  });
}

document.getElementById('authForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const data = new FormData(form);
  const payload = {
    email: String(data.get('email')).trim(),
    password: data.get('password')
  };
  if (authMode === 'register') {
    payload.fullName = String(data.get('fullName')).trim();
    payload.organizationName = String(data.get('organizationName') || '').trim() || null;
    payload.role = data.get('role');
  }
  try {
    session = await apiRequest(`/api/auth/${authMode === 'register' ? 'register' : 'login'}`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    updateAccountUI();
    document.getElementById('authModal').close();
    form.reset();
    await renderListings();
    await renderActivity();
    showToast(`Signed in as ${session.fullName} (${session.role}).`);
  } catch (error) {
    showToast(error.message);
  }
});

document.getElementById('chatForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const input = document.getElementById('chatInput');
  const message = input.value.trim();
  if (!message) return;
  appendMessage(message, 'user');
  input.value = '';
  try {
    const listings = await gateway.getListings('all');
    appendMessage(assistantReply(message, listings), 'assistant');
  } catch (error) {
    appendMessage(`I couldn't load live food listings: ${error.message}`, 'assistant');
  }
});

document.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    navigateTo('discover');
    document.getElementById('searchInput').focus();
  }
  if (event.key === 'Escape') document.getElementById('sidebar').classList.remove('is-open');
});

document.getElementById('dashboardDate').textContent = new Intl.DateTimeFormat('en-US', {
  weekday: 'long',
  month: 'long',
  day: 'numeric'
}).format(new Date()).toUpperCase();
updateAccountUI();
setAuthMode('login');
renderListings();
renderActivity();
const startingPage = window.location.hash.slice(1);
if (pageTitles[startingPage]) navigateTo(startingPage);
