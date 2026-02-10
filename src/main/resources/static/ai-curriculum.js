(function () {
  'use strict';

  var progress = {};
  var subsectionIds = [];

  function init() {
    fetch('/ai/api/progress')
      .then(function (r) { return r.json(); })
      .then(function (data) {
        progress = data || {};
        injectCheckboxes();
        injectPhaseBadges();
        buildSummary();
      })
      .catch(function () {
        injectCheckboxes();
        injectPhaseBadges();
        buildSummary();
      });
  }

  function extractSubsectionId(text) {
    var m = text.match(/^(\d+\.\d+)\s/);
    return m ? m[1] : null;
  }

  function extractPhaseNumber(text) {
    var m = text.match(/^Phase\s+(\d+)/i);
    return m ? parseInt(m[1], 10) : null;
  }

  function injectCheckboxes() {
    var prose = document.querySelector('.prose');
    if (!prose) return;
    var headings = prose.querySelectorAll('h2');
    subsectionIds = [];

    headings.forEach(function (h2) {
      var id = extractSubsectionId(h2.textContent.trim());
      if (!id) return;
      subsectionIds.push(id);

      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.className = 'ai-checkbox';
      cb.dataset.subsection = id;
      cb.checked = !!progress[id];

      if (cb.checked) h2.classList.add('ai-subsection-done');
      else h2.classList.remove('ai-subsection-done');

      cb.addEventListener('change', function () {
        onToggle(id, cb.checked, h2);
      });

      h2.insertBefore(cb, h2.firstChild);
    });
  }

  function injectPhaseBadges() {
    var prose = document.querySelector('.prose');
    if (!prose) return;
    var h1s = prose.querySelectorAll('h1');

    h1s.forEach(function (h1) {
      var phaseNum = extractPhaseNumber(h1.textContent.trim());
      if (phaseNum == null) return;

      var prefix = phaseNum + '.';
      var phaseIds = subsectionIds.filter(function (sid) {
        return sid.startsWith(prefix);
      });
      var done = phaseIds.filter(function (sid) { return !!progress[sid]; }).length;
      var total = phaseIds.length;
      if (total === 0) return;

      var existing = h1.querySelector('.ai-phase-badge');
      if (existing) existing.remove();

      var badge = document.createElement('span');
      badge.className = 'ai-phase-badge' + (done === total ? ' all-done' : '');
      badge.textContent = done + '/' + total;
      h1.appendChild(badge);
    });
  }

  function buildSummary() {
    var container = document.getElementById('ai-progress-summary');
    if (!container) return;

    var total = subsectionIds.length;
    var done = subsectionIds.filter(function (sid) { return !!progress[sid]; }).length;
    var pct = total > 0 ? Math.round((done / total) * 100) : 0;

    container.innerHTML =
      '<div class="ai-summary-text">' + done + ' / ' + total + ' subsections completed (' + pct + '%)</div>' +
      '<div class="ai-progress-bar-track">' +
      '<div class="ai-progress-bar-fill" style="width:' + pct + '%"></div>' +
      '</div>';
  }

  function onToggle(id, completed, h2) {
    progress[id] = completed;
    if (completed) h2.classList.add('ai-subsection-done');
    else h2.classList.remove('ai-subsection-done');

    injectPhaseBadges();
    buildSummary();

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
