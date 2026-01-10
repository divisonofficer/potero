/**
 * Cursor position utilities for contenteditable elements
 * Used to preserve cursor position during inline editing
 */

/**
 * Get current caret position within a contenteditable element
 * Returns the character offset from the beginning of the element
 */
export function getCaretPosition(element: HTMLElement): number {
	const selection = window.getSelection();
	if (!selection || selection.rangeCount === 0) return 0;

	const range = selection.getRangeAt(0);
	const preCaretRange = range.cloneRange();
	preCaretRange.selectNodeContents(element);
	preCaretRange.setEnd(range.endContainer, range.endOffset);

	return preCaretRange.toString().length;
}

/**
 * Set caret position within a contenteditable element
 * Position is character offset from the beginning
 */
export function setCaretPosition(element: HTMLElement, position: number): void {
	const selection = window.getSelection();
	if (!selection) return;

	const range = document.createRange();
	let currentPos = 0;
	let found = false;

	function traverse(node: Node) {
		if (found) return;

		if (node.nodeType === Node.TEXT_NODE) {
			const textLength = node.textContent?.length || 0;
			if (currentPos + textLength >= position) {
				range.setStart(node, position - currentPos);
				range.collapse(true);
				found = true;
				return;
			}
			currentPos += textLength;
		} else {
			for (const child of Array.from(node.childNodes)) {
				traverse(child);
			}
		}
	}

	traverse(element);
	selection.removeAllRanges();
	selection.addRange(range);
}

/**
 * Get coordinates of caret relative to element
 * Used for positioning autocomplete dropdown
 */
export function getCaretCoordinates(element: HTMLElement): { top: number; left: number } {
	const selection = window.getSelection();
	if (!selection || selection.rangeCount === 0) {
		return { top: 0, left: 0 };
	}

	const range = selection.getRangeAt(0);
	const rect = range.getBoundingClientRect();
	const elementRect = element.getBoundingClientRect();

	return {
		top: rect.bottom - elementRect.top + 4, // 4px offset below cursor
		left: rect.left - elementRect.left
	};
}

/**
 * Move caret to end of element
 */
export function moveCaretToEnd(element: HTMLElement): void {
	const selection = window.getSelection();
	if (!selection) return;

	const range = document.createRange();
	range.selectNodeContents(element);
	range.collapse(false); // false = collapse to end
	selection.removeAllRanges();
	selection.addRange(range);
}

/**
 * Get text content before cursor position
 */
export function getTextBeforeCursor(element: HTMLElement): string {
	const selection = window.getSelection();
	if (!selection || selection.rangeCount === 0) return '';

	const range = selection.getRangeAt(0);
	const preCaretRange = range.cloneRange();
	preCaretRange.selectNodeContents(element);
	preCaretRange.setEnd(range.endContainer, range.endOffset);

	return preCaretRange.toString();
}
