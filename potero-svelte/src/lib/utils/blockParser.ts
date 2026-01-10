/**
 * Block-level markdown parser using marked's lexer
 * Converts markdown to editable blocks and back
 */

import { marked } from 'marked';
import type { Block, BlockType } from '$lib/types/block';

/**
 * Generate unique block ID
 * Fallback for environments without crypto.randomUUID
 */
function generateBlockId(): string {
	// Try crypto.randomUUID first (modern browsers in secure context)
	if (typeof crypto !== 'undefined' && crypto.randomUUID) {
		return `block-${crypto.randomUUID()}`;
	}

	// Fallback: simple random ID generator
	return `block-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

/**
 * Parse markdown string into array of blocks using marked's lexer
 */
export function parseMarkdownToBlocks(markdown: string): Block[] {
	if (!markdown.trim()) {
		// Return single empty paragraph for empty content
		return [
			{
				id: generateBlockId(),
				type: 'paragraph',
				content: ''
			}
		];
	}

	const tokens = marked.lexer(markdown);
	const blocks: Block[] = [];

	for (const token of tokens) {
		switch (token.type) {
			case 'heading':
				blocks.push({
					id: generateBlockId(),
					type: `heading${token.depth}` as BlockType,
					content: token.text,
					metadata: { level: token.depth }
				});
				break;

			case 'paragraph':
				blocks.push({
					id: generateBlockId(),
					type: 'paragraph',
					content: token.text
				});
				break;

			case 'code':
				blocks.push({
					id: generateBlockId(),
					type: 'code',
					content: token.text,
					metadata: { language: token.lang || 'text' }
				});
				break;

			case 'list': {
				// For lists, we'll store the entire list as one block for now
				// TODO: In future, could break down to individual list items
				const listItems = token.items.map((item: any) => {
					const prefix = token.ordered ? '1. ' : '- ';
					return `${prefix}${item.text}`;
				});
				blocks.push({
					id: generateBlockId(),
					type: 'list',
					content: listItems.join('\n'),
					metadata: { ordered: token.ordered }
				});
				break;
			}

			case 'table': {
				// Convert table back to markdown
				const headers = token.header.map((cell: any) => cell.text);
				const rows = token.rows.map((row: any) => row.map((cell: any) => cell.text));

				let tableMarkdown = '| ' + headers.join(' | ') + ' |\n';
				tableMarkdown += '| ' + headers.map(() => '---').join(' | ') + ' |\n';
				for (const row of rows) {
					tableMarkdown += '| ' + row.join(' | ') + ' |\n';
				}

				blocks.push({
					id: generateBlockId(),
					type: 'table',
					content: tableMarkdown.trim()
				});
				break;
			}

			case 'blockquote':
				// Extract text from blockquote tokens
				blocks.push({
					id: generateBlockId(),
					type: 'blockquote',
					content: token.text
				});
				break;

			case 'hr':
				blocks.push({
					id: generateBlockId(),
					type: 'hr',
					content: '---'
				});
				break;

			case 'space':
				// Skip empty spaces (they'll be added back during conversion)
				break;

			default:
				// For any unhandled token types, store as paragraph
				console.warn(`[blockParser] Unhandled token type: ${token.type}`);
				if ('text' in token && typeof token.text === 'string') {
					blocks.push({
						id: generateBlockId(),
						type: 'paragraph',
						content: token.text
					});
				}
		}
	}

	// If no blocks were created (e.g., only whitespace), return empty paragraph
	if (blocks.length === 0) {
		return [
			{
				id: generateBlockId(),
				type: 'paragraph',
				content: ''
			}
		];
	}

	return blocks;
}

/**
 * Convert blocks back to markdown string
 */
export function blocksToMarkdown(blocks: Block[]): string {
	return blocks
		.map((block) => {
			switch (block.type) {
				case 'heading1':
					return `# ${block.content}`;
				case 'heading2':
					return `## ${block.content}`;
				case 'heading3':
					return `### ${block.content}`;
				case 'heading4':
					return `#### ${block.content}`;
				case 'heading5':
					return `##### ${block.content}`;
				case 'heading6':
					return `###### ${block.content}`;

				case 'paragraph':
					return block.content;

				case 'code': {
					const lang = block.metadata?.language || '';
					return `\`\`\`${lang}\n${block.content}\n\`\`\``;
				}

				case 'list':
					return block.content;

				case 'table':
					return block.content;

				case 'blockquote':
					return `> ${block.content}`;

				case 'hr':
					return '---';

				default:
					return block.content;
			}
		})
		.join('\n\n');
}
