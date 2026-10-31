import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { ROOT, SUITE, OUT, resolveWithinAllowed } from './path-safe.mjs';

const diagrams = [
  { type: 'architecture', html: 'architecture/autofirma-mapa.html', cand: 'architecture/candidate.json',
    expectLabels: ['Persona', 'Navegador', 'Página de prueba', 'Programa Autofirma', 'Ejemplos de firma', 'Comprobación local', 'Comprobaciones automáticas', 'Paquetes Linux', 'Servidor a tres pasos', 'Plataformas del Estado'],
    title: 'Cómo encaja Autofirma-2026 de punta a punta',
    focusSearch: 'Autofirma' },
  { type: 'workflow', html: 'workflow/autofirma-flujo.html', cand: 'workflow/candidate.json',
    expectLabels: ['Propone un cambio', 'Revisa en local', 'Envía a GitHub', 'Calidad remota', 'Baja Autofirma fija', 'Evidencia lista'],
    title: 'Del cambio local a la comprobación automática',
    focusSearch: 'Calidad' },
  { type: 'sequence', html: 'sequence/autofirma-secuencia.html', cand: 'sequence/candidate.json',
    expectLabels: ['Persona', 'Navegador', 'Portal prueba', 'Sistema', 'Autofirma'],
    title: 'Qué ocurre al pulsar un enlace de firma de prueba',
    focusSearch: 'Autofirma' },
  { type: 'dataflow', html: 'dataflow/autofirma-datos.html', cand: 'dataflow/candidate.json',
    expectLabels: ['Documentos prueba', 'Autofirma JAR', 'Firmas ejemplo', 'Banca pruebas', 'Informe local', 'VALIDe'],
    title: 'Cómo viajan los ejemplos de firma hasta la comprobación',
    focusSearch: 'VALIDe' },
  { type: 'lifecycle', html: 'lifecycle/autofirma-fases.html', cand: 'lifecycle/candidate.json',
    expectLabels: ['F0 Manifiesto', 'F1 Línea base', 'F2 Vectores', 'F7 Accesibilidad', 'F10 Upstream', 'Fases hechas'],
    title: 'Estado del programa por fases (F0–F10)',
    focusSearch: 'F2' },
];

function collectSources(obj, acc=[]) {
  if (Array.isArray(obj)) obj.forEach(x => collectSources(x, acc));
  else if (obj && typeof obj === 'object') {
    if (Array.isArray(obj.sources)) acc.push(...obj.sources);
    for (const v of Object.values(obj)) collectSources(v, acc);
  }
  return acc;
}

function verifySources(candPath) {
  const cand = JSON.parse(fs.readFileSync(candPath, 'utf8'));
  const sources = collectSources(cand);
  const results = [];
  for (const s of sources) {
    const abs = resolveWithinAllowed(ROOT, s.path);
    const okExists = fs.existsSync(abs);
    let okLines = false;
    let lineCount = 0;
    if (okExists) {
      const text = fs.readFileSync(abs, 'utf8');
      lineCount = text.split(/\n/).length;
      const start = s.line || 1;
      const end = s.end_line || start;
      okLines = start >= 1 && end <= lineCount && end >= start;
    }
    results.push({ path: s.path, line: s.line, end_line: s.end_line, okExists, okLines, lineCount });
  }
  return results;
}

function looksSpanish(text) {
  const esHints = /[áéíóúñ¿¡]|Cómo|Qué|Del |Persona|Autofirma|firma|comprob|página|local|fase/i;
  return esHints.test(text);
}

const report = { diagrams: [], ok: true };

const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM || '/usr/bin/google-chrome',
  args: ['--no-sandbox', '--disable-gpu'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

for (const d of diagrams) {
  const htmlPath = resolveWithinAllowed(SUITE, d.html);
  const candPath = resolveWithinAllowed(SUITE, d.cand);
  const url = 'file://' + htmlPath;
  const entry = { type: d.type, html: htmlPath, titleOk: false, labels: {}, spanishTitle: false, sourceChecks: [], errors: [] };

  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(800);
    const title = await page.title();
    entry.domTitle = title;
    entry.titleOk = title.includes(d.title) || (await page.locator('text=' + d.title).count()) > 0;
    entry.spanishTitle = looksSpanish(d.title) && looksSpanish(title);

    const bodyText = await page.innerText('body');
    for (const lab of d.expectLabels) {
      const found = bodyText.includes(lab) || (await page.locator(`text=${lab}`).count()) > 0;
      entry.labels[lab] = found;
      if (!found) entry.errors.push('missing label: ' + lab);
    }

    const englishAuthored = [];
    for (const bad of ['Sample Web App', 'API Server', 'User Interface', 'Release phases', 'clickstream']) {
      if (bodyText.includes(bad)) englishAuthored.push(bad);
    }
    entry.englishAuthoredLeaks = englishAuthored;

    try {
      await page.keyboard.press('/');
      await page.waitForTimeout(300);
      const search = page.locator('input[type="search"], input[placeholder*="Buscar"], input[placeholder*="Search"], input').first();
      if (await search.count()) {
        await search.fill(d.focusSearch);
        await page.waitForTimeout(400);
        entry.searchTried = d.focusSearch;
        entry.searchVisible = true;
      } else {
        entry.searchVisible = false;
      }
      await page.keyboard.press('Escape');
    } catch (e) {
      entry.searchError = String(e.message || e);
    }

    const shot = resolveWithinAllowed(OUT, `${d.type}-1440.png`);
    await page.screenshot({ path: shot, fullPage: false });
    entry.screenshot = shot;

    entry.sourceChecks = verifySources(candPath);
    const badSrc = entry.sourceChecks.filter(s => !s.okExists || !s.okLines);
    if (badSrc.length) {
      entry.errors.push('bad sources: ' + JSON.stringify(badSrc));
    }
    if (!entry.titleOk) entry.errors.push('title mismatch');
    if (Object.values(entry.labels).some(v => !v)) entry.errors.push('some labels missing');
    if (englishAuthored.length) entry.errors.push('english authored leaks');
  } catch (e) {
    entry.errors.push(String(e.stack || e));
  }

  entry.ok = entry.errors.length === 0;
  if (!entry.ok) report.ok = false;
  report.diagrams.push(entry);
  console.log(d.type, entry.ok ? 'PASS' : 'FAIL', entry.errors.join('; ') || 'all checks ok');
}

await browser.close();
fs.writeFileSync(resolveWithinAllowed(OUT, 'report.json'), JSON.stringify(report, null, 2));
console.log('REPORT', path.join(OUT, 'report.json'), 'overall', report.ok);
process.exit(report.ok ? 0 : 1);
