import typography from '@tailwindcss/typography';

/** @type {import('tailwindcss').Config} */
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				border: 'hsl(var(--border))',
				input: 'hsl(var(--input))',
				ring: 'hsl(var(--ring))',
				background: 'hsl(var(--background))',
				foreground: 'hsl(var(--foreground))',
				primary: {
					DEFAULT: 'hsl(var(--primary))',
					foreground: 'hsl(var(--primary-foreground))'
				},
				secondary: {
					DEFAULT: 'hsl(var(--secondary))',
					foreground: 'hsl(var(--secondary-foreground))'
				},
				destructive: {
					DEFAULT: 'hsl(var(--destructive))',
					foreground: 'hsl(var(--destructive-foreground))'
				},
				muted: {
					DEFAULT: 'hsl(var(--muted))',
					foreground: 'hsl(var(--muted-foreground))'
				},
				accent: {
					DEFAULT: 'hsl(var(--accent))',
					foreground: 'hsl(var(--accent-foreground))'
				},
				popover: {
					DEFAULT: 'hsl(var(--popover))',
					foreground: 'hsl(var(--popover-foreground))'
				},
				card: {
					DEFAULT: 'hsl(var(--card))',
					foreground: 'hsl(var(--card-foreground))'
				}
			},
			borderRadius: {
				lg: 'var(--radius)',
				md: 'calc(var(--radius) - 2px)',
				sm: 'calc(var(--radius) - 4px)'
			},
			typography: {
				DEFAULT: {
					css: {
						// Custom styling for wiki links
						'.wiki-link': {
							'background-color': 'rgb(219 234 254)',
							padding: '2px 6px',
							'border-radius': '4px',
							'text-decoration': 'none',
							color: 'rgb(29 78 216)',
							cursor: 'pointer',
							'&:hover': {
								'background-color': 'rgb(191 219 254)'
							}
						},
						'.wiki-link.unresolved': {
							'background-color': 'rgb(243 244 246)',
							color: 'rgb(107 114 128)',
							border: '1px dashed rgb(209 213 219)'
						},
						// LaTeX math styling
						'.katex-display': {
							margin: '1em 0'
						}
					}
				}
			}
		}
	},
	plugins: [typography]
};
