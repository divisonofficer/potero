/**
 * Block-level markdown types for Obsidian-style editor
 */

export type BlockType =
	| 'heading1'
	| 'heading2'
	| 'heading3'
	| 'heading4'
	| 'heading5'
	| 'heading6'
	| 'paragraph'
	| 'code'
	| 'list'
	| 'table'
	| 'blockquote'
	| 'hr';

export interface BlockMetadata {
	language?: string; // For code blocks
	level?: number; // For headings
	ordered?: boolean; // For lists
}

export interface Block {
	id: string; // Unique block ID (UUID)
	type: BlockType; // Block type
	content: string; // Raw markdown content for this block
	metadata?: BlockMetadata; // Optional metadata
}
