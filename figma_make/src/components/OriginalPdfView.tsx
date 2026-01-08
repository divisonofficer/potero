import { useState } from 'react';
import { Highlighter, MessageCircle, Underline, Search, BookOpen, User, Globe, List } from 'lucide-react';
import { PdfFile } from '../App';
import { MathEquationModal } from './MathEquationModal';
import { FloatingOutline } from './FloatingOutline';

interface OriginalPdfViewProps {
  pdf: PdfFile;
  currentPage: number;
}

interface Annotation {
  id: string;
  type: 'highlight' | 'underline' | 'note';
  text: string;
  color: string;
  note?: string;
  position: { x: number; y: number };
}

interface MathEquation {
  equation: string;
  latex: string;
}

export function OriginalPdfView({ pdf, currentPage }: OriginalPdfViewProps) {
  const [selectedTool, setSelectedTool] = useState<'highlight' | 'underline' | 'note' | null>(null);
  const [selectedText, setSelectedText] = useState('');
  const [showContextMenu, setShowContextMenu] = useState(false);
  const [contextMenuPosition, setContextMenuPosition] = useState({ x: 0, y: 0 });
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEquation, setSelectedEquation] = useState<MathEquation | null>(null);
  const [showOutline, setShowOutline] = useState(false);

  // Auto-generated annotations from preprocessing
  const [annotations, setAnnotations] = useState<Annotation[]>([
    {
      id: 'auto-ann-1',
      type: 'highlight',
      text: 'attention mechanism',
      color: '#FEF08A',
      note: 'Core concept - Auto-tagged',
      position: { x: 0, y: 0 }
    },
    {
      id: 'auto-ann-2',
      type: 'highlight',
      text: 'transformer architecture',
      color: '#BBF7D0',
      note: 'Key technology - Auto-tagged',
      position: { x: 0, y: 0 }
    },
    {
      id: 'auto-ann-3',
      type: 'highlight',
      text: 'self-attention',
      color: '#BFDBFE',
      note: 'Important method - Auto-tagged',
      position: { x: 0, y: 0 }
    },
    {
      id: 'auto-ann-4',
      type: 'highlight',
      text: 'neural network',
      color: '#FBCFE8',
      note: 'Fundamental concept - Auto-tagged',
      position: { x: 0, y: 0 }
    },
    {
      id: 'auto-ann-5',
      type: 'highlight',
      text: 'state-of-the-art',
      color: '#DDD6FE',
      note: 'Performance indicator - Auto-tagged',
      position: { x: 0, y: 0 }
    },
    {
      id: 'auto-ann-6',
      type: 'highlight',
      text: 'multi-head attention',
      color: '#BBF7D0',
      note: 'Advanced technique - Auto-tagged',
      position: { x: 0, y: 0 }
    },
  ]);

  const handleTextSelection = () => {
    const selection = window.getSelection();
    const text = selection?.toString() || '';
    
    if (text.length > 0) {
      setSelectedText(text);
      setShowContextMenu(true);
      
      const range = selection?.getRangeAt(0);
      const rect = range?.getBoundingClientRect();
      if (rect) {
        setContextMenuPosition({ 
          x: rect.left + rect.width / 2, 
          y: rect.top - 10 
        });
      }
    } else {
      setShowContextMenu(false);
    }
  };

  const handleHighlight = (color: string) => {
    if (selectedText) {
      const newAnnotation: Annotation = {
        id: Date.now().toString(),
        type: 'highlight',
        text: selectedText,
        color,
        position: contextMenuPosition
      };
      setAnnotations([...annotations, newAnnotation]);
      setShowContextMenu(false);
    }
  };

  const handleTranslate = () => {
    alert(`Translating: "${selectedText}"\n\nTranslation: [Mock translation would appear here]`);
    setShowContextMenu(false);
  };

  const handleSearchRelated = () => {
    alert(`Searching for papers related to: "${selectedText}"\n\n[Related papers would appear here]`);
    setShowContextMenu(false);
  };

  const handleFindAuthor = () => {
    alert(`Finding information about author: \"${selectedText}\"\\n\\n[Author information would appear here]`);
    setShowContextMenu(false);
  };

  // Helper to render text with auto-highlights
  const renderWithHighlights = (text: string) => {
    let result = text;
    const segments: Array<{ text: string; color?: string; isHighlight: boolean }> = [];
    const positions: Array<{ start: number; end: number; color: string; text: string }> = [];
    
    // Find all auto-highlight positions
    annotations.forEach(annotation => {
      if (annotation.type === 'highlight') {
        const lowerText = result.toLowerCase();
        const lowerAnnotation = annotation.text.toLowerCase();
        let index = lowerText.indexOf(lowerAnnotation);
        
        while (index !== -1) {
          positions.push({
            start: index,
            end: index + annotation.text.length,
            color: annotation.color,
            text: result.substring(index, index + annotation.text.length)
          });
          index = lowerText.indexOf(lowerAnnotation, index + 1);
        }
      }
    });
    
    // Sort by start position
    positions.sort((a, b) => a.start - b.start);
    
    // Build segments
    let currentPos = 0;
    positions.forEach(pos => {
      if (pos.start > currentPos) {
        segments.push({ text: result.substring(currentPos, pos.start), isHighlight: false });
      }
      segments.push({ text: pos.text, color: pos.color, isHighlight: true });
      currentPos = pos.end;
    });
    
    if (currentPos < result.length) {
      segments.push({ text: result.substring(currentPos), isHighlight: false });
    }
    
    return segments.length > 0 ? (
      <>
        {segments.map((segment, idx) => 
          segment.isHighlight && segment.color ? (
            <mark
              key={idx}
              style={{ backgroundColor: segment.color }}
              className="px-0.5 rounded cursor-pointer transition-all"
              title="Auto-tagged by AI"
            >
              {segment.text}
            </mark>
          ) : (
            <span key={idx}>{segment.text}</span>
          )
        )}
      </>
    ) : text;
  };

  return (
    <div className="relative bg-gray-100 min-h-full">
      {/* Toolbar */}
      <div className="sticky top-0 z-20 bg-white border-b border-gray-200 px-6 py-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setSelectedTool(selectedTool === 'highlight' ? null : 'highlight')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-colors ${
              selectedTool === 'highlight' 
                ? 'bg-yellow-100 text-yellow-700' 
                : 'hover:bg-gray-100 text-gray-700'
            }`}
          >
            <Highlighter className="w-4 h-4" />
            Highlight
          </button>
          
          <button
            onClick={() => setSelectedTool(selectedTool === 'underline' ? null : 'underline')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-colors ${
              selectedTool === 'underline' 
                ? 'bg-blue-100 text-blue-700' 
                : 'hover:bg-gray-100 text-gray-700'
            }`}
          >
            <Underline className="w-4 h-4" />
            Underline
          </button>
          
          <button
            onClick={() => setSelectedTool(selectedTool === 'note' ? null : 'note')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-colors ${
              selectedTool === 'note' 
                ? 'bg-green-100 text-green-700' 
                : 'hover:bg-gray-100 text-gray-700'
            }`}
          >
            <MessageCircle className="w-4 h-4" />
            Add Note
          </button>

          <div className="ml-auto flex items-center gap-2">
            <button
              onClick={() => setShowSearch(!showSearch)}
              className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-gray-100 text-gray-700 transition-colors"
            >
              <Search className="w-4 h-4" />
              Search in PDF
            </button>
          </div>
        </div>

        {/* Search Bar */}
        {showSearch && (
          <div className="mt-3 flex items-center gap-2">
            <input
              type="text"
              placeholder="Search in document..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
            />
            <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">
              Find
            </button>
          </div>
        )}
      </div>

      {/* PDF Content */}
      <div className="flex justify-center py-8 px-6">
        <div 
          className="bg-white shadow-2xl w-full max-w-4xl min-h-[1100px] p-12"
          onMouseUp={handleTextSelection}
        >
          {/* Mock PDF Content for page {currentPage} */}
          <div className="space-y-6">
            <div className="border-b pb-6">
              <h1 className="text-2xl font-bold text-gray-900 mb-4">{pdf.title}</h1>
              <div className="text-sm text-gray-600 space-y-1">
                <p>{pdf.authors.join(', ')}</p>
                <p className="italic">{pdf.conference} {pdf.year}</p>
              </div>
            </div>

            <div id="section-abstract" className="scroll-mt-20 transition-all">
              <h2 className="text-lg font-semibold text-gray-900 mb-3">Abstract</h2>
              <p className="text-sm text-gray-700 leading-relaxed">{pdf.abstract}</p>
            </div>

            <div id="section-1" className="scroll-mt-20 transition-all">
              <h2 className="text-lg font-semibold text-gray-900 mb-3">1. Introduction</h2>
              <p className="text-sm text-gray-700 leading-relaxed mb-4">
                {renderWithHighlights('Machine learning has revolutionized the field of computer science, enabling computers to learn from data without being explicitly programmed. This paper explores advanced techniques in deep learning, focusing on novel architectures and optimization methods that have emerged in recent years.')}
              </p>
              <p className="text-sm text-gray-700 leading-relaxed mb-4">
                {renderWithHighlights('The rapid advancement of neural network architectures has led to breakthrough performance in various domains including computer vision, natural language processing, and reinforcement learning. Our work builds upon these foundations while introducing innovative approaches to address current limitations.')}
              </p>
            </div>

            <div id="section-2" className="scroll-mt-20 transition-all">
              <h2 className="text-lg font-semibold text-gray-900 mb-3">2. Related Work</h2>
              <p className="text-sm text-gray-700 leading-relaxed mb-4">
                {renderWithHighlights('Previous research in this domain has established several key principles. The transformer architecture, introduced by Vaswani et al., demonstrated the power of attention mechanisms. Subsequently, models like BERT and GPT have shown remarkable success in transfer learning scenarios.')}
              </p>
              <p className="text-sm text-gray-700 leading-relaxed">
                Recent work has focused on efficiency improvements, including knowledge distillation, pruning, and 
                quantization techniques. Our approach complements these efforts by introducing a novel training paradigm 
                that achieves better performance with fewer computational resources.
              </p>
            </div>

            {currentPage > 1 && (
              <>
                <div id="section-3" className="scroll-mt-20 transition-all">
                  <h2 className="text-lg font-semibold text-gray-900 mb-3">3. Methodology</h2>
                  <p className="text-sm text-gray-700 leading-relaxed mb-4">
                    Our proposed method consists of three main components: a novel attention mechanism, an adaptive 
                    learning rate scheduler, and a regularization technique specifically designed for deep networks.
                  </p>
                  
                  {/* Math Equations - Clickable */}
                  <div id="equation-1" className="my-6 p-4 bg-gray-50 rounded-lg border border-gray-200 scroll-mt-20 transition-all">
                    <p className="text-sm text-gray-700 mb-3">The loss function is defined as:</p>
                    <div
                      onClick={() => setSelectedEquation({
                        equation: 'L(θ) = -∑ log P(y|x; θ)',
                        latex: 'L(\\\\theta) = -\\\\sum_{i=1}^{n} \\\\log P(y_i | x_i; \\\\theta)'
                      })}
                      className="p-4 bg-white rounded border border-gray-300 hover:border-indigo-500 hover:shadow-md cursor-pointer transition-all group"
                    >
                      <div className="text-center font-serif text-lg text-gray-900 group-hover:text-indigo-600 transition-colors">
                        L(θ) = -∑ log P(y|x; θ)
                      </div>
                      <p className="text-xs text-gray-500 text-center mt-2">Click to explore this equation</p>
                    </div>
                  </div>

                  <p className="text-sm text-gray-700 leading-relaxed mb-4">
                    The attention mechanism is computed using the following formula:
                  </p>

                  <div id="equation-2" className="my-6 p-4 bg-gray-50 rounded-lg border border-gray-200 scroll-mt-20 transition-all">
                    <div
                      onClick={() => setSelectedEquation({
                        equation: 'Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V',
                        latex: '\\\\text{Attention}(Q, K, V) = \\\\text{softmax}\\\\left(\\\\frac{QK^T}{\\\\sqrt{d_k}}\\\\right)V'
                      })}
                      className="p-4 bg-white rounded border border-gray-300 hover:border-indigo-500 hover:shadow-md cursor-pointer transition-all group"
                    >
                      <div className="text-center font-serif text-lg text-gray-900 group-hover:text-indigo-600 transition-colors">
                        Attention(Q,K,V) = softmax(QKᵀ / √dₖ)V
                      </div>
                      <p className="text-xs text-gray-500 text-center mt-2">Click to explore this equation</p>
                    </div>
                  </div>

                  <p className="text-sm text-gray-700 leading-relaxed">
                    The attention mechanism improves upon standard multi-head attention by incorporating positional 
                    encoding directly into the attention weights, allowing the model to better capture long-range 
                    dependencies in sequential data.
                  </p>

                  {/* Figure Example */}
                  <div id="figure-1" className="my-6 p-4 bg-gray-50 rounded-lg border border-gray-200 scroll-mt-20 transition-all">
                    <div className="bg-gradient-to-br from-indigo-100 to-purple-100 rounded-lg h-48 flex items-center justify-center mb-2">
                      <p className="text-gray-500 text-sm">Figure 1: Network Architecture Overview</p>
                    </div>
                    <p className="text-xs text-gray-600 text-center">
                      <strong>Figure 1:</strong> Schematic representation of the proposed attention mechanism.
                    </p>
                  </div>
                </div>

                <div id="section-4" className="scroll-mt-20 transition-all">
                  <h2 className="text-lg font-semibold text-gray-900 mb-3">4. Experiments</h2>
                  <p className="text-sm text-gray-700 leading-relaxed mb-4">
                    We evaluate our approach on several benchmark datasets and compare against state-of-the-art baselines.
                  </p>

                  {/* Additional Figures */}
                  <div id="figure-2" className="my-6 p-4 bg-gray-50 rounded-lg border border-gray-200 scroll-mt-20 transition-all">
                    <div className="bg-gradient-to-br from-green-100 to-blue-100 rounded-lg h-48 flex items-center justify-center mb-2">
                      <p className="text-gray-500 text-sm">Figure 2: Training Curves</p>
                    </div>
                    <p className="text-xs text-gray-600 text-center">
                      <strong>Figure 2:</strong> Loss and accuracy over training epochs.
                    </p>
                  </div>

                  <div id="equation-3" className="my-6 p-4 bg-gray-50 rounded-lg border border-gray-200 scroll-mt-20 transition-all">
                    <p className="text-sm text-gray-700 mb-3">The gradient descent update rule:</p>
                    <div className="p-4 bg-white rounded border border-gray-300">
                      <div className="text-center font-serif text-lg text-gray-900">
                        θ<sub>t+1</sub> = θ<sub>t</sub> - α∇L(θ<sub>t</sub>)
                      </div>
                    </div>
                  </div>
                </div>

                <div id="section-5" className="scroll-mt-20 transition-all">
                  <h2 className="text-lg font-semibold text-gray-900 mb-3">5. Conclusion</h2>
                  <p className="text-sm text-gray-700 leading-relaxed mb-4">
                    We have presented a novel approach that achieves state-of-the-art results while maintaining computational efficiency.
                  </p>
                </div>

                {/* References Section */}
                <div className="border-t pt-6 mt-8">
                  <h2 className="text-lg font-semibold text-gray-900 mb-4">References</h2>
                  <div className="space-y-3 text-sm text-gray-700">
                    <div id="reference-1" className="scroll-mt-20 transition-all">
                      <p><strong>[1]</strong> Vaswani, A., et al. (2017). Attention Is All You Need. <em>NeurIPS</em>.</p>
                    </div>
                    <div id="reference-2" className="scroll-mt-20 transition-all">
                      <p><strong>[2]</strong> Devlin, J., et al. (2019). BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding. <em>ACL</em>.</p>
                    </div>
                    <div id="reference-3" className="scroll-mt-20 transition-all">
                      <p><strong>[3]</strong> He, K., et al. (2016). Deep Residual Learning for Image Recognition. <em>CVPR</em>.</p>
                    </div>
                    <div id="reference-4" className="scroll-mt-20 transition-all">
                      <p><strong>[4]</strong> Goodfellow, I., et al. (2014). Generative Adversarial Networks. <em>NeurIPS</em>.</p>
                    </div>
                    <div id="reference-5" className="scroll-mt-20 transition-all">
                      <p><strong>[5]</strong> Krizhevsky, A., et al. (2012). ImageNet Classification with Deep Convolutional Neural Networks. <em>NeurIPS</em>.</p>
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Context Menu */}
      {showContextMenu && (
        <div
          className="fixed z-50 bg-white rounded-xl shadow-2xl border border-gray-200 py-2 min-w-[200px]"
          style={{
            left: `${contextMenuPosition.x}px`,
            top: `${contextMenuPosition.y}px`,
            transform: 'translate(-50%, -100%)'
          }}
        >
          <div className="px-3 py-2 border-b border-gray-100">
            <p className="text-xs text-gray-500 truncate max-w-[200px]">"{selectedText}"</p>
          </div>
          
          <button
            onClick={() => handleHighlight('yellow')}
            className="w-full px-4 py-2 text-left text-sm hover:bg-yellow-50 flex items-center gap-2 text-gray-700"
          >
            <div className="w-4 h-4 bg-yellow-300 rounded"></div>
            Highlight
          </button>
          
          <button
            onClick={handleTranslate}
            className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 flex items-center gap-2 text-gray-700"
          >
            <Globe className="w-4 h-4" />
            Translate
          </button>
          
          <button
            onClick={handleSearchRelated}
            className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 flex items-center gap-2 text-gray-700"
          >
            <BookOpen className="w-4 h-4" />
            Find Related Papers
          </button>
          
          <button
            onClick={handleFindAuthor}
            className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 flex items-center gap-2 text-gray-700"
          >
            <User className="w-4 h-4" />
            Author Info
          </button>
        </div>
      )}

      {/* Annotations List */}
      {annotations.length > 0 && (
        <div className="fixed right-6 top-32 w-64 bg-white rounded-xl shadow-lg border border-gray-200 p-4 max-h-96 overflow-y-auto">
          <h3 className="font-semibold text-gray-900 mb-3">Annotations</h3>
          <div className="space-y-2">
            {annotations.map(annotation => (
              <div key={annotation.id} className="p-2 bg-gray-50 rounded-lg">
                <div className={`w-full h-1 rounded mb-2 bg-${annotation.color}-300`}></div>
                <p className="text-xs text-gray-700">{annotation.text}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Math Equation Modal */}
      {selectedEquation && (
        <MathEquationModal
          equation={selectedEquation.equation}
          latex={selectedEquation.latex}
          onClose={() => setSelectedEquation(null)}
        />
      )}

      {/* Floating Outline */}
      {showOutline && (
        <FloatingOutline
          onClose={() => setShowOutline(false)}
          onItemClick={(item) => {
            // Scroll to element if elementId exists
            if (item.elementId) {
              const element = document.getElementById(item.elementId);
              if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'center' });
                // Highlight animation
                element.classList.add('outline-highlight');
                setTimeout(() => {
                  element.classList.remove('outline-highlight');
                }, 2000);
              }
            }
            
            // Show equation modal for equations
            if (item.type === 'equation') {
              setSelectedEquation({
                equation: item.content,
                latex: item.content
              });
            }
          }}
        />
      )}

      {/* Floating Outline Toggle Button */}
      <button
        onClick={() => setShowOutline(!showOutline)}
        className={`fixed right-6 bottom-6 p-4 rounded-full shadow-lg transition-all ${
          showOutline 
            ? 'bg-indigo-600 text-white' 
            : 'bg-white text-indigo-600 hover:bg-indigo-50'
        }`}
      >
        <List className="w-6 h-6" />
      </button>
    </div>
  );
}