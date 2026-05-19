'use strict';

/* ============== State ============== */
const state = {
  expression: '',
  radians: false,
  second: false,
  mode: 'SCIENTIFIC',   // 'SCIENTIFIC' | 'PROGRAMMER' | 'DATE' | 'GRAPHING'
  base: 10,             // programmer mode
  lastAnswer: '0',
  lastAnswerInt: 0,     // programmer mode
  justEvaluated: false,
  history: [],          // [{expr, result}]
  variables: {},        // name → value
  slots: [null, null, null, null, null], // null = empty
  pendingMemOp: null,   // 'MC' | 'MR' | 'ADD' | 'SUB' | null
  notation: 'AUTO',
  decimals: 6,
  theme: 'dark',        // 'dark' | 'light'
  autoTheme: false,
  accent: '#FF7043',
};

const PROG_LAYOUT = [
  ['DEC','HEX','BIN','OCT'],
  ['AND','OR','XOR','NOT'],
  ['<<','>>','(',')'],
  ['A','B','C','D'],
  ['E','F','Ans','÷'],
  ['7','8','9','×'],
  ['4','5','6','−'],
  ['1','2','3','+'],
  ['AC','⌫','0','='],
];

const SCI_LAYOUT = [
  ['sin','cos','tan','π'],
  ['ln','log','√','e'],
  ['(',')','^','!'],
  ['AC','⌫','Ans','÷'],
  ['7','8','9','×'],
  ['4','5','6','−'],
  ['1','2','3','+'],
  ['±','0','.','='],
];

const PROG_DIGITS = new Set(['0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F']);
const PROG_BASES = new Set(['DEC','HEX','BIN','OCT']);
const PROG_FNS = new Set(['AND','OR','XOR','NOT','<<','>>','(',')']);
const BASIC_OPS = new Set(['+','−','×','÷']);

const HISTORY_MAX = 50;

/* ============== DOM refs ============== */
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

const exprEl = $('#expr');
const resultEl = $('#result');
const indicatorsEl = $('#indicators');
const memRevealEl = $('#memReveal');
const memOpHintEl = $('#memOpHint');
const slotEls = $$('.slot-btn');
const degRadBtn = $('#degRadBtn');
const secondBtn = $('#secondBtn');
const memBtn = $('#memBtn');
const pinBtn = $('#pinBtn');
const menuBtn = $('#menuBtn');

/* ============== Persistence ============== */
function saveAll() {
  try {
    localStorage.setItem('calc-history', JSON.stringify(state.history));
    localStorage.setItem('calc-vars', JSON.stringify(state.variables));
    localStorage.setItem('calc-slots', JSON.stringify(state.slots));
    localStorage.setItem('calc-settings', JSON.stringify({
      notation: state.notation,
      decimals: state.decimals,
      theme: state.theme,
      autoTheme: state.autoTheme,
      accent: state.accent,
      radians: state.radians,
    }));
  } catch (e) {}
}

function loadAll() {
  try {
    const h = localStorage.getItem('calc-history');
    if (h) state.history = JSON.parse(h);
    const v = localStorage.getItem('calc-vars');
    if (v) state.variables = JSON.parse(v);
    const s = localStorage.getItem('calc-slots');
    if (s) state.slots = JSON.parse(s);
    const cfg = localStorage.getItem('calc-settings');
    if (cfg) {
      const o = JSON.parse(cfg);
      Object.assign(state, o);
    }
  } catch (e) {}
}

/* ============== Number formatting ============== */
function formatNumber(v) {
  if (Number.isNaN(v)) return 'NaN';
  if (!Number.isFinite(v)) return v > 0 ? '∞' : '−∞';
  if (v === 0) return '0';
  const d = state.decimals;
  if (state.notation === 'SCIENTIFIC') return v.toExponential(d).replace('-', '−');
  if (state.notation === 'ENGINEERING') return engFmt(v, d).replace('-', '−');
  return autoFmt(v, d).replace('-', '−');
}

function autoFmt(v, d) {
  if (v === Math.floor(v) && Math.abs(v) < 1e15) return String(v);
  let s = v.toPrecision(d);
  if (s.indexOf('.') >= 0 && s.toLowerCase().indexOf('e') < 0) {
    s = s.replace(/0+$/, '').replace(/\.$/, '');
  }
  return s;
}

function engFmt(v, d) {
  let exp = Math.floor(Math.log10(Math.abs(v)));
  exp = 3 * Math.floor(exp / 3);
  const mant = v / Math.pow(10, exp);
  let m = mant.toPrecision(Math.max(1, d - 1));
  if (m.indexOf('.') >= 0) m = m.replace(/0+$/, '').replace(/\.$/, '');
  return exp === 0 ? m : m + ' × 10' + supExp(exp);
}

function supExp(n) {
  const map = { '-': '⁻', '0':'⁰','1':'¹','2':'²','3':'³','4':'⁴','5':'⁵','6':'⁶','7':'⁷','8':'⁸','9':'⁹' };
  return String(n).split('').map(c => map[c] || c).join('');
}

function shortFmt(v) {
  if (v === null || v === undefined) return '—';
  if (Number.isNaN(v)) return 'NaN';
  if (!Number.isFinite(v)) return v > 0 ? '∞' : '−∞';
  if (v === 0) return '0';
  const av = Math.abs(v);
  if (av < 0.001) return v.toExponential(1).replace('-', '−');
  if (av < 1)    return v.toPrecision(3);
  if (av < 1e5) {
    if (v === Math.floor(v)) return String(v);
    return v.toPrecision(4);
  }
  if (av < 1e9) return (v / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
  return v.toExponential(1).replace('-', '−');
}

/* ============== Evaluation ============== */
function currentResultValue() {
  if (state.expression.length === 0) return 0;
  return new Parser(state.expression, state.radians, state.variables).parse();
}

function evaluate(commit) {
  if (state.expression.length === 0) {
    resultEl.textContent = '0';
    exprEl.textContent = '';
    return;
  }
  try {
    const v = new Parser(state.expression, state.radians, state.variables).parse();
    const s = formatNumber(v);
    resultEl.textContent = s;
    if (commit) {
      const exprStr = state.expression;
      exprEl.textContent = exprStr + ' =';
      addHistory(exprStr, s);
      state.lastAnswer = s;
      state.expression = s.replace('−', '-');
      state.justEvaluated = true;
    } else {
      exprEl.textContent = state.expression;
    }
  } catch (e) {
    if (commit) resultEl.textContent = 'Errore';
    exprEl.textContent = state.expression;
  }
}

function refreshDisplay() {
  refreshIndicators();
  if (state.justEvaluated) return;
  if (state.mode === 'PROGRAMMER') evaluateProgrammer(false);
  else if (state.mode === 'SCIENTIFIC') evaluate(false);
}

function evaluateProgrammer(commit) {
  if (state.expression.length === 0) {
    resultEl.textContent = '0';
    exprEl.textContent = '';
    return;
  }
  try {
    const v = new ProgParser(state.expression, state.base).parse();
    const s = v.toString(state.base).toUpperCase();
    resultEl.textContent = s;
    if (commit) {
      const exprStr = state.expression;
      exprEl.textContent = exprStr + ' =';
      addHistory(`[${baseName(state.base)}] ` + exprStr, s);
      state.lastAnswer = s;
      state.lastAnswerInt = v;
      state.expression = s;
      state.justEvaluated = true;
    } else {
      exprEl.textContent = state.expression;
    }
  } catch (e) {
    if (commit) resultEl.textContent = 'Errore';
    exprEl.textContent = state.expression;
  }
}

function convertBase(newBase) {
  try {
    const v = new ProgParser(state.expression, state.base).parse();
    state.base = newBase;
    state.expression = v.toString(newBase).toUpperCase();
  } catch (e) {
    state.base = newBase;
    state.expression = '';
  }
  updateBaseUI();
}

function refreshIndicators() {
  let s = '';
  if (state.slots.some(x => x !== null)) s += 'M  ';
  const labels = {
    SCIENTIFIC: 'SCIENTIFICA',
    PROGRAMMER: 'PROGRAMMATORE',
    DATE: 'CALCOLO DATE',
    GRAPHING: 'GRAFICI',
  };
  s += labels[state.mode] + '  ';
  if (state.mode === 'PROGRAMMER') {
    s += baseName(state.base);
  } else if (state.mode === 'SCIENTIFIC') {
    s += state.radians ? 'RAD' : 'DEG';
    if (state.second) s += '  2nd';
  }
  if (Object.keys(state.variables).length > 0) {
    s += '  ·  ' + Object.keys(state.variables).length + ' var';
  }
  indicatorsEl.textContent = s;
}

function baseName(b) {
  return b === 2 ? 'BIN' : b === 8 ? 'OCT' : b === 16 ? 'HEX' : 'DEC';
}

/* ============== Mode switching ============== */
function switchMode(m) {
  state.mode = m;
  state.expression = '';
  state.second = false;
  state.base = 10;
  state.justEvaluated = false;
  document.getElementById('secondBtn').classList.remove('selected');

  const isCalc = m === 'SCIENTIFIC' || m === 'PROGRAMMER';
  $('.display').hidden = !isCalc;
  $('.display').style.display = isCalc ? '' : 'none';
  $('.mem-cmds').style.display = isCalc ? 'grid' : 'none';
  $('.mem-reveal').style.display = isCalc ? '' : 'none';
  document.getElementById('grid').hidden = !isCalc;
  document.getElementById('grid').style.display = isCalc ? 'grid' : 'none';
  document.getElementById('dateView').hidden = m !== 'DATE';
  document.getElementById('dateView').style.display = m === 'DATE' ? 'flex' : 'none';
  document.getElementById('graphView').hidden = m !== 'GRAPHING';
  document.getElementById('graphView').style.display = m === 'GRAPHING' ? 'flex' : 'none';

  // Programmer-only: hide DEG/RAD and 2nd toggles (still callable from settings)
  $('#degRadBtn').style.display = (m === 'SCIENTIFIC') ? '' : 'none';
  $('#secondBtn').style.display = (m === 'SCIENTIFIC') ? '' : 'none';

  if (isCalc) {
    rebuildGrid();
  } else if (m === 'DATE') {
    initDateView();
  } else if (m === 'GRAPHING') {
    setTimeout(drawGraph, 50);
  }
  refreshDisplay();
}

function rebuildGrid() {
  const grid = document.getElementById('grid');
  grid.innerHTML = '';
  const layout = state.mode === 'PROGRAMMER' ? PROG_LAYOUT : SCI_LAYOUT;
  for (const row of layout) {
    for (const key of row) {
      const b = document.createElement('button');
      b.className = btnClass(key);
      b.dataset.key = key;
      b.textContent = applyLabel(key);
      b.addEventListener('click', () => press(key));
      grid.appendChild(b);
    }
  }
  if (state.mode === 'PROGRAMMER') updateBaseUI();
}

function btnClass(k) {
  if (k === '=') return 'btn eq';
  if (BASIC_OPS.has(k)) return 'btn op';
  if (k === 'AC' || k === '⌫') return 'btn clear';
  if (state.mode === 'PROGRAMMER') {
    if (PROG_BASES.has(k)) return 'btn base';
    if (PROG_FNS.has(k))   return 'btn fn';
    if (k === 'Ans')       return 'btn ans';
    if (/^[A-F]$/.test(k)) return 'btn hex';
    return 'btn digit';
  }
  if (k === 'Ans') return 'btn ans';
  if (/^[0-9]$/.test(k) || k === '.' || k === '±') return 'btn digit';
  return 'btn fn';
}

function applyLabel(k) {
  if (state.second && state.mode === 'SCIENTIFIC') {
    if (k === 'sin') return 'asin';
    if (k === 'cos') return 'acos';
    if (k === 'tan') return 'atan';
  }
  return k;
}

function updateBaseUI() {
  const grid = document.getElementById('grid');
  const allowed = state.base === 2 ? new Set(['0','1'])
    : state.base === 8 ? new Set(['0','1','2','3','4','5','6','7'])
    : state.base === 16 ? new Set(['0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'])
    : new Set(['0','1','2','3','4','5','6','7','8','9']);
  grid.querySelectorAll('button').forEach(b => {
    const k = b.dataset.key;
    if (PROG_DIGITS.has(k)) b.disabled = !allowed.has(k);
    if (PROG_BASES.has(k))  b.classList.toggle('selected', k === baseName(state.base));
  });
}

/* ============== History ============== */
function addHistory(expr, result) {
  state.history.unshift({ expr, result });
  if (state.history.length > HISTORY_MAX) state.history.length = HISTORY_MAX;
  refreshHistoryList();
  saveAll();
}

function refreshHistoryList() {
  const ul = $('#historyList');
  if (state.history.length === 0) {
    ul.innerHTML = '<li class="placeholder">— nessun calcolo ancora —</li>';
    return;
  }
  ul.innerHTML = '';
  state.history.forEach((h, i) => {
    const li = document.createElement('li');
    li.textContent = h.expr + ' = ' + h.result;
    li.onclick = () => {
      state.expression = h.expr;
      state.justEvaluated = false;
      refreshDisplay();
      closeAllOverlays();
    };
    ul.appendChild(li);
  });
}

/* ============== Variables ============== */
function refreshVarTable() {
  const tb = $('#varTbody');
  tb.innerHTML = '';
  Object.entries(state.variables).forEach(([name, value]) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${name}</td><td>${formatNumber(value)}</td>`;
    const td = document.createElement('td');
    const del = document.createElement('button');
    del.className = 'del-btn';
    del.textContent = '✕';
    del.onclick = () => {
      delete state.variables[name];
      refreshVarTable();
      refreshIndicators();
      saveAll();
    };
    td.appendChild(del);
    tr.appendChild(td);
    tb.appendChild(tr);
  });
}

function addVariable() {
  const name = $('#varNameInput').value.trim();
  const val = parseFloat($('#varValueInput').value.replace(',', '.'));
  if (!name.match(/^[A-Za-z][A-Za-z0-9_]*$/)) return;
  if (!Number.isFinite(val)) return;
  state.variables[name] = val;
  $('#varNameInput').value = '';
  $('#varValueInput').value = '';
  refreshVarTable();
  refreshIndicators();
  saveAll();
}

/* ============== Slots & memory ============== */
function refreshSlots() {
  slotEls.forEach((el, i) => {
    const v = state.slots[i];
    const valEl = el.querySelector('.slot-val');
    valEl.textContent = v === null ? '—' : shortFmt(v);
    el.classList.toggle('filled', v !== null);
  });
}

function clearSlot(i) {
  state.slots[i] = null;
  refreshSlots();
  refreshIndicators();
  saveAll();
}

function saveCurrentToSlot(i) {
  try {
    state.slots[i] = currentResultValue();
    refreshSlots();
    refreshIndicators();
    saveAll();
  } catch (e) {}
}

function addCurrentToSlot(i, sign) {
  try {
    const v = currentResultValue();
    const existing = state.slots[i] !== null ? state.slots[i] : 0;
    state.slots[i] = existing + sign * v;
    refreshSlots();
    refreshIndicators();
    saveAll();
  } catch (e) {}
}

function insertNumber(v) {
  if (state.justEvaluated) {
    state.expression = '';
    state.justEvaluated = false;
  }
  state.expression += formatNumber(v).replace('−', '-');
  refreshDisplay();
}

function updateMemHint() {
  if (!state.pendingMemOp) {
    memOpHintEl.hidden = true;
    return;
  }
  memOpHintEl.hidden = false;
  switch (state.pendingMemOp) {
    case 'MC':  memOpHintEl.textContent = '▼ Tocca uno slot da cancellare'; break;
    case 'MR':  memOpHintEl.textContent = '▼ Tocca uno slot per inserirne il valore'; break;
    case 'ADD': memOpHintEl.textContent = '▼ Tocca uno slot a cui sommare il risultato'; break;
    case 'SUB': memOpHintEl.textContent = '▼ Tocca uno slot da cui sottrarre il risultato'; break;
  }
}

function revealMemoryPanel(show) {
  if (show) {
    memRevealEl.hidden = false;
  } else {
    memRevealEl.hidden = true;
    state.pendingMemOp = null;
    updateMemHint();
  }
}

function onSlotClick(i) {
  if (state.pendingMemOp) {
    switch (state.pendingMemOp) {
      case 'MC':  clearSlot(i); break;
      case 'MR':  if (state.slots[i] !== null) insertNumber(state.slots[i]); break;
      case 'ADD': addCurrentToSlot(i, +1); break;
      case 'SUB': addCurrentToSlot(i, -1); break;
    }
    state.pendingMemOp = null;
    updateMemHint();
    revealMemoryPanel(false);
    return;
  }
  if (state.slots[i] !== null) {
    insertNumber(state.slots[i]);
    revealMemoryPanel(false);
  } else {
    saveCurrentToSlot(i);
  }
}

/* ============== Input handling ============== */
function isInputStart(t) {
  if (['AC', '=', '⌫', 'M+', 'M−', 'MR', 'MC'].includes(t)) return false;
  if (['+', '−', '×', '÷', '^', '!', '%', ')', '±', '2nd'].includes(t)) return false;
  return true;
}

function press(t) {
  if (state.justEvaluated) {
    if (isInputStart(t)) state.expression = '';
    state.justEvaluated = false;
  }
  if (state.mode === 'PROGRAMMER') { pressProgrammer(t); refreshDisplay(); return; }
  switch (t) {
    case 'AC': state.expression = ''; break;
    case '⌫': backspace(); break;
    case '=': evaluate(true); refreshDisplay(); return;
    case 'sin': state.expression += state.second ? 'asin(' : 'sin('; break;
    case 'cos': state.expression += state.second ? 'acos(' : 'cos('; break;
    case 'tan': state.expression += state.second ? 'atan(' : 'tan('; break;
    case 'ln':  state.expression += 'ln('; break;
    case 'log': state.expression += 'log('; break;
    case '√':   state.expression += '√('; break;
    case '!':   state.expression += '!'; break;
    case 'π':   state.expression += 'π'; break;
    case 'e':   state.expression += 'e'; break;
    case 'Ans': state.expression += String(state.lastAnswer).replace('−', '-'); break;
    case '±':   toggleSign(); break;
    case 'MC':  state.pendingMemOp = 'MC';  updateMemHint(); revealMemoryPanel(true); refreshDisplay(); return;
    case 'MR':  state.pendingMemOp = 'MR';  updateMemHint(); revealMemoryPanel(true); refreshDisplay(); return;
    case 'M+':  state.pendingMemOp = 'ADD'; updateMemHint(); revealMemoryPanel(true); refreshDisplay(); return;
    case 'M−':  state.pendingMemOp = 'SUB'; updateMemHint(); revealMemoryPanel(true); refreshDisplay(); return;
    default:    state.expression += t;
  }
  refreshDisplay();
}

function pressProgrammer(t) {
  switch (t) {
    case 'AC': state.expression = ''; break;
    case '⌫': backspaceProg(); break;
    case '=': evaluateProgrammer(true); return;
    case 'DEC': convertBase(10); break;
    case 'HEX': convertBase(16); break;
    case 'BIN': convertBase(2); break;
    case 'OCT': convertBase(8); break;
    case 'AND': state.expression += ' AND '; break;
    case 'OR':  state.expression += ' OR ';  break;
    case 'XOR': state.expression += ' XOR '; break;
    case 'NOT': state.expression += 'NOT ';  break;
    case '<<':  state.expression += ' << ';  break;
    case '>>':  state.expression += ' >> ';  break;
    case 'Ans': state.expression += (state.lastAnswerInt || 0).toString(state.base).toUpperCase(); break;
    default: state.expression += t;
  }
}

function backspaceProg() {
  if (!state.expression.length) return;
  const ops = [' AND ', ' OR ', ' XOR ', 'NOT ', ' << ', ' >> '];
  for (const op of ops) {
    if (state.expression.endsWith(op)) {
      state.expression = state.expression.slice(0, -op.length);
      return;
    }
  }
  state.expression = state.expression.slice(0, -1);
}

/* ============== Date view ============== */
function initDateView() {
  const today = new Date().toISOString().slice(0, 10);
  const plus30 = new Date(Date.now() + 30*86400000).toISOString().slice(0, 10);
  const from = $('#dateFrom'); const to = $('#dateTo'); const base = $('#dateBase');
  if (!from.value) from.value = today;
  if (!to.value)   to.value = plus30;
  if (!base.value) base.value = today;
  updateDateDiff();
  updateDateAdd();
}

function updateDateDiff() {
  const from = new Date($('#dateFrom').value);
  const to = new Date($('#dateTo').value);
  if (isNaN(from) || isNaN(to)) { $('#dateDiff').textContent = '—'; return; }
  const days = Math.round((to - from) / 86400000);
  const absDays = Math.abs(days);
  const years = Math.floor(absDays / 365);
  const remDays = absDays % 365;
  const months = Math.floor(remDays / 30);
  const restDays = remDays % 30;
  const sign = days < 0 ? '−' : '';
  $('#dateDiff').textContent =
    `${sign}${absDays} giorni · ${Math.trunc(days/7)} sett. · ≈ ${years} anni, ${months} mesi, ${restDays} gg`;
}

function updateDateAdd() {
  const base = new Date($('#dateBase').value);
  const delta = parseInt($('#dateDelta').value, 10);
  if (isNaN(base) || isNaN(delta)) { $('#dateResult').textContent = '—'; return; }
  const r = new Date(base.getTime() + delta * 86400000);
  const days = ['domenica','lunedì','martedì','mercoledì','giovedì','venerdì','sabato'];
  const dd = String(r.getDate()).padStart(2, '0');
  const mm = String(r.getMonth() + 1).padStart(2, '0');
  const yy = r.getFullYear();
  $('#dateResult').textContent = `${dd}/${mm}/${yy} (${days[r.getDay()]})`;
}

/* ============== Graphing ============== */
function drawGraph() {
  const canvas = $('#graphCanvas');
  const fnExpr = $('#graphFn').value;
  const xMin = parseFloat($('#graphMin').value.replace(',', '.'));
  const xMax = parseFloat($('#graphMax').value.replace(',', '.'));
  if (!Number.isFinite(xMin) || !Number.isFinite(xMax) || xMax <= xMin) return;

  const rect = canvas.parentElement.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const cssW = rect.width;
  const cssH = Math.max(180, rect.height - 100);
  canvas.style.width = cssW + 'px';
  canvas.style.height = cssH + 'px';
  canvas.width  = Math.round(cssW * dpr);
  canvas.height = Math.round(cssH * dpr);
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);

  const bg = state.theme === 'light' ? '#FFFFFF' : '#1E1F25';
  const grid = state.theme === 'light' ? '#C2C2C6' : '#3A3B43';
  const axis = state.theme === 'light' ? '#5A5A5E' : '#9498A3';
  const text = state.theme === 'light' ? '#5A5A5E' : '#6E707A';
  const curve = state.accent;

  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, cssW, cssH);

  const N = Math.max(50, Math.min(2000, Math.floor(cssW * 1.5)));
  const xs = new Float64Array(N);
  const ys = new Float64Array(N);
  let yMin = Infinity, yMax = -Infinity;
  const vars = Object.assign({}, state.variables);
  let parseOk = true;
  for (let i = 0; i < N; i++) {
    const x = xMin + (xMax - xMin) * i / (N - 1);
    xs[i] = x;
    vars.x = x;
    let y;
    try {
      y = new Parser(fnExpr, state.radians, vars).parse();
      if (!Number.isFinite(y)) y = NaN;
    } catch (e) {
      y = NaN;
      if (i === 0) parseOk = false;
    }
    ys[i] = y;
    if (Number.isFinite(y)) {
      if (y < yMin) yMin = y;
      if (y > yMax) yMax = y;
    }
  }
  if (!parseOk) {
    ctx.fillStyle = '#E57373';
    ctx.font = '12px sans-serif';
    ctx.fillText('Espressione non valida', 10, 20);
    return;
  }
  if (!Number.isFinite(yMin) || !Number.isFinite(yMax)) { yMin = -1; yMax = 1; }
  if (yMin === yMax) { yMin -= 1; yMax += 1; }
  const yMargin = (yMax - yMin) * 0.08;
  yMin -= yMargin; yMax += yMargin;

  const mapX = (x) => (x - xMin) / (xMax - xMin) * cssW;
  const mapY = (y) => cssH - (y - yMin) / (yMax - yMin) * cssH;
  const niceStep = (r) => {
    if (r <= 0) return 1;
    const pow = Math.pow(10, Math.floor(Math.log10(r)));
    const f = r / pow;
    return (f < 1.5 ? pow : f < 3 ? 2*pow : f < 7 ? 5*pow : 10*pow);
  };

  ctx.strokeStyle = grid;
  ctx.lineWidth = 0.5;
  const xStep = niceStep((xMax - xMin) / 8);
  const yStep = niceStep((yMax - yMin) / 6);
  for (let x = Math.ceil(xMin / xStep) * xStep; x <= xMax; x += xStep) {
    const sx = mapX(x);
    ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, cssH); ctx.stroke();
  }
  for (let y = Math.ceil(yMin / yStep) * yStep; y <= yMax; y += yStep) {
    const sy = mapY(y);
    ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(cssW, sy); ctx.stroke();
  }

  ctx.strokeStyle = axis;
  ctx.lineWidth = 1;
  if (yMin <= 0 && yMax >= 0) {
    const sy = mapY(0);
    ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(cssW, sy); ctx.stroke();
  }
  if (xMin <= 0 && xMax >= 0) {
    const sx = mapX(0);
    ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, cssH); ctx.stroke();
  }

  ctx.strokeStyle = curve;
  ctx.lineWidth = 1.6;
  ctx.beginPath();
  let down = false;
  for (let i = 0; i < N; i++) {
    if (!Number.isFinite(ys[i])) { down = false; continue; }
    const sx = mapX(xs[i]);
    const sy = mapY(ys[i]);
    if (sy < -10000 || sy > cssH + 10000) { down = false; continue; }
    if (!down) { ctx.moveTo(sx, sy); down = true; }
    else { ctx.lineTo(sx, sy); }
  }
  ctx.stroke();

  ctx.fillStyle = text;
  ctx.font = '10px sans-serif';
  ctx.fillText(`x ∈ [${xMin.toPrecision(3)}, ${xMax.toPrecision(3)}]`, 6, 14);
  ctx.fillText(`y ∈ [${yMin.toPrecision(3)}, ${yMax.toPrecision(3)}]`, 6, 28);
}

function backspace() {
  if (state.expression.length === 0) return;
  const funcs = ['asin(', 'acos(', 'atan(', 'sin(', 'cos(', 'tan(', 'log(', 'ln(', 'exp(', '10^(', '√('];
  for (const f of funcs) {
    if (state.expression.endsWith(f)) {
      state.expression = state.expression.slice(0, -f.length);
      return;
    }
  }
  state.expression = state.expression.slice(0, -1);
}

function toggleSign() {
  if (state.expression.length === 0) { state.expression = '−'; return; }
  let i = state.expression.length;
  while (i > 0) {
    const c = state.expression[i - 1];
    if ((c >= '0' && c <= '9') || c === '.') i--;
    else break;
  }
  if (i === state.expression.length) state.expression += '−';
  else if (i > 0 && (state.expression[i - 1] === '−' || state.expression[i - 1] === '-')) {
    state.expression = state.expression.slice(0, i - 1) + state.expression.slice(i);
  } else {
    state.expression = state.expression.slice(0, i) + '−' + state.expression.slice(i);
  }
}

/* ============== 2nd label flip ============== */
function applySecondLabels() {
  $$('.btn.fn[data-flip]').forEach(b => {
    const def = b.dataset.key;
    const flip = b.dataset.flip;
    b.textContent = state.second ? flip : def;
  });
}

/* ============== Theme & accent ============== */
function applyTheme(theme) {
  state.theme = theme;
  document.documentElement.setAttribute('data-theme', theme);
  $('#metaThemeColor')?.setAttribute('content', theme === 'light' ? '#E5E5E9' : '#15161A');
  saveAll();
}

function applyAccent(hex) {
  state.accent = hex;
  document.documentElement.style.setProperty('--accent', hex);
  // derive soft & deep
  const soft = shadeHex(hex, 0.18);
  const deep = shadeHex(hex, -0.15);
  document.documentElement.style.setProperty('--accent-soft', soft);
  document.documentElement.style.setProperty('--accent-deep', deep);
  saveAll();
}

function shadeHex(hex, amount) {
  // amount: -1..1 (positive lighter, negative darker)
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  const adj = amount >= 0
    ? (c) => Math.round(c + (255 - c) * amount)
    : (c) => Math.round(c * (1 + amount));
  return '#' + [adj(r), adj(g), adj(b)].map(x => x.toString(16).padStart(2, '0')).join('');
}

function detectSystemDark() {
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function maybeFollowSystem() {
  if (state.autoTheme) {
    applyTheme(detectSystemDark() ? 'dark' : 'light');
  }
}

/* ============== Wire up UI ============== */
function bindUI() {
  // Memory command buttons (grid buttons are wired in rebuildGrid)
  $$('.mem-cmds .btn').forEach(b => {
    const k = b.dataset.key;
    if (!k) return;
    b.addEventListener('click', () => press(k));
  });

  // slots
  slotEls.forEach((el, i) => {
    let pressTimer;
    el.addEventListener('click', () => onSlotClick(i));
    // long-press to clear
    el.addEventListener('touchstart', () => {
      pressTimer = setTimeout(() => clearSlot(i), 600);
    }, { passive: true });
    el.addEventListener('touchend', () => clearTimeout(pressTimer));
    el.addEventListener('touchmove', () => clearTimeout(pressTimer));
    el.addEventListener('contextmenu', (e) => { e.preventDefault(); clearSlot(i); });
  });

  // Toolbar chips
  degRadBtn.addEventListener('click', () => {
    state.radians = !state.radians;
    degRadBtn.textContent = state.radians ? 'RAD' : 'DEG';
    degRadBtn.classList.toggle('selected', state.radians);
    refreshDisplay();
    saveAll();
  });
  secondBtn.addEventListener('click', () => {
    state.second = !state.second;
    secondBtn.classList.toggle('selected', state.second);
    if (state.mode === 'SCIENTIFIC') rebuildGrid();
    refreshIndicators();
  });
  memBtn.addEventListener('click', () => revealMemoryPanel(memRevealEl.hidden));
  pinBtn.addEventListener('click', () => {
    pinBtn.classList.toggle('selected');
    // PWA standalone has no always-on-top, but we toggle visual state for parity
  });
  menuBtn.addEventListener('click', () => openOverlay('menuOverlay'));

  // Overlay close buttons
  $$('.close-btn').forEach(b => {
    b.addEventListener('click', () => closeOverlay(b.dataset.close));
  });
  // backdrop click
  $$('.overlay').forEach(o => {
    o.addEventListener('click', (e) => { if (e.target === o) closeOverlay(o.id); });
  });

  // Menu actions
  $$('.menu-row[data-action]').forEach(b => {
    b.addEventListener('click', () => {
      const action = b.dataset.action;
      closeOverlay('menuOverlay');
      if (action === 'open-history')   openOverlay('historyOverlay');
      if (action === 'open-variables') openOverlay('variablesOverlay');
      if (action === 'open-settings')  openOverlay('settingsOverlay');
      if (action === 'open-about')     openOverlay('aboutOverlay');
      if (action && action.startsWith('open-toolkit-')) {
        const tab = action.substring('open-toolkit-'.length);
        Toolkit.open(tab);
      }
    });
  });

  // Mode rows
  $$('.menu-row[data-mode]').forEach(b => {
    b.addEventListener('click', () => {
      const m = b.dataset.mode;
      closeOverlay('menuOverlay');
      switchMode(m);
    });
  });

  // Date view
  ['dateFrom','dateTo'].forEach(id => $('#'+id)?.addEventListener('input', updateDateDiff));
  ['dateBase','dateDelta'].forEach(id => $('#'+id)?.addEventListener('input', updateDateAdd));

  // Graph
  $('#graphPlot')?.addEventListener('click', drawGraph);
  $('#graphFn')?.addEventListener('input', drawGraph);
  $('#graphMin')?.addEventListener('input', drawGraph);
  $('#graphMax')?.addEventListener('input', drawGraph);
  window.addEventListener('resize', () => { if (state.mode === 'GRAPHING') drawGraph(); });

  // History clear
  $('#historyClear').addEventListener('click', () => {
    state.history = [];
    refreshHistoryList();
    saveAll();
  });

  // Variables
  $('#varAddBtn').addEventListener('click', addVariable);

  // Settings
  $('#accentColor').addEventListener('input', (e) => applyAccent(e.target.value));
  $('#accentReset').addEventListener('click', () => {
    $('#accentColor').value = '#FF7043';
    applyAccent('#FF7043');
  });
  $$('input[name="notation"]').forEach(r => {
    r.addEventListener('change', (e) => {
      state.notation = e.target.value;
      refreshDisplay();
      saveAll();
    });
  });
  $('#decimalsRange').addEventListener('input', (e) => {
    state.decimals = parseInt(e.target.value, 10);
    $('#decimalsLabel').textContent = 'Cifre: ' + state.decimals;
    refreshDisplay();
    saveAll();
  });
  $('#autoTheme').addEventListener('change', (e) => {
    state.autoTheme = e.target.checked;
    if (state.autoTheme) maybeFollowSystem();
    saveAll();
  });
  $$('input[name="theme"]').forEach(r => {
    r.addEventListener('change', (e) => {
      if (state.autoTheme) return; // ignored when auto
      applyTheme(e.target.value);
    });
  });

  // System theme listener
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', maybeFollowSystem);
  }
}

function openOverlay(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.hidden = false;
  if (id === 'historyOverlay') refreshHistoryList();
  if (id === 'variablesOverlay') refreshVarTable();
  if (id === 'settingsOverlay') refreshSettingsUI();
}

function closeOverlay(id) {
  const el = document.getElementById(id);
  if (el) el.hidden = true;
}

function closeAllOverlays() {
  $$('.overlay').forEach(o => o.hidden = true);
}

function refreshSettingsUI() {
  $('#accentColor').value = state.accent;
  $$('input[name="notation"]').forEach(r => r.checked = r.value === state.notation);
  $$('input[name="theme"]').forEach(r => r.checked = r.value === state.theme);
  $('#autoTheme').checked = state.autoTheme;
  $('#decimalsRange').value = state.decimals;
  $('#decimalsLabel').textContent = 'Cifre: ' + state.decimals;
}

/* ============== Init ============== */
function init() {
  loadAll();
  applyTheme(state.theme);
  applyAccent(state.accent);
  if (state.autoTheme) maybeFollowSystem();
  if (state.radians) {
    degRadBtn.textContent = 'RAD';
    degRadBtn.classList.add('selected');
  }
  refreshSlots();
  refreshHistoryList();

  // Toolkit bridge
  Toolkit.init({
    insert: (s) => {
      if (state.mode === 'DATE' || state.mode === 'GRAPHING') return;
      if (state.justEvaluated) { state.expression = ''; state.justEvaluated = false; }
      state.expression += String(s).replace('−', '-');
      refreshDisplay();
    },
    setVariable: (name, value) => {
      state.variables[name] = value;
      refreshIndicators();
      saveAll();
    },
    variables: () => state.variables,
    radians: () => state.radians,
  });

  // Start in Scientific mode (builds the grid)
  switchMode(state.mode || 'SCIENTIFIC');

  bindUI();
}

document.addEventListener('DOMContentLoaded', init);
