import { useState, useEffect } from 'react';
import { ArrowLeft, Maximize2, Minimize2, Search } from 'lucide-react';
import { PdfFile } from '../App';
import { mockPdfData } from '../data/mockPdfData';
import { motion } from 'motion/react';

interface KnowledgeMapProps {
  selectedPdf: PdfFile | null;
  onBack: () => void;
}

interface Node {
  id: string;
  label: string;
  type: 'paper' | 'author' | 'conference' | 'subject' | 'concept';
  x: number;
  y: number;
  connections: string[];
  data?: PdfFile;
}

export function KnowledgeMap({ selectedPdf, onBack }: KnowledgeMapProps) {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [hoveredNode, setHoveredNode] = useState<Node | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    generateKnowledgeMap();
  }, [selectedPdf]);

  const generateKnowledgeMap = () => {
    const centerX = 600;
    const centerY = 400;
    const radius = 250;
    
    const newNodes: Node[] = [];

    // Center node - selected paper or a central concept
    if (selectedPdf) {
      newNodes.push({
        id: selectedPdf.id,
        label: selectedPdf.title.slice(0, 50) + '...',
        type: 'paper',
        x: centerX,
        y: centerY,
        connections: [],
        data: selectedPdf
      });
    } else {
      newNodes.push({
        id: 'center',
        label: 'Machine Learning Research',
        type: 'concept',
        x: centerX,
        y: centerY,
        connections: []
      });
    }

    // Add related papers
    const relatedPapers = mockPdfData.slice(0, 5);
    relatedPapers.forEach((paper, idx) => {
      const angle = (idx / relatedPapers.length) * 2 * Math.PI;
      const node: Node = {
        id: paper.id,
        label: paper.title.slice(0, 40) + '...',
        type: 'paper',
        x: centerX + Math.cos(angle) * radius,
        y: centerY + Math.sin(angle) * radius,
        connections: [selectedPdf?.id || 'center'],
        data: paper
      };
      newNodes.push(node);
    });

    // Add subject nodes
    const subjects = selectedPdf 
      ? selectedPdf.subject 
      : ['Deep Learning', 'Computer Vision', 'NLP'];
    
    subjects.forEach((subject, idx) => {
      const angle = (idx / subjects.length) * 2 * Math.PI + Math.PI / 4;
      const subjectRadius = radius * 1.5;
      newNodes.push({
        id: `subject-${idx}`,
        label: subject,
        type: 'subject',
        x: centerX + Math.cos(angle) * subjectRadius,
        y: centerY + Math.sin(angle) * subjectRadius,
        connections: [selectedPdf?.id || 'center']
      });
    });

    // Add author nodes
    const authors = selectedPdf 
      ? selectedPdf.authors.slice(0, 3)
      : ['John Smith', 'Jane Doe', 'Alex Johnson'];
    
    authors.forEach((author, idx) => {
      const angle = (idx / authors.length) * 2 * Math.PI - Math.PI / 4;
      const authorRadius = radius * 1.3;
      newNodes.push({
        id: `author-${idx}`,
        label: author,
        type: 'author',
        x: centerX + Math.cos(angle) * authorRadius,
        y: centerY + Math.sin(angle) * authorRadius,
        connections: [selectedPdf?.id || 'center']
      });
    });

    setNodes(newNodes);
  };

  const getNodeColor = (type: string) => {
    switch (type) {
      case 'paper': return 'from-indigo-500 to-purple-500';
      case 'author': return 'from-blue-500 to-cyan-500';
      case 'conference': return 'from-green-500 to-teal-500';
      case 'subject': return 'from-orange-500 to-pink-500';
      case 'concept': return 'from-purple-600 to-indigo-600';
      default: return 'from-gray-500 to-gray-600';
    }
  };

  const getNodeSize = (type: string, isCenter: boolean) => {
    if (isCenter) return 80;
    switch (type) {
      case 'paper': return 60;
      case 'author': return 50;
      case 'subject': return 55;
      default: return 45;
    }
  };

  return (
    <div className={`${isFullscreen ? 'fixed inset-0 z-50' : 'h-full'} flex flex-col bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900`}>
      {/* Header */}
      <div className="backdrop-blur-xl bg-white/10 border-b border-white/20">
        <div className="px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <button
              onClick={onBack}
              className="flex items-center gap-2 px-3 py-2 text-white hover:bg-white/10 rounded-lg transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              Back
            </button>
            
            <div className="flex items-center gap-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/60" />
                <input
                  type="text"
                  placeholder="Search in knowledge map..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 pr-4 py-2 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/60 focus:outline-none focus:ring-2 focus:ring-purple-500 text-sm w-64"
                />
              </div>
              
              <button
                onClick={() => setIsFullscreen(!isFullscreen)}
                className="p-2 hover:bg-white/10 rounded-lg transition-colors text-white"
              >
                {isFullscreen ? <Minimize2 className="w-5 h-5" /> : <Maximize2 className="w-5 h-5" />}
              </button>
            </div>
          </div>

          <div>
            <h2 className="text-2xl font-semibold text-white mb-1">Knowledge Map</h2>
            <p className="text-sm text-white/70">
              {selectedPdf ? `Exploring connections for: ${selectedPdf.title}` : 'Explore research connections'}
            </p>
          </div>
        </div>
      </div>

      {/* Map Container */}
      <div className="flex-1 relative overflow-hidden">
        <svg className="w-full h-full">
          {/* Connections */}
          <g>
            {nodes.map(node =>
              node.connections.map((targetId, idx) => {
                const target = nodes.find(n => n.id === targetId);
                if (!target) return null;
                
                const isHighlighted = hoveredNode?.id === node.id || hoveredNode?.id === targetId;
                
                return (
                  <line
                    key={`${node.id}-${targetId}-${idx}`}
                    x1={node.x}
                    y1={node.y}
                    x2={target.x}
                    y2={target.y}
                    stroke={isHighlighted ? '#a78bfa' : '#ffffff40'}
                    strokeWidth={isHighlighted ? 2 : 1}
                    strokeDasharray={node.type === 'subject' ? '5,5' : '0'}
                    className="transition-all duration-300"
                  />
                );
              })
            )}
          </g>

          {/* Nodes */}
          <g>
            {nodes.map((node, idx) => {
              const isCenter = idx === 0;
              const size = getNodeSize(node.type, isCenter);
              const isSelected = selectedNode?.id === node.id;
              const isHovered = hoveredNode?.id === node.id;

              return (
                <g
                  key={node.id}
                  transform={`translate(${node.x}, ${node.y})`}
                  onMouseEnter={() => setHoveredNode(node)}
                  onMouseLeave={() => setHoveredNode(null)}
                  onClick={() => setSelectedNode(node)}
                  className="cursor-pointer"
                >
                  {/* Outer glow for selected/hovered */}
                  {(isSelected || isHovered) && (
                    <circle
                      r={size / 2 + 10}
                      fill="url(#glow)"
                      opacity="0.5"
                      className="animate-pulse"
                    />
                  )}
                  
                  {/* Node circle */}
                  <circle
                    r={size / 2}
                    fill={`url(#gradient-${node.type})`}
                    stroke="#ffffff"
                    strokeWidth={isSelected ? 3 : isHovered ? 2 : 1}
                    className="transition-all duration-200"
                  />
                  
                  {/* Node icon/text */}
                  <text
                    textAnchor="middle"
                    dy=".3em"
                    fill="white"
                    fontSize={isCenter ? "14" : "12"}
                    fontWeight="600"
                  >
                    {node.type === 'paper' ? '📄' : 
                     node.type === 'author' ? '👤' :
                     node.type === 'subject' ? '🏷️' :
                     node.type === 'conference' ? '🎓' : '💡'}
                  </text>
                  
                  {/* Label */}
                  <text
                    y={size / 2 + 20}
                    textAnchor="middle"
                    fill="white"
                    fontSize="11"
                    className="pointer-events-none"
                  >
                    {node.label.length > 30 ? node.label.slice(0, 30) + '...' : node.label}
                  </text>
                </g>
              );
            })}
          </g>

          {/* Gradients */}
          <defs>
            <radialGradient id="glow">
              <stop offset="0%" stopColor="#a78bfa" stopOpacity="0.8" />
              <stop offset="100%" stopColor="#a78bfa" stopOpacity="0" />
            </radialGradient>
            {['paper', 'author', 'conference', 'subject', 'concept'].map(type => (
              <linearGradient key={type} id={`gradient-${type}`} x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor={
                  type === 'paper' ? '#6366f1' :
                  type === 'author' ? '#3b82f6' :
                  type === 'conference' ? '#10b981' :
                  type === 'subject' ? '#f97316' : '#9333ea'
                } />
                <stop offset="100%" stopColor={
                  type === 'paper' ? '#9333ea' :
                  type === 'author' ? '#06b6d4' :
                  type === 'conference' ? '#14b8a6' :
                  type === 'subject' ? '#ec4899' : '#6366f1'
                } />
              </linearGradient>
            ))}
          </defs>
        </svg>

        {/* Legend */}
        <div className="absolute top-6 left-6 bg-white/10 backdrop-blur-xl rounded-xl p-4 border border-white/20">
          <h4 className="text-white font-semibold mb-3 text-sm">Legend</h4>
          <div className="space-y-2 text-xs">
            <div className="flex items-center gap-2 text-white/80">
              <div className="w-4 h-4 bg-gradient-to-br from-indigo-500 to-purple-500 rounded-full"></div>
              <span>Papers</span>
            </div>
            <div className="flex items-center gap-2 text-white/80">
              <div className="w-4 h-4 bg-gradient-to-br from-blue-500 to-cyan-500 rounded-full"></div>
              <span>Authors</span>
            </div>
            <div className="flex items-center gap-2 text-white/80">
              <div className="w-4 h-4 bg-gradient-to-br from-orange-500 to-pink-500 rounded-full"></div>
              <span>Subjects</span>
            </div>
          </div>
        </div>

        {/* Selected Node Info */}
        {selectedNode && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="absolute bottom-6 right-6 bg-white/10 backdrop-blur-xl rounded-xl p-6 border border-white/20 max-w-md"
          >
            <div className="flex items-start justify-between mb-3">
              <h4 className="text-white font-semibold">{selectedNode.label}</h4>
              <button
                onClick={() => setSelectedNode(null)}
                className="text-white/60 hover:text-white"
              >
                ✕
              </button>
            </div>
            
            {selectedNode.data && (
              <div className="space-y-2 text-sm">
                <p className="text-white/80">{selectedNode.data.abstract.slice(0, 150)}...</p>
                <div className="flex flex-wrap gap-2 mt-3">
                  {selectedNode.data.subject.map(subj => (
                    <span key={subj} className="px-2 py-1 bg-white/20 text-white rounded text-xs">
                      {subj}
                    </span>
                  ))}
                </div>
                <div className="flex items-center justify-between mt-3 text-xs text-white/70">
                  <span>{selectedNode.data.conference} {selectedNode.data.year}</span>
                  <span>{selectedNode.data.citations} citations</span>
                </div>
              </div>
            )}
            
            {selectedNode.type === 'author' && (
              <p className="text-white/80 text-sm">
                Researcher specializing in {selectedPdf?.subject[0] || 'Machine Learning'}
              </p>
            )}
            
            {selectedNode.type === 'subject' && (
              <p className="text-white/80 text-sm">
                Research area with {Math.floor(Math.random() * 500 + 100)} related papers
              </p>
            )}
          </motion.div>
        )}
      </div>
    </div>
  );
}
