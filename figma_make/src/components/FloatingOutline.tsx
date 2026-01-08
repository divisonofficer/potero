import { useState } from 'react';
import { ChevronDown, ChevronRight, Sigma, Image as ImageIcon, X, FileText, AlignLeft, BookMarked } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

interface OutlineItem {
  id: string;
  type: 'equation' | 'figure' | 'section' | 'paragraph' | 'reference';
  title: string;
  content: string;
  page: number;
  elementId?: string; // ID for scrolling to element
}

interface FloatingOutlineProps {
  onClose: () => void;
  onItemClick: (item: OutlineItem) => void;
}

export function FloatingOutline({ onClose, onItemClick }: FloatingOutlineProps) {
  const [isExpanded, setIsExpanded] = useState(true);
  const [showEquations, setShowEquations] = useState(true);
  const [showFigures, setShowFigures] = useState(true);
  const [showSections, setShowSections] = useState(true);
  const [showReferences, setShowReferences] = useState(false);

  // Mock data - in real app, this would be extracted from PDF
  const sections: OutlineItem[] = [
    {
      id: 'sec-abstract',
      type: 'section',
      title: 'Abstract',
      content: 'Paper summary',
      page: 1,
      elementId: 'section-abstract'
    },
    {
      id: 'sec-1',
      type: 'section',
      title: '1. Introduction',
      content: 'Background and motivation',
      page: 1,
      elementId: 'section-1'
    },
    {
      id: 'sec-2',
      type: 'section',
      title: '2. Related Work',
      content: 'Literature review',
      page: 1,
      elementId: 'section-2'
    },
    {
      id: 'sec-3',
      type: 'section',
      title: '3. Methodology',
      content: 'Proposed approach',
      page: 2,
      elementId: 'section-3'
    },
    {
      id: 'sec-4',
      type: 'section',
      title: '4. Experiments',
      content: 'Results and analysis',
      page: 3,
      elementId: 'section-4'
    },
    {
      id: 'sec-5',
      type: 'section',
      title: '5. Conclusion',
      content: 'Summary and future work',
      page: 4,
      elementId: 'section-5'
    },
  ];

  const equations: OutlineItem[] = [
    {
      id: 'eq1',
      type: 'equation',
      title: 'Loss Function',
      content: 'L(θ) = -∑ log P(y|x; θ)',
      page: 2,
      elementId: 'equation-1'
    },
    {
      id: 'eq2',
      type: 'equation',
      title: 'Attention Mechanism',
      content: 'Attention(Q,K,V) = softmax(QK^T / √d_k)V',
      page: 2,
      elementId: 'equation-2'
    },
    {
      id: 'eq3',
      type: 'equation',
      title: 'Gradient Descent',
      content: 'θ_{t+1} = θ_t - α∇L(θ_t)',
      page: 3,
      elementId: 'equation-3'
    },
    {
      id: 'eq4',
      type: 'equation',
      title: 'Cross Entropy',
      content: 'H(p,q) = -∑ p(x)log(q(x))',
      page: 4,
      elementId: 'equation-4'
    },
  ];

  const figures: OutlineItem[] = [
    {
      id: 'fig1',
      type: 'figure',
      title: 'Figure 1: Network Architecture',
      content: 'Overview of the proposed model',
      page: 2,
      elementId: 'figure-1'
    },
    {
      id: 'fig2',
      type: 'figure',
      title: 'Figure 2: Training Curves',
      content: 'Loss and accuracy over epochs',
      page: 3,
      elementId: 'figure-2'
    },
    {
      id: 'fig3',
      type: 'figure',
      title: 'Figure 3: Attention Visualization',
      content: 'Heatmap of attention weights',
      page: 5,
      elementId: 'figure-3'
    },
  ];

  const references: OutlineItem[] = [
    {
      id: 'ref1',
      type: 'reference',
      title: '[1] Vaswani et al., 2017',
      content: 'Attention Is All You Need',
      page: 6,
      elementId: 'reference-1'
    },
    {
      id: 'ref2',
      type: 'reference',
      title: '[2] Devlin et al., 2019',
      content: 'BERT: Pre-training of Deep Bidirectional Transformers',
      page: 6,
      elementId: 'reference-2'
    },
    {
      id: 'ref3',
      type: 'reference',
      title: '[3] He et al., 2016',
      content: 'Deep Residual Learning for Image Recognition',
      page: 6,
      elementId: 'reference-3'
    },
    {
      id: 'ref4',
      type: 'reference',
      title: '[4] Goodfellow et al., 2014',
      content: 'Generative Adversarial Networks',
      page: 6,
      elementId: 'reference-4'
    },
    {
      id: 'ref5',
      type: 'reference',
      title: '[5] Krizhevsky et al., 2012',
      content: 'ImageNet Classification with Deep Convolutional Neural Networks',
      page: 6,
      elementId: 'reference-5'
    },
  ];

  return (
    <AnimatePresence>
      <motion.div
        initial={{ x: 300, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        exit={{ x: 300, opacity: 0 }}
        className="fixed right-6 top-24 w-80 bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-gray-200 overflow-hidden max-h-[calc(100vh-150px)] flex flex-col z-40"
      >
        {/* Header */}
        <div className="px-4 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 flex items-center justify-between">
          <h3 className="font-semibold text-white">Outline</h3>
          <button
            onClick={onClose}
            className="p-1 hover:bg-white/20 rounded transition-colors text-white"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {/* Sections Section */}
          <div>
            <button
              onClick={() => setShowSections(!showSections)}
              className="w-full flex items-center justify-between px-3 py-2 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors group"
            >
              <div className="flex items-center gap-2">
                <AlignLeft className="w-4 h-4 text-gray-600" />
                <span className="font-medium text-gray-900 text-sm">
                  Sections ({sections.length})
                </span>
              </div>
              {showSections ? (
                <ChevronDown className="w-4 h-4 text-gray-600" />
              ) : (
                <ChevronRight className="w-4 h-4 text-gray-600" />
              )}
            </button>

            {showSections && (
              <div className="mt-2 space-y-1 ml-2">
                {sections.map((sec, idx) => (
                  <motion.button
                    key={sec.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    onClick={() => onItemClick(sec)}
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors group"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 group-hover:text-gray-600 transition-colors">
                          {sec.title}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          {sec.content}
                        </p>
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        p.{sec.page}
                      </span>
                    </div>
                  </motion.button>
                ))}
              </div>
            )}
          </div>

          {/* Equations Section */}
          <div>
            <button
              onClick={() => setShowEquations(!showEquations)}
              className="w-full flex items-center justify-between px-3 py-2 bg-purple-50 hover:bg-purple-100 rounded-lg transition-colors group"
            >
              <div className="flex items-center gap-2">
                <Sigma className="w-4 h-4 text-purple-600" />
                <span className="font-medium text-purple-900 text-sm">
                  Equations ({equations.length})
                </span>
              </div>
              {showEquations ? (
                <ChevronDown className="w-4 h-4 text-purple-600" />
              ) : (
                <ChevronRight className="w-4 h-4 text-purple-600" />
              )}
            </button>

            {showEquations && (
              <div className="mt-2 space-y-1 ml-2">
                {equations.map((eq, idx) => (
                  <motion.button
                    key={eq.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    onClick={() => onItemClick(eq)}
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-purple-50 transition-colors group"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 group-hover:text-purple-600 transition-colors">
                          {eq.title}
                        </p>
                        <p className="text-xs text-gray-500 font-mono mt-1 truncate">
                          {eq.content}
                        </p>
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        p.{eq.page}
                      </span>
                    </div>
                  </motion.button>
                ))}
              </div>
            )}
          </div>

          {/* Figures Section */}
          <div>
            <button
              onClick={() => setShowFigures(!showFigures)}
              className="w-full flex items-center justify-between px-3 py-2 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors group"
            >
              <div className="flex items-center gap-2">
                <ImageIcon className="w-4 h-4 text-blue-600" />
                <span className="font-medium text-blue-900 text-sm">
                  Figures ({figures.length})
                </span>
              </div>
              {showFigures ? (
                <ChevronDown className="w-4 h-4 text-blue-600" />
              ) : (
                <ChevronRight className="w-4 h-4 text-blue-600" />
              )}
            </button>

            {showFigures && (
              <div className="mt-2 space-y-1 ml-2">
                {figures.map((fig, idx) => (
                  <motion.button
                    key={fig.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    onClick={() => onItemClick(fig)}
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-blue-50 transition-colors group"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 group-hover:text-blue-600 transition-colors">
                          {fig.title}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          {fig.content}
                        </p>
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        p.{fig.page}
                      </span>
                    </div>
                  </motion.button>
                ))}
              </div>
            )}
          </div>

          {/* References Section */}
          <div>
            <button
              onClick={() => setShowReferences(!showReferences)}
              className="w-full flex items-center justify-between px-3 py-2 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors group"
            >
              <div className="flex items-center gap-2">
                <FileText className="w-4 h-4 text-gray-600" />
                <span className="font-medium text-gray-900 text-sm">
                  References ({references.length})
                </span>
              </div>
              {showReferences ? (
                <ChevronDown className="w-4 h-4 text-gray-600" />
              ) : (
                <ChevronRight className="w-4 h-4 text-gray-600" />
              )}
            </button>

            {showReferences && (
              <div className="mt-2 space-y-1 ml-2">
                {references.map((ref, idx) => (
                  <motion.button
                    key={ref.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    onClick={() => onItemClick(ref)}
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors group"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 group-hover:text-gray-600 transition-colors">
                          {ref.title}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          {ref.content}
                        </p>
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        p.{ref.page}
                      </span>
                    </div>
                  </motion.button>
                ))}
              </div>
            )}
          </div>

          {/* Quick Stats */}
          <div className="mt-4 p-3 bg-gradient-to-r from-indigo-50 to-purple-50 rounded-lg border border-indigo-200">
            <div className="grid grid-cols-2 gap-3 text-center">
              <div>
                <div className="text-2xl font-bold text-indigo-600">{equations.length}</div>
                <div className="text-xs text-gray-600">Equations</div>
              </div>
              <div>
                <div className="text-2xl font-bold text-blue-600">{figures.length}</div>
                <div className="text-xs text-gray-600">Figures</div>
              </div>
            </div>
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}