(function() {
  function cleanTextAreas() {
    var textareas = document.querySelectorAll('textarea[name="requirements"]');
    textareas.forEach(function(textarea) {
      var value = textarea.value || '';
      if (value.indexOf('<!--') > -1 || value.indexOf('onValueChange') > -1) {
        textarea.value = '';
      }
    });
  }
  
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', cleanTextAreas);
  } else {
    cleanTextAreas();
  }
  
  // Also run after a short delay to catch late renders
  setTimeout(cleanTextAreas, 100);
  setTimeout(cleanTextAreas, 500);
})();
