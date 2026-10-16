import react from "@vitejs/plugin-react";
import {loadEnv} from "vite";
import {defineConfig} from "vitest/config";

export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), "");

    const backendTarget =
        process.env.DEV_API_TARGET
        ?? env.DEV_API_TARGET
        ?? "http://localhost:8080";

    return {
        plugins: [react()],

        server: {
            host: "localhost",
            port: 3000,
            strictPort: true,
            proxy: {
                "/api": {
                    target: backendTarget,
                    changeOrigin: true
                }
            }
        },

        preview: {
            host: "localhost",
            port: 4173,
            strictPort: true,
            proxy: {
                "/api": {
                    target: backendTarget,
                    changeOrigin: true
                }
            }
        },

        build: {
            outDir: "build",
            emptyOutDir: true
        },

        test: {
            environment: "jsdom",
            globals: true,
            setupFiles: ["./src/setupTests.ts"]
        }
    };
});
