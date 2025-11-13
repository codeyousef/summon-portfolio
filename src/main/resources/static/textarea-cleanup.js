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
      
      // Check parent and siblings for comment text nodes or elements
      var parent = textarea.parentElement;
      if (parent) {
        // Remove text nodes with the comment
        var nodes = Array.from(parent.childNodes);
        nodes.forEach(function(node) {
          if (node.nodeType === Node.TEXT_NODE || node.nodeType === Node.COMMENT_NODE) {
            var text = node.textContent || '';
            if (text.indexOf('onValueChange') > -1 || text.indexOf('<!--') > -1) {
              node.parentNode.removeChild(node);
            }
          } else if (node.nodeType === Node.ELEMENT_NODE && node !== textarea) {
            var elemText = node.textContent || '';
            if (elemText.indexOf('onValueChange') > -1 && elemText.length < 100) {
              node.style.display = 'none';
            }
          }
        });
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
  setTimeout(cleanTextAreas, 2000);
  
  // Also observe for changes
  var observer = new MutationObserver(cleanTextAreas);
  setTimeout(function() {
    var form = document.querySelector('form');
    if (form) {
      observer.observe(form, { childList: true, subtree: true, characterData: true });
    }
  }, 100);
})();
