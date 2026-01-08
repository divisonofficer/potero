import { useState, useRef } from 'react';
import { ArrowLeft, FileText, BookOpen, MessageSquare, Map, ChevronLeft, ChevronRight } from 'lucide-react';
import { PdfFile } from '../App';
import { OriginalPdfView } from './OriginalPdfView';
import { SimpleBlogView } from './SimpleBlogView';
import { ChatbotPanel } from './ChatbotPanel';
import { InfoModal } from './InfoModal';
import { ScreenshotCapture } from './ScreenshotCapture';

interface PdfViewerProps {
  pdf: PdfFile;
  isChatbotOpen: boolean;
  onOpenKnowledgeMap: () => void;
  onOpenDetailedInfo?: (type: 'author' | 'conference' | 'doi', data: string) => void;
}

type ViewerMode = 'original' | 'blog';

export function PdfViewer({ pdf, isChatbotOpen, onOpenKnowledgeMap, onOpenDetailedInfo }: PdfViewerProps) {
  const [viewerMode, setViewerMode] = useState<ViewerMode>('original');
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = 12; // Mock total pages
  
  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<'author' | 'conference' | 'doi'>('author');
  const [modalData, setModalData] = useState('');
  
  const contentRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<HTMLDivElement>(null); // Full viewer container ref

  const handleInfoClick = (type: 'author' | 'conference' | 'doi', data: string) => {
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

  return (
    <div className="h-full flex flex-col bg-white">
      {/* Header */}
      <div className="backdrop-blur-xl bg-white/95 border-b border-gray-200 shadow-sm z-10">
        <div className="px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              {/* View Mode Toggle - Icon Only */}
              <div className="flex gap-1 p-1 bg-gray-100 rounded-lg">
                <button
                  onClick={() => setViewerMode('original')}
                  title="Original PDF View"
                  className={`p-2 rounded-md transition-all ${
                    viewerMode === 'original' 
                      ? 'bg-white text-indigo-600 shadow-sm' 
                      : 'text-gray-600 hover:text-gray-900'
                  }`}
                >
                  <FileText className="w-4 h-4" />
                </button>
                <button
                  onClick={() => pdf.hasBlogView && setViewerMode('blog')}
                  title={pdf.hasBlogView ? "Simple Blog View" : "Blog view not available for this paper"}
                  disabled={!pdf.hasBlogView}
                  className={`p-2 rounded-md transition-all ${
                    viewerMode === 'blog' 
                      ? 'bg-white text-indigo-600 shadow-sm' 
                      : pdf.hasBlogView
                        ? 'text-gray-600 hover:text-gray-900'
                        : 'text-gray-300 cursor-not-allowed'
                  }`}
                >
                  <BookOpen className="w-4 h-4" />
                </button>
              </div>

              {/* Screenshot Button */}
              <ScreenshotCapture 
                targetRef={contentRef}
                buttonClassName="p-2 rounded-lg hover:bg-gray-100 transition-colors text-gray-600"
              />
            </div>

            {/* Page Navigation (only for original mode) */}
            {viewerMode === 'original' && (
              <div className="flex items-center gap-4">
                <button
                  onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
                  disabled={currentPage === 1}
                  className="p-2 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <span className="text-sm text-gray-600 min-w-[100px] text-center">
                  Page {currentPage} of {totalPages}
                </span>
                <button
                  onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
                  disabled={currentPage === totalPages}
                  className="p-2 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-5 h-5" />
                </button>
              </div>
            )}
          </div>

          {/* Paper Info */}
          <div>
            <h2 className="text-xl font-semibold text-gray-900 mb-1">{pdf.title}</h2>
            <div className="flex flex-wrap items-center gap-2 mb-2">
              {pdf.authors.map((author, idx) => (
                <button
                  key={idx}
                  onClick={() => handleInfoClick('author', author)}
                  className="text-sm text-indigo-600 hover:text-indigo-700 hover:underline transition-colors"
                >
                  {author}{idx < pdf.authors.length - 1 ? ',' : ''}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-4 flex-wrap text-xs text-gray-500">
              <button
                onClick={() => handleInfoClick('conference', pdf.conference)}
                className="hover:text-indigo-600 hover:underline transition-colors"
              >
                {pdf.conference} {pdf.year}
              </button>
              <span>•</span>
              <span>{pdf.citations} citations</span>
              <span>•</span>
              <button
                onClick={() => handleInfoClick('doi', pdf.doi)}
                className="hover:text-indigo-600 hover:underline transition-colors"
              >
                DOI: {pdf.doi}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 flex overflow-hidden">
        {/* Main Viewer */}
        <div 
          ref={contentRef}
          className={`flex-1 overflow-y-auto transition-all duration-300 ${
            isChatbotOpen ? 'mr-96' : ''
          }`}
        >
          {viewerMode === 'original' ? (
            <OriginalPdfView pdf={pdf} currentPage={currentPage} />
          ) : (
            <SimpleBlogView pdf={pdf} />
          )}
        </div>

        {/* Chatbot Panel */}
        {isChatbotOpen && (
          <ChatbotPanel 
            pdf={pdf} 
            onClose={() => setIsChatbotOpen(false)} 
          />
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