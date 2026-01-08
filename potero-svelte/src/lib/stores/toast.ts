import { writable } from 'svelte/store';

export interface Toast {
	id: string;
	type: 'success' | 'error' | 'info' | 'warning';
	message: string;
	duration?: number;
}

function createToastStore() {
	const { subscribe, update } = writable<Toast[]>([]);

	function add(toast: Omit<Toast, 'id'>) {
		const id = Math.random().toString(36).slice(2);
		const newToast: Toast = { ...toast, id };

		update((toasts) => [...toasts, newToast]);

		// Auto-remove after duration (default 5 seconds)
		const duration = toast.duration ?? 5000;
		if (duration > 0) {
			setTimeout(() => {
				remove(id);
			}, duration);
		}

		return id;
	}

	function remove(id: string) {
		update((toasts) => toasts.filter((t) => t.id !== id));
	}

	function clear() {
		update(() => []);
	}

	return {
		subscribe,
		add,
		remove,
		clear,
		success: (message: string, duration?: number) => add({ type: 'success', message, duration }),
		error: (message: string, duration?: number) => add({ type: 'error', message, duration }),
		info: (message: string, duration?: number) => add({ type: 'info', message, duration }),
		warning: (message: string, duration?: number) => add({ type: 'warning', message, duration })
	};
}

export const toast = createToastStore();
