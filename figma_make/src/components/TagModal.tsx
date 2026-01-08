import { useState } from 'react';
import { X, Tag, TrendingUp, FileText, Search, Hash, ChevronRight } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { PdfFile } from '../App';

interface TagInfo {
  tag: string;
  count: number;
  papers: Array<{
    id: string;
    title: string;
    authors: string[];
    year: number;
    conference: string;
  }>;
}

interface TagModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentPdf: PdfFile;
  onPaperClick: (paperId: string) => void;
  language: 'en' | 'ko';
}

export function TagModal({ isOpen, onClose, currentPdf, onPaperClick, language }: TagModalProps) {
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Mock tag data - 실제로는 AI로 추출하거나 DB에서 가져옴
  const allTags: TagInfo[] = [
    {
      tag: language === 'ko' ? '딥러닝' : 'Deep Learning',
      count: 156,
      papers: [
        { id: '1', title: 'Attention Mechanisms in Deep Learning', authors: ['John Doe'], year: 2024, conference: 'NeurIPS' },
        { id: '2', title: 'Neural Network Optimization', authors: ['Jane Smith'], year: 2023, conference: 'ICML' },
        { id: '3', title: 'Transfer Learning Approaches', authors: ['Bob Lee'], year: 2024, conference: 'CVPR' },
      ]
    },
    {
      tag: language === 'ko' ? '트랜스포머' : 'Transformer',
      count: 89,
      papers: [
        { id: '4', title: 'Vision Transformers for Image Classification', authors: ['Alice Wang'], year: 2024, conference: 'ICLR' },
        { id: '5', title: 'Efficient Transformer Architectures', authors: ['Chris Park'], year: 2023, conference: 'NeurIPS' },
      ]
    },
    {
      tag: language === 'ko' ? '주목 메커니즘' : 'Attention Mechanism',
      count: 134,
      papers: [
        { id: '6', title: 'Multi-Head Attention Networks', authors: ['David Kim'], year: 2024, conference: 'CVPR' },
        { id: '7', title: 'Self-Attention in NLP', authors: ['Emma Chen'], year: 2023, conference: 'ACL' },
        { id: '8', title: 'Cross-Attention for Vision-Language', authors: ['Frank Liu'], year: 2024, conference: 'ICCV' },
      ]
    },
    {
      tag: language === 'ko' ? '자연어 처리' : 'Natural Language Processing',
      count: 203,
      papers: [
        { id: '9', title: 'BERT and Beyond', authors: ['Grace Yoon'], year: 2023, conference: 'EMNLP' },
        { id: '10', title: 'Large Language Models', authors: ['Henry Choi'], year: 2024, conference: 'ACL' },
      ]
    },
    {
      tag: language === 'ko' ? '컴퓨터 비전' : 'Computer Vision',
      count: 178,
      papers: [
        { id: '11', title: 'Object Detection with CNNs', authors: ['Iris Jung'], year: 2024, conference: 'CVPR' },
        { id: '12', title: 'Semantic Segmentation Methods', authors: ['Jack Seo'], year: 2023, conference: 'ICCV' },
      ]
    },
    {
      tag: language === 'ko' ? '강화학습' : 'Reinforcement Learning',
      count: 67,
      papers: [
        { id: '13', title: 'Deep Q-Networks', authors: ['Kelly Han'], year: 2023, conference: 'ICML' },
        { id: '14', title: 'Policy Gradient Methods', authors: ['Leo Kang'], year: 2024, conference: 'NeurIPS' },
      ]
    },
    {
      tag: language === 'ko' ? '전이 학습' : 'Transfer Learning',
      count: 92,
      papers: [
        { id: '15', title: 'Fine-tuning Pre-trained Models', authors: ['Mia Shin'], year: 2024, conference: 'ICLR' },
      ]
    },
    {
      tag: language === 'ko' ? '생성 모델' : 'Generative Models',
      count: 145,
      papers: [
        { id: '16', title: 'Diffusion Models for Image Generation', authors: ['Noah Baek'], year: 2024, conference: 'NeurIPS' },
        { id: '17', title: 'GANs and VAEs', authors: ['Olivia Lim'], year: 2023, conference: 'ICML' },
      ]
    },
  ];

  const filteredTags = searchQuery
    ? allTags.filter(t => t.tag.toLowerCase().includes(searchQuery.toLowerCase()))
    : allTags;

  const selectedTagInfo = allTags.find(t => t.tag === selectedTag);

  const texts = {
    en: {
      title: 'Keyword Tags',
      subtitle: 'Explore papers by research topics',
      searchPlaceholder: 'Search tags...',
      paperCount: 'papers',
      relatedPapers: 'Related Papers',
      backToTags: 'Back to Tags',
      noResults: 'No tags found',
      highlightInPaper: 'Highlight in Paper',
    },
    ko: {
      title: '키워드 태그',
      subtitle: '연구 주제별로 논문 탐색',
      searchPlaceholder: '태그 검색...',
      paperCount: '편의 논문',
      relatedPapers: '관련 논문',
      backToTags: '태그 목록으로',
      noResults: '검색 결과가 없습니다',
      highlightInPaper: '논문에서 강조하기',
    }
  };

  const t = texts[language];

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          />

          {/* Modal */}
          <div className="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl max-h-[85vh] overflow-hidden pointer-events-auto flex flex-col"
            >
              {/* Header */}
              <div className="bg-gradient-to-r from-indigo-600 to-purple-600 px-6 py-5 text-white relative">
                <button
                  onClick={onClose}
                  className="absolute right-4 top-4 p-1.5 hover:bg-white/20 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
                <div className="flex items-center gap-3 mb-2">
                  <Tag className="w-6 h-6" />
                  <div>
                    <h2 className="text-xl font-bold">{t.title}</h2>
                    <p className="text-sm text-white/80">{t.subtitle}</p>
                  </div>
                </div>
              </div>

              {/* Content */}
              <div className="flex-1 overflow-hidden flex flex-col">
                {!selectedTag ? (
                  <>
                    {/* Search */}
                    <div className="p-6 border-b border-gray-200">
                      <div className="relative">
                        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                        <input
                          type="text"
                          value={searchQuery}
                          onChange={(e) => setSearchQuery(e.target.value)}
                          placeholder={t.searchPlaceholder}
                          className="w-full pl-12 pr-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                      </div>
                    </div>

                    {/* Tag Grid */}
                    <div className="flex-1 overflow-y-auto p-6">
                      {filteredTags.length > 0 ? (
                        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                          {filteredTags.map((tagInfo) => (
                            <button
                              key={tagInfo.tag}
                              onClick={() => setSelectedTag(tagInfo.tag)}
                              className="group p-4 bg-gradient-to-br from-gray-50 to-indigo-50 hover:from-indigo-50 hover:to-purple-50 rounded-xl border border-gray-200 hover:border-indigo-300 transition-all text-left"
                            >
                              <div className="flex items-start justify-between mb-2">
                                <Hash className="w-5 h-5 text-indigo-600" />
                                <ChevronRight className="w-4 h-4 text-gray-400 group-hover:text-indigo-600 transition-colors" />
                              </div>
                              <div className="font-medium text-gray-900 mb-1">{tagInfo.tag}</div>
                              <div className="flex items-center gap-1 text-sm text-gray-600">
                                <FileText className="w-3.5 h-3.5" />
                                <span>{tagInfo.count} {t.paperCount}</span>
                              </div>
                            </button>
                          ))}
                        </div>
                      ) : (
                        <div className="text-center py-12">
                          <Tag className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                          <p className="text-gray-500">{t.noResults}</p>
                        </div>
                      )}
                    </div>
                  </>
                ) : (
                  <>
                    {/* Tag Detail View */}
                    <div className="p-6 border-b border-gray-200">
                      <button
                        onClick={() => setSelectedTag(null)}
                        className="text-sm text-indigo-600 hover:text-indigo-700 mb-4 flex items-center gap-1"
                      >
                        <ChevronRight className="w-4 h-4 rotate-180" />
                        {t.backToTags}
                      </button>
                      <div className="flex items-center justify-between">
                        <div>
                          <div className="flex items-center gap-2 mb-2">
                            <Hash className="w-6 h-6 text-indigo-600" />
                            <h3 className="text-2xl font-bold text-gray-900">{selectedTag}</h3>
                          </div>
                          <div className="flex items-center gap-2 text-sm text-gray-600">
                            <FileText className="w-4 h-4" />
                            <span>{selectedTagInfo?.count} {t.paperCount}</span>
                          </div>
                        </div>
                        <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm font-medium">
                          {t.highlightInPaper}
                        </button>
                      </div>
                    </div>

                    {/* Related Papers */}
                    <div className="flex-1 overflow-y-auto p-6">
                      <h4 className="font-semibold text-gray-900 mb-4">{t.relatedPapers}</h4>
                      <div className="space-y-3">
                        {selectedTagInfo?.papers.map((paper) => (
                          <button
                            key={paper.id}
                            onClick={() => {
                              onPaperClick(paper.id);
                              onClose();
                            }}
                            className="w-full p-4 bg-gradient-to-r from-gray-50 to-blue-50 rounded-xl hover:from-blue-50 hover:to-indigo-50 transition-all border border-gray-200 text-left"
                          >
                            <h5 className="font-medium text-gray-900 mb-2">{paper.title}</h5>
                            <div className="flex items-center gap-3 text-sm text-gray-600">
                              <span>{paper.authors.join(', ')}</span>
                              <span>•</span>
                              <span>{paper.conference} {paper.year}</span>
                            </div>
                          </button>
                        ))}
                      </div>
                    </div>
                  </>
                )}
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
