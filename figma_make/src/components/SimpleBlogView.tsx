import { useState } from 'react';
import { PdfFile } from '../App';
import { Calendar, TrendingUp, Tag, ExternalLink, Languages, Highlighter, MessageSquare, Bookmark, Hash } from 'lucide-react';
import { TagModal } from './TagModal';

interface SimpleBlogViewProps {
  pdf: PdfFile;
}

type HighlightColor = 'yellow' | 'green' | 'blue' | 'pink' | 'purple';

interface Highlight {
  id: string;
  text: string;
  color: HighlightColor;
  note?: string;
}

export function SimpleBlogView({ pdf }: SimpleBlogViewProps) {
  const [isTranslated, setIsTranslated] = useState(false);
  const [isTranslating, setIsTranslating] = useState(false);
  const [selectedColor, setSelectedColor] = useState<HighlightColor>('yellow');
  const [showHighlightToolbar, setShowHighlightToolbar] = useState(false);
  const [toolbarPosition, setToolbarPosition] = useState({ x: 0, y: 0 });
  const [selectedText, setSelectedText] = useState('');
  const [showTagModal, setShowTagModal] = useState(false);
  const [language, setLanguage] = useState<'en' | 'ko'>('en');
  
  // Check if blog view is available
  if (!pdf.hasBlogView) {
    return (
      <div className="h-full flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 p-8">
        <div className="max-w-md text-center">
          <div className="w-20 h-20 bg-gray-200 rounded-full flex items-center justify-center mx-auto mb-6">
            <MessageSquare className="w-10 h-10 text-gray-400" />
          </div>
          <h3 className="text-2xl font-semibold text-gray-900 mb-3">
            Blog View Not Available
          </h3>
          <p className="text-gray-600 mb-6">
            This paper hasn't been processed into blog format yet. The blog view requires additional preprocessing to convert LaTeX content into a readable format.
          </p>
          <div className="bg-white rounded-xl p-4 border border-gray-200">
            <p className="text-sm text-gray-500">
              <strong className="text-gray-700">Available:</strong> Original PDF View
            </p>
          </div>
        </div>
      </div>
    );
  }
  
  // Auto-generated highlights from preprocessing
  const [highlights, setHighlights] = useState<Highlight[]>([
    {
      id: 'auto-1',
      text: 'attention mechanism',
      color: 'yellow',
      note: 'Core concept - Auto-tagged'
    },
    {
      id: 'auto-2',
      text: 'transformer architecture',
      color: 'green',
      note: 'Key technology - Auto-tagged'
    },
    {
      id: 'auto-3',
      text: 'self-attention',
      color: 'blue',
      note: 'Important method - Auto-tagged'
    },
    {
      id: 'auto-4',
      text: 'neural network',
      color: 'pink',
      note: 'Fundamental concept - Auto-tagged'
    },
    {
      id: 'auto-5',
      text: 'state-of-the-art',
      color: 'purple',
      note: 'Performance indicator - Auto-tagged'
    },
    {
      id: 'auto-6',
      text: 'encoder-decoder',
      color: 'yellow',
      note: 'Architecture pattern - Auto-tagged'
    },
    {
      id: 'auto-7',
      text: 'multi-head attention',
      color: 'green',
      note: 'Advanced technique - Auto-tagged'
    },
  ]);

  const handleTranslate = async () => {
    setIsTranslating(true);
    // Simulate translation delay
    await new Promise(resolve => setTimeout(resolve, 1500));
    setIsTranslated(!isTranslated);
    setIsTranslating(false);
  };

  const handleTextSelection = () => {
    const selection = window.getSelection();
    const text = selection?.toString().trim();
    
    if (text && text.length > 0) {
      const range = selection?.getRangeAt(0);
      const rect = range?.getBoundingClientRect();
      
      if (rect) {
        setSelectedText(text);
        setToolbarPosition({
          x: rect.left + rect.width / 2,
          y: rect.top - 50
        });
        setShowHighlightToolbar(true);
      }
    } else {
      setShowHighlightToolbar(false);
    }
  };

  const addHighlight = (color: HighlightColor) => {
    if (selectedText) {
      const newHighlight: Highlight = {
        id: Date.now().toString(),
        text: selectedText,
        color
      };
      setHighlights([...highlights, newHighlight]);
      setShowHighlightToolbar(false);
      window.getSelection()?.removeAllRanges();
    }
  };

  // Helper function to apply highlights to text
  const applyHighlights = (text: string) => {
    if (!highlights.length) return text;
    
    let result = text;
    const segments: Array<{ text: string; color?: HighlightColor; isHighlight: boolean }> = [];
    
    // Find all highlight positions
    const highlightPositions: Array<{ start: number; end: number; color: HighlightColor; text: string }> = [];
    
    highlights.forEach(highlight => {
      const lowerText = result.toLowerCase();
      const lowerHighlight = highlight.text.toLowerCase();
      let index = lowerText.indexOf(lowerHighlight);
      
      while (index !== -1) {
        highlightPositions.push({
          start: index,
          end: index + highlight.text.length,
          color: highlight.color,
          text: result.substring(index, index + highlight.text.length)
        });
        index = lowerText.indexOf(lowerHighlight, index + 1);
      }
    });
    
    // Sort by start position
    highlightPositions.sort((a, b) => a.start - b.start);
    
    // Build segments
    let currentPos = 0;
    highlightPositions.forEach(pos => {
      if (pos.start > currentPos) {
        segments.push({ text: result.substring(currentPos, pos.start), isHighlight: false });
      }
      segments.push({ text: pos.text, color: pos.color, isHighlight: true });
      currentPos = pos.end;
    });
    
    if (currentPos < result.length) {
      segments.push({ text: result.substring(currentPos), isHighlight: false });
    }
    
    return segments.length > 0 ? segments : [{ text: result, isHighlight: false }];
  };

  // Render text with highlights
  const HighlightedText = ({ children }: { children: string }) => {
    const segments = applyHighlights(children);
    
    if (typeof segments === 'string') {
      return <>{segments}</>;
    }
    
    return (
      <>
        {segments.map((segment, idx) => 
          segment.isHighlight && segment.color ? (
            <mark
              key={idx}
              className={`${highlightColors[segment.color]} px-1 rounded cursor-pointer transition-all hover:shadow-sm`}
              title={`Auto-tagged: ${segment.text}`}
            >
              {segment.text}
            </mark>
          ) : (
            <span key={idx}>{segment.text}</span>
          )
        )}
      </>
    );
  };

  const highlightColors: Record<HighlightColor, string> = {
    yellow: 'bg-yellow-200',
    green: 'bg-green-200',
    blue: 'bg-blue-200',
    pink: 'bg-pink-200',
    purple: 'bg-purple-200'
  };

  const colorButtons: Array<{ color: HighlightColor; label: string; bg: string }> = [
    { color: 'yellow', label: '🟡', bg: 'bg-yellow-400 hover:bg-yellow-500' },
    { color: 'green', label: '🟢', bg: 'bg-green-400 hover:bg-green-500' },
    { color: 'blue', label: '🔵', bg: 'bg-blue-400 hover:bg-blue-500' },
    { color: 'pink', label: '🔴', bg: 'bg-pink-400 hover:bg-pink-500' },
    { color: 'purple', label: '🟣', bg: 'bg-purple-400 hover:bg-purple-500' },
  ];

  // Mock Korean translations
  const translations = {
    title: '딥러닝을 위한 주목 메커니즘: 포괄적 설문조사',
    abstract: '본 논문에서는 딥러닝 모델에서 주목(attention) 메커니즘의 최근 발전에 대한 포괄적인 설문조사를 제시합니다. 주목 메커니즘은 신경망이 입력 데이터의 가장 관련성 높은 부분에 집중할 수 있도록 하여, 컴퓨터 비전, 자연어 처리, 강화학습 등 다양한 분야에서 획기적인 성능 향상을 이끌어냈습니다.',
    introduction: {
      title: '서론',
      content: [
        '머신러닝은 컴퓨터가 명시적으로 프로그래밍되지 않고도 데이터로부터 학습할 수 있게 함으로써 컴퓨터 과학 분야를 혁신했습니다. 이 획기적인 논문은 딥러닝의 고급 기술을 탐구하며, 특히 최근 몇 년간 등장한 새로운 아키텍처와 최적화 방법에 중점을 둡니다.',
        '신경망 아키텍처의 급속한 발전은 컴퓨터 비전, 자연어 처리, 강화학습 등 다양한 분야에서 획기적인 성능을 가져왔습니다. 우리의 연구는 이러한 기반 위에 구축되면서 현재 분야의 한계를 해결하기 위한 혁신적인 접근 방식을 도입합니다.',
      ],
    },
    keyContributions: {
      title: '주요 기여',
      items: [
        '기준 모델 대비 성능을 15% 향상시키는 새로운 주목 메커니즘',
        '학습 시간을 30% 단축하는 적응형 학습률 스케줄러',
        '최신 결과를 입증하는 여러 벤치마크 데이터셋에 대한 포괄적인 평가',
      ],
    },
    relatedWork: {
      title: '관련 연구',
      content: [
        '이 분야의 이전 연구는 현대 딥러닝의 기초를 형성한 몇 가지 핵심 원칙을 확립했습니다. Vaswani 등이 2017년 발표한 획기적인 논문에서 소개한 트랜스포머 아키텍처는 장거리 의존성을 포착하는 데 있어 주목 메커니즘의 놀라운 힘을 보여주었습니다.',
        '이후 BERT와 GPT와 같은 모델들은 전이 학습 시나리오에서 놀라운 성공을 거두며 광범위한 자연어 처리 작업에서 최신 결과를 달성했습니다. 이러한 획기적인 발전은 주목 기반 모델의 다양한 측면을 탐구하는 수많은 후속 연구에 영감을 주었습니다.',
      ],
    },
    methodology: {
      title: '방법론',
      intro: '우리가 제안한 방법은 뛰어난 성능을 달성하기 위해 시너지 효과를 발휘하는 세 가지 주요 구성 요소로 이루어져 있습니다:',
      components: [
        { title: '새로운 주목', desc: '통합된 위치 인코딩을 갖춘 향상된 멀티헤드 주목' },
        { title: '적응형 스케줄러', desc: '검증 메트릭을 기반으로 한 동적 학습률 조정' },
        { title: '정규화', desc: '깊은 네트워크에서 과적합을 방지하는 특수 기술' },
      ],
      conclusion: '주목 메커니즘은 표준 멀티헤드 주목을 개선하여 위치 인코딩을 주목 가중치에 직접 통합함으로써 순차 데이터에서 장거리 의존성을 더 잘 포착하면서 계산 효율성을 유지합니다.',
    },
    results: {
      title: '실험 결과',
      intro: '우리 접근 방식의 효과를 검증���기 위해 여러 벤치마크 데이터셋에서 광범위한 실험을 수행했습니다. 결과는 모든 평가 메트릭에서 기존 최신 방법에 비해 일관된 개선을 보여줍니다.',
      metrics: [
        { label: '정확도', value: '94.7%' },
        { label: 'F1 점수', value: '0.89' },
        { label: '학습 속도 향상', value: '30%' },
      ],
    },
    conclusion: {
      title: '결론',
      content: [
        '이 연구에서 우리는 기존 방법의 주요 한계를 해결하는 딥러닝에 대한 새로운 접근 방식을 제시했습니다. 우리의 실험 결과는 제안된 기술의 효과를 입증하며, 계산 효율성을 유지하면서 최신 성능을 달성했습니다.',
        '향후 연구에서는 이러한 기술을 다른 분야에 적용하고 성능과 효율성을 더욱 향상시킬 수 있는 잠재적 개선 사항을 조사할 것입니다.',
      ],
    },
  };

  return (
    <div 
      className="bg-gradient-to-b from-white to-gray-50 min-h-full"
      onMouseUp={handleTextSelection}
    >
      <div className="max-w-4xl mx-auto px-8 py-12">
        {/* Floating Toolbar for Highlighting */}
        {showHighlightToolbar && (
          <div
            className="fixed z-50 bg-white rounded-lg shadow-2xl border border-gray-200 p-2 flex items-center gap-1"
            style={{
              left: `${toolbarPosition.x}px`,
              top: `${toolbarPosition.y}px`,
              transform: 'translateX(-50%)'
            }}
          >
            {colorButtons.map((btn) => (
              <button
                key={btn.color}
                onClick={() => addHighlight(btn.color)}
                className={`w-8 h-8 ${btn.bg} rounded transition-all flex items-center justify-center text-white`}
                title={`Highlight in ${btn.color}`}
              >
                <Highlighter className="w-4 h-4" />
              </button>
            ))}
            <div className="w-px h-6 bg-gray-300 mx-1"></div>
            <button
              onClick={() => setShowHighlightToolbar(false)}
              className="w-8 h-8 hover:bg-gray-100 rounded transition-colors flex items-center justify-center"
              title="Cancel"
            >
              ✕
            </button>
          </div>
        )}

        {/* Floating Action Buttons */}
        <div className="fixed bottom-8 right-8 z-10 flex flex-col gap-3">
          {/* Keyword Tags Button */}
          <button
            onClick={() => setShowTagModal(true)}
            className="flex items-center gap-2 px-5 py-3 bg-purple-600 text-white rounded-full shadow-lg hover:bg-purple-700 transition-all hover:shadow-xl"
          >
            <Hash className="w-5 h-5" />
            <span className="font-medium">{isTranslated ? '키워드 태그' : 'Tags'}</span>
          </button>
          
          {/* Translation Button */}
          <button
            onClick={handleTranslate}
            disabled={isTranslating}
            className="flex items-center gap-2 px-5 py-3 bg-indigo-600 text-white rounded-full shadow-lg hover:bg-indigo-700 transition-all hover:shadow-xl disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Languages className="w-5 h-5" />
            <span className="font-medium">
              {isTranslating ? '번역 중...' : isTranslated ? '원문 보기' : '한국어로 번역'}
            </span>
          </button>

          {/* Language Toggle */}
          <button
            onClick={() => setLanguage(language === 'en' ? 'ko' : 'en')}
            className="flex items-center gap-2 px-5 py-3 bg-gray-600 text-white rounded-full shadow-lg hover:bg-gray-700 transition-all hover:shadow-xl"
          >
            <span className="font-medium">{language === 'en' ? '🇰🇷 한국어' : '🇬🇧 English'}</span>
          </button>
        </div>

        {/* Header */}
        <article className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
          {/* Hero Image */}
          <div className="h-80 bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 relative overflow-hidden">
            <div className="absolute inset-0 bg-black/20"></div>
            <div className="absolute bottom-0 left-0 right-0 p-8 bg-gradient-to-t from-black/60 to-transparent">
              <div className="flex flex-wrap gap-2 mb-4">
                {pdf.subject.map(subject => (
                  <span key={subject} className="px-3 py-1 bg-white/20 backdrop-blur-sm text-white rounded-full text-sm">
                    {subject}
                  </span>
                ))}
              </div>
              <h1 className="text-4xl font-bold text-white mb-3">
                {isTranslated ? translations.title : pdf.title}
              </h1>
              <div className="flex items-center gap-6 text-white/90 text-sm">
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4" />
                  <span>{pdf.conference} {pdf.year}</span>
                </div>
                <div className="flex items-center gap-2">
                  <TrendingUp className="w-4 h-4" />
                  <span>{pdf.citations} citations</span>
                </div>
              </div>
            </div>
          </div>

          {/* Content */}
          <div className="p-8">
            {/* Authors */}
            <div className="mb-8 pb-6 border-b border-gray-200">
              <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
                {isTranslated ? '저자' : 'Authors'}
              </h3>
              <div className="flex flex-wrap gap-3">
                {pdf.authors.map((author, idx) => (
                  <div key={idx} className="flex items-center gap-2 px-3 py-2 bg-gray-50 rounded-lg">
                    <div className="w-8 h-8 bg-gradient-to-br from-indigo-400 to-purple-400 rounded-full flex items-center justify-center text-white text-sm font-medium">
                      {author.split(' ').map(n => n[0]).join('')}
                    </div>
                    <span className="text-sm text-gray-700">{author}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Abstract */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? '초록' : 'Abstract'}
              </h2>
              <p className="text-lg text-gray-700 leading-relaxed">
                {isTranslated ? translations.abstract : pdf.abstract}
              </p>
            </div>

            {/* Introduction */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.introduction.title : 'Introduction'}
              </h2>
              <div className="prose prose-lg max-w-none">
                {isTranslated ? (
                  <>
                    {translations.introduction.content.map((para, idx) => (
                      <p key={idx} className="text-gray-700 leading-relaxed mb-4">
                        {para}
                      </p>
                    ))}
                  </>
                ) : (
                  <>
                    <p className="text-gray-700 leading-relaxed mb-4">
                      <HighlightedText>
                        Machine learning has revolutionized the field of computer science, enabling computers to learn from data without being explicitly programmed. This groundbreaking paper explores advanced techniques in deep learning, with a particular focus on novel architectures and optimization methods that have emerged in recent years.
                      </HighlightedText>
                    </p>
                    <p className="text-gray-700 leading-relaxed mb-4">
                      <HighlightedText>
                        The rapid advancement of neural network architectures has led to breakthrough performance across various domains including computer vision, natural language processing, and reinforcement learning. Our work builds upon these foundations while introducing innovative approaches to address current limitations in the field.
                      </HighlightedText>
                    </p>
                  </>
                )}
                
                {/* Featured Image */}
                <div className="my-8 rounded-xl overflow-hidden">
                  <img 
                    src={pdf.thumbnailUrl} 
                    alt="Research visualization"
                    className="w-full h-64 object-cover"
                  />
                  <p className="text-sm text-gray-500 text-center mt-2 italic">
                    {isTranslated ? '그림 1: 제안된 아키텍처 개요' : 'Figure 1: Overview of the proposed architecture'}
                  </p>
                </div>
              </div>
            </div>

            {/* Key Contributions */}
            <div className="mb-8 bg-indigo-50 rounded-xl p-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.keyContributions.title : 'Key Contributions'}
              </h2>
              <ul className="space-y-3">
                {(isTranslated ? translations.keyContributions.items : [
                  'A novel attention mechanism that improves performance by 15% over baseline models',
                  'An adaptive learning rate scheduler that reduces training time by 30%',
                  'Comprehensive evaluation on multiple benchmark datasets demonstrating state-of-the-art results',
                ]).map((item, idx) => (
                  <li key={idx} className="flex items-start gap-3">
                    <div className="w-6 h-6 bg-indigo-600 text-white rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 text-sm">
                      {idx + 1}
                    </div>
                    <p className="text-gray-700">{item}</p>
                  </li>
                ))}
              </ul>
            </div>

            {/* Related Work */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.relatedWork.title : 'Related Work'}
              </h2>
              {isTranslated ? (
                <>
                  {translations.relatedWork.content.map((para, idx) => (
                    <p key={idx} className="text-gray-700 leading-relaxed mb-4">
                      {para}
                    </p>
                  ))}
                </>
              ) : (
                <>
                  <p className="text-gray-700 leading-relaxed mb-4">
                    <HighlightedText>
                      Previous research in this domain has established several key principles that form the foundation of modern deep learning. The transformer architecture, introduced by Vaswani et al. in their seminal 2017 paper, demonstrated the remarkable power of attention mechanisms in capturing long-range dependencies.
                    </HighlightedText>
                  </p>
                  <p className="text-gray-700 leading-relaxed mb-4">
                    <HighlightedText>
                      Subsequently, models like BERT and GPT have shown remarkable success in transfer learning scenarios, achieving state-of-the-art results across a wide range of natural language processing tasks. These breakthroughs have inspired numerous follow-up works exploring various aspects of attention-based models.
                    </HighlightedText>
                  </p>
                </>
              )}
            </div>

            {/* Methodology */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.methodology.title : 'Methodology'}
              </h2>
              <p className="text-gray-700 leading-relaxed mb-6">
                {isTranslated ? translations.methodology.intro : 'Our proposed method consists of three main components that work synergistically to achieve superior performance:'}
              </p>

              <div className="grid md:grid-cols-3 gap-4 mb-6">
                {(isTranslated ? translations.methodology.components : [
                  { title: 'Novel Attention', desc: 'Enhanced multi-head attention with integrated positional encoding' },
                  { title: 'Adaptive Scheduler', desc: 'Dynamic learning rate adjustment based on validation metrics' },
                  { title: 'Regularization', desc: 'Specialized techniques to prevent overfitting in deep networks' },
                ]).map((component, idx) => (
                  <div key={idx} className={`rounded-xl p-5 ${
                    idx === 0 ? 'bg-gradient-to-br from-blue-50 to-indigo-50' :
                    idx === 1 ? 'bg-gradient-to-br from-purple-50 to-pink-50' :
                    'bg-gradient-to-br from-green-50 to-teal-50'
                  }`}>
                    <h3 className="font-semibold text-gray-900 mb-2">{component.title}</h3>
                    <p className="text-sm text-gray-600">{component.desc}</p>
                  </div>
                ))}
              </div>

              <p className="text-gray-700 leading-relaxed">
                {isTranslated ? translations.methodology.conclusion : 
                  'The attention mechanism improves upon standard multi-head attention by incorporating positional encoding directly into the attention weights, allowing the model to better capture long-range dependencies in sequential data while maintaining computational efficiency.'}
              </p>
            </div>

            {/* Results */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.results.title : 'Experimental Results'}
              </h2>
              <p className="text-gray-700 leading-relaxed mb-6">
                {isTranslated ? translations.results.intro :
                  'We conducted extensive experiments on multiple benchmark datasets to validate the effectiveness of our approach. The results demonstrate consistent improvements over existing state-of-the-art methods across all evaluation metrics.'}
              </p>
              
              <div className="bg-gray-50 rounded-xl p-6 mb-6">
                <div className="grid grid-cols-3 gap-4 text-center">
                  {(isTranslated ? translations.results.metrics : [
                    { label: 'Accuracy', value: '94.7%' },
                    { label: 'F1 Score', value: '0.89' },
                    { label: 'Faster Training', value: '30%' },
                  ]).map((metric, idx) => (
                    <div key={idx}>
                      <div className={`text-3xl font-bold mb-1 ${
                        idx === 0 ? 'text-indigo-600' :
                        idx === 1 ? 'text-purple-600' :
                        'text-pink-600'
                      }`}>
                        {metric.value}
                      </div>
                      <div className="text-sm text-gray-600">{metric.label}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Conclusion */}
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {isTranslated ? translations.conclusion.title : 'Conclusion'}
              </h2>
              {isTranslated ? (
                <>
                  {translations.conclusion.content.map((para, idx) => (
                    <p key={idx} className="text-gray-700 leading-relaxed mb-4">
                      {para}
                    </p>
                  ))}
                </>
              ) : (
                <>
                  <p className="text-gray-700 leading-relaxed mb-4">
                    In this work, we have presented a novel approach to deep learning that addresses key limitations of 
                    existing methods. Our experimental results demonstrate the effectiveness of the proposed techniques, 
                    achieving state-of-the-art performance while maintaining computational efficiency.
                  </p>
                  <p className="text-gray-700 leading-relaxed">
                    Future work will explore the application of these techniques to other domains and investigate potential 
                    improvements to further enhance performance and efficiency.
                  </p>
                </>
              )}
            </div>

            {/* Footer */}
            <div className="pt-6 border-t border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <Tag className="w-4 h-4" />
                  <span>DOI: {pdf.doi}</span>
                </div>
                <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
                  <ExternalLink className="w-4 h-4" />
                  {isTranslated ? '원본 PDF 보기' : 'View Original PDF'}
                </button>
              </div>
            </div>
          </div>
        </article>

        {/* Related Papers */}
        <div className="mt-8 bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          <h3 className="text-xl font-bold text-gray-900 mb-4">
            {isTranslated ? '관련 논문' : 'Related Papers'}
          </h3>
          <div className="space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer">
                <h4 className="font-medium text-gray-900 mb-1">
                  {isTranslated 
                    ? `주목이 전부다 - 관련 연구 논문 ${i}`
                    : `Attention Is All You Need - Related Research Paper ${i}`
                  }
                </h4>
                <p className="text-sm text-gray-600">Smith et al. • NeurIPS 2023 • 1,234 citations</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Tag Modal */}
      <TagModal
        isOpen={showTagModal}
        onClose={() => setShowTagModal(false)}
        currentPdf={pdf}
        onPaperClick={(paperId) => {
          console.log('Open paper:', paperId);
          // Handle opening paper by ID
        }}
        language={language}
      />
    </div>
  );
}