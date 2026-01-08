import { useState, useMemo } from 'react';
import { 
  Search, 
  Filter, 
  TrendingUp, 
  Grid3x3, 
  List, 
  AlignJustify,
  ExternalLink,
  Download,
  ChevronDown,
  Map as MapIcon,
  Calendar,
  Building2,
  Tag
} from 'lucide-react';
import { PdfFile } from '../App';
import { mockPdfData } from '../data/mockPdfData';
import { motion } from 'motion/react';
import { InfoModal } from './InfoModal';
import { CustomDropdown } from './CustomDropdown';

interface PdfLibraryProps {
  onOpenPdf: (pdf: PdfFile) => void;
  onOpenKnowledgeMap: () => void;
  onOpenDetailedInfo?: (type: 'author' | 'conference' | 'doi', data: string) => void;
}

interface OnlinePaper {
  id: string;
  title: string;
  authors: string[];
  year: number;
  conference: string;
  citations: number;
  pdfUrl: string;
  scholarUrl: string;
}

export function PdfLibrary({ onOpenPdf, onOpenKnowledgeMap, onOpenDetailedInfo }: PdfLibraryProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedConference, setSelectedConference] = useState<string>('all');
  const [selectedYear, setSelectedYear] = useState<string>('all');
  const [selectedSubject, setSelectedSubject] = useState<string>('all');
  const [viewStyle, setViewStyle] = useState<'grid' | 'list' | 'compact'>('grid');
  const [sortBy, setSortBy] = useState<'recent' | 'citations' | 'title'>('recent');
  
  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<'author' | 'conference' | 'doi'>('author');
  const [modalData, setModalData] = useState('');

  const handleInfoClick = (e: React.MouseEvent, type: 'author' | 'conference' | 'doi', data: string) => {
    e.stopPropagation(); // Prevent opening the PDF
    setModalType(type);
    setModalData(data);
    setModalOpen(true);
  };

  const handleViewDetails = () => {
    setModalOpen(false);
    if (onOpenDetailedInfo) {
      onOpenDetailedInfo(modalType, modalData);
    }
  };

  // Mock online results for library search
  const getOnlineResults = (query: string): OnlinePaper[] => {
    if (query.length < 3) return [];
    
    return [
      {
        id: 'lib-online-1',
        title: `Novel Approaches to ${query}`,
        authors: ['John Smith', 'Emily Brown'],
        year: 2024,
        conference: 'CVPR',
        citations: 234,
        pdfUrl: 'https://example.com/paper1.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'lib-online-2',
        title: `${query}: State-of-the-Art Methods`,
        authors: ['Alice Wang', 'Bob Chen'],
        year: 2023,
        conference: 'NeurIPS',
        citations: 567,
        pdfUrl: 'https://example.com/paper2.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      },
      {
        id: 'lib-online-3',
        title: `Deep Learning for ${query}`,
        authors: ['Michael Davis', 'Sarah Lee'],
        year: 2024,
        conference: 'ICLR',
        citations: 189,
        pdfUrl: 'https://example.com/paper3.pdf',
        scholarUrl: 'https://scholar.google.com/scholar?q=' + encodeURIComponent(query)
      }
    ];
  };

  const onlineResults = useMemo(() => getOnlineResults(searchQuery), [searchQuery]);

  const conferences = useMemo(() => {
    const set = new Set(mockPdfData.map(pdf => pdf.conference));
    return ['all', ...Array.from(set)];
  }, []);

  const conferenceOptions = useMemo(() => [
    { value: 'all', label: 'All Conferences' },
    ...conferences.filter(c => c !== 'all').map(conf => ({ value: conf, label: conf }))
  ], [conferences]);

  const years = useMemo(() => {
    const set = new Set(mockPdfData.map(pdf => pdf.year.toString()));
    return ['all', ...Array.from(set).sort((a, b) => Number(b) - Number(a))];
  }, []);

  const yearOptions = useMemo(() => [
    { value: 'all', label: 'All Years' },
    ...years.filter(y => y !== 'all').map(year => ({ value: year, label: year }))
  ], [years]);

  const subjects = useMemo(() => {
    const set = new Set(mockPdfData.flatMap(pdf => pdf.subject));
    return ['all', ...Array.from(set)];
  }, []);

  const subjectOptions = useMemo(() => [
    { value: 'all', label: 'All Subjects' },
    ...subjects.filter(s => s !== 'all').map(subj => ({ value: subj, label: subj }))
  ], [subjects]);

  const filteredPdfs = useMemo(() => {
    let filtered = mockPdfData.filter(pdf => {
      const matchesSearch = 
        pdf.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        pdf.authors.some(author => author.toLowerCase().includes(searchQuery.toLowerCase())) ||
        pdf.abstract.toLowerCase().includes(searchQuery.toLowerCase()) ||
        pdf.conference.toLowerCase().includes(searchQuery.toLowerCase());
      
      const matchesConference = selectedConference === 'all' || pdf.conference === selectedConference;
      const matchesYear = selectedYear === 'all' || pdf.year.toString() === selectedYear;
      const matchesSubject = selectedSubject === 'all' || pdf.subject.includes(selectedSubject);

      return matchesSearch && matchesConference && matchesYear && matchesSubject;
    });

    // Sort
    if (sortBy === 'recent') {
      filtered.sort((a, b) => b.year - a.year);
    } else if (sortBy === 'citations') {
      filtered.sort((a, b) => b.citations - a.citations);
    } else {
      filtered.sort((a, b) => a.title.localeCompare(b.title));
    }

    return filtered;
  }, [searchQuery, selectedConference, selectedYear, selectedSubject, sortBy]);

  // Show online results if search query exists and local results < 10
  const shouldShowOnlineResults = searchQuery.length >= 3 && filteredPdfs.length < 10;
  const displayOnlineResults = shouldShowOnlineResults ? onlineResults : [];

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="backdrop-blur-xl bg-white/70 border-b border-white/20 shadow-sm relative z-40">
        <div className="px-8 py-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-3xl font-semibold text-gray-900 mb-1">Research Library</h1>
              <p className="text-sm text-gray-600">{filteredPdfs.length} papers</p>
            </div>
            <button
              onClick={onOpenKnowledgeMap}
              className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-colors shadow-sm"
            >
              <MapIcon className="w-4 h-4" />
              Knowledge Map
            </button>
          </div>

          {/* Search Bar */}
          <div className="relative mb-6">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search papers, authors, keywords..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-12 pr-4 py-3.5 bg-white/90 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-sm"
            />
          </div>

          {/* Filters */}
          <div className="flex items-center gap-3 flex-wrap">
            <CustomDropdown
              value={selectedConference}
              options={conferenceOptions}
              onChange={setSelectedConference}
              icon={<Building2 className="w-4 h-4 text-gray-500" />}
              className="min-w-[180px]"
            />

            <CustomDropdown
              value={selectedYear}
              options={yearOptions}
              onChange={setSelectedYear}
              icon={<Calendar className="w-4 h-4 text-gray-500" />}
              className="min-w-[140px]"
            />

            <CustomDropdown
              value={selectedSubject}
              options={subjectOptions}
              onChange={setSelectedSubject}
              icon={<Tag className="w-4 h-4 text-gray-500" />}
              className="min-w-[160px]"
            />

            <div className="ml-auto flex items-center gap-2">
              <div className="relative">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as any)}
                  className="pl-3 pr-10 py-2 bg-white/90 backdrop-blur-sm rounded-lg border border-gray-200 text-sm text-gray-700 cursor-pointer outline-none appearance-none hover:border-gray-300 hover:shadow-sm transition-all focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="recent">Most Recent</option>
                  <option value="citations">Most Cited</option>
                  <option value="title">Title A-Z</option>
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500 pointer-events-none" />
              </div>

              <div className="flex gap-1 p-1 bg-white/90 rounded-lg border border-gray-200">
                <button
                  onClick={() => setViewStyle('grid')}
                  className={`p-1.5 rounded transition-colors ${
                    viewStyle === 'grid' ? 'bg-indigo-100 text-indigo-600' : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <Grid3x3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setViewStyle('list')}
                  className={`p-1.5 rounded transition-colors ${
                    viewStyle === 'list' ? 'bg-indigo-100 text-indigo-600' : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <List className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setViewStyle('compact')}
                  className={`p-1.5 rounded transition-colors ${
                    viewStyle === 'compact' ? 'bg-indigo-100 text-indigo-600' : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <AlignJustify className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* PDF Grid/List */}
      <div className="flex-1 overflow-y-auto px-8 py-6">
        {viewStyle === 'grid' ? (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {filteredPdfs.map((pdf, index) => (
                <motion.div
                  key={pdf.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.05 }}
                  onClick={() => onOpenPdf(pdf)}
                  className="group cursor-pointer"
                >
                  <div className="bg-white/80 backdrop-blur-sm rounded-2xl p-4 border border-gray-200 hover:border-indigo-300 hover:shadow-lg transition-all duration-200">
                    <div className="aspect-[3/4] bg-gradient-to-br from-indigo-100 to-blue-100 rounded-xl mb-3 overflow-hidden">
                      <img 
                        src={pdf.thumbnailUrl} 
                        alt={pdf.title}
                        className="w-full h-full object-cover"
                      />
                    </div>
                    <h3 className="font-medium text-gray-900 mb-2 line-clamp-2 text-sm group-hover:text-indigo-600 transition-colors">
                      {pdf.title}
                    </h3>
                    <button
                      onClick={(e) => handleInfoClick(e, 'author', pdf.authors[0])}
                      className="text-xs text-indigo-600 hover:text-indigo-700 hover:underline mb-2 text-left"
                    >
                      {pdf.authors[0]}{pdf.authors.length > 1 ? ' et al.' : ''}
                    </button>
                    <div className="flex items-center justify-between text-xs text-gray-500">
                      <button
                        onClick={(e) => handleInfoClick(e, 'conference', pdf.conference)}
                        className="hover:text-indigo-600 hover:underline transition-colors"
                      >
                        {pdf.conference} {pdf.year}
                      </button>
                      <div className="flex items-center gap-1">
                        <TrendingUp className="w-3 h-3" />
                        <span>{pdf.citations}</span>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1 mt-3">
                      {pdf.subject.slice(0, 2).map(subj => (
                        <span key={subj} className="px-2 py-0.5 bg-indigo-50 text-indigo-600 rounded text-xs">
                          {subj}
                        </span>
                      ))}
                    </div>
                  </div>
                </motion.div>
              ))}
            </div>

            {/* Online Results for Grid View */}
            {displayOnlineResults.length > 0 && (
              <div className="mt-8">
                <div className="flex items-center gap-2 mb-4">
                  <ExternalLink className="w-4 h-4 text-blue-600" />
                  <h2 className="text-lg font-medium text-gray-900">Available to Download</h2>
                  <span className="text-sm text-gray-500">({displayOnlineResults.length} papers)</span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                  {displayOnlineResults.map((paper, index) => (
                    <motion.div
                      key={paper.id}
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: (filteredPdfs.length + index) * 0.05 }}
                      className="group"
                    >
                      <div className="bg-gradient-to-br from-blue-50 to-indigo-50 backdrop-blur-sm rounded-2xl p-4 border-2 border-blue-200 hover:border-blue-400 hover:shadow-lg transition-all duration-200">
                        <div className="aspect-[3/4] bg-gradient-to-br from-blue-100 to-indigo-200 rounded-xl mb-3 flex items-center justify-center">
                          <ExternalLink className="w-12 h-12 text-blue-600" />
                        </div>
                        <h3 className="font-medium text-gray-900 mb-2 line-clamp-2 text-sm">
                          {paper.title}
                        </h3>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleInfoClick(e, 'author', paper.authors[0]);
                          }}
                          className="text-xs text-gray-600 mb-2 hover:text-indigo-600 hover:underline transition-colors text-left"
                        >
                          {paper.authors[0]}{paper.authors.length > 1 ? ' et al.' : ''}
                        </button>
                        <div className="flex items-center justify-between text-xs text-gray-500 mb-3">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleInfoClick(e, 'conference', paper.conference);
                            }}
                            className="hover:text-indigo-600 hover:underline transition-colors"
                          >
                            {paper.conference} {paper.year}
                          </button>
                          <div className="flex items-center gap-1">
                            <TrendingUp className="w-3 h-3" />
                            <span>{paper.citations}</span>
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <button
                            onClick={() => window.open(paper.pdfUrl, '_blank')}
                            className="flex-1 flex items-center justify-center gap-1 px-3 py-1.5 bg-indigo-600 text-white text-xs rounded-lg hover:bg-indigo-700 transition-colors"
                          >
                            <Download className="w-3 h-3" />
                            Download
                          </button>
                          <button
                            onClick={() => window.open(paper.scholarUrl, '_blank')}
                            className="px-3 py-1.5 bg-white text-gray-700 text-xs rounded-lg hover:bg-gray-100 transition-colors border border-gray-300"
                          >
                            <ExternalLink className="w-3 h-3" />
                          </button>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                </div>
              </div>
            )}
          </>
        ) : viewStyle === 'list' ? (
          <>
            <div className="space-y-3">
              {filteredPdfs.map((pdf, index) => (
                <motion.div
                  key={pdf.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.03 }}
                  onClick={() => onOpenPdf(pdf)}
                  className="group cursor-pointer bg-white/80 backdrop-blur-sm rounded-xl p-5 border border-gray-200 hover:border-indigo-300 hover:shadow-md transition-all duration-200"
                >
                  <div className="flex gap-4">
                    <div className="w-20 h-28 bg-gradient-to-br from-indigo-100 to-blue-100 rounded-lg flex-shrink-0 overflow-hidden">
                      <img 
                        src={pdf.thumbnailUrl} 
                        alt={pdf.title}
                        className="w-full h-full object-cover"
                      />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h3 className="font-medium text-gray-900 mb-1 group-hover:text-indigo-600 transition-colors">
                        {pdf.title}
                      </h3>
                      <div className="text-sm text-gray-600 mb-2">
                        {pdf.authors.map((author, idx) => (
                          <span key={idx}>
                            {idx > 0 && ', '}
                            <button
                              onClick={(e) => handleInfoClick(e, 'author', author)}
                              className="hover:text-indigo-600 hover:underline transition-colors"
                            >
                              {author}
                            </button>
                          </span>
                        ))}
                      </div>
                      <p className="text-sm text-gray-500 line-clamp-2 mb-3">
                        {pdf.abstract}
                      </p>
                      <div className="flex items-center gap-4 text-xs text-gray-500">
                        <button
                          onClick={(e) => handleInfoClick(e, 'conference', pdf.conference)}
                          className="font-medium hover:text-indigo-600 hover:underline transition-colors"
                        >
                          {pdf.conference} {pdf.year}
                        </button>
                        <span className="flex items-center gap-1">
                          <TrendingUp className="w-3 h-3" />
                          {pdf.citations} citations
                        </span>
                        <button
                          onClick={(e) => handleInfoClick(e, 'doi', pdf.doi)}
                          className="hover:text-indigo-600 hover:underline transition-colors"
                        >
                          DOI: {pdf.doi}
                        </button>
                      </div>
                      <div className="flex flex-wrap gap-1 mt-3">
                        {pdf.subject.map(subj => (
                          <span key={subj} className="px-2 py-0.5 bg-indigo-50 text-indigo-600 rounded text-xs">
                            {subj}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </motion.div>
              ))}
            </div>

            {/* Online Results for List View */}
            {displayOnlineResults.length > 0 && (
              <div className="mt-8">
                <div className="flex items-center gap-2 mb-4">
                  <ExternalLink className="w-4 h-4 text-blue-600" />
                  <h2 className="text-lg font-medium text-gray-900">Available to Download</h2>
                  <span className="text-sm text-gray-500">({displayOnlineResults.length} papers)</span>
                </div>
                <div className="space-y-3">
                  {displayOnlineResults.map((paper, index) => (
                    <motion.div
                      key={paper.id}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: (filteredPdfs.length + index) * 0.03 }}
                      className="bg-gradient-to-br from-blue-50 to-indigo-50 backdrop-blur-sm rounded-xl p-5 border-2 border-blue-200 hover:border-blue-400 hover:shadow-md transition-all duration-200"
                    >
                      <div className="flex gap-4">
                        <div className="w-20 h-28 bg-gradient-to-br from-blue-100 to-indigo-200 rounded-lg flex-shrink-0 flex items-center justify-center">
                          <ExternalLink className="w-8 h-8 text-blue-600" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <h3 className="font-medium text-gray-900 mb-1">
                            {paper.title}
                          </h3>
                          <div className="text-sm text-gray-600 mb-2">
                            {paper.authors.map((author, idx) => (
                              <span key={idx}>
                                {idx > 0 && ', '}
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleInfoClick(e, 'author', author);
                                  }}
                                  className="hover:text-indigo-600 hover:underline transition-colors"
                                >
                                  {author}
                                </button>
                              </span>
                            ))}
                          </div>
                          <div className="flex items-center gap-4 text-xs text-gray-500 mb-3">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleInfoClick(e, 'conference', paper.conference);
                              }}
                              className="font-medium hover:text-indigo-600 hover:underline transition-colors"
                            >
                              {paper.conference} {paper.year}
                            </button>
                            <span className="flex items-center gap-1">
                              <TrendingUp className="w-3 h-3" />
                              {paper.citations} citations
                            </span>
                          </div>
                          <div className="flex gap-2">
                            <button
                              onClick={() => window.open(paper.pdfUrl, '_blank')}
                              className="flex items-center gap-1 px-3 py-1.5 bg-indigo-600 text-white text-xs rounded-lg hover:bg-indigo-700 transition-colors"
                            >
                              <Download className="w-3 h-3" />
                              Download
                            </button>
                            <button
                              onClick={() => window.open(paper.scholarUrl, '_blank')}
                              className="flex items-center gap-1 px-3 py-1.5 bg-white text-gray-700 text-xs rounded-lg hover:bg-gray-100 transition-colors border border-gray-300"
                            >
                              <ExternalLink className="w-3 h-3" />
                              View on Scholar
                            </button>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                </div>
              </div>
            )}
          </>
        ) : (
          <>
            {/* Compact Table View - Zotero/Papers style */}
            <div className="bg-white/80 backdrop-blur-sm rounded-xl border border-gray-200 overflow-hidden">
              {/* Table Header */}
              <div className="grid grid-cols-12 gap-4 px-4 py-3 bg-gray-50 border-b border-gray-200 text-xs font-medium text-gray-600 uppercase tracking-wider">
                <div className="col-span-5">Title</div>
                <div className="col-span-3">Authors</div>
                <div className="col-span-2">Conference</div>
                <div className="col-span-1">Year</div>
                <div className="col-span-1 text-right">Citations</div>
              </div>
              
              {/* Table Rows */}
              <div className="divide-y divide-gray-200">
                {filteredPdfs.map((pdf, index) => (
                  <motion.div
                    key={pdf.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: index * 0.02 }}
                    onClick={() => onOpenPdf(pdf)}
                    className="grid grid-cols-12 gap-4 px-4 py-3 hover:bg-indigo-50 cursor-pointer transition-colors group"
                  >
                    <div className="col-span-5 flex items-center gap-3 min-w-0">
                      <div className="w-8 h-11 bg-gradient-to-br from-indigo-100 to-blue-100 rounded flex-shrink-0 overflow-hidden">
                        <img 
                          src={pdf.thumbnailUrl} 
                          alt={pdf.title}
                          className="w-full h-full object-cover"
                        />
                      </div>
                      <div className="min-w-0">
                        <h3 className="text-sm font-medium text-gray-900 truncate group-hover:text-indigo-600 transition-colors">
                          {pdf.title}
                        </h3>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {pdf.subject.slice(0, 2).map(subj => (
                            <span key={subj} className="px-1.5 py-0.5 bg-indigo-50 text-indigo-600 rounded text-xs">
                              {subj}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                    <div className="col-span-3 flex items-center">
                      <button
                        onClick={(e) => handleInfoClick(e, 'author', pdf.authors[0])}
                        className="text-sm text-gray-600 truncate hover:text-indigo-600 hover:underline transition-colors text-left"
                      >
                        {pdf.authors[0]}{pdf.authors.length > 1 ? ` +${pdf.authors.length - 1}` : ''}
                      </button>
                    </div>
                    <div className="col-span-2 flex items-center">
                      <button
                        onClick={(e) => handleInfoClick(e, 'conference', pdf.conference)}
                        className="text-sm text-gray-600 truncate hover:text-indigo-600 hover:underline transition-colors"
                      >
                        {pdf.conference}
                      </button>
                    </div>
                    <div className="col-span-1 flex items-center">
                      <p className="text-sm text-gray-600">{pdf.year}</p>
                    </div>
                    <div className="col-span-1 flex items-center justify-end">
                      <div className="flex items-center gap-1 text-sm text-gray-500">
                        <TrendingUp className="w-3.5 h-3.5" />
                        <span>{pdf.citations}</span>
                      </div>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Online Results for Compact View */}
            {displayOnlineResults.length > 0 && (
              <div className="mt-8">
                <div className="flex items-center gap-2 mb-4">
                  <ExternalLink className="w-4 h-4 text-blue-600" />
                  <h2 className="text-lg font-medium text-gray-900">Available to Download</h2>
                  <span className="text-sm text-gray-500">({displayOnlineResults.length} papers)</span>
                </div>
                <div className="bg-gradient-to-br from-blue-50 to-indigo-50 backdrop-blur-sm rounded-xl border-2 border-blue-200 overflow-hidden">
                  {/* Table Header */}
                  <div className="grid grid-cols-12 gap-4 px-4 py-3 bg-blue-100/50 border-b border-blue-200 text-xs font-medium text-gray-700 uppercase tracking-wider">
                    <div className="col-span-5">Title</div>
                    <div className="col-span-3">Authors</div>
                    <div className="col-span-2">Conference</div>
                    <div className="col-span-1">Year</div>
                    <div className="col-span-1 text-right">Actions</div>
                  </div>
                  
                  {/* Table Rows */}
                  <div className="divide-y divide-blue-200">
                    {displayOnlineResults.map((paper, index) => (
                      <motion.div
                        key={paper.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: (filteredPdfs.length + index) * 0.02 }}
                        className="grid grid-cols-12 gap-4 px-4 py-3 hover:bg-blue-100/30 transition-colors"
                      >
                        <div className="col-span-5 flex items-center gap-3 min-w-0">
                          <ExternalLink className="w-4 h-4 text-blue-600 flex-shrink-0" />
                          <div className="min-w-0">
                            <h3 className="text-sm font-medium text-gray-900 truncate">
                              {paper.title}
                            </h3>
                          </div>
                        </div>
                        <div className="col-span-3 flex items-center">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleInfoClick(e, 'author', paper.authors[0]);
                            }}
                            className="text-sm text-gray-600 truncate hover:text-indigo-600 hover:underline transition-colors text-left"
                          >
                            {paper.authors[0]}{paper.authors.length > 1 ? ` +${paper.authors.length - 1}` : ''}
                          </button>
                        </div>
                        <div className="col-span-2 flex items-center">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleInfoClick(e, 'conference', paper.conference);
                            }}
                            className="text-sm text-gray-600 truncate hover:text-indigo-600 hover:underline transition-colors"
                          >
                            {paper.conference}
                          </button>
                        </div>
                        <div className="col-span-1 flex items-center">
                          <p className="text-sm text-gray-600">{paper.year}</p>
                        </div>
                        <div className="col-span-1 flex items-center justify-end gap-1">
                          <button
                            onClick={() => window.open(paper.pdfUrl, '_blank')}
                            className="p-1.5 bg-indigo-600 text-white rounded hover:bg-indigo-700 transition-colors"
                            title="Download"
                          >
                            <Download className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => window.open(paper.scholarUrl, '_blank')}
                            className="p-1.5 bg-white text-gray-700 rounded hover:bg-gray-100 transition-colors border border-gray-300"
                            title="View on Scholar"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Info Modal */}
      <InfoModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        type={modalType}
        data={modalData}
        onViewDetails={handleViewDetails}
      />
    </div>
  );
}