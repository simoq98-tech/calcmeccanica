'use strict';

const Toolkit = (() => {
  let bridge = null;
  let panel = null;
  let currentTab = 'units';

  function init(b) { bridge = b; }

  function build() {
    panel = document.createElement('div');
    panel.className = 'overlay';
    panel.id = 'toolkitOverlay';
    panel.hidden = true;
    panel.innerHTML = `
      <div class="overlay-panel">
        <div class="overlay-header">
          <h2>Strumenti</h2>
          <button class="chip icon close-btn" data-close="toolkitOverlay">✕</button>
        </div>
        <div class="tk-tabs">
          <button class="tk-tab selected" data-tab="units">Unità</button>
          <button class="tk-tab" data-tab="materials">Materiali</button>
          <button class="tk-tab" data-tab="sections">Sezioni</button>
          <button class="tk-tab" data-tab="formulas">Formule</button>
          <button class="tk-tab" data-tab="wizards">Procedure</button>
          <button class="tk-tab" data-tab="more">Strum.</button>
        </div>
        <div class="overlay-body">
          <div class="tk-pane" data-pane="units"></div>
          <div class="tk-pane" data-pane="materials" hidden></div>
          <div class="tk-pane" data-pane="sections" hidden></div>
          <div class="tk-pane" data-pane="formulas" hidden></div>
          <div class="tk-pane" data-pane="wizards" hidden></div>
          <div class="tk-pane" data-pane="more" hidden></div>
        </div>
      </div>
    `;
    document.body.appendChild(panel);
    panel.querySelectorAll('.tk-tab').forEach(t => {
      t.addEventListener('click', () => selectTab(t.dataset.tab));
    });
    panel.querySelector('.close-btn').addEventListener('click', () => panel.hidden = true);
    panel.addEventListener('click', (e) => { if (e.target === panel) panel.hidden = true; });

    buildUnitsPane();
    buildMaterialsPane();
    buildSectionsPane();
    buildFormulasPane();
    buildWizardsPane();
    buildMorePane();
    return panel;
  }

  function open(tab) {
    if (!panel) build();
    if (tab) selectTab(tab);
    panel.hidden = false;
  }

  function selectTab(name) {
    currentTab = name;
    panel.querySelectorAll('.tk-tab').forEach(t => t.classList.toggle('selected', t.dataset.tab === name));
    panel.querySelectorAll('.tk-pane').forEach(p => p.hidden = p.dataset.pane !== name);
  }

  // ====================== Helpers ======================
  function fmt(v) {
    if (v === null || v === undefined || isNaN(v)) return '—';
    if (!Number.isFinite(v)) return v > 0 ? '∞' : '−∞';
    if (v === 0) return '0';
    const av = Math.abs(v);
    if (av >= 1e-4 && av < 1e7) {
      if (v === Math.floor(v)) return String(v);
      let s = v.toPrecision(6);
      if (s.indexOf('.') >= 0 && s.toLowerCase().indexOf('e') < 0) s = s.replace(/0+$/, '').replace(/\.$/, '');
      return s;
    }
    return v.toExponential(3);
  }

  function ins(v) { bridge.insert(fmt(v)); panel.hidden = true; }
  function variable(name, v) { bridge.setVariable(name, v); }

  function row(key, val, vName, raw) {
    const div = document.createElement('div');
    div.className = 'tk-row';
    div.innerHTML = `<span class="tk-key">${key}</span><span class="tk-val">${val}</span>`;
    const btns = document.createElement('span');
    btns.className = 'tk-row-btns';
    if (vName !== undefined) {
      const insBtn = document.createElement('button');
      insBtn.className = 'chip';
      insBtn.textContent = 'Ins';
      insBtn.onclick = () => ins(raw);
      const varBtn = document.createElement('button');
      varBtn.className = 'chip';
      varBtn.textContent = '→ ' + vName;
      varBtn.onclick = () => variable(vName, raw);
      btns.appendChild(insBtn);
      btns.appendChild(varBtn);
    }
    div.appendChild(btns);
    return div;
  }

  function parseNum(s) {
    if (!s) throw new Error('vuoto');
    const v = parseFloat(String(s).replace(',', '.').replace('−', '-'));
    if (!Number.isFinite(v)) throw new Error('non valido');
    return v;
  }

  // ====================== Units ======================
  function buildUnitsPane() {
    const p = panel.querySelector('[data-pane="units"]');
    const catSel = mkSel(Catalog.CATEGORIES.map(c => c.name));
    const fromSel = mkSel([]);
    const toSel = mkSel([]);
    const valIn = mkInput('1');
    const out = document.createElement('div');
    out.className = 'tk-result';

    const update = () => {
      try {
        const cat = Catalog.CATEGORIES[catSel.selectedIndex];
        const from = cat.units[fromSel.selectedIndex];
        const to = cat.units[toSel.selectedIndex];
        const v = parseNum(valIn.value);
        const r = Catalog.convert(cat, from, to, v);
        out.textContent = fmt(r) + ' ' + to.symbol;
      } catch (e) {
        out.textContent = '—';
      }
    };

    catSel.addEventListener('change', () => {
      const cat = Catalog.CATEGORIES[catSel.selectedIndex];
      fromSel.innerHTML = '';
      toSel.innerHTML = '';
      cat.units.forEach(u => {
        const o1 = document.createElement('option'); o1.textContent = u.symbol + ' — ' + u.name;
        const o2 = o1.cloneNode(true);
        fromSel.appendChild(o1); toSel.appendChild(o2);
      });
      toSel.selectedIndex = Math.min(1, cat.units.length - 1);
      update();
    });
    fromSel.addEventListener('change', update);
    toSel.addEventListener('change', update);
    valIn.addEventListener('input', update);

    catSel.dispatchEvent(new Event('change'));

    const insBtn = document.createElement('button');
    insBtn.className = 'chip menu-row';
    insBtn.textContent = 'Inserisci valore convertito';
    insBtn.onclick = () => {
      try {
        const cat = Catalog.CATEGORIES[catSel.selectedIndex];
        const from = cat.units[fromSel.selectedIndex];
        const to = cat.units[toSel.selectedIndex];
        const v = parseNum(valIn.value);
        ins(Catalog.convert(cat, from, to, v));
      } catch (e) {}
    };

    p.appendChild(section('Categoria'));
    p.appendChild(catSel);
    p.appendChild(section('Da'));
    p.appendChild(fromSel);
    p.appendChild(section('Valore'));
    p.appendChild(valIn);
    p.appendChild(section('A'));
    p.appendChild(toSel);
    p.appendChild(section('Risultato'));
    p.appendChild(out);
    p.appendChild(insBtn);
  }

  // ====================== Materials ======================
  function buildMaterialsPane() {
    const p = panel.querySelector('[data-pane="materials"]');
    const search = mkInput('');
    search.placeholder = 'Cerca materiale… (es. 316, acciaio, Al)';
    const list = document.createElement('select');
    list.className = 'toolkit-input tk-listsel';
    list.size = 6;
    const props = document.createElement('div'); props.className = 'tk-rows';
    const loadAllBtn = document.createElement('button');
    loadAllBtn.className = 'chip menu-row';
    loadAllBtn.textContent = 'Carica tutte come variabili (ro, E, G, nu, sy, su, alpha)';

    const populate = (q) => {
      list.innerHTML = '';
      const filtered = Catalog.MATERIALS.filter(m =>
        !q || m.name.toLowerCase().includes(q.toLowerCase()));
      filtered.forEach((m, i) => {
        const o = document.createElement('option');
        o.value = String(Catalog.MATERIALS.indexOf(m));
        o.textContent = m.name;
        list.appendChild(o);
      });
      if (list.options.length > 0) {
        list.selectedIndex = 0;
        showProps(Catalog.MATERIALS[parseInt(list.options[0].value)]);
      } else {
        props.innerHTML = '';
      }
    };

    const showProps = (m) => {
      props.innerHTML = '';
      props.appendChild(row('ρ',  fmt(m.rho)   + ' kg/m³', 'ro',    m.rho));
      props.appendChild(row('E',  fmt(m.E)     + ' MPa',   'E',     m.E));
      props.appendChild(row('G',  fmt(m.G)     + ' MPa',   'G',     m.G));
      props.appendChild(row('ν',  fmt(m.nu),                'nu',    m.nu));
      props.appendChild(row('σy', fmt(m.sy)    + ' MPa',   'sy',    m.sy));
      props.appendChild(row('σt', fmt(m.su)    + ' MPa',   'su',    m.su));
      props.appendChild(row('α',  fmt(m.alpha) + ' ×10⁻⁶/K','alpha', m.alpha));
    };

    list.addEventListener('change', () => {
      const idx = parseInt(list.value);
      showProps(Catalog.MATERIALS[idx]);
    });
    search.addEventListener('input', () => populate(search.value));

    loadAllBtn.onclick = () => {
      const idx = parseInt(list.value);
      if (isNaN(idx)) return;
      const m = Catalog.MATERIALS[idx];
      variable('ro', m.rho);
      variable('E', m.E);
      variable('G', m.G);
      variable('nu', m.nu);
      variable('sy', m.sy);
      variable('su', m.su);
      variable('alpha', m.alpha);
    };

    populate('');

    p.appendChild(section('Cerca'));
    p.appendChild(search);
    p.appendChild(section('Materiali'));
    p.appendChild(list);
    p.appendChild(section('Proprietà'));
    p.appendChild(props);
    p.appendChild(loadAllBtn);
  }

  // ====================== Sections ======================
  function buildSectionsPane() {
    const p = panel.querySelector('[data-pane="sections"]');
    const shapes = [
      { key: 'rect',   label: 'Rettangolo (b × h)', inputs: ['b [mm]', 'h [mm]'] },
      { key: 'hollow', label: 'Rettangolo cavo (b × h × t)', inputs: ['b [mm]', 'h [mm]', 't [mm]'] },
      { key: 'circle', label: 'Cerchio pieno (D)', inputs: ['D [mm]'] },
      { key: 'tube',   label: 'Tubo (De × t)', inputs: ['De [mm]', 't [mm]'] },
    ];
    const shapeSel = mkSel(shapes.map(s => s.label));
    const inputsDiv = document.createElement('div'); inputsDiv.className = 'tk-inputs';
    const calcBtn = document.createElement('button');
    calcBtn.className = 'chip menu-row eq-chip';
    calcBtn.textContent = 'Calcola proprietà';
    const out = document.createElement('div'); out.className = 'tk-rows';

    const refresh = () => {
      inputsDiv.innerHTML = '';
      const s = shapes[shapeSel.selectedIndex];
      s.inputs.forEach((lbl, i) => {
        const w = document.createElement('div');
        w.className = 'tk-input-wrap';
        w.innerHTML = `<label>${lbl}</label>`;
        const inp = mkInput('');
        inp.dataset.idx = i;
        w.appendChild(inp);
        inputsDiv.appendChild(w);
      });
    };
    shapeSel.addEventListener('change', refresh);

    calcBtn.onclick = () => {
      out.innerHTML = '';
      try {
        const s = shapes[shapeSel.selectedIndex];
        const vals = Array.from(inputsDiv.querySelectorAll('input')).map(i => parseNum(i.value));
        let r;
        if (s.key === 'rect')   r = Catalog.sections.rectangle(vals[0], vals[1]);
        else if (s.key === 'hollow') r = Catalog.sections.hollowRect(vals[0], vals[1], vals[2]);
        else if (s.key === 'circle') r = Catalog.sections.solidCircle(vals[0]);
        else if (s.key === 'tube')   r = Catalog.sections.tube(vals[0], vals[1]);
        out.appendChild(row('A',  fmt(r.A)  + ' mm²', 'A',  r.A));
        out.appendChild(row('Ix', fmt(r.Ix) + ' mm⁴', 'Ix', r.Ix));
        out.appendChild(row('Iy', fmt(r.Iy) + ' mm⁴', 'Iy', r.Iy));
        out.appendChild(row('Wx', fmt(r.Wx) + ' mm³', 'Wx', r.Wx));
        out.appendChild(row('Wy', fmt(r.Wy) + ' mm³', 'Wy', r.Wy));
        out.appendChild(row('ix', fmt(r.ix) + ' mm',  'ix', r.ix));
        out.appendChild(row('iy', fmt(r.iy) + ' mm',  'iy', r.iy));
        out.appendChild(row('perimetro', fmt(r.perimeter) + ' mm', 'perim', r.perimeter));
      } catch (e) {
        const err = document.createElement('div'); err.className = 'tk-hint';
        err.textContent = 'Errore: ' + e.message;
        out.appendChild(err);
      }
    };

    refresh();

    // Commercial profiles
    const profDiv = document.createElement('div');
    profDiv.appendChild(section('Profili commerciali (IPE/HEA/HEB/UPN)'));
    const seriesSel = mkSel(Catalog.PROFILE_SERIES.map(s => s.name));
    const sizeSel = mkSel([]);
    const profOut = document.createElement('div'); profOut.className = 'tk-rows';

    const updProf = () => {
      profOut.innerHTML = '';
      const s = Catalog.PROFILE_SERIES[seriesSel.selectedIndex];
      const pr = s.profiles[sizeSel.selectedIndex];
      if (!pr) return;
      profOut.appendChild(row('h',  fmt(pr.h)  + ' mm', 'h',  pr.h));
      profOut.appendChild(row('b',  fmt(pr.b)  + ' mm', 'b',  pr.b));
      profOut.appendChild(row('tw', fmt(pr.tw) + ' mm', 'tw', pr.tw));
      profOut.appendChild(row('tf', fmt(pr.tf) + ' mm', 'tf', pr.tf));
      profOut.appendChild(row('A',  fmt(pr.A)  + ' mm²','A',  pr.A));
      profOut.appendChild(row('Ix', fmt(pr.Ix) + ' mm⁴','Ix', pr.Ix));
      profOut.appendChild(row('Iy', fmt(pr.Iy) + ' mm⁴','Iy', pr.Iy));
      profOut.appendChild(row('Wx', fmt(pr.Wx) + ' mm³','Wx', pr.Wx));
      profOut.appendChild(row('Wy', fmt(pr.Wy) + ' mm³','Wy', pr.Wy));
      profOut.appendChild(row('kg/m', fmt(pr.weight), 'kg_per_m', pr.weight));
    };
    seriesSel.addEventListener('change', () => {
      const s = Catalog.PROFILE_SERIES[seriesSel.selectedIndex];
      sizeSel.innerHTML = '';
      s.profiles.forEach(pr => {
        const o = document.createElement('option'); o.textContent = pr.designation;
        sizeSel.appendChild(o);
      });
      sizeSel.selectedIndex = 0;
      updProf();
    });
    sizeSel.addEventListener('change', updProf);
    seriesSel.dispatchEvent(new Event('change'));
    profDiv.appendChild(seriesSel);
    profDiv.appendChild(sizeSel);
    profDiv.appendChild(profOut);

    p.appendChild(section('Forma'));
    p.appendChild(shapeSel);
    p.appendChild(inputsDiv);
    p.appendChild(calcBtn);
    p.appendChild(section('Risultati'));
    p.appendChild(out);
    p.appendChild(profDiv);
  }

  // ====================== Formulas ======================
  function buildFormulasPane() {
    const p = panel.querySelector('[data-pane="formulas"]');
    const cards = [
      { title: 'σ = F / A — Tensione assiale',
        inputs: [['F','Forza [N]'], ['A','Area [mm²]']],
        fn: v => ({ value: v[0]/v[1], unit: 'MPa', name:'σ', vname:'sigma_ax' })},
      { title: 'σ = M·y / I — Tensione di flessione',
        inputs: [['M','Momento [N·mm]'],['y','y [mm]'],['I','I [mm⁴]']],
        fn: v => ({ value: v[0]*v[1]/v[2], unit:'MPa', name:'σ', vname:'sigma_b' })},
      { title: 'δ = F·L³ / (48·E·I) — Trave appoggiata, carico centrato',
        inputs: [['F','F [N]'],['L','L [mm]'],['E','E [MPa]'],['I','I [mm⁴]']],
        fn: v => ({ value: v[0]*Math.pow(v[1],3)/(48*v[2]*v[3]), unit:'mm', name:'δ', vname:'delta_ss' })},
      { title: 'δ = F·L³ / (3·E·I) — Mensola, carico all\'estremo',
        inputs: [['F','F [N]'],['L','L [mm]'],['E','E [MPa]'],['I','I [mm⁴]']],
        fn: v => ({ value: v[0]*Math.pow(v[1],3)/(3*v[2]*v[3]), unit:'mm', name:'δ', vname:'delta_cant' })},
      { title: 'δ = 5·q·L⁴ / (384·E·I) — Trave appoggiata, carico distribuito',
        inputs: [['q','q [N/mm]'],['L','L [mm]'],['E','E [MPa]'],['I','I [mm⁴]']],
        fn: v => ({ value: 5*v[0]*Math.pow(v[1],4)/(384*v[2]*v[3]), unit:'mm', name:'δ', vname:'delta_distr' })},
      { title: 'Pcr = π²·E·I / Lk² — Carico critico Eulero',
        inputs: [['E','E [MPa]'],['I','I [mm⁴]'],['Lk','Lk [mm]']],
        fn: v => ({ value: Math.PI*Math.PI*v[0]*v[1]/(v[2]*v[2]), unit:'N', name:'Pcr', vname:'Pcr' })},
      { title: 'T = K·d·F — Coppia serraggio bullone',
        inputs: [['K','K (0.15/0.20/0.30)'],['d','d [mm]'],['F','Fp [N]']],
        fn: v => ({ value: v[0]*v[1]*v[2], unit:'N·mm', name:'T', vname:'Tbullone' })},
      { title: 'σ = F / (0.707·a·L) — Saldatura fillet',
        inputs: [['F','F [N]'],['a','a [mm]'],['L','L [mm]']],
        fn: v => ({ value: v[0]/(0.707*v[1]*v[2]), unit:'MPa', name:'σ', vname:'sigma_weld' })},
      { title: 'σh = p·D / (2·t) — Sforzo cerchiante tubo',
        inputs: [['p','p [MPa]'],['D','D [mm]'],['t','t [mm]']],
        fn: v => ({ value: v[0]*v[1]/(2*v[2]), unit:'MPa', name:'σh', vname:'sigma_h' })},
      { title: 'P = T·2π·n/60 — Potenza meccanica',
        inputs: [['T','T [N·m]'],['n','n [rpm]']],
        fn: v => ({ value: v[0]*2*Math.PI*v[1]/60, unit:'W', name:'P', vname:'P_mech' })},
      { title: 'ΔL = α·L·ΔT — Dilatazione termica',
        inputs: [['α','α [×10⁻⁶/K]'],['L','L [mm]'],['ΔT','ΔT [K]']],
        fn: v => ({ value: v[0]*1e-6*v[1]*v[2], unit:'mm', name:'ΔL', vname:'dL' })},
      { title: 'η = σadm / σmax — Fattore di sicurezza',
        inputs: [['σadm','σadm [MPa]'],['σmax','σmax [MPa]']],
        fn: v => ({ value: v[0]/v[1], unit:'', name:'η', vname:'eta' })},
    ];
    cards.forEach(c => p.appendChild(formulaCard(c)));
  }

  function formulaCard(c) {
    const det = document.createElement('details');
    det.className = 'tk-card';
    const sum = document.createElement('summary');
    sum.textContent = c.title;
    det.appendChild(sum);
    const body = document.createElement('div');
    body.className = 'tk-card-body';
    const fields = c.inputs.map(([sym, hint]) => {
      const wrap = document.createElement('div');
      wrap.className = 'tk-input-wrap';
      wrap.innerHTML = `<label>${sym}</label>`;
      const inp = mkInput('');
      inp.placeholder = hint;
      wrap.appendChild(inp);
      body.appendChild(wrap);
      return inp;
    });
    const out = document.createElement('div'); out.className = 'tk-result';
    out.textContent = '—';
    const btnRow = document.createElement('div'); btnRow.className = 'tk-btn-row';
    const calc = document.createElement('button'); calc.className = 'chip'; calc.textContent = 'Calcola';
    const insB = document.createElement('button'); insB.className = 'chip'; insB.textContent = 'Ins';
    const varB = document.createElement('button'); varB.className = 'chip'; varB.textContent = '→ var';
    btnRow.append(calc, insB, varB);

    let last = NaN;
    calc.onclick = () => {
      try {
        const v = fields.map(f => parseNum(f.value));
        const r = c.fn(v);
        last = r.value;
        out.textContent = `${r.name} = ${fmt(r.value)}${r.unit ? ' ' + r.unit : ''}    → var ${r.vname}`;
      } catch (e) {
        out.textContent = 'input non valido';
      }
    };
    insB.onclick = () => { if (Number.isFinite(last)) ins(last); };
    varB.onclick = () => { if (Number.isFinite(last)) variable(c.fn([]).vname || 'r', last); };
    // capture vname even before calc — set via current card
    varB.onclick = () => {
      if (!Number.isFinite(last)) return;
      const v = fields.map(() => 0);
      try { variable(c.fn(v).vname, last); } catch (e) {}
    };

    body.appendChild(out);
    body.appendChild(btnRow);
    det.appendChild(body);
    return det;
  }

  // ====================== Wizards ======================
  function buildWizardsPane() {
    const p = panel.querySelector('[data-pane="wizards"]');
    p.appendChild(buildBoltWizard());
    p.appendChild(buildWeldWizard());
    p.appendChild(buildStatsWizard());
    p.appendChild(buildTimeWizard());
  }

  function buildBoltWizard() {
    const det = mkDetails('Bulloni ISO');
    const body = det.querySelector('.tk-card-body');
    const threadSel = mkSel(Catalog.BOLTS.map(b => b.designation));
    const classSel  = mkSel(Catalog.BOLT_CLASSES.map(c => c.name));
    const kIn = mkInput('0.20');
    const out = document.createElement('div'); out.className = 'tk-rows';

    threadSel.selectedIndex = 4; // M8
    classSel.selectedIndex = 2;  // 8.8

    const upd = () => {
      out.innerHTML = '';
      const b = Catalog.BOLTS[threadSel.selectedIndex];
      const cl = Catalog.BOLT_CLASSES[classSel.selectedIndex];
      const k = parseFloat(kIn.value.replace(',', '.')) || 0.20;
      const Fp = 0.7 * cl.tensile * b.As;
      const T = k * b.d * Fp;
      out.appendChild(row('d',     fmt(b.d) + ' mm',          'd_bolt', b.d));
      out.appendChild(row('passo', fmt(b.pitch) + ' mm',      'pitch', b.pitch));
      out.appendChild(row('As',    fmt(b.As) + ' mm²',        'As', b.As));
      out.appendChild(row('d foro',fmt(b.dTap) + ' mm',       'd_tap', b.dTap));
      out.appendChild(row('σy',    fmt(cl.yield) + ' MPa',    'sy_bolt', cl.yield));
      out.appendChild(row('σt',    fmt(cl.tensile) + ' MPa',  'su_bolt', cl.tensile));
      out.appendChild(row('F precar', fmt(Fp) + ' N',         'Fp', Fp));
      out.appendChild(row('T serr',   fmt(T/1000) + ' N·m',   'Tbolt', T/1000));
    };
    threadSel.addEventListener('change', upd);
    classSel.addEventListener('change', upd);
    kIn.addEventListener('input', upd);

    body.appendChild(section('Filettatura ISO'));
    body.appendChild(threadSel);
    body.appendChild(section('Classe (es. 8.8)'));
    body.appendChild(classSel);
    body.appendChild(section('Coeff. K (0.15 lub / 0.20 std / 0.30 sec)'));
    body.appendChild(kIn);
    body.appendChild(out);
    body.appendChild(hint('Precarico = 0.7·σt·As. T = K·d·Fp.'));
    upd();
    return det;
  }

  function buildWeldWizard() {
    const det = mkDetails('Saldature fillet');
    const body = det.querySelector('.tk-card-body');
    const F = mkInput('10000'); F.placeholder = 'F [N]';
    const a = mkInput('4');     a.placeholder = 'a [mm]';
    const L = mkInput('100');   L.placeholder = 'L [mm]';
    const matSel = mkSel(Catalog.MATERIALS.map(m => m.name));
    const out = document.createElement('div'); out.className = 'tk-result'; out.textContent = '—';

    const calc = () => {
      try {
        const fv = parseNum(F.value), av = parseNum(a.value), lv = parseNum(L.value);
        const m = Catalog.MATERIALS[matSel.selectedIndex];
        const sigma = fv / (av * lv);
        const sigmaEq = sigma * Math.sqrt(3);
        const adm = m ? `  ·  σadm ≈ ${fmt(m.su * 0.45)} MPa` : '';
        out.textContent = `σ ≈ ${fmt(sigma)} MPa · σeq ≈ ${fmt(sigmaEq)} MPa${adm}`;
      } catch (e) { out.textContent = 'input non valido'; }
    };
    [F, a, L].forEach(i => i.addEventListener('input', calc));
    matSel.addEventListener('change', calc);

    [['F [N]', F], ['a (gola) [mm]', a], ['L (lunghezza) [mm]', L]].forEach(([lbl, inp]) => {
      body.appendChild(section(lbl));
      body.appendChild(inp);
    });
    body.appendChild(section('Materiale base'));
    body.appendChild(matSel);
    body.appendChild(out);
    body.appendChild(hint('σ = F/(a·L); σ_eq = σ·√3 (Von Mises). σadm ≈ 0.45·σt.'));
    calc();
    return det;
  }

  function buildStatsWizard() {
    const det = mkDetails('Statistica');
    const body = det.querySelector('.tk-card-body');
    const ta = document.createElement('textarea');
    ta.className = 'toolkit-input';
    ta.rows = 4;
    ta.placeholder = 'Numeri, uno per riga o separati da spazi/virgole/punto-virgola.\nEs: 12.3 14.1 13.8';
    const out = document.createElement('div'); out.className = 'tk-rows';

    const calc = () => {
      out.innerHTML = '';
      const tokens = (ta.value || '').split(/[\s,;]+/).filter(Boolean);
      const nums = tokens.map(t => parseFloat(t.replace(',', '.'))).filter(Number.isFinite);
      if (!nums.length) return;
      const n = nums.length;
      const sum = nums.reduce((a,b) => a+b, 0);
      const mean = sum / n;
      let varr = 0; for (const v of nums) varr += (v-mean)*(v-mean);
      varr /= n;
      const sd = Math.sqrt(varr);
      const sdSample = n > 1 ? Math.sqrt(varr * n / (n-1)) : 0;
      const sorted = [...nums].sort((a,b) => a-b);
      const min = sorted[0], max = sorted[n-1];
      const med = n%2===0 ? (sorted[n/2-1]+sorted[n/2])/2 : sorted[Math.floor(n/2)];
      out.appendChild(row('n',     String(n), 'stat_n', n));
      out.appendChild(row('Σ',     fmt(sum), 'stat_sum', sum));
      out.appendChild(row('media', fmt(mean), 'stat_mu', mean));
      out.appendChild(row('mediana', fmt(med), 'stat_med', med));
      out.appendChild(row('σ pop',  fmt(sd), 'stat_sd', sd));
      out.appendChild(row('σ camp', fmt(sdSample), 'stat_sds', sdSample));
      out.appendChild(row('min',   fmt(min), 'stat_min', min));
      out.appendChild(row('max',   fmt(max), 'stat_max', max));
    };
    ta.addEventListener('input', calc);
    body.appendChild(section('Numeri'));
    body.appendChild(ta);
    body.appendChild(section('Statistiche'));
    body.appendChild(out);
    return det;
  }

  function buildTimeWizard() {
    const det = mkDetails('Tempo / Durata');
    const body = det.querySelector('.tk-card-body');
    const mkT = (h0, m0, s0) => {
      const wrap = document.createElement('div'); wrap.className = 'tk-time-row';
      const h = mkInput(h0); h.style.maxWidth = '60px';
      const m = mkInput(m0); m.style.maxWidth = '60px';
      const s = mkInput(s0); s.style.maxWidth = '60px';
      const l1 = document.createElement('span'); l1.textContent = 'h';
      const l2 = document.createElement('span'); l2.textContent = 'm';
      const l3 = document.createElement('span'); l3.textContent = 's';
      wrap.append(h, l1, m, l2, s, l3);
      return { wrap, h, m, s };
    };
    const t1 = mkT('0', '0', '0');
    const t2 = mkT('0', '0', '0');
    const outT1 = document.createElement('div'); outT1.className = 'tk-val';
    const outAdd = document.createElement('div'); outAdd.className = 'tk-val';
    const outSub = document.createElement('div'); outSub.className = 'tk-val';

    const toSec = (t) => {
      return (parseInt(t.h.value, 10) || 0) * 3600
           + (parseInt(t.m.value, 10) || 0) * 60
           + (parseInt(t.s.value, 10) || 0);
    };
    const fmtTime = (sec) => {
      const sign = sec < 0 ? '−' : '';
      sec = Math.abs(sec);
      const h = Math.floor(sec / 3600), m = Math.floor((sec%3600)/60), s = sec%60;
      return `${sign}${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    };
    const calc = () => {
      const s1 = toSec(t1), s2 = toSec(t2);
      outT1.textContent = `T1 = ${s1} s · ${(s1/60).toFixed(2)} min · ${(s1/3600).toFixed(3)} h`;
      outAdd.textContent = `T1 + T2 = ${fmtTime(s1+s2)}`;
      outSub.textContent = `T1 − T2 = ${fmtTime(s1-s2)}`;
    };
    [t1, t2].forEach(t => [t.h, t.m, t.s].forEach(i => i.addEventListener('input', calc)));
    calc();
    body.appendChild(section('T1'));
    body.appendChild(t1.wrap);
    body.appendChild(section('T2'));
    body.appendChild(t2.wrap);
    body.appendChild(outT1);
    body.appendChild(outAdd);
    body.appendChild(outSub);
    return det;
  }

  // ====================== More: triangoli, vettori, solver, pipes ======================
  function buildMorePane() {
    const p = panel.querySelector('[data-pane="more"]');
    p.appendChild(buildTrianglePane());
    p.appendChild(buildVectorsPane());
    p.appendChild(buildSolverPane());
    p.appendChild(buildPipesPane());
  }

  function buildTrianglePane() {
    const det = mkDetails('Triangoli (qualunque caso)');
    const body = det.querySelector('.tk-card-body');
    const f = {};
    ['a','b','c','A','B','C'].forEach(k => {
      const wrap = document.createElement('div');
      wrap.className = 'tk-input-wrap';
      wrap.innerHTML = `<label>${k}${'abc'.includes(k) ? ' [mm]' : ' [°]'}</label>`;
      f[k] = mkInput('');
      wrap.appendChild(f[k]);
      body.appendChild(wrap);
    });
    const out = document.createElement('div'); out.className = 'tk-hint';
    out.textContent = 'Compila 3 valori (3 lati, oppure 2 lati + 1 angolo, oppure 1 lato + 2 angoli).';
    const solveBtn = document.createElement('button');
    solveBtn.className = 'chip menu-row eq-chip';
    solveBtn.textContent = 'Risolvi';
    const clearBtn = document.createElement('button');
    clearBtn.className = 'chip menu-row';
    clearBtn.textContent = 'Pulisci';

    solveBtn.onclick = () => {
      try {
        const get = (k) => { const t = f[k].value.trim().replace(',', '.'); return t === '' ? NaN : parseFloat(t); };
        const a = get('a'), b = get('b'), c = get('c');
        let A = get('A'), B = get('B'), C = get('C');
        const r = solveTriangle(a, b, c, A, B, C);
        if (!r) { out.textContent = 'Combinazione non risolvibile.'; return; }
        if (isNaN(get('a'))) f.a.value = fmt(r[0]);
        if (isNaN(get('b'))) f.b.value = fmt(r[1]);
        if (isNaN(get('c'))) f.c.value = fmt(r[2]);
        if (isNaN(get('A'))) f.A.value = fmt(r[3]*180/Math.PI);
        if (isNaN(get('B'))) f.B.value = fmt(r[4]*180/Math.PI);
        if (isNaN(get('C'))) f.C.value = fmt(r[5]*180/Math.PI);
        const area = 0.5 * r[0] * r[1] * Math.sin(r[5]);
        out.textContent = `Area ≈ ${fmt(area)} mm² · perim ≈ ${fmt(r[0]+r[1]+r[2])} mm`;
      } catch (e) { out.textContent = 'Errore: ' + e.message; }
    };
    clearBtn.onclick = () => { Object.values(f).forEach(i => i.value = ''); out.textContent = ''; };

    body.appendChild(out);
    body.appendChild(solveBtn);
    body.appendChild(clearBtn);
    return det;
  }

  function solveTriangle(a, b, c, A, B, C) {
    if (!isNaN(A)) A = A * Math.PI / 180;
    if (!isNaN(B)) B = B * Math.PI / 180;
    if (!isNaN(C)) C = C * Math.PI / 180;
    const def = v => !isNaN(v);
    let known = +def(a)+def(b)+def(c)+def(A)+def(B)+def(C);
    if (known < 3) return null;

    if (def(a)&&def(b)&&def(c)) {
      A = Math.acos((b*b+c*c-a*a)/(2*b*c));
      B = Math.acos((a*a+c*c-b*b)/(2*a*c));
      C = Math.PI - A - B;
      return [a,b,c,A,B,C];
    }
    if (def(a)&&def(b)&&def(C)) {
      c = Math.sqrt(a*a+b*b-2*a*b*Math.cos(C));
      A = Math.acos((b*b+c*c-a*a)/(2*b*c));
      B = Math.PI - A - C;
      return [a,b,c,A,B,C];
    }
    if (def(a)&&def(c)&&def(B)) {
      b = Math.sqrt(a*a+c*c-2*a*c*Math.cos(B));
      A = Math.acos((b*b+c*c-a*a)/(2*b*c));
      C = Math.PI - A - B;
      return [a,b,c,A,B,C];
    }
    if (def(b)&&def(c)&&def(A)) {
      a = Math.sqrt(b*b+c*c-2*b*c*Math.cos(A));
      B = Math.acos((a*a+c*c-b*b)/(2*a*c));
      C = Math.PI - A - B;
      return [a,b,c,A,B,C];
    }
    const ang = +def(A)+def(B)+def(C);
    if (ang >= 2) {
      if (!def(A)) A = Math.PI - B - C;
      if (!def(B)) B = Math.PI - A - C;
      if (!def(C)) C = Math.PI - A - B;
      const sa = def(a) ? a/Math.sin(A) : def(b) ? b/Math.sin(B) : def(c) ? c/Math.sin(C) : NaN;
      if (isNaN(sa)) return null;
      if (!def(a)) a = sa * Math.sin(A);
      if (!def(b)) b = sa * Math.sin(B);
      if (!def(c)) c = sa * Math.sin(C);
      return [a,b,c,A,B,C];
    }
    const ssa = (ks, kA, os) => {
      if (!def(ks)||!def(kA)||!def(os)) return null;
      const sinX = os * Math.sin(kA) / ks;
      if (sinX > 1 + 1e-9) return null;
      const oA = Math.asin(Math.min(1, sinX));
      const tA = Math.PI - kA - oA;
      if (tA <= 0) return null;
      return [oA, tA, ks * Math.sin(tA) / Math.sin(kA)];
    };
    let s;
    if ((s = ssa(a, A, b))) return [a, b, s[2], A, s[0], s[1]];
    if ((s = ssa(a, A, c))) return [a, s[2], c, A, s[1], s[0]];
    if ((s = ssa(b, B, a))) return [a, b, s[2], s[0], B, s[1]];
    if ((s = ssa(b, B, c))) return [s[2], b, c, s[1], B, s[0]];
    if ((s = ssa(c, C, a))) return [a, s[2], c, s[0], s[1], C];
    if ((s = ssa(c, C, b))) return [s[2], b, c, s[1], s[0], C];
    return null;
  }

  function buildVectorsPane() {
    const det = mkDetails('Vettori 3D');
    const body = det.querySelector('.tk-card-body');
    const ax = mkInput(''), ay = mkInput(''), az = mkInput('');
    const bx = mkInput(''), by = mkInput(''), bz = mkInput('');
    const out = document.createElement('div'); out.className = 'tk-rows';
    const calcBtn = document.createElement('button');
    calcBtn.className = 'chip menu-row eq-chip';
    calcBtn.textContent = 'Calcola';
    calcBtn.onclick = () => {
      try {
        const a = [parseNum(ax.value), parseNum(ay.value), parseNum(az.value)];
        const b = [parseNum(bx.value), parseNum(by.value), parseNum(bz.value)];
        const la = Math.hypot(...a), lb = Math.hypot(...b);
        const d = a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
        const x = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]];
        const ang = Math.acos(Math.max(-1, Math.min(1, d/(la*lb))))*180/Math.PI;
        const p = d/lb;
        out.innerHTML = '';
        out.appendChild(row('|a|', fmt(la)));
        out.appendChild(row('|b|', fmt(lb)));
        out.appendChild(row('a·b', fmt(d)));
        out.appendChild(row('a×b', `(${fmt(x[0])}, ${fmt(x[1])}, ${fmt(x[2])})`));
        out.appendChild(row('angolo', fmt(ang) + '°'));
        out.appendChild(row('proj a su b', fmt(p)));
      } catch (e) { out.innerHTML = '<div class="tk-hint">input non valido</div>'; }
    };
    const grid = document.createElement('div'); grid.className = 'tk-vec-grid';
    const lbl = (t) => { const s = document.createElement('span'); s.textContent = t; s.className = 'tk-key'; return s; };
    grid.append(lbl('a'), ax, ay, az, lbl('b'), bx, by, bz);
    body.appendChild(grid);
    body.appendChild(calcBtn);
    body.appendChild(out);
    return det;
  }

  function buildSolverPane() {
    const det = mkDetails('Solver  f(x) = 0');
    const body = det.querySelector('.tk-card-body');
    const expr = mkInput('x^2 - 2');
    expr.placeholder = 'es: x^2 - 2  (per √2)';
    const x0 = mkInput('1');
    const result = document.createElement('div'); result.className = 'tk-result'; result.textContent = '—';
    const solveBtn = document.createElement('button');
    solveBtn.className = 'chip menu-row eq-chip';
    solveBtn.textContent = 'Risolvi f(x) = 0';
    solveBtn.onclick = () => {
      try {
        const r = newton(expr.value, parseNum(x0.value));
        result.textContent = 'x ≈ ' + fmt(r);
      } catch (e) { result.textContent = 'Errore: ' + e.message; }
    };
    body.appendChild(section('Espressione in x'));
    body.appendChild(expr);
    body.appendChild(section('Stima iniziale x0'));
    body.appendChild(x0);
    body.appendChild(solveBtn);
    body.appendChild(result);
    body.appendChild(hint('Newton-Raphson, derivata stimata numericamente.'));
    return det;
  }

  function newton(expr, x0) {
    if (!expr || !expr.trim()) throw new Error('manca f(x)');
    const baseVars = Object.assign({}, bridge.variables());
    let x = x0;
    const h = 1e-6 * Math.max(1, Math.abs(x));
    for (let i = 0; i < 200; i++) {
      baseVars.x = x;
      const f = new Parser(expr, bridge.radians(), baseVars).parse();
      baseVars.x = x + h;
      const f2 = new Parser(expr, bridge.radians(), baseVars).parse();
      const df = (f2 - f) / h;
      if (Math.abs(df) < 1e-14) throw new Error('derivata nulla');
      const dx = f / df;
      x -= dx;
      if (Math.abs(dx) < 1e-10) return x;
    }
    throw new Error('nessuna convergenza');
  }

  function buildPipesPane() {
    const det = mkDetails('Tubi (ASME B36.10)');
    const body = det.querySelector('.tk-card-body');
    const dns = [...new Set(Catalog.PIPES.map(p => p.dn))];
    const schs = [...new Set(Catalog.PIPES.map(p => p.sch))];
    const dnSel = mkSel(dns), schSel = mkSel(schs);
    const out = document.createElement('div'); out.className = 'tk-rows';
    const upd = () => {
      out.innerHTML = '';
      const dn = dnSel.value, sch = schSel.value;
      const p = Catalog.PIPES.find(x => x.dn === dnSel.options[dnSel.selectedIndex].text && x.sch === schSel.options[schSel.selectedIndex].text);
      if (!p) { out.innerHTML = '<div class="tk-hint">Combinazione non disponibile</div>'; return; }
      out.appendChild(row('OD',     fmt(p.od)      + ' mm',   'OD', p.od));
      out.appendChild(row('sp.',    fmt(p.wall)    + ' mm',   'thk', p.wall));
      out.appendChild(row('ID',     fmt(p.id)      + ' mm',   'ID', p.id));
      out.appendChild(row('peso',   fmt(p.weight)  + ' kg/m', 'kg_per_m', p.weight));
      out.appendChild(row('vol',    fmt(p.volPerM) + ' L/m',  'L_per_m', p.volPerM));
      const npsRow = document.createElement('div'); npsRow.className = 'tk-row';
      npsRow.innerHTML = `<span class="tk-key">NPS</span><span class="tk-val">${p.nps}</span><span></span>`;
      out.appendChild(npsRow);
    };
    dnSel.addEventListener('change', upd);
    schSel.addEventListener('change', upd);
    upd();
    body.appendChild(section('DN'));
    body.appendChild(dnSel);
    body.appendChild(section('Schedule'));
    body.appendChild(schSel);
    body.appendChild(section('Dati'));
    body.appendChild(out);
    return det;
  }

  // ====================== UI primitives ======================
  function mkSel(opts) {
    const s = document.createElement('select');
    s.className = 'toolkit-input';
    opts.forEach(o => { const op = document.createElement('option'); op.textContent = o; s.appendChild(op); });
    return s;
  }
  function mkInput(val) {
    const i = document.createElement('input');
    i.className = 'toolkit-input';
    i.type = 'text';
    i.inputMode = 'decimal';
    i.value = val || '';
    return i;
  }
  function mkDetails(title) {
    const det = document.createElement('details'); det.className = 'tk-card';
    const sum = document.createElement('summary'); sum.textContent = title;
    det.appendChild(sum);
    const body = document.createElement('div'); body.className = 'tk-card-body';
    det.appendChild(body);
    return det;
  }
  function section(t) {
    const d = document.createElement('div'); d.className = 'menu-section';
    d.textContent = t;
    return d;
  }
  function hint(t) {
    const d = document.createElement('div'); d.className = 'tk-hint';
    d.textContent = t;
    return d;
  }

  return { init, build, open, selectTab };
})();

window.Toolkit = Toolkit;
