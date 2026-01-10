/**
 * Generates a UUID v4 string.
 * Cross-browser compatible alternative to crypto.randomUUID()
 * which may not be available in all contexts.
 */
export function generateUUID(): string {
	// Try to use crypto.randomUUID if available (modern browsers in secure contexts)
	if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
		return crypto.randomUUID();
	}

	// Fallback: Manual UUID v4 generation
	// Format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
	// where x is any hexadecimal digit and y is one of 8, 9, A, or B
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = (Math.random() * 16) | 0;
		const v = c === 'x' ? r : (r & 0x3) | 0x8;
		return v.toString(16);
	});
}
