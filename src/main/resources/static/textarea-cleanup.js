(function() {
  function cleanTextAreas() {
    var textareas = document.querySelectorAll('textarea[name="requirements"]');
    textareas.forEach(function(textarea) {
      // Clean the value
      var value = textarea.value || '';
      if (value.indexOf('<!--') > -1 || value.indexOf('onValueChange') > -1) {
        textarea.value = '';
      }
      
      // Clean the placeholder
      var placeholder = textarea.getAttribute('placeholder') || '';
      if (placeholder.indexOf('<!--') > -1 || placeholder.indexOf('onValueChange') > -1) {
        textarea.setAttribute('placeholder', '');
      }
      
      // Clean innerHTML/textContent
      var content = textarea.innerHTML || textarea.textContent || '';
      if (content.indexOf('<!--') > -1 || content.indexOf('onValueChange') > -1) {
        textarea.innerHTML = '';
        textarea.textContent = '';
      }
    });
  }
  
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', cleanTextAreas);
  } else {
    cleanTextAreas();
  }
  
  // Run multiple times to catch late renders
  setTimeout(cleanTextAreas, 50);
  setTimeout(cleanTextAreas, 100);
  setTimeout(cleanTextAreas, 300);
  setTimeout(cleanTextAreas, 500);
  setTimeout(cleanTextAreas, 1000);
  
  // Also observe for changes
  var observer = new MutationObserver(cleanTextAreas);
  setTimeout(function() {
    var form = document.querySelector('form');
    if (form) {
      observer.observe(form, { childList: true, subtree: true, characterData: true });
    }
  }, 100);
})();
