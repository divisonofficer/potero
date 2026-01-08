import { X, ExternalLink, TrendingUp, Calendar, Building, User, FileText, BookOpen } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

interface InfoModalProps {
  isOpen: boolean;
  onClose: () => void;
  type: 'author' | 'conference' | 'doi';
  data: string;
  onViewDetails: () => void;
}

export function InfoModal({ isOpen, onClose, type, data, onViewDetails }: InfoModalProps) {
  // Mock data for demonstration
  const getModalContent = () => {
    switch (type) {
      case 'author':
        return {
          icon: <User className="w-6 h-6" />,
          title: data,
          subtitle: 'Researcher Profile',
          stats: [
            { label: 'Publications', value: '127', icon: <FileText className="w-4 h-4" /> },
            { label: 'Citations', value: '12,456', icon: <TrendingUp className="w-4 h-4" /> },
            { label: 'h-index', value: '42', icon: <BookOpen className="w-4 h-4" /> },
          ],
          description: 'Leading researcher in machine learning and artificial intelligence with extensive contributions to deep learning architectures.',
          recentPapers: [
            { title: 'Attention Is All You Need', year: 2023, citations: 1234 },
            { title: 'BERT: Pre-training of Deep Bidirectional Transformers', year: 2022, citations: 2345 },
            { title: 'GPT-3: Language Models are Few-Shot Learners', year: 2024, citations: 3456 },
          ],
          affiliations: ['Stanford University', 'Google AI'],
          researchInterests: ['Deep Learning', 'Natural Language Processing', 'Computer Vision'],
        };
      
      case 'conference':
        return {
          icon: <Building className="w-6 h-6" />,
          title: data,
          subtitle: 'Conference Information',
          stats: [
            { label: 'Acceptance Rate', value: '23%', icon: <TrendingUp className="w-4 h-4" /> },
            { label: 'Papers (2024)', value: '1,245', icon: <FileText className="w-4 h-4" /> },
            { label: 'Impact Factor', value: '8.7', icon: <BookOpen className="w-4 h-4" /> },
          ],
          description: 'Premier international conference on computer vision and pattern recognition, featuring cutting-edge research in visual recognition and understanding.',
          recentPapers: [
            { title: 'Vision Transformers for Dense Prediction', year: 2024, citations: 567 },
            { title: 'Self-Supervised Learning for Computer Vision', year: 2023, citations: 789 },
            { title: 'Diffusion Models for Image Generation', year: 2024, citations: 890 },
          ],
          location: 'Seattle, WA, USA',
          nextDate: 'June 17-21, 2026',
          website: 'https://cvpr2026.thecvf.com',
        };
      
      case 'doi':
        return {
          icon: <ExternalLink className="w-6 h-6" />,
          title: data,
          subtitle: 'Digital Object Identifier',
          stats: [
            { label: 'Citations', value: '234', icon: <TrendingUp className="w-4 h-4" /> },
            { label: 'Published', value: '2024', icon: <Calendar className="w-4 h-4" /> },
            { label: 'Access', value: 'Open', icon: <FileText className="w-4 h-4" /> },
          ],
          description: 'Permanent digital identifier for academic, professional, and government information. Click "View Details" to access external resources.',
          links: [
            { label: 'DOI.org', url: `https://doi.org/${data}` },
            { label: 'Google Scholar', url: `https://scholar.google.com/scholar?q=${encodeURIComponent(data)}` },
            { label: 'Semantic Scholar', url: `https://www.semanticscholar.org/search?q=${encodeURIComponent(data)}` },
          ],
        };
    }
  };

  const content = getModalContent();

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
              className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-hidden pointer-events-auto"
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
                  {content.icon}
                  <div>
                    <p className="text-sm text-white/80">{content.subtitle}</p>
                    <h2 className="text-xl font-bold">{content.title}</h2>
                  </div>
                </div>
              </div>

              {/* Content */}
              <div className="p-6 overflow-y-auto max-h-[calc(80vh-200px)]">
                {/* Stats */}
                <div className="grid grid-cols-3 gap-4 mb-6">
                  {content.stats.map((stat, idx) => (
                    <div key={idx} className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-4 text-center">
                      <div className="flex items-center justify-center mb-2 text-indigo-600">
                        {stat.icon}
                      </div>
                      <div className="text-2xl font-bold text-gray-900">{stat.value}</div>
                      <div className="text-xs text-gray-600 mt-1">{stat.label}</div>
                    </div>
                  ))}
                </div>

                {/* Description */}
                <div className="mb-6">
                  <h3 className="font-semibold text-gray-900 mb-2">Overview</h3>
                  <p className="text-sm text-gray-600 leading-relaxed">{content.description}</p>
                </div>

                {/* Additional Info */}
                {type === 'author' && (
                  <>
                    <div className="mb-6">
                      <h3 className="font-semibold text-gray-900 mb-3">Affiliations</h3>
                      <div className="flex flex-wrap gap-2">
                        {content.affiliations?.map((aff, idx) => (
                          <span key={idx} className="px-3 py-1.5 bg-gray-100 text-gray-700 rounded-lg text-sm">
                            {aff}
                          </span>
                        ))}
                      </div>
                    </div>
                    <div className="mb-6">
                      <h3 className="font-semibold text-gray-900 mb-3">Research Interests</h3>
                      <div className="flex flex-wrap gap-2">
                        {content.researchInterests?.map((interest, idx) => (
                          <span key={idx} className="px-3 py-1.5 bg-indigo-50 text-indigo-600 rounded-lg text-sm">
                            {interest}
                          </span>
                        ))}
                      </div>
                    </div>
                  </>
                )}

                {type === 'conference' && (
                  <div className="mb-6 grid grid-cols-2 gap-4">
                    <div>
                      <h3 className="font-semibold text-gray-900 mb-2">Location</h3>
                      <p className="text-sm text-gray-600">{content.location}</p>
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900 mb-2">Next Event</h3>
                      <p className="text-sm text-gray-600">{content.nextDate}</p>
                    </div>
                  </div>
                )}

                {type === 'doi' && (
                  <div className="mb-6">
                    <h3 className="font-semibold text-gray-900 mb-3">External Links</h3>
                    <div className="space-y-2">
                      {content.links?.map((link, idx) => (
                        <a
                          key={idx}
                          href={link.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors group"
                        >
                          <span className="text-sm text-gray-700">{link.label}</span>
                          <ExternalLink className="w-4 h-4 text-gray-400 group-hover:text-indigo-600 transition-colors" />
                        </a>
                      ))}
                    </div>
                  </div>
                )}

                {/* Recent Papers */}
                {(type === 'author' || type === 'conference') && (
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-3">Recent Papers</h3>
                    <div className="space-y-2">
                      {content.recentPapers?.map((paper, idx) => (
                        <div key={idx} className="p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer">
                          <h4 className="text-sm font-medium text-gray-900 mb-1">{paper.title}</h4>
                          <div className="flex items-center gap-3 text-xs text-gray-600">
                            <span>{paper.year}</span>
                            <span>•</span>
                            <span className="flex items-center gap-1">
                              <TrendingUp className="w-3 h-3" />
                              {paper.citations} citations
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Footer */}
              <div className="border-t border-gray-200 px-6 py-4 bg-gray-50 flex items-center justify-between">
                <button
                  onClick={onClose}
                  className="px-4 py-2 text-gray-700 hover:bg-gray-200 rounded-lg transition-colors"
                >
                  Close
                </button>
                <button
                  onClick={onViewDetails}
                  className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  View Details
                  <ExternalLink className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
