import { useState } from 'react';
import { 
  Settings as SettingsIcon, 
  Sparkles, 
  Database, 
  Globe, 
  Palette, 
  FileText, 
  Quote, 
  Clock, 
  HardDrive, 
  Network, 
  MessageSquare, 
  Keyboard, 
  Shield,
  ChevronDown,
  ChevronRight,
  Save,
  RotateCcw,
  Eye,
  EyeOff
} from 'lucide-react';
import { CustomDropdown } from './CustomDropdown';

interface SettingsProps {
  onClose?: () => void;
}

type AIProvider = 'openai' | 'claude' | 'gemini';
type SearchEngine = 'google-scholar' | 'ai-web-search';
type CitationFormat = 'apa' | 'mla' | 'chicago' | 'ieee';
type Theme = 'light' | 'dark' | 'auto';
type ViewMode = 'original' | 'blog';

interface AIModelSettings {
  summary: string;
  translation: string;
  search: string;
  knowledgeMap: string;
  chat: string;
}

export function Settings({ onClose }: SettingsProps) {
  const [activeSection, setActiveSection] = useState<string>('ai-models');
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['ai-models']));
  
  // AI Settings
  const [selectedProvider, setSelectedProvider] = useState<AIProvider>('openai');
  const [apiKeys, setApiKeys] = useState({
    openai: '',
    claude: '',
    gemini: ''
  });
  const [showApiKeys, setShowApiKeys] = useState({
    openai: false,
    claude: false,
    gemini: false
  });
  const [modelSettings, setModelSettings] = useState<AIModelSettings>({
    summary: 'gpt-4-turbo',
    translation: 'claude-3-opus',
    search: 'gemini-2.0-flash',
    knowledgeMap: 'gpt-4',
    chat: 'gpt-4-turbo'
  });

  // Database Settings
  const [dbPath, setDbPath] = useState('/Users/username/Documents/PaperDB');
  const [autoBackup, setAutoBackup] = useState(true);
  const [backupFrequency, setBackupFrequency] = useState('daily');

  // Search Engine
  const [searchEngine, setSearchEngine] = useState<SearchEngine>('google-scholar');

  // UI Settings
  const [theme, setTheme] = useState<Theme>('light');
  const [language, setLanguage] = useState('en');
  const [fontSize, setFontSize] = useState('medium');

  // PDF Viewer Settings
  const [defaultViewMode, setDefaultViewMode] = useState<ViewMode>('original');
  const [defaultZoom, setDefaultZoom] = useState(100);

  // Citation Format
  const [citationFormat, setCitationFormat] = useState<CitationFormat>('apa');

  // Storage Settings
  const [pdfDownloadPath, setPdfDownloadPath] = useState('/Users/username/Downloads/Papers');
  const [cacheSize, setCacheSize] = useState('500');

  // Knowledge Map Settings
  const [nodeSize, setNodeSize] = useState('medium');
  const [layoutAlgorithm, setLayoutAlgorithm] = useState('force-directed');

  // Chat Settings
  const [chatHistoryDays, setChatHistoryDays] = useState('30');
  const [responseLength, setResponseLength] = useState('medium');
  const [temperature, setTemperature] = useState(0.7);

  const toggleSection = (section: string) => {
    const newExpanded = new Set(expandedSections);
    if (newExpanded.has(section)) {
      newExpanded.delete(section);
    } else {
      newExpanded.add(section);
    }
    setExpandedSections(newExpanded);
  };

  const handleSave = () => {
    // Save settings logic here
    console.log('Saving settings...');
    alert('Settings saved successfully!');
  };

  const handleReset = () => {
    if (confirm('Are you sure you want to reset all settings to default?')) {
      // Reset logic here
      console.log('Resetting settings...');
    }
  };

  const modelOptions = {
    openai: [
      'gpt-4-turbo',
      'gpt-4',
      'gpt-3.5-turbo',
      'gpt-4o',
      'gpt-4o-mini'
    ],
    claude: [
      'claude-3-opus',
      'claude-3-sonnet',
      'claude-3-haiku',
      'claude-3.5-sonnet'
    ],
    gemini: [
      'gemini-2.0-flash',
      'gemini-1.5-pro',
      'gemini-1.5-flash',
      'gemini-ultra'
    ]
  };

  const sections = [
    { id: 'ai-models', icon: Sparkles, label: 'AI Models & API Keys' },
    { id: 'model-assignment', icon: Sparkles, label: 'Feature-Specific Models' },
    { id: 'database', icon: Database, label: 'Database & Backup' },
    { id: 'search', icon: Globe, label: 'Online Search Engine' },
    { id: 'ui', icon: Palette, label: 'UI & Display' },
    { id: 'viewer', icon: FileText, label: 'PDF Viewer' },
    { id: 'citation', icon: Quote, label: 'Citation Format' },
    { id: 'automation', icon: Clock, label: 'Automation' },
    { id: 'storage', icon: HardDrive, label: 'Storage & Cache' },
    { id: 'knowledge-map', icon: Network, label: 'Knowledge Map' },
    { id: 'chat', icon: MessageSquare, label: 'Chat & AI Behavior' },
    { id: 'shortcuts', icon: Keyboard, label: 'Keyboard Shortcuts' },
    { id: 'privacy', icon: Shield, label: 'Privacy & Security' },
  ];

  return (
    <div className="h-full overflow-hidden bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50 flex">
      {/* Sidebar */}
      <div className="w-72 bg-white/80 backdrop-blur-xl border-r border-gray-200 overflow-y-auto">
        <div className="p-6 border-b border-gray-200">
          <div className="flex items-center gap-3 mb-2">
            <SettingsIcon className="w-6 h-6 text-indigo-600" />
            <h2 className="text-2xl font-bold text-gray-900">Settings</h2>
          </div>
          <p className="text-sm text-gray-600">Customize your research experience</p>
        </div>
        
        <nav className="p-4">
          {sections.map((section) => {
            const Icon = section.icon;
            return (
              <button
                key={section.id}
                onClick={() => {
                  setActiveSection(section.id);
                  if (!expandedSections.has(section.id)) {
                    toggleSection(section.id);
                  }
                }}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl mb-1 transition-all ${
                  activeSection === section.id
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="text-sm font-medium flex-1 text-left">{section.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-8">
          {/* AI Models & API Keys */}
          {activeSection === 'ai-models' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">AI Models & API Keys</h3>
                <p className="text-gray-600">Configure your AI providers and API credentials</p>
              </div>

              {/* Provider Selection */}
              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm">
                <h4 className="font-semibold text-gray-900 mb-4">AI Providers</h4>
                <div className="grid grid-cols-3 gap-4">
                  {(['openai', 'claude', 'gemini'] as AIProvider[]).map((provider) => (
                    <button
                      key={provider}
                      onClick={() => setSelectedProvider(provider)}
                      className={`p-4 rounded-xl border-2 transition-all ${
                        selectedProvider === provider
                          ? 'border-indigo-600 bg-indigo-50'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <div className="text-center">
                        <div className="text-2xl mb-2">
                          {provider === 'openai' && '🤖'}
                          {provider === 'claude' && '🧠'}
                          {provider === 'gemini' && '✨'}
                        </div>
                        <div className="font-medium text-gray-900 capitalize">{provider}</div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* API Keys */}
              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm">
                <h4 className="font-semibold text-gray-900 mb-4">API Keys</h4>
                <div className="space-y-4">
                  {(['openai', 'claude', 'gemini'] as AIProvider[]).map((provider) => (
                    <div key={provider}>
                      <label className="block text-sm font-medium text-gray-700 mb-2 capitalize">
                        {provider} API Key
                      </label>
                      <div className="relative">
                        <input
                          type={showApiKeys[provider] ? 'text' : 'password'}
                          value={apiKeys[provider]}
                          onChange={(e) => setApiKeys({ ...apiKeys, [provider]: e.target.value })}
                          placeholder={`Enter your ${provider} API key`}
                          className="w-full px-4 py-3 pr-12 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                        />
                        <button
                          onClick={() => setShowApiKeys({ ...showApiKeys, [provider]: !showApiKeys[provider] })}
                          className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 hover:bg-gray-200 rounded-lg transition-colors"
                        >
                          {showApiKeys[provider] ? (
                            <EyeOff className="w-5 h-5 text-gray-500" />
                          ) : (
                            <Eye className="w-5 h-5 text-gray-500" />
                          )}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* Feature-Specific Models */}
          {activeSection === 'model-assignment' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Feature-Specific Models</h3>
                <p className="text-gray-600">Assign specific AI models to different features</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-6">
                {/* Paper Summary */}
                <div>
                  <label className="block text-sm font-medium text-gray-900 mb-3">
                    📄 Paper Summary Model
                    <span className="text-gray-500 font-normal ml-2">Best for comprehensive analysis</span>
                  </label>
                  <div className="relative">
                    <select
                      value={modelSettings.summary}
                      onChange={(e) => setModelSettings({ ...modelSettings, summary: e.target.value })}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      {Object.values(modelOptions).flat().map((model) => (
                        <option key={model} value={model}>{model}</option>
                      ))}
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>

                {/* Translation */}
                <div>
                  <label className="block text-sm font-medium text-gray-900 mb-3">
                    🌐 Translation Model
                    <span className="text-gray-500 font-normal ml-2">Best for accurate multilingual translation</span>
                  </label>
                  <div className="relative">
                    <select
                      value={modelSettings.translation}
                      onChange={(e) => setModelSettings({ ...modelSettings, translation: e.target.value })}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      {Object.values(modelOptions).flat().map((model) => (
                        <option key={model} value={model}>{model}</option>
                      ))}
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>

                {/* Search */}
                <div>
                  <label className="block text-sm font-medium text-gray-900 mb-3">
                    🔍 Search & Retrieval Model
                    <span className="text-gray-500 font-normal ml-2">Best for semantic search</span>
                  </label>
                  <div className="relative">
                    <select
                      value={modelSettings.search}
                      onChange={(e) => setModelSettings({ ...modelSettings, search: e.target.value })}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      {Object.values(modelOptions).flat().map((model) => (
                        <option key={model} value={model}>{model}</option>
                      ))}
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>

                {/* Knowledge Map */}
                <div>
                  <label className="block text-sm font-medium text-gray-900 mb-3">
                    🗺️ Knowledge Map Model
                    <span className="text-gray-500 font-normal ml-2">Best for finding paper relationships</span>
                  </label>
                  <div className="relative">
                    <select
                      value={modelSettings.knowledgeMap}
                      onChange={(e) => setModelSettings({ ...modelSettings, knowledgeMap: e.target.value })}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      {Object.values(modelOptions).flat().map((model) => (
                        <option key={model} value={model}>{model}</option>
                      ))}
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>

                {/* Chat */}
                <div>
                  <label className="block text-sm font-medium text-gray-900 mb-3">
                    💬 Chat Assistant Model
                    <span className="text-gray-500 font-normal ml-2">Best for conversational Q&A</span>
                  </label>
                  <div className="relative">
                    <select
                      value={modelSettings.chat}
                      onChange={(e) => setModelSettings({ ...modelSettings, chat: e.target.value })}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      {Object.values(modelOptions).flat().map((model) => (
                        <option key={model} value={model}>{model}</option>
                      ))}
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Database & Backup */}
          {activeSection === 'database' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Database & Backup</h3>
                <p className="text-gray-600">Configure database storage and backup settings</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Database Path
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={dbPath}
                      onChange={(e) => setDbPath(e.target.value)}
                      className="flex-1 px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                    <button className="px-4 py-3 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-colors">
                      Browse
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between p-4 bg-gray-50 rounded-xl">
                  <div>
                    <div className="font-medium text-gray-900">Auto Backup</div>
                    <div className="text-sm text-gray-600">Automatically backup your database</div>
                  </div>
                  <label className="relative inline-block w-12 h-6">
                    <input
                      type="checkbox"
                      checked={autoBackup}
                      onChange={(e) => setAutoBackup(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-12 h-6 bg-gray-300 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-indigo-300 rounded-full peer peer-checked:after:translate-x-6 peer-checked:after:border-white after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
                  </label>
                </div>

                {autoBackup && (
                  <div>
                    <label className="block text-sm font-medium text-gray-900 mb-3">
                      ⏰ Backup Frequency
                    </label>
                    <div className="relative">
                      <select
                        value={backupFrequency}
                        onChange={(e) => setBackupFrequency(e.target.value)}
                        className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                      >
                        <option value="hourly">Every Hour</option>
                        <option value="daily">Daily</option>
                        <option value="weekly">Weekly</option>
                        <option value="monthly">Monthly</option>
                      </select>
                      <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Search Engine */}
          {activeSection === 'search' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Online Search Engine</h3>
                <p className="text-gray-600">Choose your preferred paper search engine</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm">
                <div className="space-y-3">
                  <label className="flex items-start gap-4 p-4 border-2 border-gray-200 rounded-xl cursor-pointer hover:border-indigo-300 transition-all">
                    <input
                      type="radio"
                      name="search-engine"
                      value="google-scholar"
                      checked={searchEngine === 'google-scholar'}
                      onChange={(e) => setSearchEngine(e.target.value as SearchEngine)}
                      className="mt-1"
                    />
                    <div className="flex-1">
                      <div className="font-medium text-gray-900">Google Scholar</div>
                      <div className="text-sm text-gray-600 mt-1">
                        Search academic papers using Google Scholar API. Most comprehensive database.
                      </div>
                    </div>
                  </label>

                  <label className="flex items-start gap-4 p-4 border-2 border-gray-200 rounded-xl cursor-pointer hover:border-indigo-300 transition-all">
                    <input
                      type="radio"
                      name="search-engine"
                      value="ai-web-search"
                      checked={searchEngine === 'ai-web-search'}
                      onChange={(e) => setSearchEngine(e.target.value as SearchEngine)}
                      className="mt-1"
                    />
                    <div className="flex-1">
                      <div className="font-medium text-gray-900">AI-Powered Web Search</div>
                      <div className="text-sm text-gray-600 mt-1">
                        Use Gemini or GPT API web search capabilities. More contextual and intelligent results.
                      </div>
                    </div>
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* UI & Display */}
          {activeSection === 'ui' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">UI & Display</h3>
                <p className="text-gray-600">Customize the appearance of your workspace</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Theme</label>
                  <div className="grid grid-cols-3 gap-3">
                    {(['light', 'dark', 'auto'] as Theme[]).map((t) => (
                      <button
                        key={t}
                        onClick={() => setTheme(t)}
                        className={`p-3 rounded-xl border-2 transition-all capitalize ${
                          theme === t
                            ? 'border-indigo-600 bg-indigo-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Language</label>
                  <div className="relative">
                    <select
                      value={language}
                      onChange={(e) => setLanguage(e.target.value)}
                      className="w-full px-4 py-3 pr-10 bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 appearance-none cursor-pointer transition-all hover:border-gray-300 hover:shadow-sm"
                    >
                      <option value="en">English</option>
                      <option value="ko">한국어 (Korean)</option>
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Font Size</label>
                  <div className="grid grid-cols-3 gap-3">
                    {['small', 'medium', 'large'].map((size) => (
                      <button
                        key={size}
                        onClick={() => setFontSize(size)}
                        className={`p-3 rounded-xl border-2 transition-all capitalize ${
                          fontSize === size
                            ? 'border-indigo-600 bg-indigo-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        {size}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* PDF Viewer */}
          {activeSection === 'viewer' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">PDF Viewer Settings</h3>
                <p className="text-gray-600">Configure default PDF viewing preferences</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Default View Mode
                  </label>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      onClick={() => setDefaultViewMode('original')}
                      className={`p-4 rounded-xl border-2 transition-all ${
                        defaultViewMode === 'original'
                          ? 'border-indigo-600 bg-indigo-50'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <FileText className="w-6 h-6 mx-auto mb-2" />
                      <div className="font-medium">Original PDF</div>
                    </button>
                    <button
                      onClick={() => setDefaultViewMode('blog')}
                      className={`p-4 rounded-xl border-2 transition-all ${
                        defaultViewMode === 'blog'
                          ? 'border-indigo-600 bg-indigo-50'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <FileText className="w-6 h-6 mx-auto mb-2" />
                      <div className="font-medium">Blog View</div>
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Default Zoom Level: {defaultZoom}%
                  </label>
                  <input
                    type="range"
                    min="50"
                    max="200"
                    step="10"
                    value={defaultZoom}
                    onChange={(e) => setDefaultZoom(Number(e.target.value))}
                    className="w-full"
                  />
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>50%</span>
                    <span>100%</span>
                    <span>200%</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Citation Format */}
          {activeSection === 'citation' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Citation Format</h3>
                <p className="text-gray-600">Choose your preferred citation style</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm">
                <div className="grid grid-cols-2 gap-3">
                  {(['apa', 'mla', 'chicago', 'ieee'] as CitationFormat[]).map((format) => (
                    <button
                      key={format}
                      onClick={() => setCitationFormat(format)}
                      className={`p-4 rounded-xl border-2 transition-all uppercase ${
                        citationFormat === format
                          ? 'border-indigo-600 bg-indigo-50'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <div className="font-bold text-lg">{format}</div>
                      <div className="text-xs text-gray-600 mt-1">
                        {format === 'apa' && 'American Psychological Association'}
                        {format === 'mla' && 'Modern Language Association'}
                        {format === 'chicago' && 'Chicago Manual of Style'}
                        {format === 'ieee' && 'Institute of Electrical and Electronics Engineers'}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* Chat & AI Behavior */}
          {activeSection === 'chat' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Chat & AI Behavior</h3>
                <p className="text-gray-600">Configure AI assistant behavior and chat settings</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Chat History Retention (days)
                  </label>
                  <input
                    type="number"
                    value={chatHistoryDays}
                    onChange={(e) => setChatHistoryDays(e.target.value)}
                    className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Response Length
                  </label>
                  <select
                    value={responseLength}
                    onChange={(e) => setResponseLength(e.target.value)}
                    className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    <option value="concise">Concise</option>
                    <option value="medium">Medium</option>
                    <option value="detailed">Detailed</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Creativity (Temperature): {temperature}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="2"
                    step="0.1"
                    value={temperature}
                    onChange={(e) => setTemperature(Number(e.target.value))}
                    className="w-full"
                  />
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>Precise (0.0)</span>
                    <span>Balanced (1.0)</span>
                    <span>Creative (2.0)</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Storage & Cache */}
          {activeSection === 'storage' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Storage & Cache</h3>
                <p className="text-gray-600">Manage storage locations and cache settings</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    PDF Download Path
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={pdfDownloadPath}
                      onChange={(e) => setPdfDownloadPath(e.target.value)}
                      className="flex-1 px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                    <button className="px-4 py-3 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-colors">
                      Browse
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Cache Size Limit (MB)
                  </label>
                  <input
                    type="number"
                    value={cacheSize}
                    onChange={(e) => setCacheSize(e.target.value)}
                    className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>

                <button className="w-full px-4 py-3 bg-red-50 text-red-600 rounded-xl hover:bg-red-100 transition-colors font-medium">
                  Clear Cache Now
                </button>
              </div>
            </div>
          )}

          {/* Knowledge Map */}
          {activeSection === 'knowledge-map' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Knowledge Map Settings</h3>
                <p className="text-gray-600">Customize knowledge map visualization</p>
              </div>

              <div className="bg-white rounded-2xl p-6 border border-gray-200 shadow-sm space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Node Size</label>
                  <div className="grid grid-cols-3 gap-3">
                    {['small', 'medium', 'large'].map((size) => (
                      <button
                        key={size}
                        onClick={() => setNodeSize(size)}
                        className={`p-3 rounded-xl border-2 transition-all capitalize ${
                          nodeSize === size
                            ? 'border-indigo-600 bg-indigo-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        {size}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Layout Algorithm
                  </label>
                  <select
                    value={layoutAlgorithm}
                    onChange={(e) => setLayoutAlgorithm(e.target.value)}
                    className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    <option value="force-directed">Force-Directed</option>
                    <option value="hierarchical">Hierarchical</option>
                    <option value="circular">Circular</option>
                    <option value="radial">Radial</option>
                  </select>
                </div>
              </div>
            </div>
          )}

          {/* Other sections placeholder */}
          {(activeSection === 'automation' || activeSection === 'shortcuts' || activeSection === 'privacy') && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2 capitalize">{activeSection.replace('-', ' & ')}</h3>
                <p className="text-gray-600">Coming soon...</p>
              </div>
              <div className="bg-white rounded-2xl p-12 border border-gray-200 shadow-sm text-center">
                <div className="text-6xl mb-4">🚧</div>
                <p className="text-gray-600">This section is under development</p>
              </div>
            </div>
          )}

          {/* Save/Reset Buttons */}
          <div className="sticky bottom-0 bg-gradient-to-t from-white via-white to-transparent pt-6 mt-8">
            <div className="flex gap-3">
              <button
                onClick={handleSave}
                className="flex-1 flex items-center justify-center gap-2 px-6 py-3 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-colors font-medium shadow-sm"
              >
                <Save className="w-5 h-5" />
                Save Settings
              </button>
              <button
                onClick={handleReset}
                className="flex items-center justify-center gap-2 px-6 py-3 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors font-medium"
              >
                <RotateCcw className="w-5 h-5" />
                Reset to Default
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}