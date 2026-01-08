<script lang="ts">
	import type { AuthorProfile } from '$lib/types';
	import { goHome, openPaper } from '$lib/stores/tabs';

	interface Props {
		author: AuthorProfile;
	}

	let { author }: Props = $props();

	// Check if any external links are available
	let hasExternalLinks = $derived(
		author.semanticScholarUrl ||
			author.googleScholarUrl ||
			author.dblpUrl ||
			author.orcid ||
			author.homepage
	);
</script>

<div class="h-full overflow-auto">
	<!-- Header with gradient -->
	<div class="bg-gradient-to-r from-violet-500 to-purple-600">
		<div class="px-8 py-6">
			<button
				class="mb-4 flex items-center gap-2 rounded-lg bg-white/20 px-3 py-1.5 text-sm text-white hover:bg-white/30"
				onclick={() => goHome()}
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M19 12H5M12 19l-7-7 7-7" />
				</svg>
				Back
			</button>
			<div class="flex items-center gap-4">
				<div class="flex h-16 w-16 items-center justify-center rounded-full bg-white/20">
					<svg class="h-10 w-10 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
						<circle cx="12" cy="7" r="4" />
					</svg>
				</div>
				<div>
					<p class="text-sm text-white/80">Researcher Profile</p>
					<h1 class="text-3xl font-bold text-white">{author.name}</h1>
					{#if author.affiliation}
						<p class="text-sm text-white/70">{author.affiliation}</p>
					{/if}
				</div>
			</div>
		</div>
	</div>

	<div class="px-8 py-6">
		<!-- Stats Cards -->
		<div class="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-neutral-500">
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
					</svg>
					<span class="text-sm">Total Publications</span>
				</div>
				<p class="mt-2 text-3xl font-bold">{author.publications.toLocaleString()}</p>
			</div>
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-neutral-500">
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
					</svg>
					<span class="text-sm">Total Citations</span>
				</div>
				<p class="mt-2 text-3xl font-bold">{author.citations.toLocaleString()}</p>
			</div>
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-neutral-500">
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<rect x="3" y="4" width="18" height="16" rx="2" />
						<path d="M3 10h18" />
					</svg>
					<span class="text-sm">h-index</span>
				</div>
				<p class="mt-2 text-3xl font-bold">{author.hIndex || '-'}</p>
			</div>
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-neutral-500">
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<rect x="3" y="4" width="18" height="16" rx="2" />
						<path d="M3 10h18" />
					</svg>
					<span class="text-sm">i10-index</span>
				</div>
				<p class="mt-2 text-3xl font-bold">{author.i10Index || '-'}</p>
			</div>
		</div>

		<div class="grid gap-6 lg:grid-cols-3">
			<!-- Left Column: About and Research Interests -->
			<div class="lg:col-span-2 space-y-6">
				{#if author.overview}
					<div class="rounded-xl border bg-card p-6">
						<h2 class="mb-4 text-lg font-semibold">About</h2>
						<p class="text-neutral-600 dark:text-neutral-400">{author.overview}</p>
					</div>
				{/if}

				{#if author.researchInterests.length > 0}
					<div class="rounded-xl border bg-card p-6">
						<h2 class="mb-4 text-lg font-semibold">Research Interests</h2>
						<ul class="space-y-2">
							{#each author.researchInterests as interest}
								<li class="flex items-center gap-2">
									<span class="h-2 w-2 rounded-full bg-violet-500"></span>
									<span class="text-neutral-700 dark:text-neutral-300">{interest}</span>
								</li>
							{/each}
						</ul>
					</div>
				{/if}

				<!-- Recent Papers -->
				<div class="rounded-xl border bg-card p-6">
					<h2 class="mb-4 text-lg font-semibold">Papers in Library ({author.recentPapers.length})</h2>
					<div class="space-y-3">
						{#each author.recentPapers as paper}
							<button
								class="w-full rounded-lg border p-4 text-left hover:bg-muted transition-colors"
								onclick={() => openPaper(paper)}
							>
								<h3 class="font-medium line-clamp-2">{paper.title}</h3>
								<p class="mt-1 text-sm text-neutral-500">
									{paper.authors.slice(0, 3).join(', ')}
									{paper.authors.length > 3 ? ' et al.' : ''}
								</p>
								<div class="mt-2 flex items-center gap-3 text-xs text-neutral-500">
									{#if paper.year}
										<span>{paper.year}</span>
									{/if}
									{#if paper.conference}
										<span class="rounded bg-muted px-2 py-0.5">{paper.conference}</span>
									{/if}
									<span>{paper.citations} citations</span>
								</div>
							</button>
						{/each}
					</div>
				</div>
			</div>

			<!-- Right Column: External Links and Contact -->
			<div class="space-y-6">
				<!-- External Profile Links -->
				{#if hasExternalLinks}
					<div class="rounded-xl border bg-card p-6">
						<h2 class="mb-4 text-lg font-semibold">External Profiles</h2>
						<div class="space-y-2">
							{#if author.semanticScholarUrl}
								<a
									href={author.semanticScholarUrl}
									target="_blank"
									rel="noopener noreferrer"
									class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted transition-colors"
								>
									<div class="flex items-center gap-3">
										<div class="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
											<svg class="h-4 w-4 text-blue-600 dark:text-blue-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<circle cx="12" cy="12" r="10" />
												<path d="M12 6v6l4 2" />
											</svg>
										</div>
										<span>Semantic Scholar</span>
									</div>
									<svg class="h-4 w-4 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
										<polyline points="15,3 21,3 21,9" />
										<line x1="10" y1="14" x2="21" y2="3" />
									</svg>
								</a>
							{/if}
							{#if author.googleScholarUrl}
								<a
									href={author.googleScholarUrl}
									target="_blank"
									rel="noopener noreferrer"
									class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted transition-colors"
								>
									<div class="flex items-center gap-3">
										<div class="flex h-8 w-8 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
											<svg class="h-4 w-4 text-green-600 dark:text-green-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<path d="M22 10v6M2 10l10-5 10 5-10 5z" />
												<path d="M6 12v5c3 3 9 3 12 0v-5" />
											</svg>
										</div>
										<span>Google Scholar</span>
									</div>
									<svg class="h-4 w-4 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
										<polyline points="15,3 21,3 21,9" />
										<line x1="10" y1="14" x2="21" y2="3" />
									</svg>
								</a>
							{/if}
							{#if author.dblpUrl}
								<a
									href={author.dblpUrl}
									target="_blank"
									rel="noopener noreferrer"
									class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted transition-colors"
								>
									<div class="flex items-center gap-3">
										<div class="flex h-8 w-8 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
											<svg class="h-4 w-4 text-orange-600 dark:text-orange-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
												<path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
											</svg>
										</div>
										<span>DBLP</span>
									</div>
									<svg class="h-4 w-4 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
										<polyline points="15,3 21,3 21,9" />
										<line x1="10" y1="14" x2="21" y2="3" />
									</svg>
								</a>
							{/if}
							{#if author.orcid}
								<a
									href={`https://orcid.org/${author.orcid}`}
									target="_blank"
									rel="noopener noreferrer"
									class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted transition-colors"
								>
									<div class="flex items-center gap-3">
										<div class="flex h-8 w-8 items-center justify-center rounded-lg bg-lime-100 dark:bg-lime-900/30">
											<svg class="h-4 w-4 text-lime-600 dark:text-lime-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<circle cx="12" cy="12" r="10" />
												<path d="M12 8v4l2 2" />
											</svg>
										</div>
										<div>
											<span>ORCID</span>
											<p class="text-xs text-neutral-500">{author.orcid}</p>
										</div>
									</div>
									<svg class="h-4 w-4 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
										<polyline points="15,3 21,3 21,9" />
										<line x1="10" y1="14" x2="21" y2="3" />
									</svg>
								</a>
							{/if}
							{#if author.homepage}
								<a
									href={author.homepage}
									target="_blank"
									rel="noopener noreferrer"
									class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted transition-colors"
								>
									<div class="flex items-center gap-3">
										<div class="flex h-8 w-8 items-center justify-center rounded-lg bg-neutral-100 dark:bg-neutral-800">
											<svg class="h-4 w-4 text-neutral-600 dark:text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
												<polyline points="9 22 9 12 15 12 15 22" />
											</svg>
										</div>
										<span>Personal Website</span>
									</div>
									<svg class="h-4 w-4 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
										<polyline points="15,3 21,3 21,9" />
										<line x1="10" y1="14" x2="21" y2="3" />
									</svg>
								</a>
							{/if}
						</div>
					</div>
				{/if}

				<!-- Quick Actions -->
				<div class="rounded-xl border bg-card p-6">
					<h2 class="mb-4 text-lg font-semibold">Quick Actions</h2>
					<div class="space-y-2">
						<button
							class="flex w-full items-center justify-between rounded-lg bg-violet-500 px-4 py-3 text-white hover:bg-violet-600"
							onclick={() => {
								// TODO: Search papers by this author
							}}
						>
							<span>Search Papers</span>
							<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<circle cx="11" cy="11" r="8" />
								<path d="m21 21-4.3-4.3" />
							</svg>
						</button>
						<button
							class="flex w-full items-center justify-between rounded-lg border px-4 py-3 hover:bg-muted"
						>
							<span>Export Data</span>
							<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
								<polyline points="7 10 12 15 17 10" />
								<line x1="12" y1="15" x2="12" y2="3" />
							</svg>
						</button>
					</div>
				</div>

				<!-- Contact Info -->
				{#if author.affiliation}
					<div class="rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 p-6 text-white">
						<h2 class="mb-4 text-lg font-semibold">Affiliation</h2>
						<div class="flex items-center gap-3">
							<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M19 21V5a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5m-4 0h4" />
							</svg>
							<span>{author.affiliation}</span>
						</div>
					</div>
				{/if}
			</div>
		</div>
	</div>
</div>
