<script lang="ts">
	import { api, type ChatFileAttachment } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';
	import type { Paper } from '$lib/types';
	import {
		Paperclip,
		X,
		MessageSquare,
		Maximize2,
		Square,
		ChevronDown,
		Plus,
		Trash2
	} from 'lucide-svelte';
	import { generateUUID } from '$lib/utils/uuid';
	import { onMount } from 'svelte';

	interface Props {
		paper: Paper | null;
		onClose: () => void;
	}

	interface ChatMessage {
		id: string;
		role: 'user' | 'assistant';
		content: string;
		isLoading?: boolean;
	}

	interface ChatSession {
		id: string;
		paperId: string | null;
		title: string;
		messageCount: number;
		lastMessage: string | null;
	}

	type PanelSize = 'small' | 'medium' | 'large';

	let { paper, onClose }: Props = $props();

	let messages: ChatMessage[] = $state([]);
	let inputText = $state('');
	let isLoading = $state(false);
	let sessionId = $state<string | null>(null);
	let sessions: ChatSession[] = $state([]);
	let showSessionDropdown = $state(false);
	let messagesContainer: HTMLDivElement | null = $state(null);
	let attachedFiles: ChatFileAttachment[] = $state([]);
	let isUploading = $state(false);
	let fileInput: HTMLInputElement;

	// Panel state
	let panelSize: PanelSize = $state('medium');
	let position = $state({ x: 0, y: 0 });
	let isDragging = $state(false);
	let dragOffset = { x: 0, y: 0 };

	// Size configurations
	const sizeConfig = {
		small: { width: 400, height: 600 },
		medium: { width: 700, height: 800 },
		large: { width: '90vw', height: '90vh' }
	};

	// Auto-scroll to bottom when new messages arrive
	$effect(() => {
		if (messages.length > 0 && messagesContainer) {
			messagesContainer.scrollTop = messagesContainer.scrollHeight;
		}
	});

	// Load sessions and restore last session when paper changes
	$effect(() => {
		loadSessions();
	});

	// Computed: Current session info
	const currentSession = $derived(sessions.find((s) => s.id === sessionId));

	// Load saved preferences
	onMount(() => {
		const savedSize = localStorage.getItem('chatPanelSize') as PanelSize | null;

		if (savedSize && ['small', 'medium', 'large'].includes(savedSize)) {
			panelSize = savedSize;
		}

		// Position based on size
		if (panelSize === 'large') {
			// For large, try to restore saved position or center
			const savedPosition = localStorage.getItem('chatPanelPosition-large');
			if (savedPosition) {
				try {
					position = JSON.parse(savedPosition);
				} catch {
					centerPanel();
				}
			} else {
				centerPanel();
			}
		} else {
			// For small/medium, always position at bottom right
			positionBottomRight();
		}

		// Load sessions on mount
		loadSessions();
	});

	async function loadSessions() {
		try {
			const response = await api.getChatSessions(paper?.id);
			if (response.success && response.data) {
				sessions = response.data;

				// Restore last session for this paper/global
				const storageKey = paper ? `chat-session-paper-${paper.id}` : 'chat-session-global';
				const savedSessionId = localStorage.getItem(storageKey);

				if (savedSessionId && sessions.find((s) => s.id === savedSessionId)) {
					// Restore saved session
					await switchToSession(savedSessionId);
				} else if (sessions.length > 0) {
					// Use the most recent session
					await switchToSession(sessions[0].id);
				}
			}
		} catch (error) {
			console.error('[Chat] Failed to load sessions:', error);
		}
	}

	async function switchToSession(newSessionId: string) {
		try {
			sessionId = newSessionId;

			// Save as last session
			const storageKey = paper ? `chat-session-paper-${paper.id}` : 'chat-session-global';
			localStorage.setItem(storageKey, newSessionId);

			// Load message history
			const response = await api.getChatHistory(newSessionId);
			if (response.success && response.data) {
				messages = response.data.map((msg) => ({
					id: msg.id || generateUUID(),
					role: msg.role as 'user' | 'assistant',
					content: msg.content
				}));
			}

			showSessionDropdown = false;
		} catch (error) {
			console.error('[Chat] Failed to switch session:', error);
			toast.show('Failed to load chat history', 'error');
		}
	}

	async function createNewSession() {
		try {
			const title = paper ? `Chat about ${paper.title}` : 'New Chat';
			const response = await api.createChatSession(title, paper?.id);

			if (response.success && response.data) {
				const newSession = response.data;
				sessions = [newSession, ...sessions];
				await switchToSession(newSession.id);
				toast.show('New chat session created', 'success');
			} else {
				toast.show('Failed to create new session', 'error');
			}
		} catch (error) {
			console.error('[Chat] Failed to create session:', error);
			toast.show('Failed to create new session', 'error');
		}
	}

	async function deleteSession(sessionIdToDelete: string) {
		try {
			const response = await api.deleteChatSession(sessionIdToDelete);
			if (response.success) {
				sessions = sessions.filter((s) => s.id !== sessionIdToDelete);

				// If we deleted the current session, switch to another one
				if (sessionId === sessionIdToDelete) {
					if (sessions.length > 0) {
						await switchToSession(sessions[0].id);
					} else {
						sessionId = null;
						messages = [];
					}
				}

				toast.show('Chat session deleted', 'success');
			} else {
				toast.show('Failed to delete session', 'error');
			}
		} catch (error) {
			console.error('[Chat] Failed to delete session:', error);
			toast.show('Failed to delete session', 'error');
		}
	}

	function centerPanel() {
		const config = sizeConfig[panelSize];
		const width = typeof config.width === 'string' ? window.innerWidth * 0.9 : config.width;
		const height = typeof config.height === 'string' ? window.innerHeight * 0.9 : config.height;

		position = {
			x: (window.innerWidth - width) / 2,
			y: (window.innerHeight - height) / 2
		};
	}

	function positionBottomRight() {
		const config = sizeConfig[panelSize];
		const width = typeof config.width === 'string' ? window.innerWidth * 0.9 : config.width;
		const height = typeof config.height === 'string' ? window.innerHeight * 0.9 : config.height;

		position = {
			x: window.innerWidth - width - 20,
			y: window.innerHeight - height - 20
		};
	}

	function changeSize(newSize: PanelSize) {
		panelSize = newSize;
		localStorage.setItem('chatPanelSize', newSize);

		// Large size goes to center, others to bottom right
		if (newSize === 'large') {
			centerPanel();
		} else {
			positionBottomRight();
		}
	}

	function handleMouseDown(e: MouseEvent) {
		if ((e.target as HTMLElement).closest('.no-drag')) return;

		isDragging = true;
		dragOffset = {
			x: e.clientX - position.x,
			y: e.clientY - position.y
		};
	}

	function handleMouseMove(e: MouseEvent) {
		if (!isDragging) return;

		position = {
			x: e.clientX - dragOffset.x,
			y: e.clientY - dragOffset.y
		};

		// Only save position for large size
		if (panelSize === 'large') {
			localStorage.setItem('chatPanelPosition-large', JSON.stringify(position));
		}
	}

	function handleMouseUp() {
		isDragging = false;
	}

	async function handleFileSelect(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (!files || files.length === 0) return;

		isUploading = true;
		for (const file of Array.from(files)) {
			const result = await api.uploadChatFile(file);
			if (result.success && result.data) {
				attachedFiles = [...attachedFiles, ...result.data.files];
				toast.success(`Attached ${file.name}`);
			} else {
				toast.error(`Failed to upload ${file.name}`);
			}
		}
		isUploading = false;
		target.value = '';
	}

	function removeFile(index: number) {
		attachedFiles = attachedFiles.filter((_, i) => i !== index);
	}

	async function sendMessage() {
		const text = inputText.trim();
		if (!text || isLoading) return;

		inputText = '';
		isLoading = true;

		// Add user message immediately
		const userMessage: ChatMessage = {
			id: generateUUID(),
			role: 'user',
			content: text
		};
		messages = [...messages, userMessage];

		// Add streaming placeholder for assistant
		const assistantId = generateUUID();
		messages = [
			...messages,
			{
				id: assistantId,
				role: 'assistant',
				content: '',
				isLoading: true
			}
		];

		let accumulatedContent = '';
		let finalMessageId = assistantId;

		try {
			await api.sendMessageStream(text, paper?.id, sessionId ?? undefined, (event) => {
				switch (event.type) {
					case 'session':
						sessionId = (event.data as { sessionId: string }).sessionId;
						break;

					case 'start':
						break;

					case 'delta':
						accumulatedContent = (event.data as { content: string }).content;
						messages = messages.map((msg) =>
							msg.id === assistantId
								? { ...msg, content: accumulatedContent, isLoading: true }
								: msg
						);
						break;

					case 'tool_call':
						const toolName = (event.data as { toolName: string }).toolName;
						console.log('[Chat] Tool call:', toolName);
						break;

					case 'tool_result':
						const result = event.data as {
							toolName: string;
							success: boolean;
							error?: string;
						};
						console.log('[Chat] Tool result:', result);
						break;

					case 'done':
						const doneData = event.data as {
							messageId: string;
							content: string;
							toolExecutions: unknown[];
						};
						finalMessageId = doneData.messageId;
						accumulatedContent = doneData.content;

						messages = messages.map((msg) =>
							msg.id === assistantId
								? {
										id: finalMessageId,
										role: 'assistant' as const,
										content: accumulatedContent,
										isLoading: false
									}
								: msg
						);

						attachedFiles = [];
						isLoading = false;
						break;

					case 'error':
						const errorMsg = (event.data as { message: string }).message;
						console.error('[Chat] Error:', errorMsg);
						toast.error(errorMsg);

						messages = messages.filter((msg) => msg.id !== assistantId);
						isLoading = false;
						break;
				}
			});
		} catch (error) {
			console.error('[Chat] Stream error:', error);
			messages = messages.filter((msg) => msg.id !== assistantId);
			toast.error('Failed to send message. Is the API key configured in Settings?');
			isLoading = false;
		}
	}

	function handleKeyDown(event: KeyboardEvent) {
		if (event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			sendMessage();
		}
	}

	function clearChat() {
		messages = [];
		sessionId = null;
	}

	const suggestedQuestions = $derived(
		paper
			? [
					'What is the main contribution of this paper?',
					'Summarize the methodology used.',
					'What are the key findings?',
					'What are the limitations mentioned?'
				]
			: ['Ask me anything about research papers.']
	);

	// Current size config
	const currentSize = $derived(sizeConfig[panelSize]);
	const widthStyle = $derived(
		typeof currentSize.width === 'string' ? currentSize.width : `${currentSize.width}px`
	);
	const heightStyle = $derived(
		typeof currentSize.height === 'string' ? currentSize.height : `${currentSize.height}px`
	);
</script>

<svelte:window
	onmousemove={handleMouseMove}
	onmouseup={handleMouseUp}
/>

<div
	class="fixed bg-white/95 dark:bg-gray-900/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-gray-200 dark:border-gray-700 overflow-hidden flex flex-col z-50"
	style="left: {position.x}px; top: {position.y}px; width: {widthStyle}; height: {heightStyle}; cursor: {isDragging ? 'grabbing' : 'default'};"
>
	<!-- Header -->
	<div class="bg-gradient-to-r from-blue-600 to-indigo-600">
		<!-- Title row -->
		<div
			class="px-4 py-2 flex items-center justify-between cursor-grab active:cursor-grabbing"
			onmousedown={handleMouseDown}
		>
			<div class="flex items-center gap-2 flex-1 min-w-0 overflow-hidden">
				<MessageSquare class="w-4 h-4 text-white shrink-0" />
				<h3 class="font-semibold text-white shrink-0">Chat with Paper</h3>
				{#if paper}
					<span class="text-xs text-white/70 truncate">{paper.title}</span>
				{/if}
			</div>

			<div class="flex items-center gap-1 no-drag shrink-0">
			<!-- Size buttons -->
			<button
				onclick={() => changeSize('small')}
				class="p-1.5 hover:bg-white/20 rounded transition-colors {panelSize === 'small'
					? 'bg-white/30'
					: ''}"
				title="Small"
			>
				<div class="w-3 h-3 border border-white rounded"></div>
			</button>
			<button
				onclick={() => changeSize('medium')}
				class="p-1.5 hover:bg-white/20 rounded transition-colors {panelSize === 'medium'
					? 'bg-white/30'
					: ''}"
				title="Medium"
			>
				<Square class="w-3.5 h-3.5 text-white" />
			</button>
			<button
				onclick={() => changeSize('large')}
				class="p-1.5 hover:bg-white/20 rounded transition-colors {panelSize === 'large'
					? 'bg-white/30'
					: ''}"
				title="Large"
			>
				<Maximize2 class="w-3.5 h-3.5 text-white" />
			</button>

				<!-- Close button -->
				<button
					onclick={onClose}
					class="p-1.5 hover:bg-white/20 rounded transition-colors"
					title="Close"
				>
					<X class="w-4 h-4 text-white" />
				</button>
			</div>
		</div>

		<!-- Session row -->
		<div class="px-4 pb-2 flex items-center gap-2 no-drag">
			<!-- Session dropdown -->
			<div class="relative flex-1">
				<button
					onclick={() => (showSessionDropdown = !showSessionDropdown)}
					class="w-full px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded text-left text-sm text-white flex items-center justify-between transition-colors"
				>
					<span class="truncate">
						{currentSession?.title || 'No session'}
						{#if currentSession}
							<span class="text-xs text-white/60 ml-2">
								({currentSession.messageCount} messages)
							</span>
						{/if}
					</span>
					<ChevronDown class="w-4 h-4 shrink-0" />
				</button>

				{#if showSessionDropdown}
					<div
						class="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 rounded-lg shadow-xl border border-gray-200 dark:border-gray-700 max-h-60 overflow-y-auto z-10"
					>
						{#each sessions as session (session.id)}
							<button
								onclick={() => switchToSession(session.id)}
								class="w-full px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center justify-between {session.id ===
								sessionId
									? 'bg-blue-50 dark:bg-blue-900/20'
									: ''}"
							>
								<div class="flex-1 min-w-0">
									<div class="truncate font-medium">{session.title}</div>
									{#if session.lastMessage}
										<div class="text-xs text-muted-foreground truncate">
											{session.lastMessage}
										</div>
									{/if}
								</div>
								<span class="text-xs text-muted-foreground shrink-0 ml-2">
									{session.messageCount}
								</span>
							</button>
						{/each}
					</div>
				{/if}
			</div>

			<!-- New session button -->
			<button
				onclick={createNewSession}
				class="px-3 py-1.5 bg-white/10 hover:bg-white/20 rounded text-sm text-white flex items-center gap-1 transition-colors shrink-0"
				title="New chat"
			>
				<Plus class="w-4 h-4" />
			</button>
		</div>
	</div>

	<!-- Messages -->
	<div class="flex-1 overflow-auto p-4" bind:this={messagesContainer}>
			{#if messages.length === 0}
				<div class="flex flex-col items-center justify-center py-8 text-center">
					<MessageSquare class="w-12 h-12 text-gray-300 dark:text-gray-600 mb-3" />
					<p class="mb-4 text-sm text-muted-foreground">Ask questions about this paper</p>

					<!-- Suggested questions -->
					<div class="flex flex-col gap-2 w-full">
						{#each suggestedQuestions.slice(0, 3) as question}
							<button
								class="rounded-lg border px-3 py-2 text-left text-xs transition-colors hover:bg-muted"
								onclick={() => {
									inputText = question;
									sendMessage();
								}}
							>
								{question}
							</button>
						{/each}
					</div>
				</div>
			{:else}
				<div class="space-y-4">
					{#each messages as message (message.id)}
						<div class="flex gap-3 {message.role === 'user' ? 'flex-row-reverse' : ''}">
							<!-- Avatar -->
							<div
								class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full {message.role ===
								'user'
									? 'bg-blue-600 text-white'
									: 'bg-gray-200 dark:bg-gray-700'}"
							>
								{#if message.role === 'user'}
									<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
										<path
											d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"
										/>
									</svg>
								{:else}
									<img src="/icon_character.png" alt="Assistant" class="h-full w-full rounded-full object-cover" />
								{/if}
							</div>

							<!-- Message bubble -->
							<div
								class="max-w-[85%] rounded-lg px-3 py-2 {message.role === 'user'
									? 'bg-blue-600 text-white'
									: 'bg-gray-100 dark:bg-gray-800'}"
							>
								{#if message.isLoading}
									<div class="flex items-center gap-1">
										<span class="h-2 w-2 animate-bounce rounded-full bg-current"></span>
										<span
											class="h-2 w-2 animate-bounce rounded-full bg-current"
											style="animation-delay: 0.1s"
										></span>
										<span
											class="h-2 w-2 animate-bounce rounded-full bg-current"
											style="animation-delay: 0.2s"
										></span>
									</div>
								{:else}
									<p class="whitespace-pre-wrap text-sm">{message.content}</p>
								{/if}
							</div>
						</div>
					{/each}
				</div>
			{/if}
		</div>

		<!-- Input -->
		<div class="border-t p-3">
			<!-- Hidden file input -->
			<input
				type="file"
				multiple
				bind:this={fileInput}
				onchange={handleFileSelect}
				class="hidden"
			/>

			<!-- Attached files preview -->
			{#if attachedFiles.length > 0}
				<div class="mb-2 flex flex-wrap gap-1">
					{#each attachedFiles as file, i}
						<span
							class="inline-flex items-center gap-1 rounded bg-primary/10 px-2 py-1 text-xs text-primary"
						>
							<svg
								class="h-3 w-3"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								stroke-width="2"
							>
								<path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9l-7-7z" />
								<path d="M13 2v7h7" />
							</svg>
							{file.name}
							<button
								onclick={() => removeFile(i)}
								class="ml-0.5 rounded hover:bg-primary/20"
								title="Remove file"
							>
								<X class="h-3 w-3" />
							</button>
						</span>
					{/each}
				</div>
			{/if}

			<div class="flex gap-2">
				<!-- Attach button -->
				<button
					class="shrink-0 rounded-md border p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
					onclick={() => fileInput?.click()}
					disabled={isUploading || isLoading}
					title="Attach file"
				>
					{#if isUploading}
						<svg
							class="h-4 w-4 animate-spin"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
						>
							<circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20" />
						</svg>
					{:else}
						<Paperclip class="h-4 w-4" />
					{/if}
				</button>

				<textarea
					placeholder="Ask a question..."
					bind:value={inputText}
					onkeydown={handleKeyDown}
					disabled={isLoading}
					rows="1"
					class="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-50"
				></textarea>
				<button
					class="shrink-0 rounded-md bg-blue-600 px-3 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
					disabled={isLoading || !inputText.trim()}
					onclick={sendMessage}
				>
					<svg
						class="h-4 w-4"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
					</svg>
				</button>
			</div>
			{#if !paper}
				<p class="mt-2 text-xs text-muted-foreground">
					Open a paper to get context-aware answers
				</p>
			{/if}
		</div>
</div>
