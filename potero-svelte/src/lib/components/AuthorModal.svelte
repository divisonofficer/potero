<script lang="ts">
	import type { Paper, AuthorProfile } from '$lib/types';
	import { openAuthorProfile } from '$lib/stores/tabs';
	import { api, type AuthorProfileResponse } from '$lib/api/client';

	interface Props {
		authorName: string;
		papers: Paper[]; // Papers by this author in library
		onClose: () => void;
	}

	let { authorName, papers, onClose }: Props = $props();

	// State for API data
	let isLoading = $state(true);
	let apiProfile = $state<AuthorProfileResponse | null>(null);
	let apiError = $state<string | null>(null);

	// Calculate stats from local papers (fallback)
	const localPublications = papers.length;
	const localCitations = papers.reduce((sum, p) => sum + p.citations, 0);

	// Get unique research areas from paper subjects
	const researchInterests = [...new Set(papers.flatMap((p) => p.subject))].slice(0, 5);

	// Fetch author data from Semantic Scholar
	$effect(() => {
		fetchAuthorProfile();
	});

	async function fetchAuthorProfile() {
		isLoading = true;
		apiError = null;

		try {
			const result = await api.lookupAuthor(authorName);
			if (result.success && result.data) {
				apiProfile = result.data;
			} else {
				apiError = 'Author not found in Semantic Scholar';
			}
		} catch (e) {
			apiError = 'Failed to fetch author data';
		} finally {
			isLoading = false;
		}
	}

	// Use API data if available, otherwise fall back to local data
	let displayPublications = $derived(apiProfile?.paperCount ?? localPublications);
	let displayCitations = $derived(apiProfile?.citationCount ?? localCitations);
	let displayHIndex = $derived(apiProfile?.hIndex);
	let affiliations = $derived(apiProfile?.affiliations ?? []);

	function handleViewDetails() {
		const profile: AuthorProfile = {
			name: authorName,
			affiliation: affiliations[0] ?? null,
			publications: displayPublications,
			citations: displayCitations,
			hIndex: displayHIndex ?? 0,
			i10Index: apiProfile?.i10Index ?? 0,
			overview: null,
			researchInterests,
			recentPapers: papers.slice(0, 10),
			// Add external links
			semanticScholarId: apiProfile?.semanticScholarId ?? undefined,
			googleScholarUrl: apiProfile?.googleScholarUrl ?? undefined,
			semanticScholarUrl: apiProfile?.semanticScholarUrl ?? undefined,
			dblpUrl: apiProfile?.dblpUrl ?? undefined,
			orcid: apiProfile?.orcid ?? undefined,
			homepage: apiProfile?.homepage ?? undefined
		};
		openAuthorProfile(profile);
		onClose();
	}

	function handleBackdropClick(e: MouseEvent) {
		if (e.target === e.currentTarget) {
			onClose();
		}
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			onClose();
		}
	}
</script>

<svelte:window onkeydown={handleKeydown} />

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
	onclick={handleBackdropClick}
>
	<div class="w-full max-w-lg overflow-hidden rounded-xl bg-white shadow-2xl dark:bg-neutral-800">
		<!-- Header with gradient -->
		<div class="bg-gradient-to-r from-violet-500 to-purple-600 px-6 py-4">
			<div class="flex items-start justify-between">
				<div class="flex items-center gap-3">
					<div class="flex h-10 w-10 items-center justify-center rounded-full bg-white/20">
						<svg class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
							<circle cx="12" cy="7" r="4" />
						</svg>
					</div>
					<div>
						<p class="text-sm text-white/80">Researcher Profile</p>
						<h2 class="text-xl font-bold text-white">{authorName}</h2>
						{#if affiliations.length > 0}
							<p class="text-xs text-white/70">{affiliations[0]}</p>
						{/if}
					</div>
				</div>
				<button
					onclick={onClose}
					class="rounded-lg p-1 text-white/80 hover:bg-white/20 hover:text-white"
				>
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M18 6L6 18M6 6l12 12" />
					</svg>
				</button>
			</div>
		</div>

		<!-- Stats -->
		<div class="grid grid-cols-3 gap-4 border-b px-6 py-4 dark:border-neutral-700">
			<div class="text-center">
				<div class="flex items-center justify-center gap-1 text-neutral-500">
					<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
					</svg>
				</div>
				{#if isLoading}
					<div class="h-8 w-12 mx-auto bg-neutral-200 dark:bg-neutral-700 rounded animate-pulse"></div>
				{:else}
					<p class="text-2xl font-bold text-neutral-900 dark:text-white">{displayPublications.toLocaleString()}</p>
				{/if}
				<p class="text-xs text-neutral-500">Publications</p>
			</div>
			<div class="text-center">
				<div class="flex items-center justify-center gap-1 text-neutral-500">
					<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
					</svg>
				</div>
				{#if isLoading}
					<div class="h-8 w-16 mx-auto bg-neutral-200 dark:bg-neutral-700 rounded animate-pulse"></div>
				{:else}
					<p class="text-2xl font-bold text-neutral-900 dark:text-white">{displayCitations.toLocaleString()}</p>
				{/if}
				<p class="text-xs text-neutral-500">Citations</p>
			</div>
			<div class="text-center">
				<div class="flex items-center justify-center gap-1 text-neutral-500">
					<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<rect x="3" y="4" width="18" height="16" rx="2" />
						<path d="M3 10h18" />
					</svg>
				</div>
				{#if isLoading}
					<div class="h-8 w-8 mx-auto bg-neutral-200 dark:bg-neutral-700 rounded animate-pulse"></div>
				{:else}
					<p class="text-2xl font-bold text-neutral-900 dark:text-white">{displayHIndex ?? '-'}</p>
				{/if}
				<p class="text-xs text-neutral-500">h-index</p>
			</div>
		</div>

		<!-- Content -->
		<div class="max-h-64 overflow-y-auto px-6 py-4">
			<!-- External Links -->
			{#if apiProfile}
				<div class="mb-4 flex flex-wrap gap-2">
					{#if apiProfile.semanticScholarUrl}
						<a
							href={apiProfile.semanticScholarUrl}
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 rounded-full bg-blue-100 px-3 py-1 text-xs text-blue-700 hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-300"
						>
							<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
								<polyline points="15,3 21,3 21,9" />
								<line x1="10" y1="14" x2="21" y2="3" />
							</svg>
							Semantic Scholar
						</a>
					{/if}
					{#if apiProfile.googleScholarUrl}
						<a
							href={apiProfile.googleScholarUrl}
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 rounded-full bg-green-100 px-3 py-1 text-xs text-green-700 hover:bg-green-200 dark:bg-green-900/30 dark:text-green-300"
						>
							<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
								<polyline points="15,3 21,3 21,9" />
								<line x1="10" y1="14" x2="21" y2="3" />
							</svg>
							Google Scholar
						</a>
					{/if}
					{#if apiProfile.dblpUrl}
						<a
							href={apiProfile.dblpUrl}
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 rounded-full bg-orange-100 px-3 py-1 text-xs text-orange-700 hover:bg-orange-200 dark:bg-orange-900/30 dark:text-orange-300"
						>
							<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
								<polyline points="15,3 21,3 21,9" />
								<line x1="10" y1="14" x2="21" y2="3" />
							</svg>
							DBLP
						</a>
					{/if}
					{#if apiProfile.orcid}
						<a
							href={`https://orcid.org/${apiProfile.orcid}`}
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 rounded-full bg-lime-100 px-3 py-1 text-xs text-lime-700 hover:bg-lime-200 dark:bg-lime-900/30 dark:text-lime-300"
						>
							<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
								<polyline points="15,3 21,3 21,9" />
								<line x1="10" y1="14" x2="21" y2="3" />
							</svg>
							ORCID
						</a>
					{/if}
					{#if apiProfile.homepage}
						<a
							href={apiProfile.homepage}
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 rounded-full bg-neutral-100 px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-200 dark:bg-neutral-700 dark:text-neutral-300"
						>
							<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
								<polyline points="9 22 9 12 15 12 15 22" />
							</svg>
							Homepage
						</a>
					{/if}
				</div>
			{/if}

			{#if affiliations.length > 1}
				<div class="mb-4">
					<h3 class="mb-2 text-sm font-semibold text-neutral-900 dark:text-white">Affiliations</h3>
					<div class="flex flex-wrap gap-2">
						{#each affiliations as affiliation}
							<span class="rounded-full border px-3 py-1 text-sm text-neutral-700 dark:text-neutral-300">
								{affiliation}
							</span>
						{/each}
					</div>
				</div>
			{/if}

			{#if researchInterests.length > 0}
				<div class="mb-4">
					<h3 class="mb-2 text-sm font-semibold text-neutral-900 dark:text-white">Research Interests (from Library)</h3>
					<div class="flex flex-wrap gap-2">
						{#each researchInterests as interest}
							<span class="rounded-full bg-violet-100 px-3 py-1 text-sm text-violet-700 dark:bg-violet-900/30 dark:text-violet-300">
								{interest}
							</span>
						{/each}
					</div>
				</div>
			{/if}

			<div>
				<h3 class="mb-2 text-sm font-semibold text-neutral-900 dark:text-white">Papers in Library ({papers.length})</h3>
				<div class="space-y-2">
					{#each papers.slice(0, 3) as paper}
						<div class="rounded-lg border p-3 text-sm dark:border-neutral-700">
							<p class="font-medium text-neutral-900 line-clamp-1 dark:text-white">{paper.title}</p>
							<p class="text-xs text-neutral-500">{paper.year} - {paper.citations} citations</p>
						</div>
					{/each}
					{#if papers.length > 3}
						<p class="text-xs text-neutral-500 text-center">+{papers.length - 3} more papers</p>
					{/if}
				</div>
			</div>

			{#if apiError}
				<p class="mt-4 text-xs text-neutral-500 text-center">
					{apiError} - showing local library data only
				</p>
			{/if}
		</div>

		<!-- Footer -->
		<div class="flex items-center justify-between border-t px-6 py-4 dark:border-neutral-700">
			<button
				class="text-sm text-neutral-600 hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-white"
				onclick={onClose}
			>
				Close
			</button>
			<button
				class="flex items-center gap-2 rounded-lg bg-violet-500 px-4 py-2 text-sm font-medium text-white hover:bg-violet-600"
				onclick={handleViewDetails}
			>
				View Details
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
					<polyline points="15,3 21,3 21,9" />
					<line x1="10" y1="14" x2="21" y2="3" />
				</svg>
			</button>
		</div>
	</div>
</div>
