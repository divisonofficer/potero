<script lang="ts">
	import { MessageSquare, ChevronDown, ChevronUp, ExternalLink, Download } from 'lucide-svelte';
	import type { Narrative } from '$lib/api/client';
	import { api } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';

	interface Props {
		narrative: Narrative;
		paperId: string;
		language: string;
	}

	let { narrative, paperId, language }: Props = $props();

	// Parse Reddit Thread from narrative content
	interface RedditPost {
		id: string;
		parentId: string | null;
		role: string | null;
		author: string;
		content: string;
		claimReferences: number[];
		depth: number;
		order: number;
		score: number;
	}

	interface RedditThread {
		originalPost: RedditPost;
		comments: RedditPost[];
	}

	let redditThread = $state<RedditThread | null>(null);
	let expandedComments = $state<Set<string>>(new Set());
	let userCommentText = $state('');
	let replyingToId = $state<string | null>(null);
	let isSubmittingComment = $state(false);
	let showExportModal = $state(false);
	let exportedMarkdown = $state('');

	// Parse the JSON content
	$effect(() => {
		try {
			redditThread = JSON.parse(narrative.content);
			// Initially expand all top-level comments
			if (redditThread) {
				redditThread.comments.forEach(comment => {
					if (comment.depth === 1) {
						expandedComments.add(comment.id);
					}
				});
			}
		} catch (e) {
			console.error('[RedditThreadView] Failed to parse reddit thread:', e);
			toast.error('Failed to parse Reddit thread content');
		}
	});

	function toggleComment(commentId: string) {
		if (expandedComments.has(commentId)) {
			expandedComments.delete(commentId);
		} else {
			expandedComments.add(commentId);
		}
		expandedComments = new Set(expandedComments);
	}

	function getRoleColor(role: string | null): string {
		if (!role) return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300';
		const colors: Record<string, string> = {
			SKEPTIC: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
			IMPLEMENTER: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
			REVIEWER_2: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
			ELI5: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
			RELATED_PAPER: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-300',
			COMPARATIVE_CRITIC: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
			ALTERNATIVE_VIEW: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-300'
		};
		return colors[role] || 'bg-neutral-100 text-neutral-800 dark:bg-neutral-700 dark:text-neutral-300';
	}

	function getRoleLabel(role: string | null): string {
		if (!role) return 'OP';
		const labels: Record<string, string> = {
			SKEPTIC: 'Skeptic',
			IMPLEMENTER: 'Implementer',
			REVIEWER_2: 'Reviewer #2',
			ELI5: 'ELI5',
			RELATED_PAPER: 'Related Papers',
			COMPARATIVE_CRITIC: 'Comparative',
			ALTERNATIVE_VIEW: 'Alternative View'
		};
		return labels[role] || role;
	}

	function getCommentReplies(commentId: string): RedditPost[] {
		if (!redditThread) return [];
		return redditThread.comments.filter(c => c.parentId === commentId);
	}

	async function submitUserComment() {
		if (!userCommentText.trim() || isSubmittingComment) return;

		isSubmittingComment = true;
		try {
			const response = await api.addRedditComment(paperId, {
				userComment: userCommentText,
				parentId: replyingToId
			});

			if (response.success && response.data) {
				// Update the thread with the new comments
				redditThread = response.data.thread;
				userCommentText = '';
				replyingToId = null;
				toast.success('Comment added successfully!');
			} else {
				const errorMsg = typeof response.error === 'string' ? response.error : response.error?.message || 'Failed to add comment';
				toast.error(errorMsg);
			}
		} catch (e) {
			console.error('[RedditThreadView] Failed to add comment:', e);
			toast.error('Failed to add comment');
		} finally {
			isSubmittingComment = false;
		}
	}

	async function exportToMarkdown() {
		try {
			const response = await api.exportRedditMarkdown(paperId, language);
			if (response.success && response.data) {
				exportedMarkdown = response.data.markdown;
				showExportModal = true;
			} else {
				const errorMsg = typeof response.error === 'string' ? response.error : response.error?.message || 'Failed to export';
				toast.error(errorMsg);
			}
		} catch (e) {
			console.error('[RedditThreadView] Failed to export:', e);
			toast.error('Failed to export to markdown');
		}
	}

	function copyMarkdown() {
		navigator.clipboard.writeText(exportedMarkdown);
		toast.success('Copied to clipboard!');
	}

	function formatMarkdown(content: string): string {
		// Simple markdown to HTML conversion
		return content
			.replace(/^### (.*$)/gim, '<h3>$1</h3>')
			.replace(/^## (.*$)/gim, '<h2>$1</h2>')
			.replace(/^# (.*$)/gim, '<h1>$1</h1>')
			.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
			.replace(/\*(.*?)\*/g, '<em>$1</em>')
			.replace(/\n\n/g, '</p><p>')
			.replace(/!\[(.*?)\]\((.*?)\)/g, '<img src="$2" alt="$1" class="max-w-full h-auto rounded-lg my-4" />')
			.replace(/\[Claim #(\d+)\]/g, '<span class="claim-ref">Claim #$1</span>')
			.replace(/\n/g, '<br />');
	}
</script>

{#if redditThread}
	<div class="reddit-thread-container mx-auto max-w-4xl">
		<!-- Export Button -->
		<div class="mb-4 flex justify-end">
			<button
				class="flex items-center gap-2 rounded-md bg-neutral-200 px-3 py-1.5 text-sm text-neutral-700 hover:bg-neutral-300 dark:bg-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-600"
				onclick={exportToMarkdown}
			>
				<Download class="h-4 w-4" />
				Export to Markdown
			</button>
		</div>

		<!-- Original Post -->
		<div class="reddit-post op mb-6 rounded-lg border border-neutral-300 bg-white p-6 shadow-sm dark:border-neutral-600 dark:bg-neutral-800">
			<div class="mb-3 flex items-center gap-3">
				<div class="flex items-center gap-2">
					<span class="rounded-full bg-purple-500 px-2 py-1 text-xs font-bold text-white">OP</span>
					<span class="text-sm font-medium text-neutral-700 dark:text-neutral-300">
						u/{redditThread.originalPost.author}
					</span>
				</div>
				<div class="ml-auto flex items-center gap-1 text-sm">
					<ChevronUp class="h-4 w-4 text-orange-500" />
					<span class="font-semibold text-orange-500">{redditThread.originalPost.score}</span>
				</div>
			</div>
			<div class="prose prose-sm prose-neutral dark:prose-invert max-w-none">
				{@html formatMarkdown(redditThread.originalPost.content)}
			</div>
		</div>

		<!-- Comments -->
		<div class="space-y-4">
			{#each redditThread.comments.filter(c => c.depth === 1) as comment (comment.id)}
				<div class="reddit-comment rounded-lg border border-neutral-200 bg-neutral-50 dark:border-neutral-600 dark:bg-neutral-800/50">
					<div class="p-4">
						<div class="mb-2 flex items-center gap-2">
							<span class="rounded px-2 py-0.5 text-xs font-medium {getRoleColor(comment.role)}">
								{getRoleLabel(comment.role)}
							</span>
							<span class="text-sm text-neutral-600 dark:text-neutral-400">
								u/{comment.author}
							</span>
							<div class="ml-auto flex items-center gap-1 text-sm">
								<ChevronUp class="h-4 w-4 text-neutral-500" />
								<span class="font-medium text-neutral-600 dark:text-neutral-400">{comment.score}</span>
							</div>
						</div>
						<div class="prose prose-sm prose-neutral dark:prose-invert max-w-none">
							{@html formatMarkdown(comment.content)}
						</div>

						<!-- Reply button -->
						<button
							class="mt-2 text-xs text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
							onclick={() => {
								replyingToId = comment.id;
								toggleComment(comment.id);
							}}
						>
							<MessageSquare class="inline h-3 w-3 mr-1" />
							Reply
						</button>
					</div>

					<!-- Replies -->
					{#if expandedComments.has(comment.id)}
						{@const replies = getCommentReplies(comment.id)}
						{#if replies.length > 0}
							<div class="border-t border-neutral-200 bg-white p-4 dark:border-neutral-600 dark:bg-neutral-800">
								{#each replies as reply (reply.id)}
									<div class="mb-3 rounded-lg border-l-2 border-purple-300 bg-neutral-50 p-3 dark:border-purple-600 dark:bg-neutral-700/50">
										<div class="mb-2 flex items-center gap-2">
											<span class="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">
												OP
											</span>
											<span class="text-sm text-neutral-600 dark:text-neutral-400">
												u/{reply.author}
											</span>
											<div class="ml-auto flex items-center gap-1 text-sm">
												<ChevronUp class="h-4 w-4 text-neutral-500" />
												<span class="font-medium text-neutral-600 dark:text-neutral-400">{reply.score}</span>
											</div>
										</div>
										<div class="prose prose-sm prose-neutral dark:prose-invert max-w-none">
											{@html formatMarkdown(reply.content)}
										</div>
									</div>
								{/each}
							</div>
						{/if}
					{/if}
				</div>
			{/each}
		</div>

		<!-- User Comment Form -->
		<div class="mt-8 rounded-lg border border-neutral-300 bg-white p-4 dark:border-neutral-600 dark:bg-neutral-800">
			<h3 class="mb-3 text-sm font-semibold text-neutral-700 dark:text-neutral-300">
				{replyingToId ? 'Reply to comment' : 'Add your comment'}
			</h3>
			<textarea
				bind:value={userCommentText}
				placeholder="What are your thoughts?"
				class="w-full resize-none rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 placeholder-neutral-400 focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500 dark:border-neutral-600 dark:bg-neutral-700 dark:text-white dark:placeholder-neutral-500"
				rows="4"
			></textarea>
			<div class="mt-2 flex justify-between">
				{#if replyingToId}
					<button
						class="text-xs text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
						onclick={() => {
							replyingToId = null;
						}}
					>
						Cancel reply
					</button>
				{:else}
					<div></div>
				{/if}
				<button
					class="rounded-md bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
					onclick={submitUserComment}
					disabled={!userCommentText.trim() || isSubmittingComment}
				>
					{isSubmittingComment ? 'Submitting...' : 'Comment'}
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- Export Modal -->
{#if showExportModal}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
		role="dialog"
		aria-modal="true"
		tabindex="-1"
		onclick={(e) => {
			if (e.target === e.currentTarget) showExportModal = false;
		}}
		onkeydown={(e) => {
			if (e.key === 'Escape') showExportModal = false;
		}}
	>
		<div class="max-h-[80vh] w-full max-w-3xl rounded-lg bg-white p-6 shadow-xl dark:bg-neutral-800">
			<h2 class="mb-4 text-xl font-bold text-neutral-900 dark:text-white">Reddit Markdown Export</h2>
			<pre class="mb-4 max-h-96 overflow-auto rounded-md bg-neutral-100 p-4 text-sm dark:bg-neutral-900"><code>{exportedMarkdown}</code></pre>
			<div class="flex justify-end gap-2">
				<button
					class="rounded-md bg-neutral-200 px-4 py-2 text-sm text-neutral-700 hover:bg-neutral-300 dark:bg-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-600"
					onclick={() => showExportModal = false}
				>
					Close
				</button>
				<button
					class="rounded-md bg-purple-600 px-4 py-2 text-sm text-white hover:bg-purple-700"
					onclick={copyMarkdown}
				>
					Copy to Clipboard
				</button>
			</div>
		</div>
	</div>
{/if}

<style>
	:global(.claim-ref) {
		background: #fef3c7;
		padding: 2px 6px;
		border-radius: 4px;
		font-size: 0.875em;
		font-weight: 500;
		color: #92400e;
		cursor: help;
	}

	:global(.dark .claim-ref) {
		background: #78350f;
		color: #fde68a;
	}

	.reddit-thread-container {
		font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
	}
</style>
