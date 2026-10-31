import path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
export const SUITE = path.resolve(SCRIPT_DIR, '..');
export const ROOT = path.resolve(SUITE, '..', '..');
export const OUT = SCRIPT_DIR;
export const ALLOWED_ROOT = ROOT;

/**
 * Une y normaliza una ruta bajo el directorio permitido del repositorio.
 * Si el resultado queda fuera, lanza un error en lenguaje llano.
 */
export function resolveWithinAllowed(baseDir, relativePath) {
  const abs = path.resolve(baseDir, relativePath);
  const allowed = path.resolve(ALLOWED_ROOT);
  const prefix = allowed.endsWith(path.sep) ? allowed : allowed + path.sep;
  if (abs !== allowed && !abs.startsWith(prefix)) {
    throw new Error(
      'La ruta pedida sale del directorio permitido del proyecto. ' +
      'Revise la ruta en el informe y use solo ficheros dentro del repositorio.'
    );
  }
  return abs;
}
