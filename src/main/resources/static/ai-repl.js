(function () {
    "use strict";

    var pyodideReady = null; // shared Promise, resolved once Pyodide is loaded

    function loadPyodide() {
        if (pyodideReady) return pyodideReady;
        pyodideReady = new Promise(function (resolve, reject) {
            var script = document.createElement("script");
            script.src = "https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js";
            script.onload = function () {
                window.loadPyodide({
                    indexURL: "https://cdn.jsdelivr.net/pyodide/v0.26.4/full/"
                }).then(function (py) {
                    // Pre-load numpy (micropip is built-in)
                    py.loadPackage("numpy").then(function () {
                        resolve(py);
                    }).catch(function () {
                        resolve(py); // numpy optional, continue without
                    });
                }).catch(reject);
            };
            script.onerror = function () {
                pyodideReady = null;
                reject(new Error("Failed to load Pyodide"));
            };
            document.head.appendChild(script);
        });
        return pyodideReady;
    }

    function decodeCode(encoded) {
        var ta = document.createElement("textarea");
        ta.innerHTML = encoded;
        return ta.value;
    }

    function initRepl(container) {
        var code = decodeCode(container.getAttribute("data-code") || "");

        // Build UI
        var toolbar = document.createElement("div");
        toolbar.className = "ai-repl-toolbar";

        var label = document.createElement("span");
        label.textContent = "Python (Pyodide)";

        var runBtn = document.createElement("button");
        runBtn.className = "ai-repl-run";
        runBtn.textContent = "\u25B6 Run";

        toolbar.appendChild(label);
        toolbar.appendChild(runBtn);

        var editor = document.createElement("textarea");
        editor.className = "ai-repl-editor";
        editor.value = code;
        editor.spellcheck = false;
        editor.rows = Math.max(3, code.split("\n").length);

        // Handle Tab key in editor
        editor.addEventListener("keydown", function (e) {
            if (e.key === "Tab") {
                e.preventDefault();
                var start = editor.selectionStart;
                var end = editor.selectionEnd;
                editor.value = editor.value.substring(0, start) + "    " + editor.value.substring(end);
                editor.selectionStart = editor.selectionEnd = start + 4;
            }
        });

        var output = document.createElement("div");
        output.className = "ai-repl-output";
        output.textContent = "Click Run to execute";
        output.style.color = "#6c7086";

        container.innerHTML = "";
        container.appendChild(toolbar);
        container.appendChild(editor);
        container.appendChild(output);

        runBtn.addEventListener("click", function () {
            runBtn.disabled = true;
            runBtn.textContent = "Loading...";
            output.className = "ai-repl-output ai-repl-loading";
            output.textContent = "Loading Python runtime...";

            loadPyodide().then(function (py) {
                runBtn.textContent = "Running...";
                output.textContent = "";
                output.className = "ai-repl-output";

                // Capture stdout/stderr
                py.runPython([
                    "import sys, io",
                    "_stdout_capture = io.StringIO()",
                    "_stderr_capture = io.StringIO()",
                    "sys.stdout = _stdout_capture",
                    "sys.stderr = _stderr_capture"
                ].join("\n"));

                try {
                    py.runPython(editor.value);
                    var stdout = py.runPython("_stdout_capture.getvalue()");
                    var stderr = py.runPython("_stderr_capture.getvalue()");
                    if (stderr) {
                        output.className = "ai-repl-output error";
                        output.textContent = stderr;
                    } else {
                        output.textContent = stdout || "(no output)";
                    }
                } catch (err) {
                    output.className = "ai-repl-output error";
                    output.textContent = err.message;
                } finally {
                    py.runPython("sys.stdout = sys.__stdout__\nsys.stderr = sys.__stderr__");
                    runBtn.disabled = false;
                    runBtn.textContent = "\u25B6 Run";
                }
            }).catch(function (err) {
                output.className = "ai-repl-output error";
                output.textContent = "Failed to load Python: " + err.message;
                runBtn.disabled = false;
                runBtn.textContent = "\u25B6 Run";
            });
        });
    }

    function init() {
        var repls = document.querySelectorAll(".ai-repl");
        for (var i = 0; i < repls.length; i++) {
            initRepl(repls[i]);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
