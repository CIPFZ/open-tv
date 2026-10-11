import {build} from 'esbuild';
import {build as viteBuild} from 'vite';
await viteBuild();
await build({entryPoints:['src/main/main.ts'],outfile:'dist/main/main.cjs',bundle:true,platform:'node',format:'cjs',external:['electron'],target:'node22'});
await build({entryPoints:['src/preload/preload.ts'],outfile:'dist/preload/preload.cjs',bundle:true,platform:'node',format:'cjs',external:['electron'],target:'node22'});
