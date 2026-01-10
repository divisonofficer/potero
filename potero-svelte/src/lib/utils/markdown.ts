/**
 * Markdown rendering configuration with KaTeX and Prism extensions
 * Supports LaTeX math, syntax highlighting, and wiki-style links
 */

import { marked } from 'marked';
import markedKatex from 'marked-katex-extension';
import { markedHighlight } from 'marked-highlight';
import Prism from 'prismjs';

// Import Prism languages
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-c';
import 'prismjs/components/prism-cpp';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-yaml';
import 'prismjs/components/prism-markdown';

let configured = false;

/**
 * Configure marked with extensions (KaTeX, Prism, wiki links)
 */
export function configureMarked() {
	if (configured) return;

	// Configure KaTeX extension for LaTeX math
	// Supports both inline $...$ and display $$...$$
	marked.use(
		markedKatex({
			throwOnError: false,
			displayMode: false,
			output: 'html'
		})
	);

	// Configure syntax highlighting with Prism
	marked.use(
		markedHighlight({
			highlight(code, lang) {
				if (lang && Prism.languages[lang]) {
					try {
						return Prism.highlight(code, Prism.languages[lang], lang);
					} catch (e) {
						console.error(`[markdown] Failed to highlight ${lang}:`, e);
						return code;
					}
				}
				return code;
			}
		})
	);

	// Custom renderer for wiki links
	const renderer = new marked.Renderer();

	// Override link method
	const originalLink = renderer.link;
	renderer.link = function (href, title, text) {
		// Check if it's a wiki link pattern [[...]]
		if (href.startsWith('[[') && href.endsWith(']]')) {
			const linkText = href.slice(2, -2);

			if (linkText.startsWith('paper:')) {
				// Paper link: [[paper:paper_id]]
				const paperId = linkText.substring(6);
				return `<a href="#" class="wiki-link paper-link" data-paper-id="${paperId}" onclick="event.preventDefault();">${text}</a>`;
			} else {
				// Note link: [[Note Title]]
				return `<a href="#" class="wiki-link note-link" data-note-title="${linkText}" onclick="event.preventDefault();">${text}</a>`;
			}
		}

		// Standard markdown link - call original
		return originalLink.call(this, href, title, text);
	};

	marked.setOptions({ renderer });

	configured = true;
}

/**
 * Render markdown to HTML with all extensions
 */
export function renderMarkdown(content: string): string {
	configureMarked();
	try {
		return marked.parse(content) as string;
	} catch (e) {
		console.error('[markdown] Failed to parse markdown:', e);
		return `<p class="text-red-500">Failed to render markdown</p>`;
	}
}

/**
 * Lazy load additional Prism languages on demand
 */
export async function loadPrismLanguage(lang: string): Promise<void> {
	const languageMap: Record<string, () => Promise<any>> = {
		rust: () => import('prismjs/components/prism-rust' as any),
		go: () => import('prismjs/components/prism-go' as any),
		ruby: () => import('prismjs/components/prism-ruby' as any),
		php: () => import('prismjs/components/prism-php' as any),
		swift: () => import('prismjs/components/prism-swift' as any),
		kotlin: () => import('prismjs/components/prism-kotlin' as any),
		scala: () => import('prismjs/components/prism-scala' as any),
		sql: () => import('prismjs/components/prism-sql' as any),
		latex: () => import('prismjs/components/prism-latex' as any)
	};

	if (languageMap[lang] && !Prism.languages[lang]) {
		try {
			await languageMap[lang]();
		} catch (e) {
			console.error(`[markdown] Failed to load language ${lang}:`, e);
		}
	}
}

/**
 * Optimized markdown rendering with lazy-loaded languages
 */
export async function renderMarkdownOptimized(content: string): Promise<string> {
	configureMarked();

	// Pre-load only the languages used in this content
	const codeBlocks = content.match(/```(\w+)/g) || [];
	const languages = codeBlocks.map((b) => b.substring(3));

	// Load languages in parallel
	await Promise.all(languages.map(loadPrismLanguage));

	return renderMarkdown(content);
}
