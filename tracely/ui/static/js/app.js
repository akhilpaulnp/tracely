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
            resultsDiv.innerHTML = renderResults(data, toolName);
        })
        .catch(err => {
            resultsDiv.innerHTML = `<p style="color:var(--danger)">Error: ${err.message}</p>`;
        });
}

// Render results with chart + table
function renderResults(data, toolName) {
    const columns = data.columns || [];
    const rows = data.rows || [];

    if (rows.length === 0) {
        return `<p class="muted">No data found for "${toolName}".</p>`;
    }

    let html = `<div class="results-header"><h3>${toolName}</h3><span class="badge">${rows.length} rows</span></div>`;

    // Try to render a chart for known tools
    const chart = renderChart(data, toolName);
    if (chart) html += chart;

    // Always show table
    html += renderTable(columns, rows);

    if (data.truncated) {
        html += `<p class="muted">Results truncated.</p>`;
    }
    return html;
}

// Render chart based on tool type
function renderChart(data, toolName) {
    const rows = data.rows || [];
    if (rows.length === 0) return '';

    if (toolName === 'jank' || toolName === 'startup') {
        return renderBarChart(data, toolName);
    }
    if (toolName === 'memory') {
        return renderMemoryChart(data);
    }
    if (toolName === 'scheduling') {
        return renderHorizontalBars(data);
    }
    if (toolName === 'gc') {
        return renderGCChart(data);
    }
    return '';
}

// Bar chart via SVG
function renderBarChart(data, toolName) {
    const rows = data.rows || [];
    const columns = data.columns || [];

    // Find a numeric column for values and a label column
    let labelCol = columns.find(c => c.includes('name') || c.includes('package')) || columns[0];
    let valueCol = columns.find(c => c.includes('duration') || c.includes('ms') || c.includes('frames')) || columns[1];

    if (!labelCol || !valueCol) return '';

    const items = rows.slice(0, 15).map(r => ({
        label: String(r[labelCol] || '').slice(0, 30),
        value: Number(r[valueCol]) || 0
    }));

    const maxVal = Math.max(...items.map(i => i.value), 1);
    const barHeight = 28;
    const chartWidth = 600;
    const labelWidth = 200;
    const svgHeight = items.length * barHeight + 20;

    let svg = `<div class="chart-container"><svg width="100%" viewBox="0 0 ${chartWidth + labelWidth} ${svgHeight}" class="bar-chart">`;

    items.forEach((item, i) => {
        const y = i * barHeight + 10;
        const barWidth = (item.value / maxVal) * chartWidth;
        const color = item.value > maxVal * 0.8 ? 'var(--danger)' : 'var(--primary)';

        svg += `<text x="0" y="${y + 18}" fill="var(--text-muted)" font-size="11">${item.label}</text>`;
        svg += `<rect x="${labelWidth}" y="${y + 4}" width="${barWidth}" height="${barHeight - 8}" fill="${color}" rx="3"/>`;
        svg += `<text x="${labelWidth + barWidth + 5}" y="${y + 18}" fill="var(--text)" font-size="11">${item.value.toFixed(1)}</text>`;
    });

    svg += '</svg></div>';
    return svg;
}

// Memory counters chart
function renderMemoryChart(data) {
    const rows = data.rows || [];
    if (rows.length === 0) return '';

    const items = rows.slice(0, 10).map(r => ({
        label: String(r.counter_name || r.process_name || '').slice(0, 35),
        min: Number(r.min_value || 0),
        max: Number(r.max_value || 0),
        avg: Number(r.avg_value || 0),
    }));

    const maxVal = Math.max(...items.map(i => i.max), 1);
    const barHeight = 32;
    const chartWidth = 500;
    const labelWidth = 250;
    const svgHeight = items.length * barHeight + 20;

    let svg = `<div class="chart-container"><svg width="100%" viewBox="0 0 ${chartWidth + labelWidth} ${svgHeight}">`;

    items.forEach((item, i) => {
        const y = i * barHeight + 10;
        const minW = (item.min / maxVal) * chartWidth;
        const maxW = (item.max / maxVal) * chartWidth;
        const avgW = (item.avg / maxVal) * chartWidth;

        svg += `<text x="0" y="${y + 20}" fill="var(--text-muted)" font-size="11">${item.label}</text>`;
        // Range bar (min to max)
        svg += `<rect x="${labelWidth + minW}" y="${y + 8}" width="${maxW - minW}" height="${barHeight - 16}" fill="var(--border)" rx="2"/>`;
        // Average marker
        svg += `<rect x="${labelWidth + avgW - 1}" y="${y + 4}" width="3" height="${barHeight - 8}" fill="var(--primary)" rx="1"/>`;
    });

    svg += '</svg></div>';
    return svg;
}

// Horizontal bars for scheduling
function renderHorizontalBars(data) {
    const rows = data.rows || [];
    if (rows.length === 0) return '';

    const columns = data.columns || [];
    const numCols = columns.filter(c => c.includes('ms') || c.includes('running') || c.includes('runnable'));

    if (numCols.length === 0) return '';

    const items = rows.slice(0, 12).map(r => {
        const total = numCols.reduce((sum, c) => sum + (Number(r[c]) || 0), 0);
        return { label: String(r.thread_name || r.process_name || '').slice(0, 25), total, row: r };
    });

    items.sort((a, b) => b.total - a.total);
    const maxVal = Math.max(...items.map(i => i.total), 1);
    const barHeight = 24;
    const chartWidth = 500;
    const labelWidth = 180;
    const svgHeight = items.length * barHeight + 20;

    let svg = `<div class="chart-container"><svg width="100%" viewBox="0 0 ${chartWidth + labelWidth} ${svgHeight}">`;
    const colors = ['var(--success)', 'var(--warning)', 'var(--text-muted)'];

    items.forEach((item, i) => {
        const y = i * barHeight + 10;
        svg += `<text x="0" y="${y + 16}" fill="var(--text-muted)" font-size="11">${item.label}</text>`;

        let offset = labelWidth;
        numCols.forEach((col, ci) => {
            const val = Number(item.row[col]) || 0;
            const w = (val / maxVal) * chartWidth;
            svg += `<rect x="${offset}" y="${y + 2}" width="${w}" height="${barHeight - 6}" fill="${colors[ci % colors.length]}" rx="2"/>`;
            offset += w;
        });

        svg += `<text x="${offset + 4}" y="${y + 16}" fill="var(--text)" font-size="10">${item.total.toFixed(0)}ms</text>`;
    });

    svg += '</svg></div>';

    // Legend
    svg += '<div class="chart-legend">';
    numCols.forEach((col, i) => {
        svg += `<span class="legend-item"><span class="legend-dot" style="background:${colors[i % colors.length]}"></span>${col}</span>`;
    });
    svg += '</div>';

    return svg;
}

// GC events chart
function renderGCChart(data) {
    const rows = data.rows || [];
    if (rows.length === 0) return '';

    const items = rows.slice(0, 20).map(r => ({
        duration: Number(r.duration_ms || r.duration || 0),
        reclaimed: Number(r.reclaimed_mb || r.reclaimed_bytes || 0),
    }));

    const maxDur = Math.max(...items.map(i => i.duration), 1);
    const barWidth = Math.min(30, 600 / items.length);
    const chartHeight = 100;
    const svgWidth = items.length * barWidth + 20;

    let svg = `<div class="chart-container"><svg width="100%" viewBox="0 0 ${svgWidth} ${chartHeight + 30}">`;
    svg += `<line x1="10" y1="${chartHeight}" x2="${svgWidth}" y2="${chartHeight}" stroke="var(--border)" stroke-width="1"/>`;

    items.forEach((item, i) => {
        const x = i * barWidth + 15;
        const h = (item.duration / maxDur) * chartHeight;
        const color = item.duration > 16 ? 'var(--danger)' : item.duration > 8 ? 'var(--warning)' : 'var(--success)';
        svg += `<rect x="${x}" y="${chartHeight - h}" width="${barWidth - 4}" height="${h}" fill="${color}" rx="2"/>`;
    });

    svg += `<text x="5" y="${chartHeight + 20}" fill="var(--text-muted)" font-size="10">GC pauses (ms) - red >16ms, yellow >8ms</text>`;
    svg += '</svg></div>';
    return svg;
}

// Render JSON {columns, rows} as HTML table
function renderTable(columns, rows) {
    let html = '<table class="table"><thead><tr>';
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
                        <span class="device-status connected"></span>
                        <strong>${d.model || d.serial}</strong><br>
                        <small class="muted">Android ${d.android_version || '?'} (API ${d.api_level || '?'})</small><br>
                        <small class="muted">${d.manufacturer || ''} - ${d.serial}</small>
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
        document.getElementById('capture-msg').textContent =
            `Recording... ${elapsed}s / ${durationS}s`;
        if (elapsed >= durationS) {
            clearInterval(interval);
            fill.style.width = '98%';
            document.getElementById('capture-msg').textContent = 'Pulling trace from device...';
            pollCaptureStatus();
        }
    }, 1000);
}

function pollCaptureStatus() {
    const statusDiv = document.getElementById('capture-status') || document.getElementById('capture-result');
    if (!statusDiv) return;

    fetch('/api/capture/status')
        .then(r => r.json())
        .then(data => {
            if (data.status === 'done') {
                const path = data.result?.path || '';
                const fname = path.split('/').pop();
                statusDiv.innerHTML = `
                    <h3 style="color:var(--success)">Capture Complete</h3>
                    <p class="path">${fname}</p>
                    <p class="muted">Trace auto-loaded as "default"</p>
                    <a href="/analysis" class="btn btn-primary">Analyze Trace</a>
                `;
            } else if (data.status === 'error') {
                statusDiv.innerHTML = `<p style="color:var(--danger)">${data.result?.error || 'Capture failed'}</p>`;
            } else if (data.status === 'capturing') {
                setTimeout(pollCaptureStatus, 2000);
            }
        })
        .catch(() => setTimeout(pollCaptureStatus, 2000));
}

// Loaded traces sidebar handler
document.addEventListener('htmx:afterSwap', function(event) {
    if (event.detail.target.id === 'loaded-traces') {
        try {
            const data = JSON.parse(event.detail.xhr.responseText);
            const panel = event.detail.target;
            if (data.traces && data.traces.length > 0) {
                let html = '<h4 style="font-size:0.8rem;color:var(--text-muted);margin-bottom:0.5rem">Loaded Traces</h4>';
                data.traces.forEach(t => {
                    const fname = t.path.split('/').pop();
                    html += `<div class="loaded-trace-item">
                        <strong>${t.alias}</strong><br>
                        <span class="muted">${fname.slice(0, 28)}</span>
                    </div>`;
                });
                panel.innerHTML = html;
            } else {
                panel.innerHTML = '<p style="font-size:0.8rem" class="muted">No traces loaded</p>';
            }
        } catch(e) {}
    }
});

// Trace comparison
function compareTool(toolName) {
    const alias1 = document.getElementById('compare-trace-1')?.value || '';
    const alias2 = document.getElementById('compare-trace-2')?.value || '';
    const pkg = document.getElementById('compare-package')?.value || '';
    const resultsDiv = document.getElementById('compare-results');

    if (!alias1 || !alias2) {
        resultsDiv.innerHTML = '<p style="color:var(--warning)">Select two traces to compare.</p>';
        return;
    }

    resultsDiv.innerHTML = '<p class="muted">Loading comparison...</p>';

    const url1 = `/api/analysis/${toolName}?alias=${alias1}${pkg ? '&package=' + pkg : ''}`;
    const url2 = `/api/analysis/${toolName}?alias=${alias2}${pkg ? '&package=' + pkg : ''}`;

    Promise.all([fetch(url1).then(r => r.json()), fetch(url2).then(r => r.json())])
        .then(([data1, data2]) => {
            if (data1.error || data2.error) {
                resultsDiv.innerHTML = `<p style="color:var(--danger)">${data1.error || data2.error}</p>`;
                return;
            }
            resultsDiv.innerHTML = renderComparison(data1, data2, toolName, alias1, alias2);
        });
}

function renderComparison(data1, data2, toolName, alias1, alias2) {
    const cols = data1.columns || [];
    const rows1 = data1.rows || [];
    const rows2 = data2.rows || [];

    let html = `<h3>${toolName} Comparison</h3>`;
    html += `<div class="compare-legend"><span class="legend-item"><span class="legend-dot" style="background:var(--primary)"></span>${alias1}</span>`;
    html += `<span class="legend-item"><span class="legend-dot" style="background:var(--warning)"></span>${alias2}</span></div>`;

    // Find numeric columns for comparison
    const numCols = cols.filter(c => {
        const sample = rows1[0]?.[c];
        return typeof sample === 'number';
    });

    const labelCol = cols.find(c => c.includes('name') || c.includes('package')) || cols[0];

    if (numCols.length > 0 && rows1.length > 0) {
        // Side-by-side bar comparison
        const valCol = numCols[0];
        const items1 = rows1.slice(0, 10).map(r => ({ label: String(r[labelCol] || ''), value: Number(r[valCol]) || 0 }));
        const items2Map = {};
        rows2.forEach(r => { items2Map[r[labelCol]] = Number(r[valCol]) || 0; });

        const maxVal = Math.max(...items1.map(i => Math.max(i.value, items2Map[i.label] || 0)), 1);
        const barHeight = 40;
        const chartWidth = 500;
        const labelWidth = 200;
        const svgHeight = items1.length * barHeight + 20;

        html += `<div class="chart-container"><svg width="100%" viewBox="0 0 ${chartWidth + labelWidth} ${svgHeight}">`;
        items1.forEach((item, i) => {
            const y = i * barHeight + 10;
            const w1 = (item.value / maxVal) * chartWidth;
            const w2 = ((items2Map[item.label] || 0) / maxVal) * chartWidth;

            html += `<text x="0" y="${y + 22}" fill="var(--text-muted)" font-size="11">${item.label.slice(0, 25)}</text>`;
            html += `<rect x="${labelWidth}" y="${y + 2}" width="${w1}" height="14" fill="var(--primary)" rx="2" opacity="0.8"/>`;
            html += `<rect x="${labelWidth}" y="${y + 18}" width="${w2}" height="14" fill="var(--warning)" rx="2" opacity="0.8"/>`;
        });
        html += '</svg></div>';
    }

    // Delta table
    html += '<table class="table"><thead><tr><th>Metric</th>';
    html += `<th>${alias1}</th><th>${alias2}</th><th>Delta</th></tr></thead><tbody>`;

    const labelKey = labelCol;
    rows1.slice(0, 10).forEach(r1 => {
        const r2 = rows2.find(r => r[labelKey] === r1[labelKey]);
        if (!r2) return;
        numCols.slice(0, 3).forEach(col => {
            const v1 = Number(r1[col]) || 0;
            const v2 = Number(r2[col]) || 0;
            const delta = v2 - v1;
            const pct = v1 !== 0 ? ((delta / v1) * 100).toFixed(1) : '-';
            const color = delta > 0 ? 'var(--danger)' : delta < 0 ? 'var(--success)' : 'var(--text)';
            html += `<tr><td>${r1[labelKey]?.toString().slice(0, 20)} (${col})</td>`;
            html += `<td>${v1.toFixed(1)}</td><td>${v2.toFixed(1)}</td>`;
            html += `<td style="color:${color}">${delta > 0 ? '+' : ''}${delta.toFixed(1)} (${pct}%)</td></tr>`;
        });
    });

    html += '</tbody></table>';
    return html;
}
