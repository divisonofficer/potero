import { X, Home, Search, Plus, Map, MessageSquare, Download, ExternalLink, Filter, Settings as SettingsIcon } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { PdfFile } from '../App';
import { motion, AnimatePresence } from 'motion/react';

export interface Tab {
  id: string;
  type: 'home' | 'viewer' | 'knowledge-map' | 'detailed-info' | 'settings';
  title: string;
  pdf?: PdfFile;
  infoType?: 'author' | 'conference' | 'doi';
  infoData?: string;
}

interface TabBarProps {
  tabs: Tab[];
  activeTabId: string;
  onTabChange: (tabId: string) => void;
  onTabClose: (tabId: string) => void;
  onSearchSelect: (pdf: PdfFile) => void;
  onOpenKnowledgeMap: () => void;
  onOpenSettings: () => void;
  onToggleChat: () => void;
  isChatOpen: boolean;
  allPdfs: PdfFile[];
}

interface OnlinePaper {
  id: string;
  title: string;
  authors: string[];
  year: number;
  conference: string;
  abstract: string;
  citations: number;
  pdfUrl: string;
  scholarUrl: string;
}

type SearchFilter = 'all' | 'title' | 'author' | 'conference' | 'year';

export function TabBar({ 
  tabs, 
  activeTabId, 
  onTabChange, 
  onTabClose, 
  onSearchSelect, 
  onOpenKnowledgeMap,
  onOpenSettings,
  onToggleChat,
  isChatOpen,
  allPdfs 
}: TabBarProps) {
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchFilter, setSearchFilter] = useState<SearchFilter>('all');
  const [onlineResults, setOnlineResults] = useState<OnlinePaper[]>([]);
  const [isSearchingOnline, setIsSearchingOnline] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const tabsContainerRef = useRef<HTMLDivElement>(null);

  const filteredPdfs = allPdfs.filter(pdf => {
    const lowerCaseQuery = searchQuery.toLowerCase();
    switch (searchFilter) {
      case 'title':
        return pdf.title.toLowerCase().includes(lowerCaseQuery);
      case 'author':
        return pdf.authors.some(author => author.toLowerCase().includes(lowerCaseQuery));
      case 'conference':
        return pdf.conference.toLowerCase().includes(lowerCaseQuery);
      case 'year':
        return pdf.year.toString().includes(lowerCaseQuery);
      default:
        return pdf.title.toLowerCase().includes(lowerCaseQuery) ||
               pdf.authors.some(author => author.toLowerCase().includes(lowerCaseQuery));
    }
  });

  // Mock Google Scholar search
  const searchOnlineScholar = async (query: string) => {
    setIsSearchingOnline(true);
    // Simulate API call delay
    await new Promise(resolve => setTimeout(resolve, 800));
    
    // Mock online results with more variety
    const mockOnlineResults: OnlinePaper[] = [
      {
        id: 'online-1',
        title: `Attention Is All You Need for ${query}`,
        authors: ['Ashish Vaswani', 'Noam Shazeer', 'Niki Parmar'],
        year: 2024,
        conference: 'CVPR',
        abstract: 'We propose a novel transformer-based architecture...',
        citations: 1245,
        pdfUrl: 'https://example.com/paper1.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'online-2',
        title: `${query}: A Comprehensive Survey`,
        authors: ['Alice Wang', 'Bob Chen', 'Carol Davis'],
        year: 2023,
        conference: 'NeurIPS',
        abstract: 'We present a comprehensive survey of recent advances...',
        citations: 678,
        pdfUrl: 'https://example.com/paper2.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'online-3',
        title: `Efficient ${query} with Vision Transformers`,
        authors: ['Michael Brown', 'Sarah Lee'],
        year: 2024,
        conference: 'ICCV',
        abstract: 'We propose an efficient approach using vision transformers...',
        citations: 423,
        pdfUrl: 'https://example.com/paper3.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'online-4',
        title: `Self-Supervised Learning for ${query}`,
        authors: ['David Kim', 'Emma Wilson', 'Frank Zhang'],
        year: 2023,
        conference: 'CVPR',
        abstract: 'A self-supervised learning framework that achieves state-of-the-art...',
        citations: 891,
        pdfUrl: 'https://example.com/paper4.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'online-5',
        title: `Rethinking ${query} in the Age of Foundation Models`,
        authors: ['Grace Johnson', 'Henry Martinez'],
        year: 2024,
        conference: 'ECCV',
        abstract: 'We revisit traditional approaches in light of recent foundation models...',
        citations: 234,
        pdfUrl: 'https://example.com/paper5.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'online-6',
        title: `Learning ${query} from Unlabeled Data`,
        authors: ['Isabella Chen', 'Jack Thompson'],
        year: 2023,
        conference: 'ICLR',
        abstract: 'We demonstrate how to learn effective representations from unlabeled data...',
        citations: 567,
        pdfUrl: 'https://example.com/paper6.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      }
    ];
    
    setOnlineResults(mockOnlineResults);
    setIsSearchingOnline(false);
  };

  // Trigger online search when query changes
  useEffect(() => {
    if (searchQuery.length > 2) {
      const debounceTimer = setTimeout(() => {
        searchOnlineScholar(searchQuery);
      }, 500);
      return () => clearTimeout(debounceTimer);
    } else {
      setOnlineResults([]);
    }
  }, [searchQuery]);

  const handleDownloadPaper = (paper: OnlinePaper) => {
    // In a real app, this would trigger a download flow
    window.open(paper.pdfUrl, '_blank');
  };

  // Calculate tab width based on number of tabs
  const getTabMaxWidth = () => {
    if (tabs.length <= 4) return '200px';
    if (tabs.length <= 6) return '150px';
    if (tabs.length <= 8) return '120px';
    return '100px';
  };

  // Handle horizontal scroll with mouse wheel
  useEffect(() => {
    const tabsContainer = tabsContainerRef.current;
    if (!tabsContainer) return;

    const handleWheel = (e: WheelEvent) => {
      // Check if scrolling vertically
      if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
        e.preventDefault();
        // Convert vertical scroll to horizontal
        tabsContainer.scrollLeft += e.deltaY;
      }
    };

    tabsContainer.addEventListener('wheel', handleWheel, { passive: false });
    return () => tabsContainer.removeEventListener('wheel', handleWheel);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setShowSearch(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="h-12 bg-white/80 backdrop-blur-xl border-b border-gray-200 flex items-center px-3 gap-2 relative z-50">
      {/* Tabs */}
      <div className="flex-1 flex items-center gap-1 overflow-x-auto scrollbar-hide" ref={tabsContainerRef}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            style={{ maxWidth: getTabMaxWidth() }}
            className={`group flex items-center gap-2 px-3 py-1.5 rounded-lg transition-all min-w-fit ${
              activeTabId === tab.id
                ? 'bg-indigo-50 text-indigo-700'
                : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {tab.type === 'home' ? (
              <Home className="w-4 h-4 flex-shrink-0" />
            ) : (
              <div className="w-4 h-4 bg-gradient-to-br from-indigo-400 to-purple-400 rounded flex-shrink-0" />
            )}
            
            <span className="text-sm font-medium truncate">{tab.title}</span>
            
            {tab.type !== 'home' && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onTabClose(tab.id);
                }}
                className={`p-0.5 rounded hover:bg-gray-200 opacity-0 group-hover:opacity-100 transition-opacity ${
                  activeTabId === tab.id ? 'hover:bg-indigo-200' : ''
                }`}
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </button>
        ))}
      </div>

      {/* Quick Search */}
      <div className="relative" ref={searchRef}>
        <button
          onClick={() => setShowSearch(!showSearch)}
          className={`flex items-center gap-2 px-3 py-1.5 rounded-lg transition-colors ${
            showSearch ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          <Search className="w-4 h-4" />
          <span className="text-sm font-medium">Quick Search</span>
        </button>

        <AnimatePresence>
          {showSearch && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="absolute right-0 top-full mt-2 w-96 bg-white rounded-xl shadow-2xl border border-gray-200 overflow-hidden z-[9999]"
            >
              {/* Search Input */}
              <div className="p-3 border-b border-gray-200">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                  <input
                    type="text"
                    placeholder="Search papers to open in new tab..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    autoFocus
                    className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
                  />
                </div>
              </div>

              {/* Search Filter */}
              <div className="p-3 border-b border-gray-200">
                <div className="flex items-center gap-2">
                  <Filter className="w-4 h-4 text-gray-400" />
                  <select
                    value={searchFilter}
                    onChange={(e) => setSearchFilter(e.target.value as SearchFilter)}
                    className="w-full px-4 py-2 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
                  >
                    <option value="all">All</option>
                    <option value="title">Title</option>
                    <option value="author">Author</option>
                    <option value="conference">Conference</option>
                    <option value="year">Year</option>
                  </select>
                </div>
              </div>

              {/* Search Results */}
              <div className="max-h-96 overflow-y-auto">
                {searchQuery.length > 0 ? (
                  <>
                    {/* Local Results Section */}
                    {filteredPdfs.length > 0 && (
                      <>
                        <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                            <span className="text-xs font-medium text-gray-700">
                              Local Library ({filteredPdfs.length})
                            </span>
                          </div>
                        </div>
                        <div className="divide-y divide-gray-100">
                          {filteredPdfs.slice(0, 5).map((pdf) => (
                            <button
                              key={pdf.id}
                              onClick={() => {
                                onSearchSelect(pdf);
                                setShowSearch(false);
                                setSearchQuery('');
                              }}
                              className="w-full px-4 py-3 hover:bg-indigo-50 transition-colors text-left group"
                            >
                              <div className="flex gap-3">
                                <div className="w-10 h-14 bg-gradient-to-br from-green-100 to-emerald-100 rounded flex-shrink-0 overflow-hidden">
                                  <img
                                    src={pdf.thumbnailUrl}
                                    alt={pdf.title}
                                    className="w-full h-full object-cover"
                                  />
                                </div>
                                <div className="flex-1 min-w-0">
                                  <h4 className="text-sm font-medium text-gray-900 group-hover:text-indigo-600 transition-colors line-clamp-1">
                                    {pdf.title}
                                  </h4>
                                  <p className="text-xs text-gray-600 mt-0.5">
                                    {pdf.authors[0]}{pdf.authors.length > 1 ? ' et al.' : ''}
                                  </p>
                                  <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
                                    <span>{pdf.conference} {pdf.year}</span>
                                    <span>•</span>
                                    <span>{pdf.citations} citations</span>
                                  </div>
                                </div>
                              </div>
                            </button>
                          ))}
                        </div>
                      </>
                    )}

                    {/* Online Results Section */}
                    {isSearchingOnline ? (
                      <div className="px-4 py-8 text-center text-gray-500">
                        <div className="w-8 h-8 mx-auto mb-2">
                          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
                        </div>
                        <p className="text-sm">Searching Google Scholar...</p>
                      </div>
                    ) : onlineResults.length > 0 ? (
                      <>
                        <div className="px-4 py-2 bg-blue-50 border-b border-blue-200">
                          <div className="flex items-center gap-2">
                            <ExternalLink className="w-3 h-3 text-blue-600" />
                            <span className="text-xs font-medium text-blue-900">
                              Google Scholar Results ({onlineResults.length})
                            </span>
                          </div>
                        </div>
                        <div className="divide-y divide-gray-100">
                          {onlineResults.map((paper) => (
                            <div
                              key={paper.id}
                              className="w-full px-4 py-3 hover:bg-blue-50 transition-colors"
                            >
                              <div className="flex gap-3">
                                <div className="w-10 h-14 bg-gradient-to-br from-blue-100 to-indigo-100 rounded flex-shrink-0 flex items-center justify-center">
                                  <ExternalLink className="w-5 h-5 text-blue-600" />
                                </div>
                                <div className="flex-1 min-w-0">
                                  <h4 className="text-sm font-medium text-gray-900 line-clamp-1">
                                    {paper.title}
                                  </h4>
                                  <p className="text-xs text-gray-600 mt-0.5">
                                    {paper.authors[0]}{paper.authors.length > 1 ? ' et al.' : ''}
                                  </p>
                                  <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
                                    <span>{paper.conference} {paper.year}</span>
                                    <span>•</span>
                                    <span>{paper.citations} citations</span>
                                  </div>
                                  <div className="flex gap-2 mt-2">
                                    <button
                                      onClick={() => handleDownloadPaper(paper)}
                                      className="flex items-center gap-1 px-3 py-1 bg-indigo-600 text-white text-xs rounded-md hover:bg-indigo-700 transition-colors"
                                    >
                                      <Download className="w-3 h-3" />
                                      Download PDF
                                    </button>
                                    <button
                                      onClick={() => window.open(paper.scholarUrl, '_blank')}
                                      className="flex items-center gap-1 px-3 py-1 bg-gray-100 text-gray-700 text-xs rounded-md hover:bg-gray-200 transition-colors"
                                    >
                                      <ExternalLink className="w-3 h-3" />
                                      View on Scholar
                                    </button>
                                  </div>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </>
                    ) : null}

                    {/* No Results */}
                    {filteredPdfs.length === 0 && onlineResults.length === 0 && !isSearchingOnline && (
                      <div className="px-4 py-8 text-center text-gray-500">
                        <Search className="w-8 h-8 mx-auto mb-2 opacity-50" />
                        <p className="text-sm">No papers found</p>
                        <p className="text-xs mt-1">Try different keywords or filters</p>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="px-4 py-8 text-center text-gray-500">
                    <Search className="w-8 h-8 mx-auto mb-2 opacity-50" />
                    <p className="text-sm">Start typing to search...</p>
                    <p className="text-xs mt-1">Search local library and Google Scholar</p>
                  </div>
                )}
              </div>

              {/* Results Count Footer */}
              {searchQuery.length > 0 && (filteredPdfs.length > 5 || onlineResults.length > 0) && (
                <div className="px-4 py-2 bg-gray-50 border-t border-gray-200 text-center text-xs text-gray-500">
                  {filteredPdfs.length > 5 && `Showing 5 of ${filteredPdfs.length} local results`}
                  {filteredPdfs.length > 5 && onlineResults.length > 0 && ' • '}
                  {onlineResults.length > 0 && `${onlineResults.length} online results`}
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Knowledge Map */}
      <button
        onClick={onOpenKnowledgeMap}
        className={`flex items-center gap-2 px-3 py-1.5 rounded-lg transition-colors ${
          activeTabId === 'knowledge-map' ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
        }`}
      >
        <Map className="w-4 h-4" />
        <span className="text-sm font-medium">Knowledge Map</span>
      </button>

      {/* Chat Toggle */}
      <button
        onClick={onToggleChat}
        className={`flex items-center gap-2 px-3 py-1.5 rounded-lg transition-colors ${
          isChatOpen ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
        }`}
      >
        <MessageSquare className="w-4 h-4" />
        <span className="text-sm font-medium">Chat</span>
      </button>

      {/* Settings */}
      <button
        onClick={onOpenSettings}
        className={`flex items-center gap-2 px-3 py-1.5 rounded-lg transition-colors ${
          tabs.some(tab => tab.type === 'settings' && tab.id === activeTabId)
            ? 'bg-indigo-50 text-indigo-700' 
            : 'text-gray-600 hover:bg-gray-100'
        }`}
      >
        <SettingsIcon className="w-4 h-4" />
      </button>
    </div>
  );
}