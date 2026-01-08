import { X, Copy, ExternalLink, Code, Info } from 'lucide-react';
import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';

interface MathEquationModalProps {
  equation: string;
  latex: string;
  onClose: () => void;
}

export function MathEquationModal({ equation, latex, onClose }: MathEquationModalProps) {
  const [activeTab, setActiveTab] = useState<'rendered' | 'latex' | 'explain'>('rendered');
  const [copied, setCopied] = useState(false);

  const handleCopyLatex = () => {
    navigator.clipboard.writeText(latex);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleWolframAlpha = () => {
    const query = encodeURIComponent(latex);
    window.open(`https://www.wolframalpha.com/input?i=${query}`, '_blank');
  };

  // Mock explanation - in real app, this would use AI
  const explanation = `This equation represents a mathematical relationship commonly used in ${
    latex.includes('sum') ? 'series and summations' :
    latex.includes('int') ? 'calculus and integration' :
    latex.includes('frac') ? 'fractional calculations' :
    'mathematical modeling'
  }. Each variable and symbol serves a specific purpose in expressing this relationship.`;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.9 }}
          className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-hidden"
        >
          {/* Header */}
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between bg-gradient-to-r from-indigo-600 to-purple-600">
            <h3 className="text-lg font-semibold text-white">Equation Viewer</h3>
            <button
              onClick={onClose}
              className="p-1.5 hover:bg-white/20 rounded-lg transition-colors text-white"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Tabs */}
          <div className="px-6 pt-4 border-b border-gray-200">
            <div className="flex gap-2">
              <button
                onClick={() => setActiveTab('rendered')}
                className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
                  activeTab === 'rendered'
                    ? 'bg-white text-indigo-600 border-b-2 border-indigo-600'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                Rendered
              </button>
              <button
                onClick={() => setActiveTab('latex')}
                className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
                  activeTab === 'latex'
                    ? 'bg-white text-indigo-600 border-b-2 border-indigo-600'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                <Code className="w-4 h-4 inline mr-1" />
                LaTeX
              </button>
              <button
                onClick={() => setActiveTab('explain')}
                className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
                  activeTab === 'explain'
                    ? 'bg-white text-indigo-600 border-b-2 border-indigo-600'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                <Info className="w-4 h-4 inline mr-1" />
                Explain
              </button>
            </div>
          </div>

          {/* Content */}
          <div className="p-6 overflow-y-auto max-h-[60vh]">
            {activeTab === 'rendered' && (
              <div className="space-y-4">
                <div className="bg-gray-50 rounded-xl p-8 flex items-center justify-center min-h-[200px]">
                  <div className="text-2xl font-serif text-gray-900">
                    {equation}
                  </div>
                </div>
                
                <div className="flex gap-2">
                  <button
                    onClick={handleWolframAlpha}
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-orange-500 text-white rounded-lg hover:bg-orange-600 transition-colors"
                  >
                    <ExternalLink className="w-4 h-4" />
                    Open in Wolfram Alpha
                  </button>
                  <button
                    onClick={handleCopyLatex}
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    <Copy className="w-4 h-4" />
                    {copied ? 'Copied!' : 'Copy LaTeX'}
                  </button>
                </div>
              </div>
            )}

            {activeTab === 'latex' && (
              <div className="space-y-4">
                <div className="bg-gray-900 rounded-xl p-4 overflow-x-auto">
                  <code className="text-green-400 font-mono text-sm whitespace-pre">
                    {latex}
                  </code>
                </div>
                
                <div className="space-y-2">
                  <h4 className="font-semibold text-gray-900">Common LaTeX Commands</h4>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    <div className="p-2 bg-gray-50 rounded">
                      <code className="text-indigo-600">\frac{'{'}a{'}'}{'{'}b{'}'}</code> - Fraction
                    </div>
                    <div className="p-2 bg-gray-50 rounded">
                      <code className="text-indigo-600">\sum_{'{'}i=1{'}'}^n</code> - Summation
                    </div>
                    <div className="p-2 bg-gray-50 rounded">
                      <code className="text-indigo-600">\int_{'{'}a{'}'}^b</code> - Integral
                    </div>
                    <div className="p-2 bg-gray-50 rounded">
                      <code className="text-indigo-600">\sqrt{'{'}x{'}'}</code> - Square root
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'explain' && (
              <div className="space-y-4">
                <div className="bg-blue-50 rounded-xl p-4 border border-blue-200">
                  <h4 className="font-semibold text-blue-900 mb-2">AI Explanation</h4>
                  <p className="text-blue-800 text-sm leading-relaxed">
                    {explanation}
                  </p>
                </div>

                <div className="space-y-3">
                  <h4 className="font-semibold text-gray-900">Components Breakdown</h4>
                  <div className="space-y-2 text-sm">
                    {latex.includes('sum') && (
                      <div className="p-3 bg-purple-50 rounded-lg">
                        <span className="font-mono text-purple-700">∑</span>
                        <span className="ml-2 text-gray-700">Summation operator - adds up a series of terms</span>
                      </div>
                    )}
                    {latex.includes('int') && (
                      <div className="p-3 bg-green-50 rounded-lg">
                        <span className="font-mono text-green-700">∫</span>
                        <span className="ml-2 text-gray-700">Integral - calculates area under curve</span>
                      </div>
                    )}
                    {latex.includes('frac') && (
                      <div className="p-3 bg-orange-50 rounded-lg">
                        <span className="font-mono text-orange-700">÷</span>
                        <span className="ml-2 text-gray-700">Fraction - represents division</span>
                      </div>
                    )}
                    {latex.includes('alpha') && (
                      <div className="p-3 bg-indigo-50 rounded-lg">
                        <span className="font-mono text-indigo-700">α</span>
                        <span className="ml-2 text-gray-700">Alpha - commonly used for angles or coefficients</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="p-4 bg-gradient-to-r from-indigo-50 to-purple-50 rounded-lg border border-indigo-200">
                  <p className="text-sm text-gray-700">
                    💡 <strong>Tip:</strong> Use Wolfram Alpha for step-by-step solutions and graphical representations of this equation.
                  </p>
                </div>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
