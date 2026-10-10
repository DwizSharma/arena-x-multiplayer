import type { Config } from 'tailwindcss';
export default { content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}', './lib/**/*.{ts,tsx}'], theme: { extend: { colors: { ink: '#11152b', panel: '#1a2040', lime: '#c4f36b', violet: '#a78bfa' } } }, plugins: [] } satisfies Config;
