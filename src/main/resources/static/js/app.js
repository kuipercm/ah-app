// Global Application State
const state = {
  currentPlan: null,
  recipes: [],
  selectedRecipeIds: new Set(),
  evaluatedStaples: null,
  selectedStaples: new Map(), // stapleId -> { stapleId, quantity, chosenProductId }
  bonusSwapsApplied: new Map(), // stapleId -> alternativeProductId
  activeIngredientToLink: null,
  sortByBonus: false
};

// Initialization on DOMContentLoaded
document.addEventListener('DOMContentLoaded', async () => {
  setupNavigation();
  setupModals();
  setupEventHandlers();

  await checkAhStatus();
  await loadUpcomingPlan();
  await loadRecipes();
  await loadEvaluatedStaples();
  await refreshAggregatedPlan();
});

// Setup Navigation Tabs
function setupNavigation() {
  const tabs = document.querySelectorAll('.tab-btn');
  tabs.forEach(btn => {
    btn.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const tabId = btn.getAttribute('data-tab');
      document.getElementById(`${tabId}-tab`).classList.add('active');

      if (tabId === 'recipes') renderAllRecipes();
      if (tabId === 'staples') renderManageStaples();
      if (tabId === 'settings') renderSettings();
    });
  });

  document.getElementById('ahStatusBadge').addEventListener('click', () => {
    document.querySelector('.tab-btn[data-tab="settings"]').click();
  });
}

// Check AH Authentication Status
async function checkAhStatus() {
  try {
    const status = await api.getAuthStatus();
    const badge = document.getElementById('ahStatusBadge');
    const text = document.getElementById('ahStatusText');

    if (status.authenticated) {
      badge.className = 'auth-badge connected';
      text.textContent = `AH Gekoppeld (${status.userName || 'Account'})`;
    } else {
      badge.className = 'auth-badge disconnected';
      text.textContent = 'AH niet gekoppeld';
    }
  } catch (e) {
    console.error('Failed to get AH status:', e);
  }
}

// Load upcoming plan for next Tuesday
async function loadUpcomingPlan() {
  try {
    state.currentPlan = await api.getUpcomingPlan();
    const badge = document.getElementById('deliverySlotBadge');
    const d = state.currentPlan.targetDeliveryDate;
    if (d) {
      const dateParts = d.split('-');
      badge.innerHTML = `🚚 Volgende bezorging: <strong>Dinsdag ${dateParts[2]}-${dateParts[1]}</strong>`;
    }

    if (state.currentPlan.selectedRecipes) {
      state.selectedRecipeIds = new Set(state.currentPlan.selectedRecipes.map(r => r.id));
    }
  } catch (e) {
    console.error('Failed to load plan:', e);
  }
}

// Load all recipes
async function loadRecipes() {
  try {
    state.recipes = await api.getRecipes();
    renderPlannerRecipes();
  } catch (e) {
    console.error('Failed to load recipes:', e);
  }
}

// Render recipes in Planner
function renderPlannerRecipes() {
  const grid = document.getElementById('plannerRecipeGrid');
  grid.innerHTML = '';

  let list = [...state.recipes];
  if (state.sortByBonus) {
    list.sort((a, b) => (b.bonusCount || 0) - (a.bonusCount || 0));
  }

  list.forEach(recipe => {
    const isSelected = state.selectedRecipeIds.has(recipe.id);
    const card = document.createElement('div');
    card.className = `recipe-card ${isSelected ? 'selected' : ''}`;

    const bonusBadge = recipe.bonusCount > 0
      ? `<span class="badge badge-bonus" title="${recipe.bonusItems?.join(', ')}">★ ${recipe.bonusCount} in Bonus!</span>`
      : '';

    const notesHtml = recipe.notes
      ? `<div class="recipe-notes">${escapeHtml(recipe.notes)}</div>`
      : '';

    const tagsHtml = (recipe.ingredients || []).map(i => {
      const isBonus = i.matchedProduct && i.matchedProduct.isBonus;
      return `<span class="ingredient-tag ${isBonus ? 'bonus' : ''}">${escapeHtml(i.name)}</span>`;
    }).join('');

    card.innerHTML = `
      <div>
        <div class="recipe-card-header">
          <div class="recipe-title">${escapeHtml(recipe.name)}</div>
          ${bonusBadge}
        </div>
        ${notesHtml}
        <div class="ingredient-tags">${tagsHtml}</div>
      </div>
      <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center;">
        <span style="font-size: 0.8rem; color: var(--text-secondary);">${(recipe.ingredients || []).length} ingrediënten</span>
        <button class="btn btn-sm ${isSelected ? 'btn-primary' : 'btn-secondary'}">
          ${isSelected ? '✓ Gekozen' : '+ Kies recept'}
        </button>
      </div>
    `;

    card.addEventListener('click', async () => {
      if (state.selectedRecipeIds.has(recipe.id)) {
        state.selectedRecipeIds.delete(recipe.id);
      } else {
        state.selectedRecipeIds.add(recipe.id);
      }
      renderPlannerRecipes();
      await refreshAggregatedPlan();
    });

    grid.appendChild(card);
  });
}

// Load and evaluate staples for Tuesday
async function loadEvaluatedStaples() {
  try {
    state.evaluatedStaples = await api.getEvaluatedStaples();
    renderStaplesInPlanner();
  } catch (e) {
    console.error('Failed to load evaluated staples:', e);
  }
}

// Render staples in Weekly Planner
function renderStaplesInPlanner() {
  if (!state.evaluatedStaples) return;

  const { weeklyItems, occasionalDue, occasionalBonusPullForward, occasionalNotDue } = state.evaluatedStaples;

  // 1. Weekly items (pre-select by default if not set)
  const weeklyContainer = document.getElementById('weeklyStaplesList');
  weeklyContainer.innerHTML = '';

  const swapContainer = document.getElementById('bonusSwapContainer');
  swapContainer.innerHTML = '';

  weeklyItems.forEach(item => {
    if (!state.selectedStaples.has(item.id)) {
      state.selectedStaples.set(item.id, {
        stapleId: item.id,
        quantity: item.defaultQuantity || 1,
        chosenProductId: item.defaultProduct?.webshopId
      });
    }

    // Check if a Bonus Swap exists (e.g. Volkoren Brood alternative in bonus)
    if (item.bonusSwap && item.bonusSwap.hasSwap) {
      const alt = item.bonusSwap.alternativeProduct;
      const isSwapped = state.bonusSwapsApplied.get(item.id) === alt.webshopId;

      const swapEl = document.createElement('div');
      swapEl.className = 'swap-card';
      swapEl.innerHTML = `
        <div class="swap-text">
          💡 <strong>Bonus Swap voor ${escapeHtml(item.name)}:</strong>
          Vervang <em>${escapeHtml(item.defaultProduct?.title || 'Standaard')}</em> door 
          <strong>${escapeHtml(alt.title)}</strong> (${escapeHtml(item.bonusSwap.reason)})
        </div>
        <div>
          <button class="btn btn-sm ${isSwapped ? 'btn-secondary' : 'btn-bonus'}" id="btnSwap-${item.id}">
            ${isSwapped ? 'Ongedaan maken' : '✓ Vervang door Bonus!'}
          </button>
        </div>
      `;

      swapContainer.appendChild(swapEl);

      swapEl.querySelector(`#btnSwap-${item.id}`).addEventListener('click', async () => {
        if (isSwapped) {
          state.bonusSwapsApplied.delete(item.id);
          state.selectedStaples.get(item.id).chosenProductId = item.defaultProduct?.webshopId;
        } else {
          state.bonusSwapsApplied.set(item.id, alt.webshopId);
          state.selectedStaples.get(item.id).chosenProductId = alt.webshopId;
        }
        renderStaplesInPlanner();
        await refreshAggregatedPlan();
      });
    }

    weeklyContainer.appendChild(createChecklistItem(item));
  });

  // 2. Occasionals DUE (pre-select by default)
  const dueContainer = document.getElementById('occasionalDueList');
  dueContainer.innerHTML = '';
  if (occasionalDue.length === 0) {
    dueContainer.innerHTML = '<div style="color: var(--text-secondary); font-size: 0.9rem;">Geen periodieke artikelen die deze week volgens schema aan de beurt zijn.</div>';
  } else {
    occasionalDue.forEach(item => {
      if (!state.selectedStaples.has(item.id)) {
        state.selectedStaples.set(item.id, {
          stapleId: item.id,
          quantity: item.defaultQuantity || 1,
          chosenProductId: item.defaultProduct?.webshopId
        });
      }
      dueContainer.appendChild(createChecklistItem(item));
    });
  }

  // 3. Occasionals on BONUS (Pull-Forward recommendations!)
  const bonusContainer = document.getElementById('occasionalBonusList');
  bonusContainer.innerHTML = '';
  if (occasionalBonusPullForward.length === 0) {
    bonusContainer.innerHTML = '<div style="color: var(--text-secondary); font-size: 0.9rem;">Geen extra voorraadartikelen in de bonus deze week.</div>';
  } else {
    occasionalBonusPullForward.forEach(item => {
      const el = createChecklistItem(item, true);
      el.classList.add('pull-forward-item');
      bonusContainer.appendChild(el);
    });
  }

  // 4. Occasionals NOT due yet
  const notDueContainer = document.getElementById('occasionalNotDueList');
  notDueContainer.innerHTML = '';
  occasionalNotDue.forEach(item => {
    notDueContainer.appendChild(createChecklistItem(item, false));
  });
}

// Creates an interactive checklist item DOM element
function createChecklistItem(item, isPullForward = false) {
  const isChecked = state.selectedStaples.has(item.id);
  const selData = state.selectedStaples.get(item.id) || { quantity: item.defaultQuantity || 1 };

  let product = item.defaultProduct;
  const isSwapped = state.bonusSwapsApplied.has(item.id);
  if (isSwapped && item.bonusSwap?.alternativeProduct) {
    product = item.bonusSwap.alternativeProduct;
  }

  const priceText = product
    ? `€ ${product.effectivePrice ? product.effectivePrice.toFixed(2) : (product.price ? product.price.toFixed(2) : '0.00')}`
    : '';

  const bonusBadge = (product && product.isBonus)
    ? `<span class="badge badge-bonus" style="font-size: 0.7rem;">${escapeHtml(product.bonusMechanism || 'Bonus')}</span>`
    : '';

  const el = document.createElement('div');
  el.className = 'checklist-item';
  el.innerHTML = `
    <div class="item-left">
      <input type="checkbox" class="item-checkbox" ${isChecked ? 'checked' : ''}>
      ${product && product.imageUrl ? `<img src="${product.imageUrl}" class="item-thumb" alt="">` : ''}
      <div class="item-info">
        <div class="item-name">
          ${escapeHtml(item.name)}
          ${bonusBadge}
          ${isSwapped ? '<span style="font-size: 0.75rem; color: #ea580c; font-weight: 700;">(Bonus Vervanger)</span>' : ''}
        </div>
        <div class="item-subtext">
          ${product ? escapeHtml(product.title) : 'Geen AH product gekoppeld'} 
          ${product && product.unitSize ? `(${escapeHtml(product.unitSize)})` : ''}
          ${item.frequencyWeeks ? ` • Elke ${item.frequencyWeeks} wkn` : ''}
          ${item.lastOrderedDate ? ` • Laatst: ${item.lastOrderedDate}` : ''}
        </div>
      </div>
    </div>
    <div class="item-right">
      <span class="item-price ${product && product.isBonus ? 'bonus' : ''}">${priceText}</span>
      <input type="number" class="item-qty-input" min="1" value="${selData.quantity || 1}" ${!isChecked ? 'disabled' : ''}>
    </div>
  `;

  const chk = el.querySelector('.item-checkbox');
  const qtyInput = el.querySelector('.item-qty-input');

  chk.addEventListener('change', async () => {
    if (chk.checked) {
      qtyInput.disabled = false;
      state.selectedStaples.set(item.id, {
        stapleId: item.id,
        quantity: parseInt(qtyInput.value) || 1,
        chosenProductId: product?.webshopId
      });
    } else {
      qtyInput.disabled = true;
      state.selectedStaples.delete(item.id);
    }
    await refreshAggregatedPlan();
  });

  qtyInput.addEventListener('change', async () => {
    if (chk.checked) {
      const q = Math.max(1, parseInt(qtyInput.value) || 1);
      state.selectedStaples.get(item.id).quantity = q;
      await refreshAggregatedPlan();
    }
  });

  return el;
}

// Refresh aggregated plan from backend
async function refreshAggregatedPlan() {
  if (!state.currentPlan) return;

  try {
    const staplesPayload = Array.from(state.selectedStaples.values());
    const recipeIds = Array.from(state.selectedRecipeIds);

    const updatedPlan = await api.buildPlan(state.currentPlan.id, recipeIds, staplesPayload);
    state.currentPlan = updatedPlan;

    renderAggregatedCart(updatedPlan);
  } catch (e) {
    console.error('Failed to update plan:', e);
  }
}

// Render the deduplicated aggregated cart
function renderAggregatedCart(plan) {
  const container = document.getElementById('aggregatedCartList');
  container.innerHTML = '';

  const items = plan.items || [];
  let totalCost = 0;
  let totalQuantity = 0;

  if (items.length === 0) {
    container.innerHTML = '<div style="padding: 16px; color: var(--text-secondary); text-align: center;">Kies minimaal 1 recept of artikel om het geconsolideerde mandje te zien.</div>';
  } else {
    items.forEach(item => {
      const p = item.product;
      const effectivePrice = p?.bonusPrice != null && p?.isBonus ? p.bonusPrice : (p?.price || 0);
      const lineCost = effectivePrice * item.quantity;
      totalCost += lineCost;
      totalQuantity += item.quantity;

      const el = document.createElement('div');
      el.className = 'checklist-item';
      el.innerHTML = `
        <div class="item-left">
          ${p?.imageUrl ? `<img src="${p.imageUrl}" class="item-thumb" alt="">` : ''}
          <div class="item-info">
            <div class="item-name">
              ${p ? escapeHtml(p.title) : 'Onbekend product'}
              ${item.isBonus ? `<span class="badge badge-bonus" style="font-size: 0.7rem;">${escapeHtml(item.bonusMechanism || 'Bonus')}</span>` : ''}
              ${item.isSwapped ? '<span style="font-size: 0.75rem; color: #ea580c; font-weight: 700;">(Bonus Swap)</span>' : ''}
            </div>
            <div class="item-subtext">
              Bron: ${escapeHtml(item.sourceName || '')} ${p?.unitSize ? `• ${escapeHtml(p.unitSize)}` : ''}
            </div>
          </div>
        </div>
        <div class="item-right">
          <span style="font-weight: 700; font-size: 0.95rem; color: var(--ah-blue);">${item.quantity}x</span>
          <span class="item-price ${item.isBonus ? 'bonus' : ''}">€ ${lineCost.toFixed(2)}</span>
        </div>
      `;
      container.appendChild(el);
    });
  }

  document.getElementById('summaryItemCount').textContent = `${totalQuantity} stuks (${items.length} uniek)`;
  document.getElementById('summaryRecipeCount').textContent = state.selectedRecipeIds.size;
  document.getElementById('summaryTotalCost').textContent = `€ ${totalCost.toFixed(2)}`;
}

// Setup Event Handlers
function setupEventHandlers() {
  // Sort recipes by bonus
  document.getElementById('btnSortBonus').addEventListener('click', () => {
    state.sortByBonus = !state.sortByBonus;
    document.getElementById('btnSortBonus').textContent = state.sortByBonus
      ? '✓ Gesorteerd op Bonus'
      : '🔥 Sorteer op meeste Bonus';
    renderPlannerRecipes();
  });

  // Open Bulk Modal from Planner
  document.getElementById('btnOpenBulkFromPlanner').addEventListener('click', () => {
    openModal('bulkImportModal');
  });

  document.getElementById('btnOpenBulkModal').addEventListener('click', () => {
    openModal('bulkImportModal');
  });

  // Submit bulk text
  document.getElementById('btnSubmitBulk').addEventListener('click', async () => {
    const text = document.getElementById('bulkText').value.trim();
    if (!text) return alert('Voer minimaal 1 recept in');

    const autoMatch = document.getElementById('chkAutoMatch').checked;
    try {
      const newRecipes = await api.bulkParseRecipes(text, autoMatch);
      alert(`${newRecipes.length} recept(en) succesvol geïmporteerd!`);
      closeModal('bulkImportModal');
      document.getElementById('bulkText').value = '';
      await loadRecipes();
      if (document.getElementById('recipes-tab').classList.contains('active')) {
        renderAllRecipes();
      }
    } catch (e) {
      alert('Fout bij importeren: ' + e.message);
    }
  });

  // Refresh cart button
  document.getElementById('btnRefreshCart').addEventListener('click', async () => {
    await refreshAggregatedPlan();
  });

  // Push to AH
  document.getElementById('btnPushToAh').addEventListener('click', async () => {
    if (!state.currentPlan || !state.currentPlan.items || state.currentPlan.items.length === 0) {
      return alert('Je bestellijst is leeg. Selecteer eerst recepten of artikelen.');
    }

    const btn = document.getElementById('btnPushToAh');
    btn.disabled = true;
    btn.textContent = 'Bezig met versturen naar AH...';

    try {
      const res = await api.pushPlanToAh(state.currentPlan.id);
      if (res.success) {
        if (res.target === 'BESTELLING') {
          alert(`🎉 Succes! ${res.itemCount} artikelen zijn direct toegevoegd aan je actieve Albert Heijn bezorgmoment!`);
        } else {
          alert(`🎉 Succes! ${res.itemCount} artikelen zijn toegevoegd aan je AH Mijn Lijstje! Open de AH app om direct te bestellen.`);
        }
        await loadEvaluatedStaples();
      } else {
        alert('Melding van AH: ' + (res.message || 'Kon niet voltooien'));
      }
    } catch (e) {
      alert('Fout bij verzenden naar AH: ' + e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Bestelling naar Albert Heijn sturen 🚀';
    }
  });

  // Fetch AH lists
  document.getElementById('btnFetchAhLists').addEventListener('click', async () => {
    try {
      const lists = await api.getAhLists();
      renderAhLists(lists);
    } catch (e) {
      alert('Kon AH lijsten niet ophalen: ' + e.message);
    }
  });
}

// Render All Recipes tab
function renderAllRecipes() {
  const grid = document.getElementById('allRecipesGrid');
  grid.innerHTML = '';

  state.recipes.forEach(recipe => {
    const card = document.createElement('div');
    card.className = 'recipe-card';

    const ingredientsList = (recipe.ingredients || []).map(i => {
      const p = i.matchedProduct;
      return `
        <div style="display: flex; justify-content: space-between; align-items: center; margin: 4px 0; font-size: 0.85rem;">
          <div>
            <strong>${escapeHtml(i.name)}</strong>
            ${p ? `<span style="color: var(--text-secondary); font-size: 0.8rem;"> → ${escapeHtml(p.title)}</span>` : '<span style="color: #dc2626;"> (Niet gekoppeld)</span>'}
          </div>
          <button class="btn btn-secondary btn-sm" onclick="openProductLinkModal(${i.id}, '${escapeHtml(i.name)}')">
            ${p ? 'Wijzig' : 'Koppel AH'}
          </button>
        </div>
      `;
    }).join('');

    card.innerHTML = `
      <div>
        <div class="recipe-card-header">
          <div class="recipe-title">${escapeHtml(recipe.name)}</div>
          <button class="btn btn-sm" style="color: #dc2626; background: none; border: none; cursor: pointer;" onclick="deleteRecipeItem(${recipe.id})">🗑️</button>
        </div>
        ${recipe.notes ? `<div class="recipe-notes">${escapeHtml(recipe.notes)}</div>` : ''}
        <div style="margin-top: 10px; border-top: 1px solid var(--border-color); padding-top: 8px;">
          ${ingredientsList}
        </div>
      </div>
    `;

    grid.appendChild(card);
  });
}

// Global functions for inline handlers
window.deleteRecipeItem = async function(id) {
  if (confirm('Weet je zeker dat je dit recept wilt verwijderen?')) {
    await api.deleteRecipe(id);
    await loadRecipes();
    renderAllRecipes();
  }
};

window.openProductLinkModal = function(ingredientId, ingredientName) {
  state.activeIngredientToLink = ingredientId;
  document.getElementById('ahSearchModalTitle').textContent = `Koppel AH Product voor: ${ingredientName}`;
  document.getElementById('ahSearchInput').value = ingredientName;
  openModal('ahSearchModal');
  executeAhSearch(ingredientName);
};

// AH Search Modal Execution
async function executeAhSearch(query) {
  const container = document.getElementById('ahSearchResults');
  container.innerHTML = '<div style="padding: 12px; color: var(--text-secondary);">Zoeken in AH catalogus...</div>';

  try {
    const products = await api.searchAh(query);
    container.innerHTML = '';

    if (products.length === 0) {
      container.innerHTML = '<div style="padding: 12px; color: var(--text-secondary);">Geen producten gevonden</div>';
      return;
    }

    products.forEach(p => {
      const item = document.createElement('div');
      item.className = 'checklist-item';
      item.style.cursor = 'pointer';
      item.innerHTML = `
        <div class="item-left">
          ${p.imageUrl ? `<img src="${p.imageUrl}" class="item-thumb" alt="">` : ''}
          <div class="item-info">
            <div class="item-name">
              ${escapeHtml(p.title)}
              ${p.isBonus ? `<span class="badge badge-bonus" style="font-size: 0.7rem;">${escapeHtml(p.bonusMechanism || 'Bonus')}</span>` : ''}
            </div>
            <div class="item-subtext">${escapeHtml(p.unitSize || '')} • € ${p.price ? p.price.toFixed(2) : ''}</div>
          </div>
        </div>
        <button class="btn btn-primary btn-sm">Selecteer</button>
      `;

      item.addEventListener('click', async () => {
        if (state.activeIngredientToLink) {
          await api.linkIngredientProduct(state.activeIngredientToLink, p.webshopId);
          closeModal('ahSearchModal');
          await loadRecipes();
          renderAllRecipes();
          renderPlannerRecipes();
          await refreshAggregatedPlan();
        }
      });

      container.appendChild(item);
    });
  } catch (e) {
    container.innerHTML = `<div style="color: #dc2626; padding: 12px;">Fout bij zoeken: ${e.message}</div>`;
  }
}

document.getElementById('btnExecuteAhSearch').addEventListener('click', () => {
  const q = document.getElementById('ahSearchInput').value.trim();
  if (q) executeAhSearch(q);
});

// Render Staples Management
async function renderManageStaples() {
  const staples = await api.getStaples();
  const weeklyEl = document.getElementById('manageWeeklyList');
  const occEl = document.getElementById('manageOccasionalList');
  weeklyEl.innerHTML = '';
  occEl.innerHTML = '';

  staples.forEach(s => {
    const p = s.defaultProduct;
    const item = document.createElement('div');
    item.className = 'checklist-item';

    const altsText = (s.alternativeProducts || []).map(a => a.title).join(', ');

    item.innerHTML = `
      <div class="item-left">
        ${p?.imageUrl ? `<img src="${p.imageUrl}" class="item-thumb" alt="">` : ''}
        <div class="item-info">
          <div class="item-name">${escapeHtml(s.name)}</div>
          <div class="item-subtext">
            Standaard: ${p ? escapeHtml(p.title) : 'Geen'} 
            ${s.frequencyWeeks ? ` • Frequentie: elke ${s.frequencyWeeks} weken` : ''}
            ${s.bonusSearchQuery ? ` • Bonus zoekterm: "${escapeHtml(s.bonusSearchQuery)}"` : ''}
            ${altsText ? ` • Alternatieven: ${escapeHtml(altsText)}` : ''}
          </div>
        </div>
      </div>
      <button class="btn btn-secondary btn-sm" onclick="deleteStapleItem(${s.id})">Verwijder</button>
    `;

    if (s.type === 'WEEKLY') {
      weeklyEl.appendChild(item);
    } else {
      occEl.appendChild(item);
    }
  });
}

window.deleteStapleItem = async function(id) {
  if (confirm('Weet je zeker dat je dit artikel wilt verwijderen?')) {
    await api.deleteStaple(id);
    await renderManageStaples();
    await loadEvaluatedStaples();
  }
};

// Render Settings & AH Connection
async function renderSettings() {
  const status = await api.getAuthStatus();
  const card = document.getElementById('ahConnectionCard');

  if (status.authenticated) {
    card.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div>
          <h3 style="color: var(--success); margin-bottom: 6px;">✓ Gekoppeld met Albert Heijn</h3>
          <p style="font-size: 0.9rem; color: var(--text-secondary);">
            Ingelogd als: <strong>${escapeHtml(status.userName)}</strong><br>
            Actief sessietoken wordt automatisch vernieuwd op de achtergrond.
          </p>
        </div>
        <button class="btn btn-secondary" id="btnAhLogout">Ontkoppelen</button>
      </div>
    `;

    document.getElementById('btnAhLogout').addEventListener('click', async () => {
      await api.logout();
      await checkAhStatus();
      renderSettings();
    });
  } else {
    card.innerHTML = `
      <h3 style="margin-bottom: 8px;">Koppel je Albert Heijn Account</h3>
      <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 16px;">
        Volg deze eenmalige stappen om je bestellingen direct in je actieve bezorgslot of lijstje te kunnen plaatsen:
      </p>

      <ol style="font-size: 0.9rem; margin-left: 20px; line-height: 1.8; margin-bottom: 20px;">
        <li>Klik hieronder op <strong>"Open AH Inlogpagina"</strong> (opent in nieuw tabblad).</li>
        <li>Log in met je Albert Heijn e-mailadres en wachtwoord.</li>
        <li>Na inloggen probeert de pagina te openen naar een link die begint met <code>appie://login-exit?code=...</code></li>
        <li>Kopieer die volledige link of code uit je browser adresbalk en plak hem hieronder:</li>
      </ol>

      <div style="margin-bottom: 16px;">
        <a href="${status.loginUrl}" target="_blank" class="btn btn-primary" style="text-decoration: none;">
          1. Open AH Inlogpagina ↗
        </a>
      </div>

      <div class="form-group">
        <label class="form-label">2. Plak hier de gekopieerde code of redirect URL:</label>
        <div style="display: flex; gap: 8px;">
          <input type="text" class="form-control" id="ahAuthCodeInput" placeholder="appie://login-exit?code=xxxxxxxx of code direct">
          <button class="btn btn-bonus" id="btnSubmitAhCode">Koppelen</button>
        </div>
      </div>
    `;

    document.getElementById('btnSubmitAhCode').addEventListener('click', async () => {
      const code = document.getElementById('ahAuthCodeInput').value.trim();
      if (!code) return alert('Voer de code of redirect link in');

      const res = await api.exchangeAuthCode(code);
      if (res.success) {
        alert('🎉 Succesvol gekoppeld met Albert Heijn!');
        await checkAhStatus();
        renderSettings();
      } else {
        alert('Koppeling mislukt. Controleer of de code compleet is gekopieerd.');
      }
    });
  }
}

// Render AH lists
function renderAhLists(lists) {
  const container = document.getElementById('ahListsContainer');
  container.innerHTML = '';

  if (lists.length === 0) {
    container.innerHTML = '<div style="color: var(--text-secondary); font-size: 0.9rem;">Geen persoonlijke lijsten gevonden in je profiel.</div>';
    return;
  }

  lists.forEach(l => {
    const el = document.createElement('div');
    el.className = 'checklist-item';
    el.innerHTML = `
      <div class="item-left">
        <div class="item-info">
          <div class="item-name">${escapeHtml(l.title)}</div>
          <div class="item-subtext">${l.itemCount} artikelen</div>
        </div>
      </div>
      <span class="badge badge-cadence">AH Lijst</span>
    `;
    container.appendChild(el);
  });
}

// Modal helper
function setupModals() {
  document.querySelectorAll('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => {
      const modalId = btn.getAttribute('data-close');
      closeModal(modalId);
    });
  });

  window.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal-overlay')) {
      e.target.classList.remove('active');
    }
  });
}

function openModal(id) {
  document.getElementById(id).classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
