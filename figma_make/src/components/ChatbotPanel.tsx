import { useState, useRef, useEffect } from 'react';
import { X, Send, Bot, User } from 'lucide-react';
import { PdfFile } from '../App';

interface ChatbotPanelProps {
  pdf: PdfFile | null;
  onClose: () => void;
}

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

export function ChatbotPanel({ pdf, onClose }: ChatbotPanelProps) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      role: 'assistant',
      content: pdf 
        ? `Hello! I'm here to help you understand "${pdf.title}". You can ask me about the methodology, results, key findings, or any specific section of the paper.`
        : `Hello! I'm your Research Assistant. Open a paper to start asking questions about it, or ask me general research questions.`,
      timestamp: new Date()
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const generateResponse = (userMessage: string): string => {
    const lowerMessage = userMessage.toLowerCase();
    
    if (!pdf) {
      // General research assistant responses when no paper is selected
      if (lowerMessage.includes('help') || lowerMessage.includes('what can you do')) {
        return `I'm your Research Assistant! I can help you with:\n\n• Understanding academic papers\n• Explaining methodologies and results\n• Comparing different approaches\n• Finding related work\n• Summarizing key findings\n\nOpen a paper to get started, or ask me general research questions!`;
      }
      return `I'm ready to help! To provide specific insights about a paper, please open one from the library. I can then help you understand the methodology, results, and key contributions in detail.`;
    }
    
    if (lowerMessage.includes('abstract') || lowerMessage.includes('summary')) {
      return `The paper's abstract explains: ${pdf.abstract}\n\nThe main contribution focuses on advancing the field through novel techniques and comprehensive evaluation.`;
    } else if (lowerMessage.includes('method') || lowerMessage.includes('approach')) {
      return `The methodology consists of three main components:\n\n1. A novel attention mechanism that improves performance\n2. An adaptive learning rate scheduler\n3. Specialized regularization techniques\n\nThese components work together to achieve state-of-the-art results while maintaining computational efficiency.`;
    } else if (lowerMessage.includes('result') || lowerMessage.includes('performance')) {
      return `The experimental results are impressive:\n\n• 94.7% accuracy on benchmark datasets\n• 15% improvement over baseline models\n• 30% reduction in training time\n• Consistent performance across multiple evaluation metrics\n\nThe paper demonstrates state-of-the-art results across all tested scenarios.`;
    } else if (lowerMessage.includes('author')) {
      return `The authors of this paper are: ${pdf.authors.join(', ')}. This work was presented at ${pdf.conference} in ${pdf.year} and has received ${pdf.citations} citations, indicating significant impact in the research community.`;
    } else if (lowerMessage.includes('citation') || lowerMessage.includes('reference')) {
      return `This paper has been cited ${pdf.citations} times since publication. It references several foundational works including the transformer architecture and BERT model. The DOI is ${pdf.doi}.`;
    } else if (lowerMessage.includes('limitation') || lowerMessage.includes('future')) {
      return `The paper acknowledges some limitations and suggests future work:\n\n• Exploring applications to other domains\n• Further optimization of the attention mechanism\n• Investigation of model interpretability\n• Extension to multi-modal learning scenarios\n\nThese directions could lead to even more significant improvements.`;
    } else {
      return `That's an interesting question about "${pdf.title}". Based on the paper's content, the authors focus on ${pdf.subject.join(', ')}. The work was presented at ${pdf.conference} ${pdf.year}. Could you be more specific about what aspect you'd like to explore?`;
    }
  };

  const handleSend = () => {
    if (!input.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: input,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsTyping(true);

    // Simulate AI response delay
    setTimeout(() => {
      const response = generateResponse(input);
      const assistantMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response,
        timestamp: new Date()
      };
      setMessages(prev => [...prev, assistantMessage]);
      setIsTyping(false);
    }, 1000);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const suggestedQuestions = [
    "What is the main contribution?",
    "Explain the methodology",
    "What are the key results?",
    "What are the limitations?"
  ];

  return (
    <div className="fixed right-0 top-0 bottom-0 w-96 z-30 bg-white border-l border-gray-200 shadow-2xl flex flex-col">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 bg-gradient-to-r from-indigo-600 to-purple-600">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white rounded-full flex items-center justify-center">
              <Bot className="w-6 h-6 text-indigo-600" />
            </div>
            <div>
              <h3 className="font-semibold text-white">Research Assistant</h3>
              <p className="text-xs text-indigo-100">{pdf ? `Discussing: ${pdf.title.slice(0, 40)}...` : 'Ask me anything'}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white text-sm font-semibold hover:text-gray-100"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4 bg-gray-50">
        {messages.map(message => (
          <div
            key={message.id}
            className={`flex gap-3 ${message.role === 'user' ? 'flex-row-reverse' : ''}`}
          >
            <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
              message.role === 'user' 
                ? 'bg-indigo-600' 
                : 'bg-white border-2 border-indigo-200'
            }`}>
              {message.role === 'user' ? (
                <User className="w-4 h-4 text-white" />
              ) : (
                <Bot className="w-5 h-5 text-indigo-600" />
              )}
            </div>
            <div className={`flex-1 ${message.role === 'user' ? 'flex justify-end' : ''}`}>
              <div className={`inline-block px-4 py-3 rounded-2xl max-w-[85%] ${
                message.role === 'user'
                  ? 'bg-indigo-600 text-white'
                  : 'bg-white text-gray-800 border border-gray-200'
              }`}>
                <p className="text-sm leading-relaxed whitespace-pre-line">{message.content}</p>
                <p className={`text-xs mt-1 ${
                  message.role === 'user' ? 'text-indigo-200' : 'text-gray-400'
                }`}>
                  {message.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </p>
              </div>
            </div>
          </div>
        ))}

        {isTyping && (
          <div className="flex gap-3">
            <div className="w-8 h-8 rounded-full bg-white border-2 border-indigo-200 flex items-center justify-center">
              <Bot className="w-5 h-5 text-indigo-600" />
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl px-4 py-3">
              <div className="flex gap-1">
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></div>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggested Questions */}
      {messages.length === 1 && (
        <div className="px-6 py-3 bg-white border-t border-gray-200">
          <p className="text-xs text-gray-500 mb-2">Suggested questions:</p>
          <div className="space-y-2">
            {suggestedQuestions.map((question, idx) => (
              <button
                key={idx}
                onClick={() => setInput(question)}
                className="w-full text-left px-3 py-2 bg-gray-50 hover:bg-gray-100 rounded-lg text-xs text-gray-700 transition-colors"
              >
                {question}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Input */}
      <div className="p-4 bg-white border-t border-gray-200">
        <div className="flex gap-2">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Ask a question..."
            rows={2}
            className="flex-1 px-4 py-3 border border-gray-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none text-sm"
          />
          <button
            onClick={handleSend}
            disabled={!input.trim()}
            className="px-4 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center"
          >
            <Send className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
}