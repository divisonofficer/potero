<script lang="ts">
	import { BookOpen, Search, FileText, Sparkles } from 'lucide-svelte';
</script>

<div class="welcome-step">
	<div class="logo-container">
		<div class="logo">
			<BookOpen size={48} />
		</div>
	</div>

	<h1 class="title">Welcome to Potero</h1>
	<p class="subtitle">Your AI-powered research paper manager</p>

	<div class="features">
		<div class="feature">
			<div class="feature-icon">
				<FileText size={24} />
			</div>
			<div class="feature-text">
				<h3>PDF Management</h3>
				<p>Import, organize, and read your research papers</p>
			</div>
		</div>

		<div class="feature">
			<div class="feature-icon">
				<Search size={24} />
			</div>
			<div class="feature-text">
				<h3>Smart Search</h3>
				<p>Search across multiple academic databases</p>
			</div>
		</div>

		<div class="feature">
			<div class="feature-icon">
				<Sparkles size={24} />
			</div>
			<div class="feature-text">
				<h3>AI Assistant</h3>
				<p>Chat with your papers using POSTECH GenAI</p>
			</div>
		</div>
	</div>

	<p class="setup-note">
		Let's set up your account in a few quick steps.
	</p>
</div>

<style>
	.welcome-step {
		text-align: center;
		padding: 2rem 1rem;
		animation: fadeIn 0.5s ease;
	}

	@keyframes fadeIn {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.logo-container {
		margin-bottom: 1.5rem;
	}

	.logo {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 100px;
		height: 100px;
		border-radius: 24px;
		background: linear-gradient(135deg, #0066cc, #0044aa);
		color: white;
		box-shadow: 0 8px 32px rgba(0, 102, 204, 0.3);
	}

	.title {
		font-size: 2rem;
		font-weight: 700;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.5rem;
	}

	.subtitle {
		font-size: 1.1rem;
		color: var(--color-text-secondary, #666);
		margin: 0 0 2rem;
	}

	.features {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		max-width: 400px;
		margin: 0 auto 2rem;
		text-align: left;
	}

	.feature {
		display: flex;
		align-items: flex-start;
		gap: 1rem;
		padding: 1rem;
		background: var(--color-surface-secondary, #f8f9fa);
		border-radius: 12px;
		transition: transform 0.2s ease;
	}

	.feature:hover {
		transform: translateX(4px);
	}

	.feature-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 48px;
		height: 48px;
		border-radius: 12px;
		background: white;
		color: var(--color-primary, #0066cc);
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
		flex-shrink: 0;
	}

	.feature-text h3 {
		font-size: 0.95rem;
		font-weight: 600;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.25rem;
	}

	.feature-text p {
		font-size: 0.85rem;
		color: var(--color-text-secondary, #666);
		margin: 0;
	}

	.setup-note {
		font-size: 0.95rem;
		color: var(--color-text-secondary, #666);
		margin: 0;
	}
</style>
