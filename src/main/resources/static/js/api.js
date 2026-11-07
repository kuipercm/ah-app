const api = {
  async get(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  },

  async post(url, data) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  },

  async delete(url) {
    const res = await fetch(url, { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return res.status === 204 ? null : await res.json();
  },

  // AH Auth & API
  getAuthStatus() {
    return this.get('/api/ah/auth/status');
  },
  exchangeAuthCode(code) {
    return this.post('/api/ah/auth/exchange', { code });
  },
  logout() {
    return this.post('/api/ah/auth/logout', {});
  },
  searchAh(query) {
    return this.get(`/api/ah/search?query=${encodeURIComponent(query)}`);
  },
  getActiveOrder() {
    return this.get('/api/ah/active-order');
  },
  getAhLists() {
    return this.get('/api/ah/lists');
  },

  // Recipes
  getRecipes() {
    return this.get('/api/recipes');
  },
  saveRecipe(recipe) {
    return this.post('/api/recipes', recipe);
  },
  deleteRecipe(id) {
    return this.delete(`/api/recipes/${id}`);
  },
  bulkParseRecipes(text, autoMatch = true) {
    return this.post('/api/recipes/bulk-parse', { text, autoMatch: String(autoMatch) });
  },
  linkIngredientProduct(ingredientId, webshopId) {
    return this.post(`/api/recipes/ingredients/${ingredientId}/link`, { webshopId });
  },

  // Staples
  getStaples() {
    return this.get('/api/staples');
  },
  getEvaluatedStaples() {
    return this.get('/api/staples/evaluated');
  },
  saveStaple(staple) {
    return this.post('/api/staples', staple);
  },
  deleteStaple(id) {
    return this.delete(`/api/staples/${id}`);
  },
  addStapleAlternative(stapleId, webshopId) {
    return this.post(`/api/staples/${stapleId}/alternative`, { webshopId });
  },

  // Planner
  getUpcomingPlan() {
    return this.get('/api/planner/upcoming');
  },
  buildPlan(planId, recipeIds, staples) {
    return this.post(`/api/planner/${planId}/build`, { recipeIds, staples });
  },
  pushPlanToAh(planId) {
    return this.post(`/api/planner/${planId}/push-ah`, {});
  }
};
