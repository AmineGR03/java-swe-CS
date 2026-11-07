'use strict';

// ---------------------------------------------------------------- helpers

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

async function api(path, body) {
  const res = await fetch(path, body === undefined ? { cache: 'no-store' } : {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  let data = {};
  try { data = await res.json(); } catch (e) { data = {}; }
  data.__status = res.status;
  return data;
}

function mmss(ms) {
  const total = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

// ------------------------------------------------- markdown (the subset)

function md(src) {
  const lines = String(src || '').replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let i = 0;

  const inline = (text) => {
    const spans = [];
    let t = text.replace(/`([^`]+)`/g, (_, c) => { spans.push(c); return '\u0000' + (spans.length - 1) + '\u0000'; });
    t = esc(t);
    t = t.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    t = t.replace(/(^|[^\w*])\*([^*\n]+?)\*(?![\w*])/g, '$1<em>$2</em>');
    return t.replace(/\u0000(\d+)\u0000/g, (_, k) => '<code>' + esc(spans[+k]) + '</code>');
  };

  const cells = (line) => line.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());

  while (i < lines.length) {
    const line = lines[i];
    const t = line.trim();

    if (t.startsWith('```')) {
      const buf = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith('```')) buf.push(lines[i++]);
      i++;
      out.push('<pre><code>' + esc(buf.join('\n')) + '</code></pre>');
      continue;
    }
    if (!t) { i++; continue; }
    if (/^---+$/.test(t)) { out.push('<hr>'); i++; continue; }
    if (t.startsWith('<!--')) { i++; continue; }

    const head = t.match(/^(#{1,4})\s+(.*)$/);
    if (head) {
      const level = Math.min(6, head[1].length + 1);
      out.push(`<h${level}>${inline(head[2])}</h${level}>`);
      i++;
      continue;
    }

    // A line that is nothing but bold text acts as a sub-heading.
    const strongOnly = t.match(/^\*\*(.+)\*\*$/);
    if (strongOnly && !strongOnly[1].includes('**')) {
      out.push('<h4>' + inline(strongOnly[1]) + '</h4>');
      i++;
      continue;
    }

    if (t.startsWith('|')) {
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) rows.push(lines[i++]);
      if (rows.length >= 2) {
        const header = cells(rows[0]);
        const align = cells(rows[1]).map((c) => (c.endsWith(':') ? ' class="right"' : ''));
        let html = '<table><thead><tr>';
        header.forEach((c, k) => { html += `<th${align[k] || ''}>${inline(c)}</th>`; });
        html += '</tr></thead><tbody>';
        rows.slice(2).forEach((r) => {
          html += '<tr>';
          cells(r).forEach((c, k) => { html += `<td${align[k] || ''}>${inline(c)}</td>`; });
          html += '</tr>';
        });
        out.push(html + '</tbody></table>');
      }
      continue;
    }

    if (t.startsWith('>')) {
      const buf = [];
      while (i < lines.length && lines[i].trim().startsWith('>')) buf.push(lines[i++].trim().slice(1).trim());
      out.push('<blockquote><p>' + inline(buf.join(' ')) + '</p></blockquote>');
      continue;
    }

    const bullet = line.match(/^\s*([-*]|\d+\.)\s+(.*)$/);
    if (bullet) {
      const ordered = /\d/.test(bullet[1]);
      const items = [];
      while (i < lines.length) {
        const m = lines[i].match(/^\s*([-*]|\d+\.)\s+(.*)$/);
        if (!m) {
          // fold indented continuation lines into the current item
          if (items.length && lines[i].trim() && /^\s{2,}/.test(lines[i])) {
            items[items.length - 1] += ' ' + lines[i].trim();
            i++;
            continue;
          }
          break;
        }
        items.push(m[2]);
        i++;
      }
      const tag = ordered ? 'ol' : 'ul';
      out.push(`<${tag}>` + items.map((x) => '<li>' + inline(x) + '</li>').join('') + `</${tag}>`);
      continue;
    }

    const buf = [t];
    i++;
    while (i < lines.length) {
      const n = lines[i].trim();
      if (!n || /^(```|\||>|#{1,4}\s|---+$)/.test(n) || /^([-*]|\d+\.)\s/.test(n)) break;
      buf.push(n);
      i++;
    }
    out.push('<p>' + inline(buf.join(' ')) + '</p>');
  }
  return out.join('\n');
}

// ---------------------------------------------------------------- state

const S = {
  manifest: null,
  part: null,       // PartSpec currently being attempted
  tasks: [],
  files: {},        // path -> source
  starters: {},     // path -> original source
  active: 0,
  deadline: 0,      // local epoch ms, corrected on every poll
  duration: 0,
  phase: 'idle',
  saveTimer: null,
  dirty: false,
  closing: false,
  submitting: false,
};

// ---------------------------------------------------------------- boot

async function boot() {
  S.manifest = await api('/api/manifest');
  if (S.manifest.demoSeconds > 0) {
    const b = $('demo-banner');
    b.textContent = 'Demo clock: both parts last ' + S.manifest.demoSeconds
      + ' seconds. Restart the server without --demo-seconds for a real attempt.';
    b.classList.remove('hidden');
  }
  const state = await api('/api/state');
  if (state.phase === 'running' || state.phase === 'grading') {
    const resumed = await api('/api/resume');
    enterExam(state.part, resumed.files || {}, state);
  } else if (state.phase === 'submitted') {
    const report = await api('/api/report');
    showResult(report);
  } else {
    showStart();
  }
  setInterval(poll, 4000);
  setInterval(tick, 250);
}

function showStart() {
  const host = $('parts');
  host.innerHTML = '';
  S.manifest.parts.forEach((p) => {
    const card = document.createElement('div');
    card.className = 'part-card';
    card.innerHTML = `
      <h3>${esc(p.name)}</h3>
      <div class="meta">
        <span><b>${p.minutes}</b> min</span>
        <span><b>${p.points}</b> pts</span>
        <span>${esc(p.blurb)}</span>
      </div>
      <ol>${p.tasks.map((t) => '<li>' + esc(t.title) + '</li>').join('')}</ol>
      <button class="btn btn-primary" data-part="${p.id}">Start Part ${esc(p.id)}</button>`;
    card.querySelector('button').addEventListener('click', () => confirmStart(p));
    host.appendChild(card);
  });
  document.body.dataset.screen = 'start';
}

function confirmStart(part) {
  const minutes = S.manifest.demoSeconds > 0
    ? (S.manifest.demoSeconds + ' seconds') : (part.minutes + ' minutes');
  dialog(`
    <h3>Start ${esc(part.name)}?</h3>
    <p>You get <strong>${minutes}</strong>. The clock starts now and runs on the
       server — it cannot be paused, and reloading does not help.</p>
    <p>When it reaches zero your work is submitted and graded automatically.</p>
    <div class="row">
      <button class="btn" id="dlg-cancel">Not yet</button>
      <button class="btn btn-primary" id="dlg-go">Start the clock</button>
    </div>`);
  $('dlg-cancel').addEventListener('click', closeDialog);
  $('dlg-go').addEventListener('click', async () => {
    $('dlg-go').disabled = true;
    const state = await api('/api/start', { part: part.id });
    if (state.__status !== 200) {
      dialog(`<h3>Could not start</h3><p>${esc(state.error || 'unknown error')}</p>
              <div class="row"><button class="btn" id="dlg-cancel2">Close</button></div>`);
      $('dlg-cancel2').addEventListener('click', closeDialog);
      return;
    }
    const resumed = await api('/api/resume');
    closeDialog();
    enterExam(part.id, resumed.files || {}, state);
  });
}

// ---------------------------------------------------------------- exam

function enterExam(partId, files, state) {
  S.part = S.manifest.parts.find((p) => p.id === partId);
  S.tasks = S.part.tasks;
  S.files = Object.assign({}, files);
  // Baseline from the manifest, so a reload does not make edited files look
  // untouched (the resumed save is the candidate's work, not the starter).
  S.starters = {};
  S.tasks.forEach((t) => { S.starters[t.file] = t.starter || ''; });
  S.duration = state.durationMs;
  S.deadline = Date.now() + state.remainingMs;
  S.phase = state.phase;
  S.active = -1; // nothing loaded yet, so selectTask must not stash the empty box

  $('exam-part').textContent = S.part.name;
  renderTabs();
  selectTask(0);
  document.body.dataset.screen = 'exam';
  $('code').focus();
  tick();
}

function renderTabs() {
  const host = $('tabs');
  host.innerHTML = '';
  S.tasks.forEach((task, idx) => {
    const b = document.createElement('button');
    b.className = 'tab';
    b.setAttribute('role', 'tab');
    b.setAttribute('aria-selected', String(idx === S.active));
    b.dataset.touched = String((S.files[task.file] || '') !== (S.starters[task.file] || ''));
    b.innerHTML = `<span class="dot"></span>${esc(task.label)}`;
    b.title = task.title;
    b.addEventListener('click', () => selectTask(idx));
    host.appendChild(b);
  });
}

function selectTask(idx) {
  stash();
  S.active = idx;
  const task = S.tasks[idx];
  $('statement').innerHTML =
    `<p class="statement-title">${esc(S.part.id === 'A' ? 'Task' : 'Level')} ${idx + 1} of ${S.tasks.length}</p>`
    + `<h2>${esc(task.title)}</h2>`
    + md(task.statement)
    + (S.part.id === 'B' && idx === 0 ? '<hr>' + md(S.part.intro) : '');
  $('statement').scrollTop = 0;
  $('filename').textContent = task.file;
  const code = $('code');
  if (code.value !== (S.files[task.file] || '')) {
    code.value = S.files[task.file] || '';
  }
  syncGutter();
  renderTabs();
}

/** Copy the textarea back into the in-memory file map. */
function stash() {
  if (S.active < 0 || !S.tasks.length) return;
  const task = S.tasks[S.active];
  if (!task) return;
  S.files[task.file] = $('code').value;
}

// ---------------------------------------------------------------- clock

async function poll() {
  if (S.phase === 'submitted' || S.phase === 'idle') return;
  const state = await api('/api/state');
  if (state.phase === 'submitted') {
    S.phase = 'submitted';
    const report = await api('/api/report');
    showResult(report);
    return;
  }
  if (state.phase === 'idle') return;
  S.phase = state.phase;
  S.deadline = Date.now() + state.remainingMs;
  S.duration = state.durationMs;
}

function tick() {
  if (document.body.dataset.screen !== 'exam') return;
  const left = S.deadline - Date.now();
  const frac = S.duration > 0 ? Math.max(0, Math.min(1, left / S.duration)) : 0;
  const level = left <= 120000 ? 'crit' : left <= 600000 ? 'warn' : 'ok';

  $('clock-time').textContent = mmss(left);
  $('clock').dataset.level = level;
  const track = document.querySelector('.clock-track');
  track.dataset.level = level;
  $('clock-fill').style.width = (frac * 100).toFixed(2) + '%';
  $('clock-label').textContent = left <= 0 ? 'time up' : 'remaining';

  if (left <= 0 && !S.submitting && S.phase !== 'submitted') {
    submit(true);
  }
}

// ---------------------------------------------------------------- saving

function refreshDots() {
  const host = $('tabs');
  S.tasks.forEach((task, idx) => {
    const tab = host.children[idx];
    if (tab) tab.dataset.touched = String((S.files[task.file] || '') !== (S.starters[task.file] || ''));
  });
}

function markDirty() {
  S.dirty = true;
  stash();
  refreshDots();
  $('save-state').textContent = 'unsaved';
  clearTimeout(S.saveTimer);
  S.saveTimer = setTimeout(save, 1200);
}

async function save() {
  if (S.phase !== 'running' || S.submitting) return;
  stash();
  const res = await api('/api/save', { files: S.files });
  if (res.__status === 200) {
    S.dirty = false;
    const t = new Date();
    $('save-state').textContent = 'saved ' + String(t.getHours()).padStart(2, '0')
      + ':' + String(t.getMinutes()).padStart(2, '0') + ':' + String(t.getSeconds()).padStart(2, '0');
  } else if (res.__status === 410) {
    $('save-state').textContent = 'time up';
  }
}

setInterval(() => { if (S.dirty) save(); }, 15000);

// ---------------------------------------------------------------- compile

async function compile() {
  if (S.phase !== 'running') return;
  stash();
  const btn = $('compile-btn');
  btn.disabled = true;
  btn.textContent = 'Compiling…';
  showConsole('running javac…', '');
  const res = await api('/api/compile', { files: S.files });
  btn.disabled = false;
  btn.textContent = 'Compile check';
  if (res.__status === 410) {
    showConsole('Time is up.', 'bad');
    return;
  }
  if (res.ok) {
    showConsole('Compiles cleanly.\n\nNo test results until you submit — that is by design.', 'ok');
  } else {
    showConsole(res.output || 'Compilation failed.', 'bad');
  }
}

function showConsole(text, cls) {
  $('console').classList.remove('hidden');
  const out = $('console-out');
  out.className = cls || '';
  out.textContent = text;
}

// ---------------------------------------------------------------- submit

function confirmSubmit() {
  const left = Math.max(0, S.deadline - Date.now());
  const files = [];
  S.tasks.forEach((t) => { if (files.indexOf(t.file) < 0) files.push(t.file); });
  const unstarted = files.filter((f) => (S.files[f] || '') === (S.starters[f] || ''));
  dialog(`
    <h3>Submit ${esc(S.part.name)}?</h3>
    <p>This ends the part. You cannot reopen it, and you still have
       <strong>${mmss(left)}</strong> on the clock.</p>
    ${unstarted.length ? `<p><strong>${unstarted.length} of ${files.length} file${files.length === 1 ? '' : 's'}</strong>
       ${unstarted.length === 1 ? 'is' : 'are'} still untouched: <code>${unstarted.map(esc).join('</code> <code>')}</code></p>` : ''}
    <div class="row">
      <button class="btn" id="dlg-cancel">Keep working</button>
      <button class="btn btn-danger" id="dlg-go">Submit now</button>
    </div>`);
  $('dlg-cancel').addEventListener('click', closeDialog);
  $('dlg-go').addEventListener('click', () => { closeDialog(); submit(false); });
}

async function submit(auto) {
  if (S.submitting) return;
  S.submitting = true;
  stash();
  $('submit-btn').disabled = true;
  dialog(`<h3>${auto ? 'Time is up' : 'Submitting'}</h3>
          <p class="spin">Compiling your code and running the full grader. This takes a few seconds.</p>`);
  const report = await api('/api/submit', { files: S.files });
  S.phase = 'submitted';
  closeDialog();
  showResult(report);
}

// ---------------------------------------------------------------- result

function showResult(report) {
  document.body.dataset.screen = 'result';
  const host = $('result');
  if (!report || report.__status === 404 || report.error) {
    host.innerHTML = `<h1>No report</h1><p class="sub">${esc((report && report.error) || 'nothing to show')}</p>
      <div class="result-actions"><button class="btn btn-primary" id="again">Back to the start</button></div>`;
    $('again').addEventListener('click', reset);
    return;
  }

  const r = report.result || {};
  const sections = r.sections || [];
  const pct = r.available > 0 ? Math.round((r.earned / r.available) * 100) : 0;

  let html = `<h1>${esc(report.partName || 'Result')}</h1>
    <p class="sub">Attempt ${esc(report.attempt || '')}</p>
    <div class="score-card">
      <div class="score-big">${r.earned != null ? r.earned : 0}<small> / ${r.available != null ? r.available : 0} pts</small></div>
      <div class="score-meta">
        <span><b>${r.passed || 0}</b> of <b>${r.total || 0}</b> checks passed</span>
        <span><b>${pct}%</b> of this part</span>
        <span>Time used <b>${mmss(report.usedMs || 0)}</b> of ${mmss(report.durationMs || 0)}</span>
      </div>
      <div class="score-band">${esc(r.band || '')}</div>
    </div>`;

  if (report.autoSubmitted) {
    html += `<div class="notice notice-warn"><strong>Submitted automatically.</strong>
      The clock ran out, so the last autosave was graded.</div>`;
  }
  if (r.compiled === false) {
    html += `<div class="notice notice-bad"><strong>Your submission did not compile,</strong>
      so every check counts as failed. This is what a real assessment does too.
      <pre>${esc(r.compileOutput || '')}</pre></div>`;
  } else if (r.compileOutput) {
    html += `<div class="notice notice-warn"><strong>Compiler warnings.</strong><pre>${esc(r.compileOutput)}</pre></div>`;
  }

  html += '<div class="sections">';
  sections.forEach((s, idx) => {
    const ratio = s.total > 0 ? s.passed / s.total : 0;
    const cls = ratio === 1 ? '' : ratio === 0 ? 'none' : 'partial';
    html += `<div class="section-row" data-open="false" data-idx="${idx}">
      <div class="section-head">
        <span class="name">${esc(s.title)}</span>
        <span class="nums">${s.passed}/${s.total} checks &middot; ${s.points}/${s.weight} pts</span>
        <span class="caret">&#9662;</span>
      </div>
      <div class="meter"><span class="${cls}" style="width:${(ratio * 100).toFixed(1)}%"></span></div>
      <div class="checks">
        ${(s.checks || []).map((c) => `<div class="check ${c.pass ? 'pass' : 'fail'}">
            <span class="mark">${c.pass ? 'PASS' : 'FAIL'}</span>
            <span class="body">${esc(c.name)}${c.detail ? `<div class="detail">${esc(c.detail)}</div>` : ''}</span>
          </div>`).join('')}
      </div>
    </div>`;
  });
  html += '</div>';

  html += `<div class="result-actions">
      <button class="btn btn-primary" id="again">New attempt</button>
      <button class="btn" id="download">Download report (JSON)</button>
    </div>
    <p class="saved-at">Your submitted files, the report and a summary are in attempts/${esc(report.attempt || '')}/</p>`;

  host.innerHTML = html;
  host.querySelectorAll('.section-head').forEach((head) => {
    head.addEventListener('click', () => {
      const row = head.parentElement;
      row.dataset.open = row.dataset.open === 'true' ? 'false' : 'true';
    });
  });
  $('again').addEventListener('click', reset);
  $('download').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'report-' + (report.attempt || 'attempt') + '.json';
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

async function reset() {
  S.closing = true;
  await api('/api/abandon', {});
  location.reload();
}

// ---------------------------------------------------------------- dialog

function dialog(html) {
  $('dialog').innerHTML = html;
  $('overlay').classList.remove('hidden');
}
function closeDialog() { $('overlay').classList.add('hidden'); }

// ---------------------------------------------------------------- editor

const code = $('code');
const gutter = $('gutter');
let gutterLines = -1;

function syncGutter() {
  const n = code.value.split('\n').length;
  if (n !== gutterLines) {
    let s = '';
    for (let k = 1; k <= n; k++) s += k + '\n';
    gutter.textContent = s;
    gutterLines = n;
  }
  gutter.scrollTop = code.scrollTop;
}

function type(text, tail) {
  document.execCommand('insertText', false, text);
  if (tail) {
    const p = code.selectionStart - tail;
    code.setSelectionRange(p, p);
  }
}

code.addEventListener('input', () => { syncGutter(); markDirty(); });
code.addEventListener('scroll', () => { gutter.scrollTop = code.scrollTop; });

code.addEventListener('keydown', (e) => {
  const v = code.value;
  const a = code.selectionStart;
  const b = code.selectionEnd;

  if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); save(); return; }
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); compile(); return; }

  if (e.key === 'Tab') {
    e.preventDefault();
    const multi = a !== b && v.slice(a, b).indexOf('\n') >= 0;
    if (multi) {
      const start = v.lastIndexOf('\n', a - 1) + 1;
      const block = v.slice(start, b);
      const next = e.shiftKey ? block.replace(/^(\t| {1,4})/gm, '') : block.replace(/^(?!$)/gm, '    ');
      code.setSelectionRange(start, b);
      document.execCommand('insertText', false, next);
      code.setSelectionRange(start, start + next.length);
    } else if (e.shiftKey) {
      const start = v.lastIndexOf('\n', a - 1) + 1;
      const before = v.slice(start, a);
      const cut = before.match(/( {1,4}|\t)$/);
      if (cut) {
        code.setSelectionRange(a - cut[0].length, a);
        document.execCommand('insertText', false, '');
      }
    } else {
      type('    ');
    }
    return;
  }

  if (e.key === 'Enter' && !e.shiftKey && a === b) {
    const start = v.lastIndexOf('\n', a - 1) + 1;
    const line = v.slice(start, a);
    const indent = (line.match(/^[ \t]*/) || [''])[0];
    const opens = /[{(\[]\s*$/.test(line);
    const nextCh = v.charAt(a);
    let text = '\n' + indent + (opens ? '    ' : '');
    let tail = 0;
    if (opens && (nextCh === '}' || nextCh === ')' || nextCh === ']')) {
      const close = '\n' + indent;
      text += close;
      tail = close.length;
    }
    e.preventDefault();
    type(text, tail);
    return;
  }

  // Bracket pairing, with type-over for the closer.
  const pairs = { '(': ')', '[': ']', '{': '}' };
  if (pairs[e.key] && a === b) {
    e.preventDefault();
    type(e.key + pairs[e.key], 1);
    return;
  }
  if ((e.key === ')' || e.key === ']' || e.key === '}') && a === b && v.charAt(a) === e.key) {
    e.preventDefault();
    code.setSelectionRange(a + 1, a + 1);
  }
});

// ---------------------------------------------------------------- wiring

$('compile-btn').addEventListener('click', compile);
$('submit-btn').addEventListener('click', confirmSubmit);
$('console-close').addEventListener('click', () => $('console').classList.add('hidden'));
$('console').classList.add('hidden');

(function resizable() {
  const handle = $('handle');
  const pane = $('statement');
  let dragging = false;
  handle.addEventListener('mousedown', (e) => { dragging = true; e.preventDefault(); });
  window.addEventListener('mouseup', () => { dragging = false; });
  window.addEventListener('mousemove', (e) => {
    if (!dragging) return;
    const pct = Math.max(22, Math.min(70, (e.clientX / window.innerWidth) * 100));
    pane.style.flexBasis = pct + '%';
  });
})();

window.addEventListener('beforeunload', (e) => {
  if (S.phase === 'running' && !S.closing) {
    e.preventDefault();
    e.returnValue = 'The clock keeps running whether this tab is open or not.';
    return e.returnValue;
  }
});

boot().catch((err) => {
  document.body.dataset.screen = 'start';
  $('parts').innerHTML = '<div class="part-card"><h3>Could not load the exam</h3><p>'
    + esc(String(err)) + '</p></div>';
});
