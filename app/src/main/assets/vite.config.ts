import { defineConfig, loadEnv } from 'vite'
import { viteSingleFile } from "vite-plugin-singlefile"
const config = loadEnv(process.env.NODE_ENV, process.cwd());
// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  if (command === 'serve') {
    // dev specific config
    return {
      plugins: [],
    }
  } else {
    // command === 'build'
    /// prod build
    /// dev build
    if (mode == "development") {
      console.info("Runing development build mode......")
      return {
        // build specific config
        plugins: [],
        logLevel: "info",
        build: {
          minify: false
        }
      }
    } else {
      console.info("Runing prodution build mode......")
      return {
        plugins: [viteSingleFile()],
        build: {
          target: "esnext",
          assetsInlineLimit: 100000000,
          chunkSizeWarningLimit: 100000000,
          cssCodeSplit: false,
          brotliSize: false,
          rollupOptions: {
            inlineDynamicImports: true,
            output: {
              manualChunks: () => "everything.js",
            },
          },
        },
      }
    }
  }
});
