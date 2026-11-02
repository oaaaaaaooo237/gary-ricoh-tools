// GRKit 1.3.5 MIT converter. No network calls; all input/output stays in the supplied local job.
import { readFile, writeFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import init, { importLut, haldImage, effectLabel, guessInput } from './grkit_wasm-Bv1y84tD.js';

const job = path.resolve(process.argv[2]);
try {
  const request = JSON.parse(await readFile(path.join(job, 'request.json'), 'utf8'));
  const here = path.dirname(fileURLToPath(import.meta.url));
  await init({ module_or_path: await readFile(path.join(here, 'grkit_wasm_bg-Ddg15dz0.wasm')) });
  if (request.kind === 'hald') {
    await writeFile(path.join(job, 'hald.png'), haldImage(12));
    await writeFile(path.join(job, 'result.json'), JSON.stringify({ ok: true }));
  } else {
    if (!['gr3-2.10', 'gr4-1.11', 'gr4-1.20', 'gr4-1.20-0528'].includes(request.target)) throw Error('不支持的色彩目标');
    if (!['auto', 'standard', 'srgb', 'linear', 'vlog', 'flog', 'flog2', 'flog2c'].includes(request.input)) throw Error('不支持的输入色彩空间');
    if ((await stat(path.join(job, 'input.lut'))).size > 32 * 1024 * 1024) throw Error('滤镜文件超过32MB');
    const catalog = JSON.parse(await readFile(path.join(here, 'catalog', request.target, 'catalog.json'), 'utf8'));
    const bytes = new Uint8Array(await readFile(path.join(job, 'input.lut')));
    const gamma = Uint16Array.from(catalog.base_gamma);
    const input = request.input === 'auto' ? guessInput(bytes, gamma) : request.input;
    const result = importLut(bytes, request.source,
      input, gamma, request.id, request.name,
      JSON.stringify({ label: 'USR', shape: 'filter' }), () => {});
    try {
      const label = effectLabel(result.blob);
      try {
        await writeFile(path.join(job, 'effect.grf'), result.blob);
        await writeFile(path.join(job, 'preview.lut'), result.preview);
        await writeFile(path.join(job, 'result.json'), JSON.stringify({ ok: true, id: label.id, name: label.name,
          monochrome: label.monochrome, grade: result.grade, warnings: result.warnings, input }));
      } finally { label.free(); }
    } finally { result.free(); }
  }
} catch (error) {
  await writeFile(path.join(job, 'result.json'), JSON.stringify({ ok: false, error: String(error?.message ?? error).slice(0,2000) }));
  process.exitCode = 1;
}
