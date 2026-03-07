(function () {
  var textarea = document.querySelector('textarea[name="content"]');
  if (!textarea) return;

  // Load marked.js
  var script = document.createElement('script');
  script.src = 'https://cdn.jsdelivr.net/npm/marked/marked.min.js';
  script.onload = initPreview;
  document.head.appendChild(script);

  function initPreview() {
    // Find the textarea's parent form-group container
    var group = textarea.parentElement;
    if (!group) return;

    // Create wrapper for side-by-side layout
    var wrapper = document.createElement('div');
    wrapper.style.cssText = 'display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start;';

    // Move textarea into wrapper
    var textareaContainer = document.createElement('div');
    textareaContainer.appendChild(textarea.cloneNode(true));
    var newTextarea = textareaContainer.querySelector('textarea');
    textarea.parentNode.replaceChild(wrapper, textarea);

    // Create preview panel
    var preview = document.createElement('div');
    preview.style.cssText =
      'background:#1a1a2e;color:#e0e0e0;border:1px solid #333;border-radius:8px;' +
      'padding:16px;min-height:200px;max-height:500px;overflow-y:auto;font-size:14px;line-height:1.6;';
    preview.innerHTML = '<em style="color:#888;">Markdown preview...</em>';

    // Style embedded elements
    var previewStyles = document.createElement('style');
    previewStyles.textContent =
      '.md-preview h1,.md-preview h2,.md-preview h3{margin:0.5em 0 0.3em;color:#fff;}' +
      '.md-preview code{background:#0b0d12;padding:0.15em 0.4em;border-radius:4px;font-size:0.9em;}' +
      '.md-preview pre{background:#0b0d12;padding:1rem;border-radius:8px;overflow-x:auto;}' +
      '.md-preview pre code{background:transparent;padding:0;}' +
      '.md-preview a{color:#6ea8fe;}' +
      '.md-preview blockquote{border-left:3px solid #444;margin:0.5em 0;padding:0.3em 1em;color:#aaa;}' +
      '.md-preview ul,.md-preview ol{padding-left:1.5em;}' +
      '.md-preview table{border-collapse:collapse;width:100%;}' +
      '.md-preview th,.md-preview td{border:1px solid #444;padding:6px 10px;text-align:left;}' +
      '.md-preview img{max-width:100%;height:auto;}';
    document.head.appendChild(previewStyles);
    preview.classList.add('md-preview');

    wrapper.appendChild(textareaContainer);
    wrapper.appendChild(preview);

    // Sync value and name
    newTextarea.style.width = '100%';
    newTextarea.style.minHeight = '200px';
    newTextarea.style.boxSizing = 'border-box';

    function render() {
      var text = newTextarea.value;
      if (!text.trim()) {
        preview.innerHTML = '<em style="color:#888;">Markdown preview...</em>';
      } else {
        preview.innerHTML = marked.parse(text);
      }
    }

    newTextarea.addEventListener('input', render);
    render();
  }
})();
