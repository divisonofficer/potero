/**
 * Known venue abbreviations
 * Maps full venue names to their commonly used abbreviations
 */
const KNOWN_ABBREVIATIONS: Record<string, string> = {
	// IEEE Transactions
	'IEEE Transactions on Pattern Analysis and Machine Intelligence': 'TPAMI',
	'IEEE Transactions on Image Processing': 'TIP',
	'IEEE Transactions on Neural Networks and Learning Systems': 'TNNLS',
	'IEEE Transactions on Intelligent Transportation Systems': 'TITS',
	'IEEE Transactions on Knowledge and Data Engineering': 'TKDE',
	'IEEE Transactions on Visualization and Computer Graphics': 'TVCG',
	'IEEE Transactions on Information Forensics and Security': 'TIFS',
	'IEEE Transactions on Multimedia': 'TMM',
	'IEEE Transactions on Cybernetics': 'TCYB',
	'IEEE Transactions on Medical Imaging': 'TMI',
	'IEEE Transactions on Signal Processing': 'TSP',
	'IEEE Transactions on Circuits and Systems for Video Technology': 'TCSVT',
	'IEEE Transactions on Automatic Control': 'TAC',
	'IEEE Transactions on Robotics': 'TRO',
	'IEEE Transactions on Software Engineering': 'TSE',
	'IEEE Transactions on Parallel and Distributed Systems': 'TPDS',
	'IEEE Transactions on Geoscience and Remote Sensing': 'TGRS',
	'IEEE Transactions on Industrial Electronics': 'TIE',
	'IEEE Transactions on Wireless Communications': 'TWC',
	'IEEE Transactions on Communications': 'TCOM',

	// Top Conferences - Computer Vision
	'Conference on Computer Vision and Pattern Recognition': 'CVPR',
	'IEEE Conference on Computer Vision and Pattern Recognition': 'CVPR',
	'IEEE/CVF Conference on Computer Vision and Pattern Recognition': 'CVPR',
	'International Conference on Computer Vision': 'ICCV',
	'IEEE International Conference on Computer Vision': 'ICCV',
	'IEEE/CVF International Conference on Computer Vision': 'ICCV',
	'European Conference on Computer Vision': 'ECCV',

	// Top Conferences - Machine Learning
	'Neural Information Processing Systems': 'NeurIPS',
	'Advances in Neural Information Processing Systems': 'NeurIPS',
	'Conference on Neural Information Processing Systems': 'NeurIPS',
	'International Conference on Machine Learning': 'ICML',
	'International Conference on Learning Representations': 'ICLR',
	'AAAI Conference on Artificial Intelligence': 'AAAI',
	'International Joint Conference on Artificial Intelligence': 'IJCAI',

	// Top Conferences - NLP
	'Annual Meeting of the Association for Computational Linguistics': 'ACL',
	'Conference on Empirical Methods in Natural Language Processing': 'EMNLP',
	'North American Chapter of the Association for Computational Linguistics': 'NAACL',

	// Top Conferences - Systems
	'USENIX Symposium on Operating Systems Design and Implementation': 'OSDI',
	'ACM Symposium on Operating Systems Principles': 'SOSP',
	'ACM SIGCOMM Conference': 'SIGCOMM',
	'USENIX Annual Technical Conference': 'USENIX ATC',

	// Top Conferences - Data
	'ACM SIGMOD International Conference on Management of Data': 'SIGMOD',
	'International Conference on Very Large Data Bases': 'VLDB',
	'ACM SIGKDD Conference on Knowledge Discovery and Data Mining': 'KDD',
	'IEEE International Conference on Data Engineering': 'ICDE',

	// Top Conferences - HCI
	'ACM Conference on Human Factors in Computing Systems': 'CHI',
	'ACM Symposium on User Interface Software and Technology': 'UIST',

	// Top Conferences - Graphics
	'ACM SIGGRAPH': 'SIGGRAPH',
	'ACM SIGGRAPH Asia': 'SIGGRAPH Asia',

	// Top Conferences - Robotics
	'IEEE International Conference on Robotics and Automation': 'ICRA',
	'IEEE/RSJ International Conference on Intelligent Robots and Systems': 'IROS',
	'Robotics: Science and Systems': 'RSS',
	'Conference on Robot Learning': 'CoRL',

	// Top Conferences - Security
	'IEEE Symposium on Security and Privacy': 'S&P',
	'USENIX Security Symposium': 'USENIX Security',
	'ACM Conference on Computer and Communications Security': 'CCS',
	'Network and Distributed System Security Symposium': 'NDSS',

	// Journals - General Science
	'Nature': 'Nature',
	'Science': 'Science',
	'Nature Communications': 'Nat. Commun.',
	'Nature Machine Intelligence': 'Nat. Mach. Intell.',
	'Science Robotics': 'Sci. Robot.',

	// Journals - CS
	'Journal of Machine Learning Research': 'JMLR',
	'Artificial Intelligence': 'AIJ',
	'International Journal of Computer Vision': 'IJCV',
	'ACM Computing Surveys': 'CSUR',
	'Communications of the ACM': 'CACM',

	// arXiv
	'arXiv preprint': 'arXiv',
	'arXiv': 'arXiv'
};

/**
 * Normalize venue name for matching
 */
function normalizeVenue(venue: string): string {
	return venue
		.toLowerCase()
		.replace(/[^a-z0-9\s]/g, '')
		.replace(/\s+/g, ' ')
		.trim();
}

/**
 * Get abbreviation for a venue
 * First tries known abbreviations, then generates one automatically
 */
export function getVenueAbbreviation(venue: string | null | undefined): string | null {
	if (!venue) return null;

	const trimmed = venue.trim();

	// Check known abbreviations (case-insensitive)
	for (const [fullName, abbr] of Object.entries(KNOWN_ABBREVIATIONS)) {
		if (normalizeVenue(fullName) === normalizeVenue(trimmed)) {
			return abbr;
		}

		// Also check if venue contains the full name
		if (normalizeVenue(trimmed).includes(normalizeVenue(fullName))) {
			return abbr;
		}
	}

	// Check if venue is already short (likely already an abbreviation)
	if (trimmed.length <= 10 && !trimmed.includes(' ')) {
		return trimmed;
	}

	// Generate abbreviation automatically
	return generateAbbreviation(trimmed);
}

/**
 * Generate an abbreviation from a venue name
 * Uses common academic abbreviation patterns
 */
function generateAbbreviation(venue: string): string {
	// Words to skip in abbreviations
	const skipWords = new Set([
		'on',
		'of',
		'the',
		'and',
		'in',
		'for',
		'to',
		'a',
		'an',
		'with',
		'proceedings',
		'conference',
		'symposium',
		'workshop',
		'journal',
		'transactions',
		'annual',
		'international'
	]);

	// Words to replace with standard abbreviations
	const wordAbbreviations: Record<string, string> = {
		ieee: 'IEEE',
		acm: 'ACM',
		artificial: 'Art.',
		intelligence: 'Intell.',
		computer: 'Comp.',
		computing: 'Comp.',
		computational: 'Comp.',
		learning: 'Learn.',
		machine: 'Mach.',
		neural: 'Neur.',
		network: 'Net.',
		networks: 'Net.',
		vision: 'Vis.',
		visual: 'Vis.',
		pattern: 'Patt.',
		recognition: 'Recog.',
		processing: 'Proc.',
		systems: 'Sys.',
		system: 'Sys.',
		analysis: 'Anal.',
		engineering: 'Eng.',
		science: 'Sci.',
		scientific: 'Sci.',
		data: 'Data',
		database: 'DB',
		information: 'Inf.',
		knowledge: 'Know.',
		language: 'Lang.',
		natural: 'Nat.',
		robotics: 'Robot.',
		automation: 'Auto.',
		graphics: 'Graph.',
		multimedia: 'MM',
		security: 'Sec.',
		software: 'Soft.',
		hardware: 'Hardw.',
		communication: 'Comm.',
		communications: 'Comm.',
		medical: 'Med.',
		image: 'Image',
		imaging: 'Imag.',
		signal: 'Sig.',
		european: 'Eur.',
		american: 'Am.',
		asian: 'Asian',
		pacific: 'Pac.'
	};

	const words = venue.split(/\s+/);

	// Try to extract meaningful initials
	const parts: string[] = [];

	for (const word of words) {
		const lowerWord = word.toLowerCase().replace(/[^a-z0-9]/g, '');

		if (skipWords.has(lowerWord)) {
			continue;
		}

		if (wordAbbreviations[lowerWord]) {
			parts.push(wordAbbreviations[lowerWord]);
		} else if (lowerWord.length > 0) {
			// Take first letter uppercase
			parts.push(lowerWord.charAt(0).toUpperCase());
		}
	}

	const result = parts.join('');

	// If result is too short or just initials, try a different approach
	if (result.length <= 2 || (result.length <= 4 && !result.includes('.'))) {
		// Take first letters of major words
		const initials = words
			.filter((w) => !skipWords.has(w.toLowerCase().replace(/[^a-z]/g, '')))
			.map((w) => w.charAt(0).toUpperCase())
			.join('');

		return initials.length > 1 ? initials : result;
	}

	return result;
}

/**
 * Format venue for display - shows abbreviation with tooltip for full name
 * Returns both the display text and full name
 */
export function formatVenue(venue: string | null | undefined): {
	display: string;
	full: string | null;
} {
	if (!venue) return { display: '', full: null };

	const abbr = getVenueAbbreviation(venue);

	if (abbr && abbr !== venue) {
		return { display: abbr, full: venue };
	}

	return { display: venue, full: null };
}
