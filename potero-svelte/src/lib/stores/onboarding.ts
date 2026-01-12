import { writable, get } from 'svelte/store';
import { api, type Settings } from '$lib/api/client';

export interface OnboardingState {
	isActive: boolean;
	currentStep: number;
	// SSO
	ssoCompleted: boolean;
	ssoToken?: string;
	ssoExpiresAt?: number;
	// API Key
	apiKey?: string;
	// Search engines
	searchEngines: Record<string, boolean>;
	// PDF engines
	pdfEngines: {
		grobid: boolean;
		pdftotext: boolean;
		ocr: boolean;
	};
	// Storage paths
	storagePath: string;
}

const getDefaultStoragePath = (): string => {
	// Windows default path
	if (typeof window !== 'undefined') {
		// Try to get user's Documents folder via environment or use a sensible default
		return 'C:\\Users\\Documents\\Potero';
	}
	return '';
};

const initialState: OnboardingState = {
	isActive: false,
	currentStep: 0,
	ssoCompleted: false,
	searchEngines: {
		semanticscholar: true,
		openalex: false,
		pubmed: false,
		dblp: false
	},
	pdfEngines: {
		grobid: true,
		pdftotext: true,
		ocr: false
	},
	storagePath: getDefaultStoragePath()
};

export const onboardingState = writable<OnboardingState>(initialState);

// Step definitions
export const ONBOARDING_STEPS = [
	{ id: 'welcome', title: 'Welcome', description: 'Get started with Potero' },
	{ id: 'sso', title: 'SSO Login', description: 'Connect to POSTECH GenAI' },
	{ id: 'search', title: 'Search Engines', description: 'Choose your search APIs' },
	{ id: 'pdf', title: 'PDF Engines', description: 'Configure extraction engines' },
	{ id: 'storage', title: 'Storage', description: 'Set storage location' }
] as const;

export const TOTAL_STEPS = ONBOARDING_STEPS.length;

// Actions
export function startOnboarding() {
	onboardingState.update((s) => ({ ...s, isActive: true, currentStep: 0 }));
}

export function nextStep() {
	onboardingState.update((s) => ({
		...s,
		currentStep: Math.min(s.currentStep + 1, TOTAL_STEPS - 1)
	}));
}

export function prevStep() {
	onboardingState.update((s) => ({
		...s,
		currentStep: Math.max(s.currentStep - 1, 0)
	}));
}

export function setStep(step: number) {
	onboardingState.update((s) => ({
		...s,
		currentStep: Math.max(0, Math.min(step, TOTAL_STEPS - 1))
	}));
}

export function setSSOComplete(token: string, expiresAt?: number) {
	onboardingState.update((s) => ({
		...s,
		ssoCompleted: true,
		ssoToken: token,
		ssoExpiresAt: expiresAt
	}));
}

export function setApiKey(apiKey: string) {
	onboardingState.update((s) => ({
		...s,
		apiKey
	}));
}

export function toggleSearchEngine(id: string) {
	onboardingState.update((s) => ({
		...s,
		searchEngines: {
			...s.searchEngines,
			[id]: !s.searchEngines[id]
		}
	}));
}

export function setSearchEngine(id: string, enabled: boolean) {
	onboardingState.update((s) => ({
		...s,
		searchEngines: {
			...s.searchEngines,
			[id]: enabled
		}
	}));
}

export function togglePDFEngine(engine: keyof OnboardingState['pdfEngines']) {
	onboardingState.update((s) => ({
		...s,
		pdfEngines: {
			...s.pdfEngines,
			[engine]: !s.pdfEngines[engine]
		}
	}));
}

export function setPDFEngine(engine: keyof OnboardingState['pdfEngines'], enabled: boolean) {
	onboardingState.update((s) => ({
		...s,
		pdfEngines: {
			...s.pdfEngines,
			[engine]: enabled
		}
	}));
}

export function setStoragePath(path: string) {
	onboardingState.update((s) => ({ ...s, storagePath: path }));
}

// Validation
export function isStepValid(step: number): boolean {
	const state = get(onboardingState);

	switch (step) {
		case 0: // Welcome - always valid
			return true;
		case 1: // SSO - completed and API key obtained
			return state.ssoCompleted && !!state.apiKey;
		case 2: // Search engines - at least one enabled
			return Object.values(state.searchEngines).some((v) => v);
		case 3: // PDF engines - at least one enabled
			return Object.values(state.pdfEngines).some((v) => v);
		case 4: // Storage - path must be set
			return state.storagePath.trim().length > 0;
		default:
			return false;
	}
}

export function canProceed(): boolean {
	const state = get(onboardingState);
	return isStepValid(state.currentStep);
}

// Check if onboarding is required
export async function checkOnboardingRequired(): Promise<boolean> {
	try {
		const response = await api.getSettings();
		if (response.success && response.data) {
			return !response.data.onboardingCompleted;
		}
		return true; // Assume needed if can't fetch
	} catch {
		return true;
	}
}

// Complete onboarding and save all settings
export async function completeOnboarding(): Promise<boolean> {
	const state = get(onboardingState);

	try {
		// Save SSO token if present
		if (state.ssoToken) {
			await api.saveSSOToken(
				state.ssoToken,
				'robi-gpt-dev',
				state.ssoExpiresAt
			);
		}

		// Save search engine settings
		for (const [id, enabled] of Object.entries(state.searchEngines)) {
			await api.updateAPIConfig(id, enabled);
		}

		// Save PDF engine and storage settings, mark onboarding complete
		const updateResult = await api.updateSettings({
			grobidEnabled: state.pdfEngines.grobid,
			pdftotextEnabled: state.pdfEngines.pdftotext,
			ocrEnabled: state.pdfEngines.ocr,
			pdfStoragePath: state.storagePath,
			onboardingCompleted: true
		});

		if (updateResult.success) {
			onboardingState.update((s) => ({ ...s, isActive: false }));
			return true;
		}

		return false;
	} catch (error) {
		console.error('Failed to complete onboarding:', error);
		return false;
	}
}

// Reset onboarding state
export function resetOnboarding() {
	onboardingState.set(initialState);
}
