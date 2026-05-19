import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: false, // If 3000 is occupied, it'll try next one, but we want 3000. Actually, better true to alert us.
    proxy: {
      '/api': 'http://localhost:8080',
      '/signup': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
      '/logout': 'http://localhost:8080',
      '/refresh': 'http://localhost:8080',
    }
  }
})
