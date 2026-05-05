/* Tracely UI - client-side JS */

// Run analysis tool
function runTool(toolName) {
    const alias = document.getElementById('trace-select')?.value || 'default';
    const pkg = document.getElementById('package-input')?.value || '';
    const resultsDiv = document.getElementById('analysis-results');

    resultsDiv.innerHTML = '<p class="muted">Loading...</p>';

    let url = `/api/analysis/${toolName}?alias=${alias}`;
    if (pkg) url += `&package=${encodeURIComponent(pkg)}`;

    fetch(url)
        .then(r => r.json())
        .then(data => {
            if (data.error) {
                resultsDiv.innerHTML = `<p style="color:var(--danger)">${data.error}</p>`;
                return;
            }
            resultsDiv.innerHTML = renderTable(data, toolName);
        })
        .catch(err => {
            resultsDiv.innerHTML = `<p style="color:var(--danger)">Error: ${err.message}</p>`;
        });
}

// Render JSON {columns, rows} as HTML table
function renderTable(data, toolName) {
    const columns = data.columns || [];
    const rows = data.rows || [];

    if (rows.length === 0) {
        return `<p class="muted">No data found for "${toolName}".</p>`;
    }

    let html = `<h3>${toolName} (${rows.length} rows)</h3>`;
    html += '<table class="table"><thead><tr>';
    columns.forEach(col => { html += `<th>${col}</th>`; });
    html += '</tr></thead><tbody>';

    rows.forEach(row => {
        html += '<tr>';
        columns.forEach(col => {
            let val = row[col];
            if (typeof val === 'number' && !Number.isInteger(val)) {
                val = val.toFixed(2);
            }
            html += `<td>${val ?? '-'}</td>`;
        });
        html += '</tr>';
    });

    html += '</tbody></table>';
    if (data.truncated) {
        html += `<p class="muted">Results truncated to ${rows.length} rows.</p>`;
    }
    return html;
}

// Device panel HTMX response handler
document.addEventListener('htmx:afterSwap', function(event) {
    if (event.detail.target.id === 'device-panel') {
        try {
            const data = JSON.parse(event.detail.xhr.responseText);
            const panel = event.detail.target;
            if (data.devices && data.devices.length > 0) {
                let html = '';
                data.devices.forEach(d => {
                    html += `<div class="device-card">
                        <strong>${d.model || d.serial}</strong><br>
                        <small class="muted">Android ${d.android_version || '?'} (API ${d.api_level || '?'})</small><br>
                        <small class="muted">${d.serial}</small>
                    </div>`;
                });
                panel.innerHTML = html;
            } else if (data.error) {
                panel.innerHTML = `<p class="muted">${data.error}</p>`;
            } else {
                panel.innerHTML = '<p class="muted">No devices connected.</p>';
            }
        } catch(e) {}
    }
});

// Capture form handler
document.addEventListener('htmx:afterSwap', function(event) {
    if (event.detail.target.id === 'capture-status' || event.detail.target.id === 'capture-result') {
        try {
            const data = JSON.parse(event.detail.xhr.responseText);
            const target = event.detail.target;
            if (data.status === 'capture_started') {
                const dur = data.duration_s || 10;
                target.innerHTML = `
                    <h3>Capturing... (${dur}s)</h3>
                    <div class="progress-bar"><div class="progress-bar-fill" id="progress-fill"></div></div>
                    <p class="muted" id="capture-msg">Recording trace data from device...</p>
                `;
                startProgressAnimation(dur);
            } else if (data.error) {
                target.innerHTML = `<p style="color:var(--danger)">${data.error}</p>`;
            }
        } catch(e) {}
    }
});

function startProgressAnimation(durationS) {
    const fill = document.getElementById('progress-fill');
    if (!fill) return;
    let elapsed = 0;
    const interval = setInterval(() => {
        elapsed++;
        const pct = Math.min((elapsed / durationS) * 100, 95);
        fill.style.width = pct + '%';
        if (elapsed >= durationS) {
            clearInterval(interval);
            // Poll for completion
            pollCaptureStatus();
        }
    }, 1000);
}

function pollCaptureStatus() {
    const statusDiv = document.getElementById('capture-status') || document.getElementById('capture-result');
    fetch('/api/capture/status')
        .then(r => r.json())
        .then(data => {
            if (data.status === 'done') {
                const path = data.result?.path || '';
                const fname = path.split('/').pop();
                statusDiv.innerHTML = `
                    <h3 style="color:var(--success)">Capture Complete</h3>
                    <p>${fname}</p>
                    <a href="/analysis" class="btn btn-primary">Analyze Trace</a>
                `;
            } else if (data.status === 'error') {
                statusDiv.innerHTML = `<p style="color:var(--danger)">${data.result?.error || 'Capture failed'}</p>`;
            } else if (data.status === 'capturing') {
                setTimeout(pollCaptureStatus, 1000);
            }
        });
}

// Loaded traces sidebar handler
document.addEventListener('htmx:afterSwap', function(event) {
    if (event.detail.target.id === 'loaded-traces') {
        try {
            const data = JSON.parse(event.detail.xhr.responseText);
            const panel = event.detail.target;
            if (data.traces && data.traces.length > 0) {
                let html = '<h4 style="font-size:0.8rem;color:var(--text-muted);margin-bottom:0.5rem">Loaded</h4>';
                data.traces.forEach(t => {
                    const fname = t.path.split('/').pop();
                    html += `<div style="padding:0.3rem 0;font-size:0.8rem">
                        <strong>${t.alias}</strong><br>
                        <span class="muted">${fname.slice(0, 25)}</span>
                    </div>`;
                });
                panel.innerHTML = html;
            } else {
                panel.innerHTML = '<p style="font-size:0.8rem" class="muted">No traces loaded</p>';
            }
        } catch(e) {}
    }
});
