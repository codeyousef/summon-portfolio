(function () {
  'use strict';

  var progress = {};

  function init() {
    fetch('/ai/api/progress')
      .then(function (r) { return r.json(); })
      .then(function (data) {
        progress = data || {};
        render();
      })
      .catch(function () {
        render();
      });
  }

  function render() {
    var lessonEl = document.getElementById('ai-lesson-progress');
    if (lessonEl) {
      renderLessonPage(lessonEl);
    } else {
      renderOverviewPage();
    }
  }

  // ── Lesson page ──────────────────────────────────────────────────────────

  function renderLessonPage(container) {
    var sectionId = container.getAttribute('data-section-id');
    if (!sectionId) return;

    var label = document.createElement('label');
    var cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.className = 'ai-checkbox';
    cb.checked = !!progress[sectionId];

    cb.addEventListener('change', function () {
      progress[sectionId] = cb.checked;
      saveProgress(sectionId, cb.checked);
    });

    var span = document.createElement('span');
    span.textContent = 'Mark as completed';

    label.appendChild(cb);
    label.appendChild(span);
    container.appendChild(label);
  }

  // ── Overview page ────────────────────────────────────────────────────────

  function renderOverviewPage() {
    var subsectionIds = injectOverviewCheckboxes();
    injectPhaseBadges();
    buildSummary(subsectionIds);
  }

  function injectOverviewCheckboxes() {
    var placeholders = document.querySelectorAll('.ai-overview-checkbox');
    var ids = [];

    placeholders.forEach(function (el) {
      var id = el.getAttribute('data-subsection');
      if (!id) return;
      ids.push(id);

      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.className = 'ai-checkbox';
      cb.checked = !!progress[id];

      // Find the sibling lesson link to apply done styling
      var link = el.parentElement && el.parentElement.querySelector('.ai-lesson-link');

      if (cb.checked && link) link.classList.add('ai-subsection-done');

      cb.addEventListener('change', function () {
        progress[id] = cb.checked;
        if (link) {
          if (cb.checked) link.classList.add('ai-subsection-done');
          else link.classList.remove('ai-subsection-done');
        }
        injectPhaseBadges();
        buildSummary(ids);
        saveProgress(id, cb.checked);
      });

      el.appendChild(cb);
    });

    return ids;
  }

  function injectPhaseBadges() {
    var cards = document.querySelectorAll('.ai-phase-card');

    cards.forEach(function (card) {
      var checkboxes = card.querySelectorAll('.ai-checkbox');
      if (checkboxes.length === 0) return;

      var total = checkboxes.length;
      var done = 0;
      checkboxes.forEach(function (cb) { if (cb.checked) done++; });

      var titleEl = card.querySelector('.ai-phase-title');
      if (!titleEl) return;

      var existing = titleEl.parentElement.querySelector('.ai-phase-badge');
      if (existing) existing.remove();

      var badge = document.createElement('span');
      badge.className = 'ai-phase-badge' + (done === total ? ' all-done' : '');
      badge.textContent = done + '/' + total;
      titleEl.parentElement.appendChild(badge);
    });
  }

  function buildSummary(subsectionIds) {
    var container = document.getElementById('ai-progress-summary');
    if (!container || !subsectionIds) return;

    var total = subsectionIds.length;
    var done = subsectionIds.filter(function (id) { return !!progress[id]; }).length;
    var pct = total > 0 ? Math.round((done / total) * 100) : 0;

    container.innerHTML =
      '<div class="ai-summary-text">' + done + ' / ' + total + ' subsections completed (' + pct + '%)</div>' +
      '<div class="ai-progress-bar-track">' +
      '<div class="ai-progress-bar-fill" style="width:' + pct + '%"></div>' +
      '</div>';
  }

  // ── Shared ───────────────────────────────────────────────────────────────

  function saveProgress(id, completed) {
    fetch('/ai/api/progress', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: id, completed: completed })
    }).catch(function (err) {
      console.error('Failed to save progress:', err);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
