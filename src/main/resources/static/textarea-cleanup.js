(function() {
  'use strict';
  
  function cleanTextAreas() {
    var textareas = document.querySelectorAll('textarea[name="requirements"]');
    textareas.forEach(function(textarea) {
      // Force clear if it contains the comment - be extremely aggressive
      var value = textarea.value || '';
      var innerHTML = textarea.innerHTML || '';
      var textContent = textarea.textContent || '';
      
      var hasComment = value.indexOf('onValueChange') > -1 || 
                      value.indexOf('<!--') > -1 ||
                      innerHTML.indexOf('onValueChange') > -1 ||
                      innerHTML.indexOf('<!--') > -1 ||
                      textContent.indexOf('onValueChange') > -1 ||
                      textContent.indexOf('<!--') > -1;
      
      if (hasComment) {
        // Nuclear option: clear everything
        textarea.value = '';
        textarea.innerHTML = '';
        textarea.textContent = '';
        
        // Also clear any child nodes
        while (textarea.firstChild) {
          textarea.removeChild(textarea.firstChild);
        }
      }
      
      // Clean the placeholder
      var placeholder = textarea.getAttribute('placeholder') || '';
      if (placeholder.indexOf('<!--') > -1 || placeholder.indexOf('onValueChange') > -1) {
        textarea.removeAttribute('placeholder');
      }
      
      // Check parent and siblings for comment text nodes or elements
      var parent = textarea.parentElement;
      if (parent) {
        var nodes = Array.from(parent.childNodes);
        nodes.forEach(function(node) {
          if (node !== textarea) {
            var text = node.textContent || '';
            if (text.indexOf('onValueChange') > -1 || text.indexOf('<!--') > -1) {
              if (node.nodeType === Node.TEXT_NODE || node.nodeType === Node.COMMENT_NODE) {
                try {
                  node.parentNode.removeChild(node);
                } catch(e) {}
              } else if (node.nodeType === Node.ELEMENT_NODE && text.length < 100) {
                node.style.display = 'none';
              }
            }
          }
        });
      }
    });
  }
  
  // Run immediately
  cleanTextAreas();
  
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', cleanTextAreas);
  }
  
  // Aggressive repeated cleaning
  setTimeout(cleanTextAreas, 0);
  setTimeout(cleanTextAreas, 10);
  setTimeout(cleanTextAreas, 50);
  setTimeout(cleanTextAreas, 100);
  setTimeout(cleanTextAreas, 200);
  setTimeout(cleanTextAreas, 300);
  setTimeout(cleanTextAreas, 500);
  setTimeout(cleanTextAreas, 1000);
  setTimeout(cleanTextAreas, 2000);
  
  // Observe changes and clean immediately
  var observer = new MutationObserver(function() {
    cleanTextAreas();
  });
  
  setTimeout(function() {
    var form = document.querySelector('form');
    if (form) {
      observer.observe(form, { 
        childList: true, 
        subtree: true, 
        characterData: true,
        attributes: true,
        attributeOldValue: true
      });
    }
    
    // Also observe the textarea directly
    var textarea = document.querySelector('textarea[name="requirements"]');
    if (textarea) {
      observer.observe(textarea, {
        childList: true,
        characterData: true,
        subtree: true
      });
      
      // Listen to input events
      textarea.addEventListener('DOMSubtreeModified', cleanTextAreas, false);
    }
  }, 10);
})();
